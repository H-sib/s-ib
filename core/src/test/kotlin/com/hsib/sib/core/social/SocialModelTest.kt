package com.hsib.sib.core.social

import com.hsib.sib.core.model.Category
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Effort
import com.hsib.sib.core.model.FogWindow
import com.hsib.sib.core.model.Importance
import com.hsib.sib.core.model.Lifecycle
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.model.Space
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SocialModelTest {
    private val due = DueSpec(LocalDateTime.of(2026, 10, 16, 17, 0), ZoneId.of("Asia/Dhaka"), Precision.DATE_TIME)
    private val mine = Obligation(
        id = "local1", title = "Signals lab report", notes = "Bring the draft", category = Category.ASSIGNMENT, due = due,
        importance = Importance.HIGH, effort = Effort.fromLikely(240), progressMin = 90, leadDays = listOf(3, 1),
    )

    // ---- the shared payload --------------------------------------------------------------------

    @Test
    fun aPayloadRoundTripsThroughBytes() {
        val p = SharedPayload.from(mine, 4)!!
        val back = SharedPayload.fromBytes(p.toBytes())!!
        assertEquals(p, back)
        assertEquals(4, back.rev)
        assertEquals(due, back.dueSpec())
    }

    @Test
    fun onlyTheSharedFieldsReplaceTheLocalOnes() {
        val update = SharedPayload.from(mine.copy(title = "Signals lab report v2", notes = "New notes"), 2)!!
            .copy(dueLocal = "2026-10-20T09:00")
        val mineNow = mine.copy(importance = Importance.LOW, effort = Effort.fromLikely(60), progressMin = 10, leadDays = listOf(7))
        val applied = update.applyTo(mineNow)
        assertEquals("Signals lab report v2", applied.title)
        assertEquals("New notes", applied.notes)
        assertEquals(LocalDateTime.of(2026, 10, 20, 9, 0), applied.due?.local)
        assertEquals(Importance.LOW, applied.importance)
        assertEquals(Effort.fromLikely(60), applied.effort)
        assertEquals(10, applied.progressMin)
        assertEquals(listOf(7), applied.leadDays)
        assertEquals("local1", applied.id)
    }

    @Test
    fun closeOnesItemsCanNeverBeShared() {
        assertNull(SharedPayload.from(mine.copy(space = Space.CLOSE), 1))
        assertNull(SharedPayload.from(mine.copy(category = Category.ANNIVERSARY), 1))
        val sneaky = SharedPayload(title = "x", category = Category.BIRTHDAY.name).cleaned()
        assertEquals(Category.OTHER.name, sneaky.category)
    }

    @Test
    fun aTentativeWindowIsShared() {
        val fogged = mine.copy(due = null, fog = FogWindow(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22)))
        val p = SharedPayload.fromBytes(SharedPayload.from(fogged, 1)!!.toBytes())!!
        assertNull(p.dueSpec())
        assertEquals(FogWindow(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22)), p.fogWindow())
    }

    @Test
    fun aCancelledSharedDeadlineCancelsActiveOnesButNeverReopensFinishedOnes() {
        val p = SharedPayload.from(mine.copy(lifecycle = Lifecycle.CANCELLED), 2)!!
        assertEquals(Lifecycle.CANCELLED, p.applyTo(mine).lifecycle)
        assertEquals(Lifecycle.COMPLETED, p.applyTo(mine.copy(lifecycle = Lifecycle.COMPLETED)).lifecycle)
        assertEquals(Lifecycle.ACTIVE, SharedPayload.from(mine, 3)!!.applyTo(mine.copy(lifecycle = Lifecycle.ACTIVE)).lifecycle)
    }

    @Test
    fun aNewMemberStartsWithPersonalDefaults() {
        val o = SharedPayload.from(mine, 1)!!.toNewObligation("fresh")
        assertEquals("fresh", o.id)
        assertEquals("Signals lab report", o.title)
        assertEquals(Importance.NORMAL, o.importance)
        assertNull(o.effort)
        assertEquals(0, o.progressMin)
        assertTrue(o.leadDays.isEmpty())
        assertEquals(Space.WORK, o.space)
    }

    @Test
    fun hostilePayloadsAreRefusedOrCleaned() {
        for (bad in listOf("", "{", "[]", "null", "not json", "{\"title\":5}", "{\"title\":[1,2]}")) {
            assertNull("accepted: $bad", SharedPayload.fromBytes(bad.toByteArray()))
        }
        assertNull(SharedPayload.fromBytes(ByteArray(SocialLimits.MAX_SEALED_BYTES + 1)))

        val long = SharedPayload.fromBytes("""{"title":"${"T".repeat(5000)}","notes":"${"n".repeat(9000)}"}""".toByteArray())!!
        assertEquals(SocialLimits.TITLE_CHARS, long.title.length)
        assertEquals(SocialLimits.NOTES_CHARS, long.notes.length)

        val control = SharedPayload.fromBytes("""{"title":"A\u0000B\u0007C\nD","notes":"x\u0000y"}""".toByteArray())!!
        assertEquals("A B C D", control.title)
        assertFalse(control.title.any { it.isISOControl() })
        assertFalse(control.notes.contains('\u0000'))

        assertEquals("Shared deadline", SharedPayload.fromBytes("""{"title":"   "}""".toByteArray())!!.title)
        assertNotNull(SharedPayload.fromBytes("""{"title":"ok","somethingNew":123}""".toByteArray()))
    }

    @Test
    fun unreadableDatesNeverBecomeAWrongDate() {
        fun due(local: String?, zone: String?, precision: String?) = SharedPayload(title = "t", dueLocal = local, dueZone = zone, duePrecision = precision).dueSpec()
        assertNotNull(due("2026-10-16T17:00", "Asia/Dhaka", "DATE_TIME"))
        assertNull(due(null, "Asia/Dhaka", "DATE_TIME"))
        assertNull(due("2026-10-16T17:00", null, "DATE_TIME"))
        assertNull(due("2026-10-16T17:00", "Asia/Dhaka", null))
        assertNull(due("2026-13-45T17:00", "Asia/Dhaka", "DATE_TIME"))
        assertNull(due("9999-01-01T00:00", "Asia/Dhaka", "DATE_TIME"))
        assertNull(due("1066-10-14T00:00", "Asia/Dhaka", "DATE_TIME"))
        assertNull(due("2026-10-16T17:00", "Not/AZone", "DATE_TIME"))
        assertNull(due("2026-10-16T17:00", "Asia/Dhaka", "SOMETIMES"))
        assertNull(SharedPayload(title = "t", fogFrom = "2026-11-22", fogTo = "2026-11-16").fogWindow())
        assertNull(SharedPayload(title = "t", fogFrom = "x", fogTo = "y").fogWindow())
    }

    @Test
    fun aDamagedPayloadStillAppliesWithoutCrashing() {
        val broken = SharedPayload(title = "t", dueLocal = "nonsense", dueZone = "nope", duePrecision = "??")
        val applied = broken.applyTo(mine)
        assertNull(applied.due)
        assertEquals("t", applied.title)
    }

    // ---- the rules ---------------------------------------------------------------------------------

    @Test
    fun namesAndMessagesAreCleanedAndBounded() {
        assertEquals("Sam", SocialRules.cleanName("  Sam\n"))
        assertNull(SocialRules.cleanName("   \n "))
        assertEquals(SocialLimits.DISPLAY_NAME_CHARS, SocialRules.cleanName("N".repeat(500))!!.length)
        assertFalse(SocialRules.cleanName("Sa\u0000m\u0007")!!.any { it.isISOControl() })
        assertNull(SocialRules.cleanMessage("   "))
        assertEquals("a\nb", SocialRules.cleanMessage("a\r\nb"))
        assertEquals(SocialLimits.MESSAGE_CHARS, SocialRules.cleanMessage("m".repeat(10_000))!!.length)
        // The 1,999th character is the first half of a pair, so the cut has to step back one.
        val emoji = "a" + "👍".repeat(SocialLimits.MESSAGE_CHARS)
        val cut = SocialRules.cleanMessage(emoji)!!
        assertTrue(cut.length <= SocialLimits.MESSAGE_CHARS)
        assertFalse("half a surrogate pair was left", Character.isHighSurrogate(cut.last()))
    }

    @Test
    fun reportsAreCleanedAndTheQuoteIsOptional() {
        val r = SocialRules.cleanReport(Report("r", "u", "m", ReportReason.SPAM, "d".repeat(5000), "q".repeat(9000)))
        assertEquals(SocialLimits.REPORT_DETAIL_CHARS, r.detail.length)
        assertEquals(SocialLimits.REPORT_QUOTE_CHARS, r.quote!!.length)
        assertNull(SocialRules.cleanReport(Report("r", "u", null, ReportReason.OTHER, "", "   ")).quote)
    }

    @Test
    fun oneMessageASecondIsAllowed() {
        assertFalse(SocialRules.canSendNow(10_000, 10_500))
        assertTrue(SocialRules.canSendNow(10_000, 11_000))
        // Someone who has never written has no last message, which is stored as 0.
        assertTrue(SocialRules.canSendNow(0, 1_760_000_000_000))
    }

    @Test
    fun joiningIsCheckedInOrder() {
        val now = 1_000_000L
        val base = InviteInfo("i", "r", "owner", now + 1000, false, "sealed", 1)
        assertEquals(SocialRules.JoinCheck.Ok, SocialRules.checkJoin(base, now, 3, false, 2))
        assertEquals(SocialRules.JoinCheck.AlreadyIn, SocialRules.checkJoin(base, now, 3, true, 2))
        assertEquals(SocialRules.JoinCheck.Revoked, SocialRules.checkJoin(base.copy(revoked = true), now, 3, false, 2))
        assertEquals(SocialRules.JoinCheck.Expired, SocialRules.checkJoin(base.copy(expiresAtMs = now), now, 3, false, 2))
        assertEquals(SocialRules.JoinCheck.RoomFull, SocialRules.checkJoin(base, now, SocialLimits.MEMBERS_PER_ROOM, false, 2))
        assertEquals(SocialRules.JoinCheck.TooManyRooms, SocialRules.checkJoin(base, now, 3, false, SocialLimits.ROOMS_PER_PERSON))
        assertEquals(now + 7L * 24 * 60 * 60 * 1000, SocialRules.inviteExpiry(now))
    }

    private fun msg(id: String, at: Long, sender: String = "a") = MessageInfo(id, "r", sender, at, MessageKind.TEXT, "sealed-$id", 1)

    @Test
    fun mergingMessagesIsOrderedAndRepeatable() {
        val first = listOf(msg("b", 20), msg("a", 10))
        val second = listOf(msg("c", 30), msg("b", 20), msg("z", 20))
        val merged = SocialRules.mergeMessages(first, second)
        assertEquals(listOf("a", "b", "z", "c"), merged.map { it.id })
        assertEquals(merged, SocialRules.mergeMessages(merged, second))
        assertEquals(merged, SocialRules.mergeMessages(second, first))
    }

    private fun chat(id: String, sender: String, kind: MessageKind = MessageKind.TEXT, mine: Boolean = false) =
        ChatMessage(id, "r", sender, sender, 1, "hi", kind, mine)

    @Test
    fun blockedPeopleAreHiddenButSystemLinesAndYourOwnStay() {
        val all = listOf(chat("1", "bad"), chat("2", "good"), chat("3", "bad", MessageKind.SYSTEM), chat("4", "bad", mine = true))
        assertEquals(listOf("2", "3", "4"), SocialRules.visible(all, setOf("bad")).map { it.id })
        assertEquals(4, SocialRules.visible(all, emptySet()).size)
    }

    @Test
    fun theDoneLineAppearsOnlyOnceSomeoneIsDone() {
        fun member(uid: String, done: Long?) = MemberInfo(uid, "r", RoomRole.MEMBER, uid, 1, done, null, 0, 0)
        assertNull(SocialRules.doneSummary(listOf(member("a", null), member("b", null))))
        assertEquals("1 of 3 done", SocialRules.doneSummary(listOf(member("a", 5), member("b", null), member("c", null))))
    }

    @Test
    fun onlyANewerPayloadIsApplied() {
        assertTrue(SocialRules.shouldApply(1, 2))
        assertFalse(SocialRules.shouldApply(2, 2))
        assertFalse(SocialRules.shouldApply(3, 2))
    }

    @Test
    fun theCommunityRulesAreShortAndFollowTheStyleRules() {
        assertTrue(CommunityRules.rules.size in 4..8)
        for (r in CommunityRules.rules) {
            assertTrue(r.length in 10..200)
            assertFalse("dash in: $r", r.contains('\u2014') || r.contains('\u2013'))
        }
    }
}
