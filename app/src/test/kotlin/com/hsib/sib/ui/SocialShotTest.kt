package com.hsib.sib.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.hsib.sib.Ready
import com.hsib.sib.core.model.Space
import com.hsib.sib.data.ThemeMode
import com.hsib.sib.social.FakeAuth
import com.hsib.sib.social.FakeBackend
import com.hsib.sib.social.FakeServer
import com.hsib.sib.social.SocialBuilder
import com.hsib.sib.social.SocialRepository
import com.hsib.sib.social.SocialResult
import com.hsib.sib.social.SocialUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the sharing screens with a real conversation between simulated phones (real sealing, a fake server in memory), so
 * the design can be judged without a phone. Images are written only with -Precord=true; otherwise this proves the screens
 * compose and show the right words.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class SocialShotTest {
    @get:Rule
    val rule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private var now = Fixtures.now.toEpochMilli()
    private val server = FakeServer { now }
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val st = Fixtures.soon
    private val noImport = ImportActions(paste = {}, photo = {}, scan = {}, file = {})

    private class Phone(val ready: Ready, val social: SocialRepository)

    private fun later(minutes: Int = 2) { now += minutes * 60_000L }

    private fun phone(uid: String, name: String, signedIn: Boolean = true, accept: Boolean = true, practice: Boolean = false, fixtures: Boolean = true): Phone {
        val user = SocialUser(uid, name, "$uid@example.test")
        val ready = TestReady.create(context, scope, SocialBuilder { db, settings, store, sc ->
            SocialRepository(db, settings, store, FakeBackend(server, uid), FakeAuth(user, signedIn), sc, clock = { now }, practice = practice)
        })
        if (signedIn && accept) runBlocking { ready.social!!.acceptTerms() }
        if (fixtures) runBlocking { Fixtures.soon.items.forEach { ready.store.create(it.o) } }
        return Phone(ready, ready.social!!)
    }

    private fun <T> ok(r: SocialResult<T>): T = (r as? SocialResult.Ok)?.value ?: throw AssertionError("expected Ok but got $r")

    /** Maya owns "Signals lab report" and shares it with Sam and Ayesha, who have been talking about it. */
    private fun conversation(chat: Boolean = true): Phone = runBlocking {
        val maya = phone("maya", "Maya Rahman")
        val roomId = ok(maya.social.share("a"))
        val sam = phone("sam", "Sam Carter", fixtures = false)
        val ayesha = phone("ayesha", "Ayesha Khan", fixtures = false)
        for (p in listOf(sam, ayesha)) {
            later()
            ok(p.social.join(ok(maya.social.invite(roomId))))
        }
        if (chat) {
            later(); ok(sam.social.send(roomId, "I can take the plots if you do the write-up"))
            later(); ok(maya.social.syncOne(roomId)); ok(maya.social.send(roomId, "Deal. I will start tonight"))
            later(); ok(ayesha.social.syncOne(roomId)); ok(ayesha.social.send(roomId, "Lab room is free on Thursday at 3, shall I book it?"))
            later(); ok(sam.social.markDone(sam.ready.store.allObligations().single { it.title == "Signals lab report" }.id, true))
            later(); ok(maya.social.send(roomId, "Yes please, book it"))
        }
        later()
        ok(maya.social.sync())
        maya
    }

    private fun shot(name: String, mode: ThemeMode = ThemeMode.DARK, interact: (() -> Unit)? = null, content: @Composable () -> Unit) {
        rule.setContent { SibTheme(mode, Space.WORK) { Box(Modifier.fillMaxSize().background(SibTheme.colors.paper)) { content() } } }
        rule.mainClock.advanceTimeBy(2500)
        rule.waitForIdle()
        interact?.invoke()
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/$name.png")
    }

    // ---- the friends layer --------------------------------------------------------------------------

    @Test fun friendsSignedOutDark() {
        val p = phone("zed", "Zed", signedIn = false)
        shot("friends-signedout-dark") { FriendsScreen(st, p.ready, {}, {}, {}, {}) }
        rule.onNodeWithText("Continue with Google").assertExists()
    }

    @Test fun friendsDark() {
        val maya = conversation()
        shot("friends-dark") { FriendsScreen(st, maya.ready, {}, {}, {}, {}) }
        rule.onNodeWithText("Join with a code").assertExists()
        rule.onNodeWithText("Signals lab report").assertExists()
    }

    @Test fun friendsEmptyDark() {
        val p = phone("lee", "Lee")
        shot("friends-empty-dark") { FriendsScreen(st, p.ready, {}, {}, {}, {}) }
        rule.onNodeWithText("Nothing shared yet. Open a deadline and tap People.").assertExists()
    }

    @Test fun accountDark() {
        val maya = conversation(chat = false)
        shot("account-dark") { AccountScreen(maya.ready, {}, {}) }
        rule.onNodeWithText("Name friends see").assertExists()
    }

    @Test fun accountPracticeDark() {
        val p = phone("practice-me", "You", practice = true)
        shot("account-practice-dark") { AccountScreen(p.ready, {}, {}) }
        rule.onNodeWithText("Practice mode").assertExists()
    }

    @Test fun joinDark() {
        val p = phone("lee", "Lee")
        shot("join-dark") { JoinScreen(p.ready, {}, {}) }
        rule.onNodeWithText("Got a code?").assertExists()
    }

    @Test fun joinSignedOutDark() {
        val p = phone("zed", "Zed", signedIn = false)
        shot("join-signedout-dark") { JoinScreen(p.ready, {}, {}) }
        rule.onNodeWithText("Sign in first").assertExists()
    }

    // ---- one shared deadline ---------------------------------------------------------------------------------

    @Test fun roomNotSharedDark() {
        val p = phone("lee", "Lee")
        shot("room-share-dark") { RoomScreen("a", st, p.ready, {}, {}) }
        rule.onNodeWithText("Share this deadline").assertExists()
    }

    @Test fun roomOwnerDark() {
        val maya = conversation()
        shot("room-owner-dark") { RoomScreen("a", st, maya.ready, {}, {}) }
        rule.onNodeWithText("Maya Rahman (you)").assertExists()
        rule.onNodeWithText("1 of 3 done").assertExists()
    }

    @Test fun roomInviteDark() {
        val maya = conversation(chat = false)
        shot("room-invite-dark", interact = { rule.onNodeWithText("Invite someone").performClick() }) { RoomScreen("a", st, maya.ready, {}, {}) }
        rule.onNodeWithText("Invite code").assertExists()
    }

    @Test fun roomOwnerLight() {
        val maya = conversation()
        shot("room-owner-light", ThemeMode.LIGHT) { RoomScreen("a", st, maya.ready, {}, {}) }
    }

    @Test fun roomOwnerNeon() {
        val maya = conversation()
        shot("room-owner-neon", ThemeMode.NEON) { RoomScreen("a", st, maya.ready, {}, {}) }
    }

    @Test fun rulesAreAskedForBeforeSharing() {
        val p = phone("kim", "Kim", accept = false)
        shot("room-rules-dark", interact = { rule.onNodeWithText("Share this deadline").performClick() }) { RoomScreen("a", st, p.ready, {}, {}) }
        rule.onNodeWithText("Community rules").assertExists()
        rule.onNodeWithText("I agree").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("People").assertExists()
    }

    // ---- the conversation --------------------------------------------------------------------------------------

    @Test fun chatDark() {
        val maya = conversation()
        shot("chat-dark") { ChatScreen("a", st, maya.ready, {}) }
        rule.onNodeWithText("Deal. I will start tonight").assertExists()
    }

    @Test fun chatLight() {
        val maya = conversation()
        shot("chat-light", ThemeMode.LIGHT) { ChatScreen("a", st, maya.ready, {}) }
    }

    @Test fun chatBlack() {
        val maya = conversation()
        shot("chat-black", ThemeMode.BLACK) { ChatScreen("a", st, maya.ready, {}) }
    }

    @Test fun chatNeon() {
        val maya = conversation()
        shot("chat-neon", ThemeMode.NEON) { ChatScreen("a", st, maya.ready, {}) }
    }

    @Test fun chatEmptyDark() {
        val maya = conversation(chat = false)
        shot("chat-empty-dark") { ChatScreen("a", st, maya.ready, {}) }
        rule.onNodeWithText("Say hello.", substring = true).assertExists()
    }

    @Test fun chatMessageActionsDark() {
        val maya = conversation()
        shot("chat-actions-dark", interact = { rule.onNodeWithText("Lab room is free on Thursday at 3, shall I book it?").performClick() }) { ChatScreen("a", st, maya.ready, {}) }
        rule.onNodeWithText("Report").assertExists()
        rule.onNodeWithText("Block Ayesha Khan").assertExists()
    }

    @Test fun reportFormShowsItsChoices() {
        // Shown inline: in a real dialog window the text field takes focus and its blinking cursor keeps the test clock busy.
        shot("report-form-dark") {
            androidx.compose.foundation.layout.Column(Modifier.padding(24.dp)) {
                androidx.compose.material3.Text("Report Ayesha Khan", style = SibType.title, color = SibTheme.colors.ink)
                androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                ReportFields("Ayesha Khan", true, null, com.hsib.sib.core.social.ReportReason.HARASSMENT, {}, "", {}, true, {}, true, {})
            }
        }
        rule.onNodeWithText("Harassment or bullying").assertExists()
        rule.onNodeWithText("Include the message").assertExists()
        rule.onNodeWithText("Also block Ayesha Khan").assertExists()
    }

    // ---- the places it shows up elsewhere ---------------------------------------------------------------------------

    @Test fun detailSharedDark() {
        val maya = conversation()
        shot("detail-shared-dark") { DetailScreen("a", st, maya.ready, {}, {}) }
        rule.onNodeWithContentDescription("People and chat").assertExists()
    }

    @Test fun detailNotSharedDark() {
        val p = phone("lee", "Lee")
        shot("detail-people-dark") { DetailScreen("a", st, p.ready, {}, {}) }
        rule.onNodeWithContentDescription("Share with people").assertExists()
    }

    @Test fun todayWithFriendsDark() {
        val maya = conversation()
        shot("today-friends-dark") { TodayScreen(st, noImport, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, social = rememberSocial(maya.ready), onFriends = {}) }
        rule.onNodeWithText("Friends").assertExists()
    }

    @Test fun todayWithoutSharingIsUnchanged() {
        shot("today-nofriends-dark") { TodayScreen(st, noImport, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}) }
        rule.onNodeWithText("All deadlines").assertExists()
    }

    @Test fun settingsHomeShowsFriends() {
        val maya = conversation(chat = false)
        shot("settings-friends-dark") { SettingsHome(st, social = rememberSocial(maya.ready)) {} }
        rule.onNodeWithText("Friends").assertExists()
    }
}
