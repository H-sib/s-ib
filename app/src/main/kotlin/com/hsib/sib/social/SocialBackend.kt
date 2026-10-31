package com.hsib.sib.social

import android.content.Context
import com.hsib.sib.core.social.ChatRequest
import com.hsib.sib.core.social.InviteInfo
import com.hsib.sib.core.social.MemberInfo
import com.hsib.sib.core.social.MessageInfo
import com.hsib.sib.core.social.Profile
import com.hsib.sib.core.social.Report
import com.hsib.sib.core.social.RoomInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * Why something with sharing did not work, in words that can be shown as they are. The server side raises these codes and
 * the screens never have to guess from a stack trace.
 */
enum class SocialError(val text: String) {
    OFFLINE("There is no connection right now. Try again when you are online."),
    NOT_SIGNED_IN("Sign in first."),
    NOT_FOUND("That did not match anything. Check it and try again."),
    BAD_CODE("That code does not look right. Check it and try again."),
    EXPIRED("That invite has expired. Ask for a new one."),
    REVOKED("That invite was cancelled. Ask for a new one."),
    ROOM_FULL("This shared deadline already has the most people it can have."),
    TOO_MANY_ROOMS("You have reached the limit of shared deadlines. Leave one first."),
    ALREADY_IN("You are already in this shared deadline."),
    JOIN_REFUSED("You could not join. The invite may be full, expired or cancelled. Ask for a new one."),
    DENIED("You are not allowed to do that."),
    RATE_LIMITED("Slow down a little and try again."),
    CLOSE_ONES("Close ones deadlines are private and cannot be shared."),
    CANNOT_READ("This phone cannot read this shared deadline yet. Open the app again in a moment."),
    TERMS("Accept the community rules first."),
    NO_GOOGLE_ACCOUNT("There is no Google account on this phone. Add one in Android settings, then try again."),
    SIGN_IN_FAILED("Google sign in did not work. Check the connection and try again."),
    BAD_USERNAME("Use 3 to 20 letters, numbers, dots or underscores."),
    USERNAME_TAKEN("That username is taken. Try another."),
    BLOCKED("You blocked this person. Unblock them in Account first."),
    FAILED("Something went wrong. Try again."),
}

class SocialException(val error: SocialError) : Exception(error.text)

sealed interface SocialResult<out T> {
    data class Ok<T>(val value: T) : SocialResult<T>
    data class Err(val error: SocialError) : SocialResult<Nothing>
}

data class SocialUser(val uid: String, val displayName: String, val email: String?)

sealed interface SignInResult {
    data class Ok(val user: SocialUser) : SignInResult
    data object Cancelled : SignInResult
    data class Failed(val error: SocialError) : SignInResult
}

/** The sign in side. The real one is Google sign in through Android's Credential Manager and Firebase Auth. */
interface SocialAuth {
    /** The signed in person, or null. Stays null in builds and phones where sharing is not available. */
    val user: StateFlow<SocialUser?>

    suspend fun signIn(context: Context): SignInResult

    suspend fun signOut()

    /**
     * Deletes the sign in account itself. The caller deletes the person's data first. If the service wants a fresh sign in
     * before it will delete an account, this asks for one, which is why it needs a window to show it in.
     */
    suspend fun deleteAccount(context: Context)
}

/**
 * Everything the app asks of the server. Each call acts as the signed in person. A denied or impossible call throws a
 * [SocialException] with the reason. The real implementation is Firestore; the server rules do the permission checks, and
 * [FakeBackend] applies the same rules in memory so the whole flow can be tested.
 */
interface SocialBackend {
    suspend fun saveProfile(profile: Profile)

    /**
     * Records once, with the service's own clock, that this account accepted the community rules of [version], so acceptance can
     * be shown later. Holds no content. Calling it again for the same version changes nothing.
     */
    suspend fun recordConsent(version: Int, app: String)

    suspend fun profiles(uids: Set<String>): Map<String, Profile>

    /** Creates the room with its owner as the first member. */
    suspend fun createRoom(room: RoomInfo, owner: MemberInfo)

    /** The room, readable only by its members. Null if it no longer exists. */
    suspend fun room(roomId: String): RoomInfo?

    /** The owner publishes a new sealed payload. [rev] must be higher than the stored one. */
    suspend fun updatePayload(roomId: String, rev: Long, sealedPayload: String)

    suspend fun members(roomId: String): List<MemberInfo>

    suspend fun createInvite(invite: InviteInfo)

    /** An invite by its public id, readable by any signed in person who knows the id. */
    suspend fun invite(inviteId: String): InviteInfo?

    suspend fun revokeInvite(inviteId: String)

    /** Joins through an invite. The server checks the invite, the member limit and the person's room limit. */
    suspend fun join(inviteId: String, member: MemberInfo)

    suspend fun setDone(roomId: String, doneAtMs: Long?)

    /** A member leaves. The owner cannot leave, they delete the room. */
    suspend fun leave(roomId: String)

    /** The owner deletes the room for everyone. */
    suspend fun deleteRoom(roomId: String)

    /**
     * The owner removes a member and starts a new key version. [wrapped] has the new room key sealed for every person who
     * stays, and is stored on their member records in the same step. [sealedPayload] is the deadline's details sealed again
     * under the new key, so someone who joins later with only the new key can still read them.
     */
    suspend fun remove(roomId: String, targetUid: String, newKeyVersion: Int, wrapped: Map<String, String>, sealedPayload: String)

    suspend fun messages(roomId: String, afterMs: Long, limit: Int): List<MessageInfo>

    /** Stores a message. The server sets the time, so the returned message is the one that was stored. */
    suspend fun send(message: MessageInfo): MessageInfo

    suspend fun deleteMessage(roomId: String, messageId: String)

    suspend fun report(report: Report)

    /** The rooms this person belongs to, so a new phone or a restore finds them again. */
    suspend fun myRoomIds(): List<String>

    // ---- finding people and asking to chat --------------------------------------------------------------

    /** Takes [name] as this person's username and gives up [previous]. Refused when someone else has it. Null [name] only gives up the old one. */
    suspend fun setUsername(name: String?, previous: String?)

    /** The account that has this username, or null. Found only by exact name; there is no way to list names. */
    suspend fun userByUsername(name: String): String?

    /** The account that allowed itself to be found by the hash of its email, or null. Found only by exact match. */
    suspend fun userByEmailHash(hash: String): String?

    /** Lets people find this account by the hash of its email, or stops that. */
    suspend fun setEmailLookup(hash: String, on: Boolean)

    /** Asks someone to chat. The request holds the new chat's key sealed for that person's own phone key. One waiting request per pair. */
    suspend fun sendChatRequest(request: ChatRequest)

    /** The requests waiting for this person. */
    suspend fun chatRequests(): List<ChatRequest>

    /** Removes a request. The person it was sent to can remove it, and so can the sender. */
    suspend fun deleteChatRequest(toUid: String, fromUid: String)

    /** Adds this person once to the anonymous count of people using the setup with this content hash. Refused quietly if already counted. */
    suspend fun countCourseUse(courseHash: String, name: String)

    /** Removes everything the server holds about this person: memberships, their messages, their profile, rooms they own. */
    suspend fun deleteMyData()
}
