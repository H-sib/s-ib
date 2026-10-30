package com.hsib.sib.core.social

import com.hsib.sib.core.model.Limits

enum class RoomRole { OWNER, MEMBER }

enum class MessageKind { TEXT, SYSTEM }

/** A person's public profile. The public key is what other members seal the room key for. */
data class Profile(val uid: String, val displayName: String, val publicKey: String)

/** One shared deadline as the server holds it. The details are sealed; the server cannot read them. */
data class RoomInfo(
    val id: String,
    val ownerUid: String,
    val keyVersion: Int,
    val rev: Long,
    val sealedPayload: String,
    val memberCount: Int,
    val createdAtMs: Long,
)

data class MemberInfo(
    val uid: String,
    val roomId: String,
    val role: RoomRole,
    val displayName: String,
    val joinedAtMs: Long,
    /** When this person marked their own part done, or null. */
    val doneAtMs: Long?,
    /** The room key sealed for this person, for the key version it was made for. */
    val wrappedKey: String?,
    val wrappedKeyVersion: Int,
    val lastMessageAtMs: Long,
)

data class MessageInfo(
    val id: String,
    val roomId: String,
    val senderUid: String,
    val createdAtMs: Long,
    val kind: MessageKind,
    val sealedBody: String,
    val keyVersion: Int,
)

data class InviteInfo(
    val id: String,
    val roomId: String,
    val createdByUid: String,
    val expiresAtMs: Long,
    val revoked: Boolean,
    /** The room key sealed with the invite's secret. */
    val sealedKey: String,
    val keyVersion: Int,
)

/** A message after it has been opened, ready to show. */
data class ChatMessage(
    val id: String,
    val roomId: String,
    val senderUid: String,
    val senderName: String,
    val atMs: Long,
    val text: String,
    val kind: MessageKind,
    val mine: Boolean,
)

enum class ReportReason(val label: String) {
    HARASSMENT("Harassment or bullying"),
    HATE("Hate or threats"),
    SEXUAL("Sexual or explicit content"),
    SPAM("Spam or scams"),
    ILLEGAL("Anything illegal"),
    OTHER("Something else"),
}

/** What is sent to the developer when someone reports a person or a message. The reporter chooses to include the text. */
data class Report(
    val roomId: String,
    val reportedUid: String,
    val messageId: String?,
    val reason: ReportReason,
    val detail: String,
    /** The readable message, included only because the reporter agreed to, since the server cannot read chat. */
    val quote: String?,
)

/** The short rules everyone agrees to before joining or chatting. Kept here so the app and the web page say the same thing. */
object CommunityRules {
    val rules: List<String> = listOf(
        "Be kind. No harassment, bullying, hate or threats.",
        "Nothing sexual, violent, illegal or misleading, and no spam.",
        "Only invite people you know. Share an invite code only with the people it is for.",
        "Everyone in a shared deadline can read its chat. Do not post what you would not want them to see.",
        "You can block a person or report a message at any time. Reports go to the developer, who may remove people or shared deadlines.",
        "You must be 13 or older.",
    )
}

/** Cleaning, limits and the small decisions the rules make, kept as pure functions so they can be tested without a phone. */
object SocialRules {
    fun cleanName(raw: String): String? = Limits.line(raw, SocialLimits.DISPLAY_NAME_CHARS).ifEmpty { null }

    fun cleanMessage(raw: String): String? = Limits.text(raw, SocialLimits.MESSAGE_CHARS).ifEmpty { null }

    fun cleanReport(r: Report): Report = r.copy(
        detail = Limits.text(r.detail, SocialLimits.REPORT_DETAIL_CHARS),
        quote = r.quote?.let { Limits.text(it, SocialLimits.REPORT_QUOTE_CHARS) }?.ifEmpty { null },
    )

    /** One message a second per person, the same rule the server applies. */
    fun canSendNow(lastMessageAtMs: Long, nowMs: Long): Boolean = nowMs - lastMessageAtMs >= SocialLimits.MESSAGE_GAP_MS

    fun inviteExpiry(nowMs: Long): Long = nowMs + SocialLimits.INVITE_DAYS * 24 * 60 * 60 * 1000

    sealed interface JoinCheck {
        data object Ok : JoinCheck
        data object Expired : JoinCheck
        data object Revoked : JoinCheck
        data object RoomFull : JoinCheck
        data object AlreadyIn : JoinCheck
        data object TooManyRooms : JoinCheck
    }

    fun checkJoin(invite: InviteInfo, nowMs: Long, memberCount: Int, alreadyMember: Boolean, myRoomCount: Int): JoinCheck = when {
        alreadyMember -> JoinCheck.AlreadyIn
        invite.revoked -> JoinCheck.Revoked
        invite.expiresAtMs <= nowMs -> JoinCheck.Expired
        memberCount >= SocialLimits.MEMBERS_PER_ROOM -> JoinCheck.RoomFull
        myRoomCount >= SocialLimits.ROOMS_PER_PERSON -> JoinCheck.TooManyRooms
        else -> JoinCheck.Ok
    }

    /** Messages by time then id, one per id, so syncing the same page twice or out of order gives the same list. */
    fun mergeMessages(existing: List<MessageInfo>, incoming: List<MessageInfo>): List<MessageInfo> =
        (existing + incoming).associateBy { it.id }.values.sortedWith(compareBy({ it.createdAtMs }, { it.id }))

    /** Hides what blocked people wrote. System lines stay, so the history still makes sense. */
    fun visible(messages: List<ChatMessage>, blocked: Set<String>): List<ChatMessage> =
        messages.filter { it.kind == MessageKind.SYSTEM || it.senderUid !in blocked || it.mine }

    /** "2 of 4 done", or null when nobody has finished yet and the line would only add noise. */
    fun doneSummary(members: List<MemberInfo>): String? {
        val done = members.count { it.doneAtMs != null }
        return if (done == 0) null else "$done of ${members.size} done"
    }

    /** The owner's version wins: a payload is applied only if it is newer than the one already applied. */
    fun shouldApply(appliedRev: Long, remoteRev: Long): Boolean = remoteRev > appliedRev
}
