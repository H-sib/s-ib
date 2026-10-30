package com.hsib.sib.social

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hsib.sib.core.model.Category
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.social.MessageKind
import com.hsib.sib.ui.TestReady
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/** The pretend friend of the practice mode really joins and really answers, through the same sealing as a person. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PracticeFriendTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private var now = 1_760_000_000_000L
    private val server = FakeServer { now }
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun <T> ok(r: SocialResult<T>): T = (r as? SocialResult.Ok)?.value ?: throw AssertionError("expected Ok but got $r")

    @Test
    fun theFriendJoinsTheSharedDeadlineAndAnswersWhatYouWrite() {
        runBlocking {
            val me = SocialUser("practice-me", "You", null)
            val ready = TestReady.create(context, scope, SocialBuilder { db, settings, store, sc ->
                SocialRepository(db, settings, store, FakeBackend(server, me.uid), FakeAuth(me), sc, clock = { now }, practice = true)
            })
            val social = ready.social!!
            social.signIn(context)
            social.acceptTerms()
            ready.store.create(Obligation("o1", "Signals lab report", category = Category.ASSIGNMENT, due = DueSpec(LocalDateTime.of(2026, 10, 16, 17, 0), ZoneId.of("UTC"), Precision.DATE_TIME)))
            val roomId = ok(social.share("o1"))

            val friend = PracticeFriend.create(context, server, social, me.uid, scope, clock = { now })
            now += 3_000
            friend.step()
            assertTrue("the friend joined", server.members.getValue(roomId).containsKey(PracticeFriend.UID))
            assertEquals(2, social.state.value.roomFor("o1")!!.members.size)
            assertEquals("Ayesha (practice)", social.state.value.roomFor("o1")!!.members.single { !it.isMe }.name)

            ok(social.send(roomId, "Shall we split the report?"))
            now += 3_000
            friend.step()
            now += 3_000
            ok(social.refreshChat(roomId))
            val reply = social.chat(roomId).first().last { it.kind == MessageKind.TEXT && !it.mine }
            assertEquals("Ayesha (practice)", reply.senderName)
            assertTrue(reply.text.isNotBlank())

            // Answering the same message twice would be noise.
            now += 3_000
            friend.step()
            now += 3_000
            ok(social.refreshChat(roomId))
            assertEquals(1, social.chat(roomId).first().count { it.kind == MessageKind.TEXT && !it.mine })

            // And the server held only sealed text.
            assertTrue(server.sealedValues().none { it.contains("Signals", ignoreCase = true) || it.contains("split", ignoreCase = true) })
        }
    }
}
