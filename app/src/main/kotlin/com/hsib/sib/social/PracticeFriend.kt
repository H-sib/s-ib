package com.hsib.sib.social

import android.content.Context
import androidx.room.Room
import com.hsib.sib.core.social.MessageKind
import com.hsib.sib.data.BlobFiles
import com.hsib.sib.data.HistoryKeys
import com.hsib.sib.data.SettingsStore
import com.hsib.sib.data.SibDatabase
import com.hsib.sib.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * A pretend friend for the practice mode of debug builds, so the whole flow (share, invite, join, chat) can be tried on one
 * phone. It is a second, complete phone of its own: its own database, its own keys, the same sealing as a real person, talking
 * to the same in-memory server. It joins every deadline the person shares and answers what they write. Release builds never
 * contain a reachable copy of this.
 */
internal class PracticeFriend private constructor(
    private val owner: SocialRepository,
    private val ownerUid: String,
    private val friend: SocialRepository,
) {
    private val joined = HashSet<String>()
    private val answered = HashSet<String>()

    /** One pass: join the deadlines the person shares, then answer the newest thing they said in each. */
    suspend fun step() {
        for (room in owner.state.value.rooms.values) {
            if (!room.isOwner || room.removed) continue
            if (room.roomId !in joined) {
                val code = (owner.invite(room.roomId) as? SocialResult.Ok)?.value ?: continue
                val result = friend.join(code)
                if (result is SocialResult.Ok || (result as? SocialResult.Err)?.error == SocialError.ALREADY_IN) joined += room.roomId
                owner.syncOne(room.roomId)
                continue
            }
            friend.refreshChat(room.roomId)
            val next = friend.chat(room.roomId).first().firstOrNull { it.kind == MessageKind.TEXT && it.senderUid == ownerUid && it.id !in answered } ?: continue
            answered += next.id
            friend.send(room.roomId, replyTo(next.text))
        }
    }

    private fun replyTo(text: String): String = REPLIES[Math.floorMod(text.hashCode(), REPLIES.size)]

    companion object {
        const val UID = "practice-ayesha"
        const val NAME = "Ayesha (practice)"

        private val REPLIES = listOf(
            "Sounds good.",
            "I can do Thursday afternoon.",
            "I will start tonight and send you my part.",
            "Can we check in again tomorrow?",
            "Nice, thank you.",
            "Done on my side, have a look.",
        )

        suspend fun create(
            context: Context, server: FakeServer, owner: SocialRepository, ownerUid: String, scope: CoroutineScope,
            clock: () -> Long = { System.currentTimeMillis() },
        ): PracticeFriend {
            val db = Room.inMemoryDatabaseBuilder(context.applicationContext, SibDatabase::class.java).build()
            val settings = SettingsStore(db.settings())
            val keys = HistoryKeys(settings)
            keys.load()
            val store = Store(db, keys, BlobFiles(File(context.cacheDir, "practice-friend"), ByteArray(32)))
            val repository = SocialRepository(db, settings, store, FakeBackend(server, UID), FakeAuth(SocialUser(UID, NAME, null), signedIn = true), scope, clock = clock, practice = true)
            repository.acceptTerms()
            repository.signIn(context)
            return PracticeFriend(owner, ownerUid, repository)
        }
    }
}
