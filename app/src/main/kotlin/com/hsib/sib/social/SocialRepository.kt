package com.hsib.sib.social

import android.content.Context
import androidx.room.withTransaction
import com.hsib.sib.BuildConfig
import com.hsib.sib.core.model.Lifecycle
import com.hsib.sib.core.social.ChatMessage
import com.hsib.sib.core.social.CommunityRules
import com.hsib.sib.core.social.DeviceKeyPair
import com.hsib.sib.core.social.InviteCode
import com.hsib.sib.core.social.InviteInfo
import com.hsib.sib.core.social.InviteSeal
import com.hsib.sib.core.social.KeyWrap
import com.hsib.sib.core.social.MemberInfo
import com.hsib.sib.core.social.MessageInfo
import com.hsib.sib.core.social.MessageKind
import com.hsib.sib.core.social.Opened
import com.hsib.sib.core.social.Profile
import com.hsib.sib.core.social.Purpose
import com.hsib.sib.core.social.Report
import com.hsib.sib.core.social.ReportReason
import com.hsib.sib.core.social.RoomInfo
import com.hsib.sib.core.social.RoomKey
import com.hsib.sib.core.social.RoomRole
import com.hsib.sib.core.social.SealContext
import com.hsib.sib.core.social.SharedPayload
import com.hsib.sib.core.social.SocialCrypto
import com.hsib.sib.core.social.SocialLimits
import com.hsib.sib.core.social.SocialRules
import com.hsib.sib.data.BlockedEntity
import com.hsib.sib.data.RoomEntity
import com.hsib.sib.data.RoomKeyEntity
import com.hsib.sib.data.RoomMemberEntity
import com.hsib.sib.data.RoomMessageEntity
import com.hsib.sib.data.SOCIAL_DEVICE_KEY
import com.hsib.sib.data.SettingsStore
import com.hsib.sib.data.SibDatabase
import com.hsib.sib.data.Store
import com.hsib.sib.data.toHex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

data class MemberView(val uid: String, val name: String, val isOwner: Boolean, val isMe: Boolean, val done: Boolean, val blocked: Boolean)

/** One shared deadline as the screens see it. */
data class SharedRoomView(
    val roomId: String,
    val obligationId: String,
    val isOwner: Boolean,
    val ownerName: String,
    val members: List<MemberView>,
    val unread: Int,
    /** The server says this person is not in the room any more (removed, or it was deleted). */
    val removed: Boolean,
    /** False when this phone has no key for the current version, so details and chat cannot be shown yet. */
    val canRead: Boolean,
    val doneLine: String?,
)

data class SocialState(
    val user: SocialUser?,
    val termsAccepted: Boolean,
    val rooms: Map<String, SharedRoomView>,
    val blocked: List<BlockedEntity>,
    val practice: Boolean,
) {
    fun roomFor(obligationId: String): SharedRoomView? = rooms[obligationId]

    companion object {
        val Off = SocialState(null, false, emptyMap(), emptyList(), false)
    }
}

/**
 * Sharing and chat for this phone. It keeps rooms, keys and chat in the encrypted database, talks to the server through a
 * [SocialBackend], and does all the sealing and opening here, so the server only ever holds text it cannot read.
 * Every public action returns a [SocialResult] with a reason that can be shown, never an exception.
 */
class SocialRepository(
    private val db: SibDatabase,
    private val settings: SettingsStore,
    private val store: Store,
    private val backend: SocialBackend,
    val auth: SocialAuth,
    scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
    val practice: Boolean = false,
) {
    private val lock = Mutex()
    private val termsAccepted = MutableStateFlow(false)

    init {
        scope.launch { termsAccepted.value = (settings.raw(TERMS_KEY)?.toIntOrNull() ?: 0) >= SocialLimits.TERMS_VERSION }
    }

    // ---- what the screens watch ------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SocialState> = combine(
        auth.user,
        db.rooms().observeAll(),
        db.roomMembers().observeAll(),
        auth.user.flatMapLatest { u -> db.roomMessages().observeUnread(u?.uid.orEmpty()) },
        db.blocked().observeAll(),
    ) { user, rooms, members, unread, blocked ->
        val blockedIds = blocked.map { it.uid }.toSet()
        val unreadBy = unread.associate { it.roomId to it.unread }
        SocialState(
            user = user, termsAccepted = false, practice = practice, blocked = blocked,
            rooms = rooms.associate { r ->
                val people = members.filter { it.roomId == r.roomId }
                val views = people.map { m ->
                    MemberView(m.uid, m.displayName, m.role == RoomRole.OWNER.name, m.uid == user?.uid, m.doneAt != null, m.uid in blockedIds)
                }
                r.obligationId to SharedRoomView(
                    roomId = r.roomId, obligationId = r.obligationId, isOwner = user != null && r.ownerUid == user.uid,
                    ownerName = views.firstOrNull { it.isOwner }?.name ?: "Someone",
                    members = views, unread = unreadBy[r.roomId] ?: 0, removed = r.removed,
                    canRead = r.canRead && !r.removed,
                    doneLine = if (views.any { it.done }) "${views.count { it.done }} of ${views.size} done" else null,
                )
            },
        )
    }.combine(termsAccepted) { s, accepted -> s.copy(termsAccepted = accepted) }
        .stateIn(scope, SharingStarted.Eagerly, SocialState.Off.copy(practice = practice))

    /** The conversation of one shared deadline, opened, with blocked people hidden. */
    fun chat(roomId: String): Flow<List<ChatMessage>> = combine(
        db.roomMessages().observeFor(roomId), db.roomMembers().observeAll(), db.blocked().observeAll(), auth.user,
    ) { messages, members, blocked, user ->
        val names = members.filter { it.roomId == roomId }.associate { it.uid to it.displayName }
        SocialRules.visible(
            messages.map { m ->
                ChatMessage(
                    id = m.id, roomId = m.roomId, senderUid = m.senderUid, senderName = names[m.senderUid] ?: "Someone",
                    atMs = m.createdAt, text = m.text, kind = runCatching { MessageKind.valueOf(m.kind) }.getOrDefault(MessageKind.TEXT),
                    mine = user != null && m.senderUid == user.uid,
                )
            },
            blocked.map { it.uid }.toSet(),
        )
    }

    // ---- small helpers ----------------------------------------------------------------------------

    private suspend inline fun <T> guarded(block: () -> SocialResult<T>): SocialResult<T> = try {
        block()
    } catch (e: SocialException) {
        SocialResult.Err(e.error)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        SocialResult.Err(SocialError.FAILED)
    }

    private fun requireUser(): SocialUser = auth.user.value ?: throw SocialException(SocialError.NOT_SIGNED_IN)

    private fun requireTerms() { if (!termsAccepted.value) throw SocialException(SocialError.TERMS) }

    private suspend fun deviceKey(): DeviceKeyPair {
        settings.raw(SOCIAL_DEVICE_KEY)?.let(DeviceKeyPair::decode)?.let { return it }
        val fresh = DeviceKeyPair.generate()
        settings.putRaw(SOCIAL_DEVICE_KEY, fresh.encode())
        return fresh
    }

    private suspend fun myName(user: SocialUser): String =
        SocialRules.cleanName(settings.raw(NAME_KEY).orEmpty()) ?: SocialRules.cleanName(user.displayName) ?: "Me"

    suspend fun displayName(): String = auth.user.value?.let { myName(it) }.orEmpty()

    private suspend fun publishProfile(user: SocialUser) {
        val profile = Profile(user.uid, myName(user), deviceKey().publicText())
        backend.saveProfile(profile)
        settings.putRaw(PUBLISHED_KEY, profile.uid + "|" + profile.displayName + "|" + profile.publicKey)
    }

    /**
     * Publishes the profile only if the name or this phone's key differ from what was last published. A phone that had to
     * make a new key (a restore without the backed up key) must tell the server, or others would seal for the old key.
     */
    private suspend fun ensureProfile(user: SocialUser) {
        val now = user.uid + "|" + myName(user) + "|" + deviceKey().publicText()
        if (settings.raw(PUBLISHED_KEY) != now) publishProfile(user)
    }

    private suspend fun roomKeys(roomId: String): Map<Int, RoomKey> =
        db.roomKeys().forRoom(roomId).mapNotNull { k -> RoomKey.decode(k.keyText)?.let { k.version to it } }.toMap()

    private fun fingerprint(p: SharedPayload): String =
        MessageDigest.getInstance("SHA-256").digest(p.copy(rev = 0).toBytes()).toHex()

    private fun newRow(roomId: String, obligationId: String, ownerUid: String, keyVersion: Int, appliedRev: Long, memberCount: Int, hash: String, canRead: Boolean = true) =
        RoomEntity(
            roomId = roomId, obligationId = obligationId, ownerUid = ownerUid, keyVersion = keyVersion, appliedRev = appliedRev,
            memberCount = memberCount, messageCursor = 0, lastReadAt = clock(), lastSyncAt = 0, myLastMessageAt = 0, removed = false,
            payloadHash = hash, canRead = canRead,
        )

    private suspend fun forgetRoom(roomId: String) = db.withTransaction {
        db.roomMessages().deleteFor(roomId); db.roomMembers().deleteFor(roomId); db.roomKeys().deleteFor(roomId); db.rooms().delete(roomId)
    }

    private suspend fun clearLocal() = db.withTransaction {
        db.rooms().clear(); db.roomKeys().clear(); db.roomMembers().clear(); db.roomMessages().clear()
    }

    // ---- the account -------------------------------------------------------------------------------

    suspend fun acceptTerms() {
        settings.putRaw(TERMS_KEY, SocialLimits.TERMS_VERSION.toString())
        settings.putRaw(TERMS_AT_KEY, java.time.Instant.ofEpochMilli(clock()).toString())
        termsAccepted.value = true
        auth.user.value?.let { runCatching { recordConsentIfNeeded(it) } }
    }

    /** Leaves the one record on the service that this account accepted these rules. Retried on the next sync if it could not be sent. */
    private suspend fun recordConsentIfNeeded(user: SocialUser) {
        if (!termsAccepted.value) return
        val mark = user.uid + "|" + SocialLimits.TERMS_VERSION
        if (settings.raw(CONSENT_SENT_KEY) == mark) return
        backend.recordConsent(SocialLimits.TERMS_VERSION, BuildConfig.VERSION_NAME)
        settings.putRaw(CONSENT_SENT_KEY, mark)
    }

    suspend fun signIn(context: Context): SignInResult {
        val result = auth.signIn(context)
        // The screen that started this is replaced as soon as the account appears, which cancels it. What follows must finish anyway.
        if (result is SignInResult.Ok) withContext(NonCancellable) {
            lock.withLock { forgetRoomsOfOtherAccount(result.user) }
            runCatching { publishProfile(result.user) }
        }
        return result
    }

    /**
     * The rooms on this phone belong to the account that fetched them. When a different account is signed in, they are forgotten
     * before anything else, so one account never sees another's rooms. Runs under [lock], first in every sync, so it holds
     * whether or not the sign in call got to finish.
     */
    private suspend fun forgetRoomsOfOtherAccount(user: SocialUser) {
        val known = settings.raw(UID_KEY)
        if (known == user.uid) return
        if (known != null) clearLocal()
        settings.putRaw(UID_KEY, user.uid)
    }

    suspend fun signOut() = auth.signOut()

    /** What friends see next to a message. Kept on the server in the public profile, and in each room the next time it syncs. */
    suspend fun setDisplayName(name: String): SocialResult<Unit> = guarded {
        val user = requireUser()
        val clean = SocialRules.cleanName(name) ?: return SocialResult.Err(SocialError.FAILED)
        settings.putRaw(NAME_KEY, clean)
        publishProfile(user)
        SocialResult.Ok(Unit)
    }

    /**
     * Deletes everything the server holds about this person, then the sign in account, then what this phone holds for it.
     * The local deadlines stay, as ordinary private deadlines.
     */
    suspend fun deleteAccount(context: Context): SocialResult<Unit> = lock.withLock {
        guarded {
            requireUser()
            backend.deleteMyData()
            auth.deleteAccount(context)
            clearLocal()
            db.blocked().clear()
            for (k in listOf(UID_KEY, NAME_KEY, SOCIAL_DEVICE_KEY, TERMS_KEY, PUBLISHED_KEY)) settings.removeRaw(k)
            termsAccepted.value = false
            SocialResult.Ok(Unit)
        }
    }

    // ---- making and joining shared deadlines ---------------------------------------------------------

    suspend fun share(obligationId: String): SocialResult<String> = lock.withLock {
        guarded {
            val user = requireUser()
            requireTerms()
            val o = store.obligation(obligationId) ?: return SocialResult.Err(SocialError.NOT_FOUND)
            if (db.rooms().forObligation(obligationId) != null) return SocialResult.Err(SocialError.ALREADY_IN)
            if (o.lifecycle != Lifecycle.ACTIVE) return SocialResult.Err(SocialError.FAILED)
            val payload = SharedPayload.from(o, 1) ?: return SocialResult.Err(SocialError.CLOSE_ONES)
            val roomId = newId()
            val key = RoomKey.generate()
            val me = deviceKey()
            val sealed = SocialCrypto.seal(key, SealContext(roomId, Purpose.PAYLOAD, 1), payload.toBytes())
            val wrapped = KeyWrap.wrap(key, me.publicKey, roomId, 1, user.uid) ?: return SocialResult.Err(SocialError.FAILED)
            ensureProfile(user)
            backend.createRoom(
                RoomInfo(roomId, user.uid, 1, 1, sealed, 1, 0),
                MemberInfo(user.uid, roomId, RoomRole.OWNER, myName(user), 0, null, wrapped, 1, 0),
            )
            db.withTransaction {
                db.roomKeys().upsert(RoomKeyEntity(roomId, 1, key.encode()))
                db.rooms().upsert(newRow(roomId, obligationId, user.uid, 1, 1, 1, fingerprint(payload)))
            }
            syncRoom(roomId, user)
            SocialResult.Ok(roomId)
        }
    }

    /** A code the owner sends to a friend. It carries the room key sealed so that only someone holding the code can open it. */
    suspend fun invite(roomId: String): SocialResult<String> = lock.withLock {
        guarded {
            val user = requireUser()
            requireTerms()
            val row = db.rooms().get(roomId) ?: return SocialResult.Err(SocialError.NOT_FOUND)
            if (row.ownerUid != user.uid || row.removed) return SocialResult.Err(SocialError.DENIED)
            val key = roomKeys(roomId)[row.keyVersion] ?: return SocialResult.Err(SocialError.CANNOT_READ)
            val code = InviteCode.generate()
            backend.createInvite(
                InviteInfo(code.id, roomId, user.uid, SocialRules.inviteExpiry(clock()), false, InviteSeal.seal(key, code, roomId, row.keyVersion), row.keyVersion),
            )
            val ids = (settings.raw(invitesKey(roomId)).orEmpty().split(",").filter { it.isNotBlank() } + code.id).takeLast(10)
            settings.putRaw(invitesKey(roomId), ids.joinToString(","))
            SocialResult.Ok(code.text())
        }
    }

    /** Cancels every invite this phone made for the room, for example when one was sent to the wrong person. */
    suspend fun cancelInvites(roomId: String): SocialResult<Unit> = lock.withLock {
        guarded {
            requireUser()
            val ids = settings.raw(invitesKey(roomId)).orEmpty().split(",").filter { it.isNotBlank() }
            for (id in ids) try { backend.revokeInvite(id) } catch (e: SocialException) { if (e.error != SocialError.NOT_FOUND) throw e }
            settings.putRaw(invitesKey(roomId), "")
            SocialResult.Ok(Unit)
        }
    }

    suspend fun join(codeText: String): SocialResult<String> = lock.withLock {
        guarded {
            val user = requireUser()
            requireTerms()
            val code = InviteCode.parse(codeText) ?: return SocialResult.Err(SocialError.BAD_CODE)
            val invite = backend.invite(code.id) ?: return SocialResult.Err(SocialError.NOT_FOUND)
            if (db.rooms().get(invite.roomId) != null) return SocialResult.Err(SocialError.ALREADY_IN)
            if (invite.revoked) return SocialResult.Err(SocialError.REVOKED)
            if (invite.expiresAtMs <= clock()) return SocialResult.Err(SocialError.EXPIRED)
            val (key, version) = (InviteSeal.open(invite.sealedKey, code, invite.roomId) as? Opened.Ok)?.value
                ?: return SocialResult.Err(SocialError.BAD_CODE)
            val me = deviceKey()
            ensureProfile(user)
            val wrapped = KeyWrap.wrap(key, me.publicKey, invite.roomId, version, user.uid) ?: return SocialResult.Err(SocialError.FAILED)
            try {
                backend.join(invite.id, MemberInfo(user.uid, invite.roomId, RoomRole.MEMBER, myName(user), 0, null, wrapped, version, 0))
            } catch (e: SocialException) {
                if (e.error == SocialError.DENIED) return SocialResult.Err(SocialError.JOIN_REFUSED) else throw e
            }
            val info = backend.room(invite.roomId) ?: return SocialResult.Err(SocialError.NOT_FOUND)
            val payload = readPayload(info, key) ?: return SocialResult.Err(SocialError.CANNOT_READ)
            val obligationId = newId()
            store.create(payload.toNewObligation(obligationId), "SHARED", "joined a shared deadline")
            db.withTransaction {
                db.roomKeys().upsert(RoomKeyEntity(info.id, version, key.encode()))
                db.rooms().upsert(newRow(info.id, obligationId, info.ownerUid, version, info.rev, info.memberCount, fingerprint(payload)))
            }
            syncRoom(info.id, user)
            SocialResult.Ok(obligationId)
        }
    }

    private fun readPayload(info: RoomInfo, key: RoomKey): SharedPayload? {
        val opened = SocialCrypto.open(key, SealContext(info.id, Purpose.PAYLOAD, info.keyVersion), info.sealedPayload)
        return (opened as? Opened.Ok)?.let { SharedPayload.fromBytes(it.value) }
    }

    // ---- keeping everyone's copy the same ----------------------------------------------------------------

    /** Brings every shared deadline up to date: its details, its people, its messages. Safe to call as often as wanted. */
    suspend fun sync(): SocialResult<Unit> = lock.withLock {
        guarded {
            val user = auth.user.value ?: return SocialResult.Ok(Unit)
            forgetRoomsOfOtherAccount(user)
            ensureProfile(user)
            runCatching { recordConsentIfNeeded(user) }
            // A new phone or a restore: find rooms this person belongs to that this phone does not know yet.
            val known = db.rooms().all().map { it.roomId }.toSet()
            for (roomId in backend.myRoomIds() - known) adoptRoom(roomId, user)
            for (row in db.rooms().all()) if (!row.removed) syncRoom(row.roomId, user)
            SocialResult.Ok(Unit)
        }
    }

    /** Fetches just one room, for example right after opening its chat. */
    suspend fun syncOne(roomId: String): SocialResult<Unit> = lock.withLock {
        guarded {
            val user = requireUser()
            syncRoom(roomId, user)
            SocialResult.Ok(Unit)
        }
    }

    private suspend fun adoptRoom(roomId: String, user: SocialUser) {
        val info = backend.room(roomId) ?: return
        val me = backend.members(roomId).firstOrNull { it.uid == user.uid } ?: return
        val wrapped = me.wrappedKey ?: return
        val key = (KeyWrap.unwrap(wrapped, deviceKey(), roomId, me.wrappedKeyVersion, user.uid) as? Opened.Ok)?.value ?: return
        val payload = readPayload(info.copy(keyVersion = me.wrappedKeyVersion), key) ?: return
        val obligationId = newId()
        store.create(payload.toNewObligation(obligationId), "SHARED", "found on this phone again")
        db.withTransaction {
            db.roomKeys().upsert(RoomKeyEntity(roomId, me.wrappedKeyVersion, key.encode()))
            db.rooms().upsert(newRow(roomId, obligationId, info.ownerUid, me.wrappedKeyVersion, 0, info.memberCount, ""))
        }
    }

    private suspend fun markRemoved(row: RoomEntity) {
        db.rooms().upsert(row.copy(removed = true))
    }

    private suspend fun syncRoom(roomId: String, user: SocialUser) {
        val row = db.rooms().get(roomId) ?: return
        val info: RoomInfo? = try {
            backend.room(roomId)
        } catch (e: SocialException) {
            if (e.error == SocialError.DENIED || e.error == SocialError.NOT_FOUND) null else throw e
        }
        if (info == null) { markRemoved(row); return }
        val members = backend.members(roomId)
        val me = members.firstOrNull { it.uid == user.uid } ?: run { markRemoved(row); return }
        // Profiles (names and public keys) are fetched again only when the people changed or it has been a while, because
        // every read counts against the free quota of the real server.
        val known = db.roomMembers().forRoom(roomId)
        val samePeople = known.map { it.uid }.toSet() == members.map { it.uid }.toSet() && known.none { it.publicKey == null }
        val profiles: Map<String, Profile> =
            if (samePeople && clock() - row.lastSyncAt < PROFILE_REFRESH_MS) emptyMap()
            else runCatching { backend.profiles(members.map { it.uid }.toSet()) }.getOrDefault(emptyMap())

        // Keys: a new version appears when the owner removed someone. My copy is sealed for my phone's key.
        var keys = roomKeys(roomId)
        if (info.keyVersion !in keys && me.wrappedKey != null && me.wrappedKeyVersion == info.keyVersion) {
            val opened = KeyWrap.unwrap(me.wrappedKey!!, deviceKey(), roomId, info.keyVersion, user.uid)
            if (opened is Opened.Ok) {
                db.roomKeys().upsert(RoomKeyEntity(roomId, info.keyVersion, opened.value.encode()))
                keys = roomKeys(roomId)
            }
        }
        val currentKey = keys[info.keyVersion]

        db.roomMembers().deleteFor(roomId)
        db.roomMembers().upsertAll(
            members.map { m ->
                val p = profiles[m.uid]
                val before = known.firstOrNull { it.uid == m.uid }
                RoomMemberEntity(roomId, m.uid, m.role.name, p?.displayName ?: before?.displayName ?: m.displayName, m.joinedAtMs, m.doneAtMs, p?.publicKey ?: before?.publicKey)
            },
        )

        // The deadline itself. The owner's version wins, and only a newer one is applied.
        var appliedRev = row.appliedRev
        var hash = row.payloadHash
        val local = store.obligation(row.obligationId)
        if (currentKey != null && (SocialRules.shouldApply(row.appliedRev, info.rev) || local == null)) {
            val payload = readPayload(info, currentKey)
            if (payload != null) {
                val ownerName = members.firstOrNull { it.uid == info.ownerUid }
                    ?.let { profiles[it.uid]?.displayName ?: known.firstOrNull { k -> k.uid == it.uid }?.displayName ?: it.displayName } ?: "the owner"
                if (local == null) store.create(payload.toNewObligation(row.obligationId), "SHARED", "found on this phone again")
                else store.save(payload.applyTo(local), "SHARED", "changed by $ownerName")
                appliedRev = info.rev
                hash = fingerprint(payload)
            }
        }

        val cursor = fetchMessages(roomId, row.messageCursor, keys)

        db.rooms().upsert(
            (db.rooms().get(roomId) ?: row).copy(
                ownerUid = info.ownerUid, keyVersion = info.keyVersion, appliedRev = appliedRev, memberCount = info.memberCount,
                messageCursor = cursor, lastSyncAt = clock(), myLastMessageAt = me.lastMessageAtMs, removed = false, payloadHash = hash,
                canRead = currentKey != null,
            ),
        )
    }

    /**
     * Fetches messages newer than [from], with a few seconds of overlap so one written at nearly the same moment as the last
     * is never missed, and stores the ones that open. Returns the new cursor.
     */
    private suspend fun fetchMessages(roomId: String, from: Long, keys: Map<Int, RoomKey>): Long {
        var cursor = from
        var pages = 0
        while (pages < MAX_PAGES) {
            val page = backend.messages(roomId, (cursor - OVERLAP_MS).coerceAtLeast(0), SocialLimits.FETCH_PAGE)
            val opened = page.mapNotNull { m -> openMessage(m, keys) }
            if (opened.isNotEmpty()) db.roomMessages().upsertAll(opened)
            val newest = page.maxOfOrNull { it.createdAtMs } ?: break
            if (newest <= cursor) break
            cursor = newest
            pages++
            if (page.size < SocialLimits.FETCH_PAGE) break
        }
        return cursor
    }

    /**
     * The light refresh for an open conversation: only new messages, one cheap request when nothing is new. If the server says
     * this person is no longer in the room, the room is marked removed so the screen can say so.
     */
    suspend fun refreshChat(roomId: String): SocialResult<Unit> = lock.withLock {
        guarded {
            requireUser()
            val row = db.rooms().get(roomId) ?: return SocialResult.Err(SocialError.NOT_FOUND)
            if (row.removed) return SocialResult.Ok(Unit)
            val cursor = try {
                fetchMessages(roomId, row.messageCursor, roomKeys(roomId))
            } catch (e: SocialException) {
                if (e.error == SocialError.DENIED) { markRemoved(row); return SocialResult.Ok(Unit) } else throw e
            }
            if (cursor != row.messageCursor) db.rooms().get(roomId)?.let { db.rooms().upsert(it.copy(messageCursor = cursor)) }
            SocialResult.Ok(Unit)
        }
    }

    private fun openMessage(m: MessageInfo, keys: Map<Int, RoomKey>): RoomMessageEntity? {
        val key = keys[m.keyVersion] ?: return null
        val ctx = SealContext(m.roomId, Purpose.MESSAGE, m.keyVersion, m.id, m.senderUid)
        val text = (SocialCrypto.openText(key, ctx, m.sealedBody) as? Opened.Ok)?.value ?: return null
        val clean = SocialRules.cleanMessage(text) ?: return null
        return RoomMessageEntity(m.roomId, m.id, m.senderUid, m.createdAtMs, m.kind.name, clean)
    }

    // ---- changes the owner makes, and what each person does ---------------------------------------------

    /**
     * After the owner edits a shared deadline, sends the new title and date to everyone. Does nothing if the deadline is not
     * shared, if this person is not its owner, or if only personal fields (effort, importance) changed.
     * [note] is added to the chat as a line from the owner, for example "moved the date to Fri, Oct 16".
     */
    suspend fun pushIfShared(obligationId: String, note: String? = null): SocialResult<Unit> = lock.withLock {
        guarded {
            val user = auth.user.value ?: return SocialResult.Ok(Unit)
            val row = db.rooms().forObligation(obligationId) ?: return SocialResult.Ok(Unit)
            if (row.ownerUid != user.uid || row.removed) return SocialResult.Ok(Unit)
            val o = store.obligation(obligationId) ?: return SocialResult.Ok(Unit)
            val next = SharedPayload.from(o, row.appliedRev + 1) ?: return SocialResult.Ok(Unit)
            val hash = fingerprint(next)
            if (hash == row.payloadHash) return SocialResult.Ok(Unit)
            val key = roomKeys(row.roomId)[row.keyVersion] ?: return SocialResult.Err(SocialError.CANNOT_READ)
            backend.updatePayload(row.roomId, next.rev, SocialCrypto.seal(key, SealContext(row.roomId, Purpose.PAYLOAD, row.keyVersion), next.toBytes()))
            db.rooms().upsert(row.copy(appliedRev = next.rev, payloadHash = hash))
            if (note != null) postLine(row, user, note)
            SocialResult.Ok(Unit)
        }
    }

    /** Tells the room that this person finished (or reopened) their part. Does nothing if the deadline is not shared. */
    suspend fun markDone(obligationId: String, done: Boolean): SocialResult<Unit> = lock.withLock {
        guarded {
            val user = auth.user.value ?: return SocialResult.Ok(Unit)
            val row = db.rooms().forObligation(obligationId) ?: return SocialResult.Ok(Unit)
            if (row.removed) return SocialResult.Ok(Unit)
            backend.setDone(row.roomId, if (done) clock() else null)
            postLine(row, user, if (done) "finished their part" else "reopened their part")
            syncRoom(row.roomId, user)
            SocialResult.Ok(Unit)
        }
    }

    private suspend fun postLine(row: RoomEntity, user: SocialUser, text: String) {
        val key = roomKeys(row.roomId)[row.keyVersion] ?: return
        val id = newId()
        val sealed = SocialCrypto.sealText(key, SealContext(row.roomId, Purpose.MESSAGE, row.keyVersion, id, user.uid), text)
        // A line is a courtesy: if the one a second rule or the network says no, the change itself has still been made.
        runCatching { backend.send(MessageInfo(id, row.roomId, user.uid, 0, MessageKind.SYSTEM, sealed, row.keyVersion)) }
            .onSuccess { stored ->
                db.roomMessages().upsertAll(listOf(RoomMessageEntity(row.roomId, id, user.uid, stored.createdAtMs, MessageKind.SYSTEM.name, text)))
                db.rooms().get(row.roomId)?.let { db.rooms().upsert(it.copy(myLastMessageAt = stored.createdAtMs)) }
            }
    }

    // ---- chat -----------------------------------------------------------------------------------------------

    suspend fun send(roomId: String, raw: String): SocialResult<Unit> = lock.withLock {
        guarded {
            val user = requireUser()
            requireTerms()
            val text = SocialRules.cleanMessage(raw) ?: return SocialResult.Ok(Unit)
            val row = db.rooms().get(roomId) ?: return SocialResult.Err(SocialError.NOT_FOUND)
            if (row.removed) return SocialResult.Err(SocialError.DENIED)
            if (!SocialRules.canSendNow(row.myLastMessageAt, clock())) return SocialResult.Err(SocialError.RATE_LIMITED)
            val key = roomKeys(roomId)[row.keyVersion] ?: return SocialResult.Err(SocialError.CANNOT_READ)
            val id = newId()
            val sealed = SocialCrypto.sealText(key, SealContext(roomId, Purpose.MESSAGE, row.keyVersion, id, user.uid), text)
            val stored = try {
                backend.send(MessageInfo(id, roomId, user.uid, 0, MessageKind.TEXT, sealed, row.keyVersion))
            } catch (e: SocialException) {
                // Usually the key changed or this person was removed a moment ago. Find out, so the screen can say so.
                if (e.error == SocialError.DENIED) runCatching { syncRoom(roomId, user) }
                throw e
            }
            db.roomMessages().upsertAll(listOf(RoomMessageEntity(roomId, id, user.uid, stored.createdAtMs, MessageKind.TEXT.name, text)))
            db.rooms().upsert((db.rooms().get(roomId) ?: row).copy(myLastMessageAt = stored.createdAtMs, lastReadAt = stored.createdAtMs))
            SocialResult.Ok(Unit)
        }
    }

    suspend fun markRead(roomId: String) {
        val row = db.rooms().get(roomId) ?: return
        val newest = db.roomMessages().forRoom(roomId).maxOfOrNull { it.createdAt } ?: return
        if (newest > row.lastReadAt) db.rooms().upsert(row.copy(lastReadAt = newest))
    }

    suspend fun deleteMessage(roomId: String, messageId: String): SocialResult<Unit> = lock.withLock {
        guarded {
            requireUser()
            backend.deleteMessage(roomId, messageId)
            db.roomMessages().delete(roomId, messageId)
            SocialResult.Ok(Unit)
        }
    }

    // ---- people -----------------------------------------------------------------------------------------------

    /** Leaves (or, for the owner, deletes for everyone). The local deadline stays, as an ordinary private one. */
    suspend fun leaveOrDelete(roomId: String): SocialResult<Unit> = lock.withLock {
        guarded {
            val user = requireUser()
            val row = db.rooms().get(roomId) ?: return SocialResult.Ok(Unit)
            if (!row.removed) {
                try {
                    if (row.ownerUid == user.uid) backend.deleteRoom(roomId) else backend.leave(roomId)
                } catch (e: SocialException) {
                    // Already gone or already removed: the result is the same, so carry on and tidy up this phone.
                    if (e.error != SocialError.DENIED && e.error != SocialError.NOT_FOUND) throw e
                }
            }
            forgetRoom(roomId)
            SocialResult.Ok(Unit)
        }
    }

    /** Forgets a room this person was removed from, keeping the deadline as a private one. */
    suspend fun dismissRemoved(roomId: String) = forgetRoom(roomId)

    /**
     * The owner removes someone. A new room key is made and sealed for everyone who stays, and the deadline's details are
     * sealed again under it, so the removed person cannot read anything from now on. Refuses rather than lock out a person
     * whose key cannot be used.
     */
    suspend fun removeMember(roomId: String, targetUid: String): SocialResult<Unit> = lock.withLock {
        guarded {
            val user = requireUser()
            val row = db.rooms().get(roomId) ?: return SocialResult.Err(SocialError.NOT_FOUND)
            if (row.ownerUid != user.uid || targetUid == user.uid || row.removed) return SocialResult.Err(SocialError.DENIED)
            val staying = backend.members(roomId).map { it.uid }.toSet() - targetUid
            val profiles = backend.profiles(staying)
            val publicKeys = staying.mapNotNull { u -> profiles[u]?.let { DeviceKeyPair.publicFromText(it.publicKey) }?.let { u to it } }.toMap()
            val newVersion = row.keyVersion + 1
            val newKey = RoomKey.generate()
            val wrapped = KeyWrap.wrapForAll(newKey, publicKeys, roomId, newVersion)
            if (!wrapped.keys.containsAll(staying)) return SocialResult.Err(SocialError.FAILED)
            val o = store.obligation(row.obligationId) ?: return SocialResult.Err(SocialError.NOT_FOUND)
            val payload = SharedPayload.from(o, row.appliedRev) ?: return SocialResult.Err(SocialError.CLOSE_ONES)
            val sealed = SocialCrypto.seal(newKey, SealContext(roomId, Purpose.PAYLOAD, newVersion), payload.toBytes())
            val targetName = db.roomMembers().forRoom(roomId).firstOrNull { it.uid == targetUid }?.displayName ?: "someone"
            backend.remove(roomId, targetUid, newVersion, wrapped, sealed)
            db.roomKeys().upsert(RoomKeyEntity(roomId, newVersion, newKey.encode()))
            db.rooms().upsert(row.copy(keyVersion = newVersion, memberCount = staying.size))
            settings.putRaw(invitesKey(roomId), "")
            postLine(db.rooms().get(roomId) ?: row, user, "removed $targetName")
            syncRoom(roomId, user)
            SocialResult.Ok(Unit)
        }
    }

    suspend fun block(uid: String, name: String) = db.blocked().upsert(BlockedEntity(uid, name, clock()))

    suspend fun unblock(uid: String) = db.blocked().delete(uid)

    /** Sends a report to the developer. The text of the message is included only if [includeText] is true. */
    suspend fun report(roomId: String, reportedUid: String, messageId: String?, reason: ReportReason, detail: String, includeText: Boolean): SocialResult<Unit> =
        guarded {
            requireUser()
            val quote = if (includeText && messageId != null) db.roomMessages().forRoom(roomId).firstOrNull { it.id == messageId }?.text else null
            backend.report(SocialRules.cleanReport(Report(roomId, reportedUid, messageId, reason, detail, quote)))
            SocialResult.Ok(Unit)
        }

    /** The community rules, so the screen and the web page show the same words. */
    val rules: List<String> get() = CommunityRules.rules

    companion object {
        const val TERMS_KEY = "social.terms"
        const val TERMS_AT_KEY = "social.termsAt"
        private const val CONSENT_SENT_KEY = "social.consentSent"
        const val UID_KEY = "social.uid"
        const val NAME_KEY = "social.displayName"
        private const val PUBLISHED_KEY = "social.published"
        private fun invitesKey(roomId: String) = "social.invites.$roomId"
        private const val OVERLAP_MS = 5_000L
        private const val MAX_PAGES = 5
        private const val PROFILE_REFRESH_MS = 6L * 60 * 60 * 1000
    }
}
