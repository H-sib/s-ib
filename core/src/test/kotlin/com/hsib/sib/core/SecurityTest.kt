package com.hsib.sib.core

import com.hsib.sib.core.ingest.IcsWriter
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Limits
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.model.clamped
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Hostile input at the two places where text leaves or enters the app's own data: the calendar file and the store. */
class SecurityTest {
    private val due = DueSpec(LocalDate.of(2026, 11, 2).atTime(0, 0), ZoneOffset.UTC, Precision.DATE_ONLY)

    private fun ics(title: String, notes: String = ""): String =
        IcsWriter.write(listOf(Obligation(id = "id-1", title = title, notes = notes, due = due)), ZoneOffset.UTC, Instant.EPOCH)

    /** The file's real lines: split on both kinds of line break, as a lenient reader would. */
    private fun lines(text: String) = text.split('\r', '\n').filter { it.isNotEmpty() }

    @Test
    fun aBareCarriageReturnCannotStartANewCalendarLine() {
        val out = ics("Report\rATTENDEE:mailto:evil@example.com")
        assertTrue(lines(out).none { it.startsWith("ATTENDEE") })
        assertTrue(lines(out).any { it.startsWith("SUMMARY:Report") })
    }

    @Test
    fun everyLineBreakVariantIsEscapedNotWritten() {
        for (bad in listOf("\r\n", "\r", "\n", " ", " ", "\u0085")) {
            val out = ics("A" + bad + "ORGANIZER:x", "n" + bad + "ATTACH:y")
            assertTrue("variant ${bad.map { it.code }}", lines(out).none { it.startsWith("ORGANIZER") || it.startsWith("ATTACH") })
        }
    }

    @Test
    fun controlCharactersAreDropped() {
        val out = ics("Ti\u0000t\u0007le")
        assertTrue(out.contains("SUMMARY:Title"))
        assertFalse(out.any { it.code < 32 && it != '\r' && it != '\n' })
    }

    @Test
    fun separatorsAndBackslashesAreEscaped() {
        assertTrue(ics("a;b,c\\d").contains("SUMMARY:a\\;b\\,c\\\\d"))
    }

    @Test
    fun theIdentifierCannotCarryAnInjection() {
        val out = IcsWriter.write(listOf(Obligation(id = "x\r\nATTENDEE:evil", title = "t", due = due)), ZoneOffset.UTC, Instant.EPOCH)
        assertTrue(lines(out).none { it.startsWith("ATTENDEE") })
        assertTrue(out.contains("UID:xATTENDEEevil@sib"))
    }

    @Test
    fun longLinesFoldBetweenWholeCharacters() {
        val out = ics("Emoji 😀 ".repeat(30))
        for (line in out.split("\r\n")) assertTrue("line too long: ${line.toByteArray().size}", line.toByteArray().size <= 76)
        assertFalse("a surrogate pair was split", out.contains("\uD83D\r\n "))
    }

    @Test
    fun titlesAreCutAndCleaned() {
        val o = Obligation(id = "1", title = "  " + "x".repeat(500) + "\n\u0000", notes = "a\r\nb\rc" + "z".repeat(20_000), person = "P\nQ", relation = "r".repeat(100))
        val c = o.clamped()
        assertEquals(Limits.TITLE, c.title.length)
        assertTrue(c.notes.length <= Limits.NOTES)
        assertFalse(c.notes.contains('\r'))
        assertTrue(c.notes.startsWith("a\nb\nc"))
        assertEquals("P Q", c.person)
        assertEquals(Limits.RELATION, c.relation.length)
    }

    @Test
    fun aBlankTitleBecomesUntitledAndNeverEmpty() {
        assertEquals("Untitled", Obligation(id = "1", title = " \n\t ").clamped().title)
    }

    @Test
    fun cuttingNeverLeavesHalfASurrogatePair() {
        val s = "a".repeat(Limits.TITLE - 1) + "😀"
        val cut = Obligation(id = "1", title = s).clamped().title
        assertFalse(Character.isHighSurrogate(cut.last()))
    }

    @Test
    fun leadDaysAreBoundedAndSane() {
        val c = Obligation(id = "1", title = "t", leadDays = listOf(-5, 0, 7, 7, 400, 1) + (2..40).toList()).clamped()
        assertTrue(c.leadDays.size <= Limits.LEAD_DAYS)
        assertTrue(c.leadDays.all { it in 1..Limits.MAX_LEAD })
        assertEquals(c.leadDays.distinct(), c.leadDays)
    }
}
