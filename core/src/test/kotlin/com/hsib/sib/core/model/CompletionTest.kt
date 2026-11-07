package com.hsib.sib.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class CompletionTest {
    private val zone = ZoneId.of("Asia/Dhaka")

    private fun due(day: String, hour: Int = 0, precision: Precision = Precision.DATE_ONLY) =
        DueSpec(LocalDateTime.of(LocalDate.parse(day), java.time.LocalTime.of(hour, 0)), zone, precision)

    @Test fun aBirthdayJumpsAYearAndTheRecordSaysSo() {
        val o = Obligation(id = "b", title = "Birthday", due = due("2026-11-28"), repeat = Repeat.YEARLY, repeatAnchor = LocalDate.parse("2024-11-28"))
        assertEquals(LocalDate.parse("2027-11-28"), Completion.nextDate(o))
        val r = Completion.recordFor(o, 1_000L)
        assertTrue(r.advanced)
        assertEquals(o.due, r.previousDue)
        assertEquals(LocalDate.parse("2027-11-28"), r.newDue?.toLocalDate())
    }

    @Test fun aWeeklyThingMovesOneWeekAndKeepsItsTime() {
        val o = Obligation(id = "w", title = "Check in", due = due("2026-11-07", 20, Precision.DATE_TIME), repeat = Repeat.WEEKLY, repeatAnchor = LocalDate.parse("2026-10-31"))
        assertEquals(LocalDate.parse("2026-11-14"), Completion.nextDate(o))
        assertEquals(20, Completion.recordFor(o, 1L).newDue?.hour)
    }

    @Test fun aDeadlineThatDoesNotRepeatIsClosedNotMoved() {
        val once = Obligation(id = "o", title = "Report", due = due("2026-11-10"))
        assertNull(Completion.nextDate(once))
        assertFalse(Completion.recordFor(once, 5L).advanced)
        assertNull(Completion.nextDate(once.copy(repeat = Repeat.WEEKLY)))
        assertNull(Completion.nextDate(once.copy(repeatAnchor = LocalDate.parse("2026-11-10"))))
    }

    @Test fun theRecordSurvivesStorage() {
        val o = Obligation(id = "b", title = "Birthday", due = due("2026-11-28", 9, Precision.DATE_TIME), postponedUntil = LocalDate.parse("2026-11-20"), repeat = Repeat.YEARLY, repeatAnchor = LocalDate.parse("2024-11-28"))
        val r = Completion.recordFor(o, 123_456L)
        assertEquals(r, UndoRecord.decode(r.encode()))
        val closed = Completion.recordFor(Obligation(id = "c", title = "T", due = due("2026-11-10")), 9L)
        assertEquals(closed, UndoRecord.decode(closed.encode()))
        val noDate = Completion.recordFor(Obligation(id = "d", title = "No date"), 9L)
        assertEquals(noDate, UndoRecord.decode(noDate.encode()))
    }

    @Test fun damagedRecordsReadAsNothing() {
        assertNull(UndoRecord.decode(null))
        assertNull(UndoRecord.decode(""))
        assertNull(UndoRecord.decode("1|abc|1||||0||"))
        assertNull(UndoRecord.decode("2|1|1||||0||"))
        assertNull(UndoRecord.decode("1|1|1|not-a-date|Asia/Dhaka|DATE_ONLY|0||"))
        assertNull(UndoRecord.decode("1|1|1"))
    }

    @Test fun undoIsOfferedForThirtyDays() {
        val r = UndoRecord(1_000L, false, null, null, null)
        val day = 86_400_000L
        assertTrue(r.isFresh(1_000L))
        assertTrue(r.isFresh(1_000L + 30 * day))
        assertFalse(r.isFresh(1_000L + 30 * day + 1))
        assertFalse(r.isFresh(999L))
    }
}
