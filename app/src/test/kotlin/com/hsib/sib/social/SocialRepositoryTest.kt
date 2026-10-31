package com.hsib.sib.social

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hsib.sib.Ready
import com.hsib.sib.core.model.Category
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Effort
import com.hsib.sib.core.model.Importance
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.model.Space
import com.hsib.sib.core.social.SocialLimits
import com.hsib.sib.core.social.DeviceKeyPair
import com.hsib.sib.core.social.InviteCode
import com.hsib.sib.core.social.MessageInfo
import com.hsib.sib.core.social.MessageKind
import com.hsib.sib.core.social.Purpose
import com.hsib.sib.core.social.ReportReason
import com.hsib.sib.core.social.RoomKey
import com.hsib.sib.core.social.SealContext
import com.hsib.sib.core.social.SocialCrypto
import com.hsib.sib.data.SOCIAL_DEVICE_KEY
import com.hsib.sib.ui.TestReady
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The whole sharing flow with real sealing and key wrapping, between simulated phones that each have their own database and
 * share one in-memory server. The server applies the same rules the real one will, and the tests check it never holds
 * readable text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SocialRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private var now = 1_760_000_000_000L
    private val server = FakeServer { now }
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun later(ms: Long = 2_000) { now += ms }

    /** A test body that may call suspend functions and always returns nothing, which JUnit requires of a test method. */
    private fun scenario(block: suspend () -> Unit) { runBlocking { block() } }

    private inner class Phone(val uid: String, val name: String, signedIn: Boolean = true, acceptTerms: Boolean = true) {
        val user = SocialUser(uid, name, "$uid@example.test")
        val auth = FakeAuth(user, signedIn)
        val ready: Ready = TestReady.create(context, scope, SocialBuilder { db, settings, store, sc ->
            SocialRepository(db, settings, store, FakeBackend(server, uid), auth, sc, clock = { now })
        })
        val social: SocialRepository = ready.social!!
        val backend = FakeBackend(server, uid)

        init { if (signedIn && acceptTerms) runBlocking { social.acceptTerms() } }
    }

    private val due = DueSpec(LocalDateTime.of(2026, 10, 16, 17, 0), ZoneId.of("Asia/Dhaka"), Precision.DATE_TIME)
    private fun deadline(id: String = "o1", title: String = "Signals lab report") =
        Obligation(id = id, title = title, notes = "Bring the draft", category = Category.ASSIGNMENT, due = due)

    private fun <T> ok(r: SocialResult<T>): T = (r as? SocialResult.Ok)?.value ?: throw AssertionError("expected Ok but got $r")
    private fun errorOf(r: SocialResult<*>): SocialError = (r as? SocialResult.Err)?.error ?: throw AssertionError("expected an error but got $r")

    /** Ana owns a shared deadline and each name in [others] has joined it. Returns the room id. */
    private fun room(ana: Phone, vararg others: Phone): String = runBlocking {
        ana.ready.store.create(deadline())
        val roomId = ok(ana.social.share("o1"))
        for (p in others) {
            later()
            ok(p.social.join(ok(ana.social.invite(roomId))))
        }
        ana.social.sync()
        roomId
    }

    private fun chat(p: Phone, roomId: String) = runBlocking { p.social.chat(roomId).first() }

    @Test
    fun acceptingTheRulesLeavesOneRecordOnTheServerForThatVersion() = scenario {
        val ana = Phone("ana", "Ana")
        assertEquals(setOf(SocialLimits.TERMS_VERSION), server.consents["ana"]?.keys)
        ana.social.sync()
        ana.social.sync()
        assertEquals(1, server.consents["ana"]?.size)
        assertTrue(server.consents["ben"] == null)
    }

    @Test
    fun anotherAccountOnTheSamePhoneNeverSeesTheFirstOnesRooms() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        room(ana, ben)
        assertTrue(ana.social.state.value.roomFor("o1") != null)

        ana.auth.switchTo(SocialUser("cat", "Cat", "cat@example.test"))
        ana.social.sync()
        assertTrue(ana.social.state.value.roomFor("o1") == null)
    }

    // ---- the main flow ------------------------------------------------------------------------------

    @Test
    fun aFriendJoinsWithACodeAndTheyChat() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        val roomId = room(ana, ben)

        // Ben has the deadline on his phone, as a normal deadline, with his own defaults.
        val bens = ben.ready.store.allObligations().single()
        assertEquals("Signals lab report", bens.title)
        assertEquals(due, bens.due)
        assertEquals(Importance.NORMAL, bens.importance)
        assertEquals(setOf("Ana", "Ben"), ben.social.state.value.roomFor(bens.id)!!.members.map { it.name }.toSet())
        assertTrue(ana.social.state.value.roomFor("o1")!!.isOwner)
        assertFalse(ben.social.state.value.roomFor(bens.id)!!.isOwner)

        ok(ben.social.send(roomId, "I can do Thursday"))
        later()
        ok(ana.social.syncOne(roomId))
        val seen = chat(ana, roomId).single { it.kind == MessageKind.TEXT }
        assertEquals("I can do Thursday", seen.text)
        assertEquals("Ben", seen.senderName)
        assertFalse(seen.mine)
        assertEquals(1, ana.social.state.value.roomFor("o1")!!.unread)
        ana.social.markRead(roomId)
        assertEquals(0, ana.social.state.value.roomFor("o1")!!.unread)

        ok(ana.social.send(roomId, "Thursday works"))
        later()
        ok(ben.social.refreshChat(roomId))
        assertEquals(listOf("I can do Thursday", "Thursday works"), chat(ben, roomId).filter { it.kind == MessageKind.TEXT }.map { it.text })

        // The server held only sealed text: not the title, the notes, nor a single word of the chat.
        val stored = server.sealedValues()
        assertTrue(stored.size >= 4)
        for (secret in listOf("Signals", "Bring the draft", "Thursday", "lab report")) {
            assertTrue("the server could read '$secret'", stored.none { it.contains(secret, ignoreCase = true) })
        }
    }

    @Test
    fun theOwnerChangesTheDateAndEveryoneFollowsWhileKeepingTheirOwnPlan() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        val roomId = room(ana, ben)
        val bensId = ben.ready.store.allObligations().single().id
        ben.ready.store.save(ben.ready.store.obligation(bensId)!!.copy(importance = Importance.HIGH, effort = Effort.fromLikely(120)), "USER", "adjusted")

        val moved = due.copy(local = LocalDateTime.of(2026, 10, 20, 9, 0))
        ana.ready.store.save(ana.ready.store.obligation("o1")!!.copy(title = "Signals lab report, final", due = moved), "USER", "date changed")
        later()
        ok(ana.social.pushIfShared("o1", "moved the date"))
        later()
        ok(ben.social.sync())

        val bens = ben.ready.store.obligation(bensId)!!
        assertEquals("Signals lab report, final", bens.title)
        assertEquals(moved, bens.due)
        assertEquals(Importance.HIGH, bens.importance)
        assertEquals(Effort.fromLikely(120), bens.effort)
        val entry = ben.ready.store.history(bensId).first().last { it.field == "due" }
        assertEquals("SHARED", entry.actor)
        assertTrue(entry.reason.contains("Ana"))
        assertTrue(chat(ben, roomId).any { it.kind == MessageKind.SYSTEM && it.text == "moved the date" && it.senderName == "Ana" })
    }

    @Test
    fun onlyTheOwnersSharedFieldsAreSentAndOnlyWhenTheyChange() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        val roomId = room(ana, ben)
        val bensId = ben.ready.store.allObligations().single().id
        val rev = { server.rooms.getValue(roomId).rev }
        assertEquals(1L, rev())

        // A personal change by the owner is not a new revision.
        ana.ready.store.save(ana.ready.store.obligation("o1")!!.copy(importance = Importance.CRITICAL, effort = Effort.fromLikely(60)), "USER", "adjusted")
        ok(ana.social.pushIfShared("o1"))
        assertEquals(1L, rev())

        // A member's change is never sent, and the owner's next update overwrites it.
        ben.ready.store.save(ben.ready.store.obligation(bensId)!!.copy(title = "My private rename"), "USER", "renamed")
        ok(ben.social.pushIfShared(bensId))
        assertEquals(1L, rev())

        ana.ready.store.save(ana.ready.store.obligation("o1")!!.copy(notes = "New notes"), "USER", "edited")
        later()
        ok(ana.social.pushIfShared("o1"))
        assertEquals(2L, rev())
        later()
        ok(ben.social.sync())
        assertEquals("Signals lab report", ben.ready.store.obligation(bensId)!!.title)
        assertEquals("New notes", ben.ready.store.obligation(bensId)!!.notes)
    }

    @Test
    fun finishingYourPartIsSharedWithTheRoom() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        val roomId = room(ana, ben)
        val bensId = ben.ready.store.allObligations().single().id
        assertNull(ana.social.state.value.roomFor("o1")!!.doneLine)

        later()
        ok(ben.social.markDone(bensId, true))
        later()
        ok(ana.social.sync())
        val view = ana.social.state.value.roomFor("o1")!!
        assertEquals("1 of 2 done", view.doneLine)
        assertTrue(view.members.single { it.uid == "ben" }.done)
        assertTrue(chat(ana, roomId).any { it.text == "finished their part" && it.senderName == "Ben" })

        later()
        ok(ben.social.markDone(bensId, false))
        later()
        ok(ana.social.sync())
        assertNull(ana.social.state.value.roomFor("o1")!!.doneLine)
    }

    // ---- the rules, seen from the phone ----------------------------------------------------------------

    @Test
    fun removingSomeoneLocksThemOutAndLeavesTheOthersChatting() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        val cy = Phone("cy", "Cy")
        val roomId = room(ana, ben, cy)
        ok(ben.social.send(roomId, "before the change"))
        later()
        ok(cy.social.sync())

        later()
        ok(ana.social.removeMember(roomId, "ben"))
        assertEquals(2, server.rooms.getValue(roomId).keyVersion)
        later()
        ok(cy.social.sync())
        assertEquals(2, cy.ready.db.rooms().all().first { it.roomId == roomId }.keyVersion)
        ok(cy.social.send(roomId, "after the change"))
        later()
        ok(ana.social.refreshChat(roomId))
        assertTrue(chat(ana, roomId).any { it.text == "after the change" })

        // Ben finds out the next time his phone asks, and can neither read nor write.
        ok(ben.social.sync())
        val view = ben.social.state.value.roomFor(ben.ready.store.allObligations().single().id)!!
        assertTrue(view.removed)
        later()
        assertEquals(SocialError.DENIED, errorOf(ben.social.send(roomId, "let me back in")))
        assertTrue(server.messages.getValue(roomId).none { it.senderUid == "ben" && it.keyVersion == 2 })
    }

    @Test
    fun anInviteMadeBeforeAChangeIsRefusedAndANewOneWorks() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        val roomId = room(ana, ben)
        val before = ok(ana.social.invite(roomId))
        ok(ben.social.send(roomId, "old history"))
        later()
        ok(ana.social.removeMember(roomId, "ben"))
        later()

        val dan = Phone("dan", "Dan")
        // Removing someone also forgets the invites this phone made, but the old one is still on the server: the key check refuses it.
        assertEquals(SocialError.JOIN_REFUSED, errorOf(dan.social.join(before)))
        val fresh = ok(ana.social.invite(roomId))
        later()
        ok(dan.social.join(fresh))
        assertEquals("Signals lab report", dan.ready.store.allObligations().single().title)
        ok(ana.social.send(roomId, "welcome Dan"))
        later()
        ok(dan.social.sync())
        val texts = chat(dan, roomId).map { it.text }
        assertTrue(texts.contains("welcome Dan"))
        assertFalse("a new member must not read what was said under the old key", texts.contains("old history"))
    }

    @Test
    fun leavingKeepsTheDeadlineAndDeletingForEveryoneEndsTheRoom() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        val cy = Phone("cy", "Cy")
        val roomId = room(ana, ben, cy)
        val bensId = ben.ready.store.allObligations().single().id

        ok(ben.social.leaveOrDelete(roomId))
        assertEquals(2, server.rooms.getValue(roomId).memberCount)
        assertNull(ben.social.state.value.roomFor(bensId))
        assertNotNull("the deadline stays as a private one", ben.ready.store.obligation(bensId))

        later()
        ok(ana.social.leaveOrDelete(roomId))
        assertEquals(0, server.roomCount())
        later()
        ok(cy.social.sync())
        val cysId = cy.ready.store.allObligations().single().id
        assertTrue(cy.social.state.value.roomFor(cysId)!!.removed)
        cy.social.dismissRemoved(roomId)
        assertNull(cy.social.state.value.roomFor(cysId))
        assertNotNull(cy.ready.store.obligation(cysId))
    }

    @Test
    fun sharingNeedsTheRulesToBeAccepted() = scenario {
        val lone = Phone("lone", "Lone", acceptTerms = false)
        lone.ready.store.create(deadline())
        assertEquals(SocialError.TERMS, errorOf(lone.social.share("o1")))
        assertEquals(SocialError.TERMS, errorOf(lone.social.join(InviteCode.generate().text())))
        assertEquals(0, server.roomCount())
        assertFalse(lone.social.state.value.termsAccepted)
        lone.social.acceptTerms()
        assertTrue(lone.social.state.value.termsAccepted)
        ok(lone.social.share("o1"))
    }

    @Test
    fun onlyOrdinaryActiveDeadlinesCanBeSharedAndOnlyOnce() = scenario {
        val ana = Phone("ana", "Ana")
        ana.ready.store.create(deadline("close1", "Anniversary").copy(space = Space.CLOSE, category = Category.ANNIVERSARY))
        assertEquals(SocialError.CLOSE_ONES, errorOf(ana.social.share("close1")))
        assertEquals(SocialError.NOT_FOUND, errorOf(ana.social.share("missing")))
        ana.ready.store.create(deadline("o2"))
        ok(ana.social.share("o2"))
        assertEquals(SocialError.ALREADY_IN, errorOf(ana.social.share("o2")))
        ana.ready.store.create(deadline("o3"))
        ana.ready.store.complete("o3", null, null)
        assertEquals(SocialError.FAILED, errorOf(ana.social.share("o3")))
    }

    @Test
    fun signedOutPeopleCannotDoAnything() = scenario {
        val out = Phone("out", "Out", signedIn = false)
        out.ready.store.create(deadline())
        assertEquals(SocialError.NOT_SIGNED_IN, errorOf(out.social.share("o1")))
        assertEquals(SocialError.NOT_SIGNED_IN, errorOf(out.social.join(InviteCode.generate().text())))
        ok(out.social.sync())
        ok(out.social.pushIfShared("o1"))
    }

    @Test
    fun noConnectionIsReportedAndNothingIsHalfDone() = scenario {
        val ana = Phone("ana", "Ana")
        ana.ready.store.create(deadline())
        server.online = false
        assertEquals(SocialError.OFFLINE, errorOf(ana.social.share("o1")))
        assertNull(ana.social.state.value.roomFor("o1"))
        server.online = true
        val roomId = ok(ana.social.share("o1"))
        server.online = false
        later()
        assertEquals(SocialError.OFFLINE, errorOf(ana.social.send(roomId, "hello")))
        assertEquals(SocialError.OFFLINE, errorOf(ana.social.sync()))
        server.online = true
        ok(ana.social.send(roomId, "hello"))
    }

    @Test
    fun messagesAreLimitedToOneASecondAndCleaned() = scenario {
        val ana = Phone("ana", "Ana")
        val roomId = room(ana)
        later()
        ok(ana.social.send(roomId, "  one\u0000  "))
        assertEquals(SocialError.RATE_LIMITED, errorOf(ana.social.send(roomId, "two")))
        later()
        ok(ana.social.send(roomId, "two"))
        ok(ana.social.send(roomId, "   ")) // nothing to send is not an error
        val texts = chat(ana, roomId).filter { it.kind == MessageKind.TEXT }.map { it.text }
        assertEquals(listOf("one", "two"), texts)
    }

    @Test
    fun invitesAreCheckedBeforeAnythingIsSent() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        ana.ready.store.create(deadline())
        val roomId = ok(ana.social.share("o1"))
        assertEquals(SocialError.BAD_CODE, errorOf(ben.social.join("hello there")))
        assertEquals(SocialError.BAD_CODE, errorOf(ben.social.join("")))
        assertEquals(SocialError.NOT_FOUND, errorOf(ben.social.join(InviteCode.generate().text())))

        val cancelled = ok(ana.social.invite(roomId))
        ok(ana.social.cancelInvites(roomId))
        assertEquals(SocialError.REVOKED, errorOf(ben.social.join(cancelled)))

        val expiring = ok(ana.social.invite(roomId))
        now += 8L * 24 * 60 * 60 * 1000
        assertEquals(SocialError.EXPIRED, errorOf(ben.social.join(expiring)))

        val current = ok(ana.social.invite(roomId))
        ok(ben.social.join(current))
        assertEquals(SocialError.ALREADY_IN, errorOf(ben.social.join(current)))
        assertEquals(SocialError.DENIED, errorOf(ben.social.invite(roomId)))
    }

    @Test
    fun aFullRoomRefusesAnotherPerson() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        ana.ready.store.create(deadline())
        val roomId = ok(ana.social.share("o1"))
        val code = ok(ana.social.invite(roomId))
        server.rooms[roomId] = server.rooms.getValue(roomId).copy(memberCount = 20)
        assertEquals(SocialError.JOIN_REFUSED, errorOf(ben.social.join(code)))
        assertEquals(0, ben.ready.store.allObligations().size)
    }

    // ---- safety ----------------------------------------------------------------------------------------------------

    @Test
    fun blockingHidesAPersonButKeepsYourOwnWordsAndTheSystemLines() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        val roomId = room(ana, ben)
        ok(ben.social.send(roomId, "rude message"))
        later()
        ok(ana.social.send(roomId, "my reply"))
        later()
        ok(ana.social.syncOne(roomId))
        assertTrue(chat(ana, roomId).any { it.text == "rude message" })

        ana.social.block("ben", "Ben")
        val hidden = chat(ana, roomId)
        assertTrue(hidden.none { it.text == "rude message" })
        assertTrue(hidden.any { it.text == "my reply" })
        assertTrue(ana.social.state.value.roomFor("o1")!!.members.single { it.uid == "ben" }.blocked)
        ana.social.unblock("ben")
        assertTrue(chat(ana, roomId).any { it.text == "rude message" })
    }

    @Test
    fun aReportReachesTheDeveloperWithTheTextOnlyIfAllowed() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        val roomId = room(ana, ben)
        ok(ben.social.send(roomId, "something awful"))
        later()
        ok(ana.social.syncOne(roomId))
        val messageId = server.messages.getValue(roomId).single { it.senderUid == "ben" && it.kind == MessageKind.TEXT }.id

        ok(ana.social.report(roomId, "ben", messageId, ReportReason.HARASSMENT, "d".repeat(5000), includeText = true))
        ok(ana.social.report(roomId, "ben", messageId, ReportReason.SPAM, "", includeText = false))
        assertEquals(2, server.reports.size)
        val (reporter, first) = server.reports[0]
        assertEquals("ana", reporter)
        assertEquals("something awful", first.quote)
        assertEquals(1000, first.detail.length)
        assertNull(server.reports[1].second.quote)
    }

    @Test
    fun aHostileOwnerCannotCorruptOrCrashAMembersPhone() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        val roomId = room(ana, ben)
        val bensId = ben.ready.store.allObligations().single().id
        val key = ana.ready.db.roomKeys().forRoom(roomId).single().let { RoomKey.decode(it.keyText)!! }

        // Properly sealed, but not a deadline at all.
        later()
        ana.backend.updatePayload(roomId, 2, SocialCrypto.seal(key, SealContext(roomId, Purpose.PAYLOAD, 1), "not json at all".toByteArray()))
        ok(ben.social.sync())
        assertEquals("Signals lab report", ben.ready.store.obligation(bensId)!!.title)

        // A date that cannot be read becomes no date, never a wrong date, and the huge title is cut.
        val hostile = """{"title":"${"T".repeat(9000)}","dueLocal":"2026-99-99T00:00","dueZone":"Nope/Nope","duePrecision":"X","rev":3}"""
        ana.backend.updatePayload(roomId, 3, SocialCrypto.seal(key, SealContext(roomId, Purpose.PAYLOAD, 1), hostile.toByteArray()))
        later()
        ok(ben.social.sync())
        val bens = ben.ready.store.obligation(bensId)!!
        assertEquals(200, bens.title.length)
        assertNull(bens.due)

        // A message sealed for some other room, and one that is plain garbage, are skipped without a word.
        server.messages.getValue(roomId) += MessageInfo("x1", roomId, "ana", now + 1, MessageKind.TEXT, SocialCrypto.sealText(key, SealContext("other", Purpose.MESSAGE, 1, "x1", "ana"), "elsewhere"), 1)
        server.messages.getValue(roomId) += MessageInfo("x2", roomId, "ana", now + 2, MessageKind.TEXT, "garbage!!", 1)
        later()
        ok(ben.social.refreshChat(roomId))
        assertTrue(chat(ben, roomId).none { it.text == "elsewhere" || it.id == "x2" })
    }

    // ---- new phone, restore, account ----------------------------------------------------------------------------------

    @Test
    fun aNewPhoneWithTheBackedUpKeyFindsItsRoomsAndHistory() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        val roomId = room(ana, ben)
        ok(ben.social.send(roomId, "history to keep"))
        later()

        val newBen = Phone("ben", "Ben")
        newBen.ready.settings.putRaw(SOCIAL_DEVICE_KEY, ben.ready.settings.raw(SOCIAL_DEVICE_KEY)!!)
        ok(newBen.social.sync())
        val id = newBen.ready.store.allObligations().single().id
        assertEquals("Signals lab report", newBen.ready.store.obligation(id)!!.title)
        assertTrue(chat(newBen, roomId).any { it.text == "history to keep" })

        // Without the key, the room is not opened, and nothing breaks.
        val lost = Phone("ben", "Ben")
        ok(lost.social.sync())
        assertEquals(0, lost.ready.store.allObligations().size)
        assertTrue(lost.social.state.value.rooms.isEmpty())
    }

    @Test
    fun theDeviceKeyTravelsInTheEncryptedBackupSnapshot() = scenario {
        val ana = Phone("ana", "Ana")
        room(ana)
        val snapshot = ana.ready.store.exportSnapshot(emptyMap())
        val key = snapshot.settings[SOCIAL_DEVICE_KEY]
        assertNotNull(key)
        assertNotNull(DeviceKeyPair.decode(key!!))
        val fresh = Phone("ana", "Ana")
        fresh.ready.store.importSnapshot(snapshot, merge = false, localSettings = emptyMap())
        assertEquals(key, fresh.ready.settings.raw(SOCIAL_DEVICE_KEY))
        // A restore never replaces a key that is already in use.
        val other = DeviceKeyPair.generate().encode()
        fresh.ready.settings.putRaw(SOCIAL_DEVICE_KEY, other)
        fresh.ready.store.importSnapshot(snapshot, merge = false, localSettings = emptyMap())
        assertEquals(other, fresh.ready.settings.raw(SOCIAL_DEVICE_KEY))
    }

    @Test
    fun deletingTheAccountRemovesWhatTheServerHoldsAndKeepsTheDeadlines() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        val roomId = room(ana, ben)
        val bensId = ben.ready.store.allObligations().single().id
        ok(ben.social.send(roomId, "mine"))
        later()

        ok(ben.social.deleteAccount(context))
        assertFalse(server.members.getValue(roomId).containsKey("ben"))
        assertTrue(server.messages.getValue(roomId).none { it.senderUid == "ben" })
        assertNull(server.profiles["ben"])
        assertNull(ben.ready.settings.raw(SOCIAL_DEVICE_KEY))
        assertTrue(ben.social.state.value.rooms.isEmpty())
        assertNull(ben.social.state.value.user)
        assertNotNull(ben.ready.store.obligation(bensId))

        // The owner deleting their account ends the room for everyone.
        later()
        ok(ana.social.deleteAccount(context))
        assertEquals(0, server.roomCount())
    }

    @Test
    fun eraseEverythingAlsoClearsSharedDeadlinesChatKeysAndBlocks() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        val roomId = room(ana, ben)
        ok(ben.social.send(roomId, "hello"))
        ben.social.block("ana", "Ana")
        ben.ready.store.wipeAll()
        assertTrue(ben.social.state.value.rooms.isEmpty())
        assertTrue(ben.social.state.value.blocked.isEmpty())
        assertTrue(ben.ready.db.roomKeys().forRoom(roomId).isEmpty())
        assertTrue(ben.ready.db.roomMessages().forRoom(roomId).isEmpty())
    }

    // ---- finding people and chatting first --------------------------------------------------------------

    private fun roomOf(p: Phone, obligationId: String): String = p.social.state.value.roomFor(obligationId)!!.roomId

    @Test
    fun usernamesAreUniqueAndFoundByExactNameOnly() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        val cat = Phone("cat", "Cat")
        assertEquals("ana.k", ok(ana.social.setUsername("  @Ana.K ")))
        assertEquals(SocialError.USERNAME_TAKEN, errorOf(ben.social.setUsername("ana.k")))
        assertEquals(SocialError.BAD_USERNAME, errorOf(ben.social.setUsername("x")))
        val hit = ok(cat.social.findPeople("ana.k")).single()
        assertEquals("ana", hit.uid)
        assertEquals("Ana", hit.displayName)
        assertTrue(ok(cat.social.findPeople("ana")).isEmpty())
        assertTrue(ok(cat.social.findPeople("nobody.here")).isEmpty())
        assertTrue(ok(ana.social.findPeople("ana.k")).isEmpty())
        ok(ana.social.removeUsername())
        assertTrue(ok(cat.social.findPeople("ana.k")).isEmpty())
        assertEquals("ana.k", ok(ben.social.setUsername("ana.k")))
    }

    @Test
    fun anEmailIsFoundOnlyWhenTheOwnerAllowedItAndTheServerNeverHoldsIt() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        assertTrue(ok(ben.social.findPeople("ana@example.test")).isEmpty())
        ok(ana.social.setFindableByEmail(true))
        assertEquals("ana", ok(ben.social.findPeople("Ana@Example.test")).single().uid)
        assertTrue(server.emails.keys.none { it.contains("ana") || it.contains("@") })
        ok(ana.social.setFindableByEmail(false))
        assertTrue(ok(ben.social.findPeople("ana@example.test")).isEmpty())
    }

    @Test
    fun aPersonCanBeAskedToChatAndTheChatIsSealedAndHasNoDeadline() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        ok(ben.social.setUsername("ben.b"))
        val hit = ok(ana.social.findPeople("ben.b")).single()
        val mine = ok(ana.social.startChat(hit))
        assertEquals(Space.CHAT, ana.ready.store.obligation(mine)!!.space)

        ben.social.refreshRequestsNow()
        val req = ben.social.requests.value.single()
        assertEquals("ana", req.fromUid)
        assertEquals("Ana", req.fromName)
        assertNull(ben.social.state.value.rooms.values.firstOrNull())

        later()
        val theirs = ok(ben.social.acceptChat(req))
        assertEquals(Space.CHAT, ben.ready.store.obligation(theirs)!!.space)
        assertEquals("Chat with Ana", ben.ready.store.obligation(theirs)!!.title)
        assertTrue(ben.social.requests.value.isEmpty())

        later()
        ok(ana.social.send(roomOf(ana, mine), "Hello Ben, shall we study together?"))
        later()
        ben.social.syncOne(roomOf(ben, theirs))
        assertEquals(listOf("Hello Ben, shall we study together?"), chat(ben, roomOf(ben, theirs)).map { it.text })
        later()
        ok(ben.social.send(roomOf(ben, theirs), "Yes, tonight."))
        ana.social.syncOne(roomOf(ana, mine))
        assertEquals(2, chat(ana, roomOf(ana, mine)).size)
        assertTrue(server.sealedValues().none { it.contains("study together") || it.contains("tonight") })
    }

    @Test
    fun askingTwiceReusesTheSameChat() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        ok(ben.social.setUsername("ben.b"))
        val hit = ok(ana.social.findPeople("ben.b")).single()
        val first = ok(ana.social.startChat(hit))
        later()
        assertEquals(first, ok(ana.social.startChat(hit)))
        assertEquals(1, server.roomCount())
    }

    @Test
    fun decliningWithBlockHidesThePersonAndStopsNewAsks() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        ok(ben.social.setUsername("ben.b"))
        ok(ana.social.startChat(ok(ana.social.findPeople("ben.b")).single()))
        ben.social.refreshRequestsNow()
        ok(ben.social.declineChat(ben.social.requests.value.single(), block = true))
        assertTrue(ben.social.requests.value.isEmpty())
        ben.social.refreshRequestsNow()
        assertTrue(ben.social.requests.value.isEmpty())
        ok(ana.social.setUsername("ana.k"))
        assertEquals(SocialError.BLOCKED, errorOf(ben.social.startChat(ok(ben.social.findPeople("ana.k")).single())))
    }

    @Test
    fun onlyTheOwnerOfARoomCanAskSomeoneToJoinItAndOneAskPerPairAtATime() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        val cat = Phone("cat", "Cat")
        val roomId = room(ana)
        val req = com.hsib.sib.core.social.ChatRequest("cat", "Cat", "ben", roomId, "ABCDEFGHJK", "x", 1, 0)
        try { FakeBackend(server, "cat").sendChatRequest(req); throw AssertionError("a person who does not own the room was allowed") } catch (e: SocialException) { assertEquals(SocialError.DENIED, e.error) }
        ok(ben.social.setUsername("ben.b"))
        ok(ana.social.startChat(ok(ana.social.findPeople("ben.b")).single()))
        val again = com.hsib.sib.core.social.ChatRequest("ana", "Ana", "ben", roomId, "ABCDEFGHJK", "x", 1, 0)
        try { FakeBackend(server, "ana").sendChatRequest(again); throw AssertionError("a second waiting ask was allowed") } catch (e: SocialException) { assertEquals(SocialError.DENIED, e.error) }
        assertEquals(cat.uid, "cat")
    }

    @Test
    fun theAnonymousCountCountsEachPersonOnceAndHoldsNothingElse() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        val course = com.hsib.sib.core.medical.Courses.mbbs
        ok(ana.social.countCourse(course))
        ok(ana.social.countCourse(course))
        ok(ben.social.countCourse(course.copy(name = "Same content, other name")))
        val hash = server.courseUse.keys.single()
        assertEquals(2, server.usersOfCourse(hash))
        ok(ben.social.countCourse(course.copy(phases = course.phases.take(2))))
        assertEquals(2, server.courseUse.size)
    }

    @Test
    fun deletingTheAccountRemovesTheNameTheEmailLookupAndWaitingAsks() = scenario {
        val ana = Phone("ana", "Ana")
        val ben = Phone("ben", "Ben")
        ok(ana.social.setUsername("ana.k"))
        ok(ana.social.setFindableByEmail(true))
        ok(ben.social.setUsername("ben.b"))
        ok(ana.social.startChat(ok(ana.social.findPeople("ben.b")).single()))
        ok(ana.social.deleteAccount(context))
        assertTrue(server.usernames.values.none { it == "ana" })
        assertTrue(server.emails.values.none { it == "ana" })
        assertTrue(server.chatRequests.values.none { it.containsKey("ana") })
    }
}
