package com.hsib.sib.social

import android.content.Context
import com.hsib.sib.core.social.Crockford
import com.hsib.sib.core.social.DeviceKeyPair
import com.hsib.sib.core.social.InviteInfo
import com.hsib.sib.core.social.MemberInfo
import com.hsib.sib.core.social.MessageInfo
import com.hsib.sib.core.social.Profile
import com.hsib.sib.core.social.Report
import com.hsib.sib.core.social.RoomInfo
import com.hsib.sib.core.social.RoomRole
import com.hsib.sib.core.social.SocialLimits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A server in memory. It applies the same permission rules the real server rules will apply, and like the real one it only
 * ever holds sealed text, so a test can check that no plaintext reaches it. Several [FakeBackend]s (one per simulated phone)
 * share one [FakeServer]. It exists for tests and for the practice mode of debug builds; release builds never use it.
 *
 * Like the real server it does not explain a refusal: a rule that says no is just "denied".
 */
class FakeServer(private val clock: () -> Long = { System.currentTimeMillis() }) {
    var online = true
    internal val profiles = HashMap<String, Profile>()
    internal val consents = HashMap<String, MutableMap<Int, String>>()
    internal val rooms = LinkedHashMap<String, RoomInfo>()
    internal val members = HashMap<String, LinkedHashMap<String, MemberInfo>>()
    internal val messages = HashMap<String, MutableList<MessageInfo>>()
    internal val invites = HashMap<String, InviteInfo>()
    val reports = ArrayList<Pair<String, Report>>()
    private var last = 0L

    /** Server time, never repeated, so messages have a stable order. */
    fun now(): Long { last = maxOf(clock(), last + 1); return last }

    /** Every stored text value that is meant to be sealed, so a test can look for plaintext in it. */
    fun sealedValues(): List<String> =
        rooms.values.map { it.sealedPayload } + messages.values.flatten().map { it.sealedBody } +
            members.values.flatMap { m -> m.values.mapNotNull { it.wrappedKey } } + invites.values.map { it.sealedKey }

    fun roomCount() = rooms.size
    fun messageCount(roomId: String) = messages[roomId]?.size ?: 0
}

class FakeBackend(private val server: FakeServer, private val uid: String) : SocialBackend {
    private fun <T> online(block: () -> T): T {
        if (!server.online) throw SocialException(SocialError.OFFLINE)
        return block()
    }

    private fun deny(): Nothing = throw SocialException(SocialError.DENIED)

    private fun member(roomId: String): MemberInfo = server.members[roomId]?.get(uid) ?: deny()

    private fun ownedRoom(roomId: String): RoomInfo {
        val r = server.rooms[roomId] ?: deny()
        if (r.ownerUid != uid || member(roomId).role != RoomRole.OWNER) deny()
        return r
    }

    private fun roomsOf(person: String) = server.members.count { (_, m) -> m.containsKey(person) }

    override suspend fun saveProfile(profile: Profile) = online {
        if (profile.uid != uid || profile.displayName.length > SocialLimits.DISPLAY_NAME_CHARS || profile.displayName.isBlank()) deny()
        if (DeviceKeyPair.publicFromText(profile.publicKey) == null) deny()
        server.profiles[uid] = profile
    }

    override suspend fun recordConsent(version: Int, app: String) = online {
        server.consents.getOrPut(uid) { HashMap() }.putIfAbsent(version, app)
        Unit
    }

    override suspend fun profiles(uids: Set<String>): Map<String, Profile> = online { uids.mapNotNull { u -> server.profiles[u]?.let { u to it } }.toMap() }

    override suspend fun createRoom(room: RoomInfo, owner: MemberInfo) = online {
        if (room.ownerUid != uid || owner.uid != uid || owner.role != RoomRole.OWNER || owner.roomId != room.id) deny()
        if (room.keyVersion != 1 || room.rev != 1L || room.memberCount != 1 || server.rooms.containsKey(room.id)) deny()
        if (room.sealedPayload.length > SocialLimits.MAX_ENVELOPE_CHARS) deny()
        if (roomsOf(uid) >= SocialLimits.ROOMS_PER_PERSON) throw SocialException(SocialError.TOO_MANY_ROOMS)
        val at = server.now()
        server.rooms[room.id] = room.copy(createdAtMs = at)
        server.members[room.id] = linkedMapOf(uid to owner.copy(joinedAtMs = at, doneAtMs = null, lastMessageAtMs = 0))
        server.messages[room.id] = ArrayList()
    }

    override suspend fun room(roomId: String): RoomInfo? = online { member(roomId); server.rooms[roomId] }

    override suspend fun updatePayload(roomId: String, rev: Long, sealedPayload: String) = online {
        val r = ownedRoom(roomId)
        if (rev != r.rev + 1 || sealedPayload.length > SocialLimits.MAX_ENVELOPE_CHARS) deny()
        server.rooms[roomId] = r.copy(rev = rev, sealedPayload = sealedPayload)
    }

    override suspend fun members(roomId: String): List<MemberInfo> = online { member(roomId); server.members[roomId].orEmpty().values.toList() }

    override suspend fun createInvite(invite: InviteInfo) = online {
        val r = ownedRoom(invite.roomId)
        val now = server.now()
        if (invite.createdByUid != uid || invite.revoked || invite.keyVersion != r.keyVersion) deny()
        if (invite.expiresAtMs <= now || invite.expiresAtMs > now + SocialLimits.INVITE_DAYS * 24 * 60 * 60 * 1000 + 3_600_000) deny()
        if (invite.id.length != 10 || invite.id.any { Crockford.ALPHABET.indexOf(it) < 0 } || invite.sealedKey.length > 512) deny()
        if (server.invites.containsKey(invite.id)) deny()
        server.invites[invite.id] = invite
    }

    override suspend fun invite(inviteId: String): InviteInfo? = online { server.invites[inviteId] }

    override suspend fun revokeInvite(inviteId: String) = online {
        val inv = server.invites[inviteId] ?: throw SocialException(SocialError.NOT_FOUND)
        ownedRoom(inv.roomId)
        server.invites[inviteId] = inv.copy(revoked = true)
    }

    override suspend fun join(inviteId: String, member: MemberInfo) = online {
        val inv = server.invites[inviteId] ?: deny()
        val r = server.rooms[inv.roomId] ?: deny()
        val people = server.members[r.id] ?: deny()
        val now = server.now()
        if (inv.revoked || inv.expiresAtMs <= now || inv.keyVersion != r.keyVersion) deny()
        if (member.uid != uid || member.roomId != r.id || member.role != RoomRole.MEMBER || member.doneAtMs != null) deny()
        if (member.displayName.isBlank() || member.displayName.length > SocialLimits.DISPLAY_NAME_CHARS) deny()
        if ((member.wrappedKey?.length ?: 0) > 512) deny()
        if (people.containsKey(uid) || r.memberCount >= SocialLimits.MEMBERS_PER_ROOM || roomsOf(uid) >= SocialLimits.ROOMS_PER_PERSON) deny()
        people[uid] = member.copy(joinedAtMs = now, lastMessageAtMs = 0)
        server.rooms[r.id] = r.copy(memberCount = r.memberCount + 1)
    }

    override suspend fun setDone(roomId: String, doneAtMs: Long?) = online {
        val m = member(roomId)
        server.members.getValue(roomId)[uid] = m.copy(doneAtMs = doneAtMs?.let { server.now() })
    }

    override suspend fun leave(roomId: String) = online {
        val r = server.rooms[roomId] ?: deny()
        member(roomId)
        if (r.ownerUid == uid) deny()
        server.members.getValue(roomId).remove(uid)
        server.rooms[roomId] = r.copy(memberCount = r.memberCount - 1)
    }

    override suspend fun deleteRoom(roomId: String) = online {
        ownedRoom(roomId)
        removeRoom(roomId)
    }

    private fun removeRoom(roomId: String) {
        server.rooms.remove(roomId); server.members.remove(roomId); server.messages.remove(roomId)
        server.invites.values.removeAll { it.roomId == roomId }
    }

    override suspend fun remove(roomId: String, targetUid: String, newKeyVersion: Int, wrapped: Map<String, String>, sealedPayload: String) = online {
        val r = ownedRoom(roomId)
        val people = server.members.getValue(roomId)
        if (targetUid == uid || !people.containsKey(targetUid) || newKeyVersion != r.keyVersion + 1) deny()
        val staying = people.keys - targetUid
        if (!wrapped.keys.containsAll(staying) || wrapped.any { it.value.length > 512 } || sealedPayload.length > SocialLimits.MAX_ENVELOPE_CHARS) deny()
        people.remove(targetUid)
        for (u in staying) people[u] = people.getValue(u).copy(wrappedKey = wrapped.getValue(u), wrappedKeyVersion = newKeyVersion)
        server.rooms[roomId] = r.copy(keyVersion = newKeyVersion, memberCount = people.size, sealedPayload = sealedPayload)
    }

    override suspend fun messages(roomId: String, afterMs: Long, limit: Int): List<MessageInfo> = online {
        member(roomId)
        server.messages[roomId].orEmpty().filter { it.createdAtMs > afterMs }.sortedWith(compareBy({ it.createdAtMs }, { it.id })).take(limit.coerceIn(1, SocialLimits.FETCH_PAGE))
    }

    override suspend fun send(message: MessageInfo): MessageInfo = online {
        val m = member(message.roomId)
        val r = server.rooms[message.roomId] ?: deny()
        val list = server.messages.getValue(message.roomId)
        val now = server.now()
        if (message.senderUid != uid || message.keyVersion != r.keyVersion) deny()
        if (message.sealedBody.length > SocialLimits.MAX_ENVELOPE_CHARS || list.any { it.id == message.id }) deny()
        if (now - m.lastMessageAtMs < SocialLimits.MESSAGE_GAP_MS) deny()
        val stored = message.copy(createdAtMs = now)
        list += stored
        server.members.getValue(message.roomId)[uid] = m.copy(lastMessageAtMs = now)
        stored
    }

    override suspend fun deleteMessage(roomId: String, messageId: String) = online {
        member(roomId)
        val list = server.messages[roomId] ?: deny()
        val m = list.firstOrNull { it.id == messageId } ?: throw SocialException(SocialError.NOT_FOUND)
        // The sender can delete their own message, and the room's owner can delete any message in it.
        if (m.senderUid != uid && server.rooms[roomId]?.ownerUid != uid) deny()
        list.remove(m)
        Unit
    }

    override suspend fun report(report: Report) = online {
        member(report.roomId)
        server.reports += uid to report
    }

    override suspend fun myRoomIds(): List<String> = online { server.members.filter { it.value.containsKey(uid) }.keys.toList() }

    override suspend fun deleteMyData() = online {
        for (roomId in server.members.filter { it.value.containsKey(uid) }.keys.toList()) {
            val r = server.rooms[roomId]
            if (r != null && r.ownerUid == uid) {
                removeRoom(roomId)
            } else if (r != null) {
                server.messages[roomId]?.removeAll { it.senderUid == uid }
                server.members.getValue(roomId).remove(uid)
                server.rooms[roomId] = r.copy(memberCount = r.memberCount - 1)
            }
        }
        server.profiles.remove(uid)
        Unit
    }
}

/** Signs in as a fixed person without asking anyone. For tests and the practice mode of debug builds. */
class FakeAuth(private var account: SocialUser, signedIn: Boolean = false) : SocialAuth {
    private val _user = MutableStateFlow<SocialUser?>(if (signedIn) account else null)
    override val user: StateFlow<SocialUser?> = _user

    override suspend fun signIn(context: Context): SignInResult { _user.value = account; return SignInResult.Ok(account) }

    override suspend fun signOut() { _user.value = null }

    /** For tests: a different Google account on the same phone becomes the signed in one. */
    fun switchTo(other: SocialUser) { account = other; _user.value = other }

    override suspend fun deleteAccount(context: Context) { _user.value = null }
}
