package com.hsib.sib.social.firebase

import com.google.firebase.Timestamp
import com.hsib.sib.core.social.ChatRequest
import com.hsib.sib.core.social.InviteInfo
import com.hsib.sib.core.social.MemberInfo
import com.hsib.sib.core.social.MessageInfo
import com.hsib.sib.core.social.MessageKind
import com.hsib.sib.core.social.Profile
import com.hsib.sib.core.social.Report
import com.hsib.sib.core.social.RoomInfo
import com.hsib.sib.core.social.RoomRole
import com.hsib.sib.core.social.SocialLimits

/** The names of the collections and fields, in one place, so the backend and firebase/firestore.rules cannot drift apart. */
internal object Fields {
    const val USERS = "users"
    const val ROOMS = "rooms"
    const val MEMBERS = "members"
    const val MESSAGES = "messages"
    const val INVITES = "invites"
    const val REPORTS = "reports"
    const val CONSENT = "consent"
    const val USERNAMES = "usernames"
    const val EMAIL_LOOKUP = "emailLookup"
    const val CHAT_REQUESTS = "chatRequests"
    const val FROM = "from"
    const val COURSE_USE = "courseUse"
    const val USERNAME = "username"
    const val UID = "uid"
    const val FROM_NAME = "fromName"
    const val COUNT = "count"
    const val NAME = "name"
    const val ACCEPTED_AT = "acceptedAt"
    const val VERSION = "version"
    const val APP = "app"

    const val DISPLAY_NAME = "displayName"
    const val PUBLIC_KEY = "publicKey"
    const val OWNER_UID = "ownerUid"
    const val KEY_VERSION = "keyVersion"
    const val REV = "rev"
    const val SEALED_PAYLOAD = "sealedPayload"
    const val MEMBER_COUNT = "memberCount"
    const val CREATED_AT = "createdAt"
    const val ROLE = "role"
    const val JOINED_AT = "joinedAt"
    const val DONE_AT = "doneAt"
    const val WRAPPED_KEY = "wrappedKey"
    const val WRAPPED_KEY_VERSION = "wrappedKeyVersion"
    const val LAST_MESSAGE_AT = "lastMessageAt"
    const val INVITE_ID = "inviteId"
    const val SENDER_UID = "senderUid"
    const val KIND = "kind"
    const val SEALED_BODY = "sealedBody"
    const val ROOM_ID = "roomId"
    const val CREATED_BY = "createdByUid"
    const val EXPIRES_AT = "expiresAt"
    const val REVOKED = "revoked"
    const val SEALED_KEY = "sealedKey"
}

/**
 * Turns Firestore documents into the app's models and back. Everything read from the server is treated as hostile: a
 * document with a missing field, a field of the wrong type or an oversized sealed value is skipped (null), never half read.
 * Free of any network code so it can be tested on its own.
 */
internal object FirestoreMapping {
    fun millis(v: Any?): Long? = when (v) {
        is Timestamp -> v.seconds * 1000 + v.nanoseconds / 1_000_000
        is Number -> v.toLong()
        else -> null
    }

    private fun text(v: Any?, max: Int): String? = (v as? String)?.takeIf { it.length <= max }

    private fun sealed(v: Any?): String? = (v as? String)?.takeIf { it.isNotEmpty() && it.length <= SocialLimits.MAX_ENVELOPE_CHARS }

    private fun int(v: Any?): Int? = (v as? Number)?.toInt()

    // ---- profiles --------------------------------------------------------------------------------------

    fun profile(uid: String, d: Map<String, Any?>): Profile? {
        val name = text(d[Fields.DISPLAY_NAME], SocialLimits.DISPLAY_NAME_CHARS)?.takeIf { it.isNotBlank() } ?: return null
        val key = (d[Fields.PUBLIC_KEY] as? String)?.takeIf { it.length == 43 } ?: return null
        val handle = (d[Fields.USERNAME] as? String)?.takeIf { Regex("[a-z0-9_.]{3,20}").matches(it) }
        return Profile(uid, name, key, handle)
    }

    fun profileData(p: Profile): Map<String, Any?> =
        buildMap { put(Fields.DISPLAY_NAME, p.displayName); put(Fields.PUBLIC_KEY, p.publicKey); p.username?.let { put(Fields.USERNAME, it) } }

    // ---- chat requests -----------------------------------------------------------------------------------

    fun chatRequest(toUid: String, fromUid: String, d: Map<String, Any?>): ChatRequest? = ChatRequest(
        fromUid = fromUid, toUid = toUid,
        fromName = text(d[Fields.FROM_NAME], SocialLimits.DISPLAY_NAME_CHARS)?.takeIf { it.isNotBlank() } ?: return null,
        roomId = text(d[Fields.ROOM_ID], 128)?.takeIf { it.isNotEmpty() } ?: return null,
        inviteId = text(d[Fields.INVITE_ID], 10)?.takeIf { it.length == 10 } ?: return null,
        wrappedKey = text(d[Fields.WRAPPED_KEY], 512)?.takeIf { it.isNotEmpty() } ?: return null,
        keyVersion = int(d[Fields.KEY_VERSION]) ?: return null,
        createdAtMs = millis(d[Fields.CREATED_AT]) ?: 0,
    )

    // ---- rooms -----------------------------------------------------------------------------------------

    fun room(id: String, d: Map<String, Any?>): RoomInfo? = RoomInfo(
        id = id,
        ownerUid = text(d[Fields.OWNER_UID], 128) ?: return null,
        keyVersion = int(d[Fields.KEY_VERSION])?.takeIf { it >= 1 } ?: return null,
        rev = (d[Fields.REV] as? Number)?.toLong()?.takeIf { it >= 1 } ?: return null,
        sealedPayload = sealed(d[Fields.SEALED_PAYLOAD]) ?: return null,
        memberCount = int(d[Fields.MEMBER_COUNT])?.takeIf { it in 0..SocialLimits.MEMBERS_PER_ROOM } ?: return null,
        createdAtMs = millis(d[Fields.CREATED_AT]) ?: 0,
    )

    // ---- members ---------------------------------------------------------------------------------------

    fun member(roomId: String, uid: String, d: Map<String, Any?>): MemberInfo? = MemberInfo(
        uid = uid,
        roomId = roomId,
        role = when (d[Fields.ROLE]) { "OWNER" -> RoomRole.OWNER; "MEMBER" -> RoomRole.MEMBER; else -> return null },
        displayName = text(d[Fields.DISPLAY_NAME], SocialLimits.DISPLAY_NAME_CHARS)?.takeIf { it.isNotBlank() } ?: return null,
        joinedAtMs = millis(d[Fields.JOINED_AT]) ?: 0,
        doneAtMs = millis(d[Fields.DONE_AT]),
        wrappedKey = text(d[Fields.WRAPPED_KEY], 512),
        wrappedKeyVersion = int(d[Fields.WRAPPED_KEY_VERSION]) ?: 0,
        lastMessageAtMs = millis(d[Fields.LAST_MESSAGE_AT]) ?: 0,
    )

    // ---- messages --------------------------------------------------------------------------------------

    fun message(roomId: String, id: String, d: Map<String, Any?>): MessageInfo? = MessageInfo(
        id = id,
        roomId = roomId,
        senderUid = text(d[Fields.SENDER_UID], 128) ?: return null,
        createdAtMs = millis(d[Fields.CREATED_AT]) ?: return null,
        kind = when (d[Fields.KIND]) { "TEXT" -> MessageKind.TEXT; "SYSTEM" -> MessageKind.SYSTEM; else -> return null },
        sealedBody = sealed(d[Fields.SEALED_BODY]) ?: return null,
        keyVersion = int(d[Fields.KEY_VERSION]) ?: return null,
    )

    // ---- invites ---------------------------------------------------------------------------------------

    fun invite(id: String, d: Map<String, Any?>): InviteInfo? = InviteInfo(
        id = id,
        roomId = text(d[Fields.ROOM_ID], 128) ?: return null,
        createdByUid = text(d[Fields.CREATED_BY], 128) ?: return null,
        expiresAtMs = millis(d[Fields.EXPIRES_AT]) ?: return null,
        revoked = d[Fields.REVOKED] as? Boolean ?: return null,
        sealedKey = text(d[Fields.SEALED_KEY], 512)?.takeIf { it.isNotEmpty() } ?: return null,
        keyVersion = int(d[Fields.KEY_VERSION]) ?: return null,
    )

    // ---- reports ---------------------------------------------------------------------------------------

    /** The fields of a report except the time, which the backend asks the server to fill in. */
    fun reportData(r: Report, reporterUid: String): Map<String, Any?> = buildMap {
        put(Fields.ROOM_ID, r.roomId)
        put("reportedUid", r.reportedUid)
        put("reporterUid", reporterUid)
        r.messageId?.let { put("messageId", it) }
        put("reason", r.reason.name)
        put("detail", r.detail)
        r.quote?.let { put("quote", it) }
    }
}
