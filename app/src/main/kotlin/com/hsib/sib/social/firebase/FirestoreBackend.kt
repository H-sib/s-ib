package com.hsib.sib.social.firebase

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import com.hsib.sib.core.social.ChatRequest
import com.hsib.sib.core.social.InviteInfo
import com.hsib.sib.core.social.MemberInfo
import com.hsib.sib.core.social.MessageInfo
import com.hsib.sib.core.social.Profile
import com.hsib.sib.core.social.Report
import com.hsib.sib.core.social.RoomInfo
import com.hsib.sib.core.social.RoomRole
import com.hsib.sib.core.social.SocialLimits
import com.hsib.sib.social.SocialBackend
import com.hsib.sib.social.SocialError
import com.hsib.sib.social.SocialException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.Date
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The real server: Cloud Firestore, with the permission rules in firebase/firestore.rules deciding who may do what. Every
 * action that changes more than one document is one transaction, which is what those rules expect (they look at the state
 * "after" the write). Everything stored in a sealed field was sealed on the phone before it got here.
 *
 * Not yet run against a real project: nothing here can be proven until the owner connects one. The mapping of documents
 * is tested on its own (FirestoreMappingTest) and the rules have emulator tests (firebase/tests).
 */
internal class FirestoreBackend(private val db: FirebaseFirestore, private val auth: FirebaseAuth) : SocialBackend {
    private val uid: String get() = auth.currentUser?.uid ?: throw SocialException(SocialError.NOT_SIGNED_IN)

    private fun usersRef() = db.collection(Fields.USERS)
    private fun roomsRef() = db.collection(Fields.ROOMS)
    private fun roomRef(id: String) = roomsRef().document(id)
    private fun membersRef(roomId: String) = roomRef(roomId).collection(Fields.MEMBERS)
    private fun messagesRef(roomId: String) = roomRef(roomId).collection(Fields.MESSAGES)
    private fun mirrorRef(person: String, roomId: String) = usersRef().document(person).collection(Fields.ROOMS).document(roomId)

    private val now get() = FieldValue.serverTimestamp()

    // ---- plumbing ----------------------------------------------------------------------------------------

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnCompleteListener { t ->
            if (t.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                c.resume(t.result as T)
            } else {
                c.resumeWithException(t.exception ?: IllegalStateException("task failed"))
            }
        }
    }

    private fun refused(e: FirebaseFirestoreException): SocialError = when (e.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> SocialError.DENIED
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> SocialError.NOT_SIGNED_IN
        FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> SocialError.OFFLINE
        FirebaseFirestoreException.Code.NOT_FOUND -> SocialError.NOT_FOUND
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> SocialError.RATE_LIMITED
        else -> SocialError.FAILED
    }

    /** Runs a server call with a time limit and turns every failure into a reason that can be shown. */
    private suspend fun <T> call(block: suspend () -> T): T = try {
        withTimeout(TIMEOUT_MS) { block() }
    } catch (e: TimeoutCancellationException) {
        throw SocialException(SocialError.OFFLINE)
    } catch (e: CancellationException) {
        throw e
    } catch (e: SocialException) {
        throw e
    } catch (e: FirebaseFirestoreException) {
        throw SocialException(refused(e))
    } catch (e: FirebaseNetworkException) {
        throw SocialException(SocialError.OFFLINE)
    } catch (e: Exception) {
        throw SocialException(SocialError.FAILED)
    }

    private suspend fun transaction(body: (Transaction) -> Unit) {
        db.runTransaction(Transaction.Function { tx -> body(tx); null }).await()
    }

    private suspend fun DocumentReference.data(): Map<String, Any?>? = get(Source.SERVER).await().takeIf { it.exists() }?.data

    private suspend fun Query.snapshot() = get(Source.SERVER).await()

    private fun stamp(ms: Long) = Timestamp(Date(ms))

    private suspend fun roomCount(): Int = usersRef().document(uid).collection(Fields.ROOMS).snapshot().size()

    // ---- profiles ----------------------------------------------------------------------------------------

    override suspend fun saveProfile(profile: Profile) = call {
        if (profile.uid != uid) throw SocialException(SocialError.DENIED)
        usersRef().document(uid).set(FirestoreMapping.profileData(profile)).await()
        Unit
    }

    override suspend fun recordConsent(version: Int, app: String) = call {
        val ref = usersRef().document(uid).collection(Fields.CONSENT).document("rules-$version")
        try {
            ref.set(mapOf(Fields.VERSION to version, Fields.ACCEPTED_AT to FieldValue.serverTimestamp(), Fields.APP to app)).await()
        } catch (e: FirebaseFirestoreException) {
            // The rules allow this record to be created once and never changed, so a denial means it already exists.
            if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
        }
        Unit
    }

    override suspend fun profiles(uids: Set<String>): Map<String, Profile> = call {
        coroutineScope {
            uids.map { person ->
                async { FirestoreMapping.profile(person, usersRef().document(person).data() ?: return@async null) }
            }.awaitAll().filterNotNull().associateBy { it.uid }
        }
    }

    // ---- rooms -------------------------------------------------------------------------------------------

    override suspend fun createRoom(room: RoomInfo, owner: MemberInfo) = call {
        if (room.ownerUid != uid || owner.uid != uid || owner.role != RoomRole.OWNER || owner.roomId != room.id) throw SocialException(SocialError.DENIED)
        if (roomCount() >= SocialLimits.ROOMS_PER_PERSON) throw SocialException(SocialError.TOO_MANY_ROOMS)
        transaction { tx ->
            tx.set(
                roomRef(room.id),
                mapOf(
                    Fields.OWNER_UID to uid, Fields.KEY_VERSION to 1, Fields.REV to 1, Fields.SEALED_PAYLOAD to room.sealedPayload,
                    Fields.MEMBER_COUNT to 1, Fields.CREATED_AT to now,
                ),
            )
            tx.set(
                membersRef(room.id).document(uid),
                mapOf(
                    Fields.ROLE to "OWNER", Fields.DISPLAY_NAME to owner.displayName, Fields.JOINED_AT to now,
                    Fields.WRAPPED_KEY to owner.wrappedKey, Fields.WRAPPED_KEY_VERSION to 1, Fields.LAST_MESSAGE_AT to now,
                ),
            )
            tx.set(mirrorRef(uid, room.id), mapOf(Fields.JOINED_AT to now))
        }
    }

    override suspend fun room(roomId: String): RoomInfo? = call {
        val data = roomRef(roomId).data() ?: return@call null
        FirestoreMapping.room(roomId, data) ?: throw SocialException(SocialError.FAILED)
    }

    override suspend fun updatePayload(roomId: String, rev: Long, sealedPayload: String) = call {
        transaction { tx -> tx.update(roomRef(roomId), mapOf(Fields.REV to rev, Fields.SEALED_PAYLOAD to sealedPayload)) }
    }

    override suspend fun members(roomId: String): List<MemberInfo> = call {
        membersRef(roomId).snapshot().documents.mapNotNull { FirestoreMapping.member(roomId, it.id, it.data ?: return@mapNotNull null) }
    }

    // ---- invites -----------------------------------------------------------------------------------------

    override suspend fun createInvite(invite: InviteInfo) = call {
        transaction { tx ->
            tx.set(
                db.collection(Fields.INVITES).document(invite.id),
                mapOf(
                    Fields.ROOM_ID to invite.roomId, Fields.CREATED_BY to uid, Fields.EXPIRES_AT to stamp(invite.expiresAtMs),
                    Fields.REVOKED to false, Fields.SEALED_KEY to invite.sealedKey, Fields.KEY_VERSION to invite.keyVersion,
                ),
            )
        }
    }

    override suspend fun invite(inviteId: String): InviteInfo? = call {
        val data = db.collection(Fields.INVITES).document(inviteId).data() ?: return@call null
        FirestoreMapping.invite(inviteId, data) ?: throw SocialException(SocialError.FAILED)
    }

    override suspend fun revokeInvite(inviteId: String) = call {
        transaction { tx -> tx.update(db.collection(Fields.INVITES).document(inviteId), Fields.REVOKED, true) }
    }

    override suspend fun join(inviteId: String, member: MemberInfo) = call {
        if (member.uid != uid) throw SocialException(SocialError.DENIED)
        if (roomCount() >= SocialLimits.ROOMS_PER_PERSON) throw SocialException(SocialError.TOO_MANY_ROOMS)
        transaction { tx ->
            tx.set(
                membersRef(member.roomId).document(uid),
                mapOf(
                    Fields.ROLE to "MEMBER", Fields.DISPLAY_NAME to member.displayName, Fields.JOINED_AT to now,
                    Fields.WRAPPED_KEY to member.wrappedKey, Fields.WRAPPED_KEY_VERSION to member.wrappedKeyVersion,
                    Fields.LAST_MESSAGE_AT to now, Fields.INVITE_ID to inviteId,
                ),
            )
            // The count moves by one in the same transaction: the rules allow nothing else from a person who is not the owner.
            tx.update(roomRef(member.roomId), Fields.MEMBER_COUNT, FieldValue.increment(1))
            tx.set(mirrorRef(uid, member.roomId), mapOf(Fields.JOINED_AT to now))
        }
    }

    // ---- people ------------------------------------------------------------------------------------------

    override suspend fun setDone(roomId: String, doneAtMs: Long?) = call {
        transaction { tx -> tx.update(membersRef(roomId).document(uid), Fields.DONE_AT, if (doneAtMs != null) now else null) }
    }

    override suspend fun leave(roomId: String) = call {
        transaction { tx ->
            tx.delete(membersRef(roomId).document(uid))
            tx.update(roomRef(roomId), Fields.MEMBER_COUNT, FieldValue.increment(-1))
            tx.delete(mirrorRef(uid, roomId))
        }
    }

    /** Deletes in batches of 400, messages first, then the other people, then the owner's own record and the room. */
    override suspend fun deleteRoom(roomId: String) = call {
        while (true) {
            val page = messagesRef(roomId).limit(PAGE).snapshot()
            if (page.isEmpty) break
            val batch = db.batch()
            page.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        val everyone = membersRef(roomId).snapshot().documents.map { it.id }
        for (chunk in everyone.filter { it != uid }.chunked(PAGE.toInt() / 2)) {
            val batch = db.batch()
            chunk.forEach { person -> batch.delete(membersRef(roomId).document(person)); batch.delete(mirrorRef(person, roomId)) }
            batch.commit().await()
        }
        transaction { tx ->
            tx.delete(membersRef(roomId).document(uid))
            tx.delete(mirrorRef(uid, roomId))
            tx.delete(roomRef(roomId))
        }
    }

    override suspend fun remove(roomId: String, targetUid: String, newKeyVersion: Int, wrapped: Map<String, String>, sealedPayload: String) = call {
        transaction { tx ->
            tx.update(
                roomRef(roomId),
                mapOf(Fields.KEY_VERSION to newKeyVersion, Fields.MEMBER_COUNT to FieldValue.increment(-1), Fields.SEALED_PAYLOAD to sealedPayload),
            )
            tx.delete(membersRef(roomId).document(targetUid))
            for ((person, key) in wrapped) {
                tx.update(membersRef(roomId).document(person), mapOf(Fields.WRAPPED_KEY to key, Fields.WRAPPED_KEY_VERSION to newKeyVersion))
            }
            tx.delete(mirrorRef(targetUid, roomId))
        }
    }

    // ---- chat --------------------------------------------------------------------------------------------

    override suspend fun messages(roomId: String, afterMs: Long, limit: Int): List<MessageInfo> = call {
        messagesRef(roomId).whereGreaterThan(Fields.CREATED_AT, stamp(afterMs)).orderBy(Fields.CREATED_AT)
            .limit(limit.coerceIn(1, SocialLimits.FETCH_PAGE).toLong()).snapshot().documents
            .mapNotNull { FirestoreMapping.message(roomId, it.id, it.data ?: return@mapNotNull null) }
    }

    override suspend fun send(message: MessageInfo): MessageInfo = call {
        if (message.senderUid != uid) throw SocialException(SocialError.DENIED)
        val ref = messagesRef(message.roomId).document(message.id)
        transaction { tx ->
            tx.set(
                ref,
                mapOf(
                    Fields.SENDER_UID to uid, Fields.CREATED_AT to now, Fields.KIND to message.kind.name,
                    Fields.SEALED_BODY to message.sealedBody, Fields.KEY_VERSION to message.keyVersion,
                ),
            )
            tx.update(membersRef(message.roomId).document(uid), Fields.LAST_MESSAGE_AT, now)
        }
        // The server chose the time, so read the message back to learn it.
        FirestoreMapping.message(message.roomId, message.id, ref.data() ?: throw SocialException(SocialError.FAILED))
            ?: throw SocialException(SocialError.FAILED)
    }

    override suspend fun deleteMessage(roomId: String, messageId: String) = call {
        messagesRef(roomId).document(messageId).delete().await()
        Unit
    }

    override suspend fun report(report: Report) = call {
        db.collection(Fields.REPORTS).add(FirestoreMapping.reportData(report, uid) + (Fields.CREATED_AT to now)).await()
        Unit
    }

    // ---- the person's own footprint ----------------------------------------------------------------------

    override suspend fun myRoomIds(): List<String> = call {
        usersRef().document(uid).collection(Fields.ROOMS).snapshot().documents.map { it.id }
    }

    // ---- finding people and asking to chat -----------------------------------------------------------------

    override suspend fun setUsername(name: String?, previous: String?) = call {
        val batch = db.batch()
        if (name != null && name != previous) batch.set(db.collection(Fields.USERNAMES).document(name), mapOf(Fields.UID to uid))
        if (previous != null && previous != name) batch.delete(db.collection(Fields.USERNAMES).document(previous))
        batch.commit().await()
        Unit
    }

    override suspend fun userByUsername(name: String): String? = call {
        (db.collection(Fields.USERNAMES).document(name).data()?.get(Fields.UID) as? String)?.takeIf { it.length in 1..128 }
    }

    override suspend fun userByEmailHash(hash: String): String? = call {
        (db.collection(Fields.EMAIL_LOOKUP).document(hash).data()?.get(Fields.UID) as? String)?.takeIf { it.length in 1..128 }
    }

    override suspend fun setEmailLookup(hash: String, on: Boolean) = call {
        val ref = db.collection(Fields.EMAIL_LOOKUP).document(hash)
        if (on) { if (ref.data()?.get(Fields.UID) != uid) ref.set(mapOf(Fields.UID to uid)).await() } else ref.delete().await()
        Unit
    }

    override suspend fun sendChatRequest(request: ChatRequest) = call {
        if (request.fromUid != uid || request.toUid == uid) throw SocialException(SocialError.DENIED)
        db.collection(Fields.CHAT_REQUESTS).document(request.toUid).collection(Fields.FROM).document(uid).set(
            mapOf(
                Fields.FROM_NAME to request.fromName, Fields.ROOM_ID to request.roomId, Fields.INVITE_ID to request.inviteId,
                Fields.WRAPPED_KEY to request.wrappedKey, Fields.KEY_VERSION to request.keyVersion, Fields.CREATED_AT to now,
            ),
        ).await()
        Unit
    }

    override suspend fun chatRequests(): List<ChatRequest> = call {
        db.collection(Fields.CHAT_REQUESTS).document(uid).collection(Fields.FROM).snapshot().documents
            .mapNotNull { FirestoreMapping.chatRequest(uid, it.id, it.data ?: return@mapNotNull null) }
    }

    override suspend fun deleteChatRequest(toUid: String, fromUid: String) = call {
        db.collection(Fields.CHAT_REQUESTS).document(toUid).collection(Fields.FROM).document(fromUid).delete().await()
        Unit
    }

    override suspend fun countCourseUse(courseHash: String, name: String) = call {
        val parent = db.collection(Fields.COURSE_USE).document(courseHash)
        val mine = parent.collection(Fields.USERS).document(uid)
        // One transaction: the person is marked as counted and the number goes up by one. Counted already means refused.
        db.runTransaction(
            Transaction.Function { tx ->
                if (tx.get(mine).exists()) return@Function null
                val current = tx.get(parent)
                tx.set(mine, mapOf(Fields.CREATED_AT to now))
                if (current.exists()) tx.update(parent, Fields.COUNT, FieldValue.increment(1))
                else tx.set(parent, mapOf(Fields.COUNT to 1, Fields.NAME to name.take(60)))
                null
            },
        ).await()
        Unit
    }

    override suspend fun deleteMyData() = call {
        // Names and lookups first, so nobody can find or ask this account while the rest is removed.
        val handle = usersRef().document(uid).data()?.get(Fields.USERNAME) as? String
        if (handle != null) db.collection(Fields.USERNAMES).document(handle).delete().await()
        auth.currentUser?.email?.let { db.collection(Fields.EMAIL_LOOKUP).document(com.hsib.sib.core.social.SocialRules.emailHash(it)).delete().await() }
        for (req in db.collection(Fields.CHAT_REQUESTS).document(uid).collection(Fields.FROM).snapshot().documents) req.reference.delete().await()
        for (roomId in usersRef().document(uid).collection(Fields.ROOMS).snapshot().documents.map { it.id }) {
            val data = try { roomRef(roomId).data() } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) null else throw e
            }
            when {
                // The room is gone or this person was removed: only their own list entry is left to tidy.
                data == null -> mirrorRef(uid, roomId).delete().await()
                data[Fields.OWNER_UID] == uid -> deleteRoom(roomId)
                else -> {
                    val mine = messagesRef(roomId).whereEqualTo(Fields.SENDER_UID, uid).snapshot().documents
                    for (chunk in mine.chunked(PAGE.toInt())) {
                        val batch = db.batch()
                        chunk.forEach { batch.delete(it.reference) }
                        batch.commit().await()
                    }
                    leave(roomId)
                }
            }
        }
        usersRef().document(uid).delete().await()
        Unit
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
        const val PAGE = 400L
    }
}
