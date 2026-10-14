package com.hsib.sib.core.extract

import com.hsib.sib.core.ingest.IcsWriter
import com.hsib.sib.core.ingest.Ics
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Kind
import com.hsib.sib.core.model.Lifecycle
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Precision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class EffortParserTest {
    @Test fun hoursAndMinutes() {
        assertEquals(360, EffortParser.parse("Submit the signals assignment on November 18 at 11:59 PM. I need around six hours to finish it."))
        assertEquals(150, EffortParser.parse("it takes about 2.5 hours"))
        assertEquals(45, EffortParser.parse("should take roughly 45 minutes"))
        assertEquals(30, EffortParser.parse("needs about half an hour"))
        assertEquals(240, EffortParser.parse("will take half a day"))
        assertEquals(480, EffortParser.parse("needs a full day"))
    }

    @Test fun noEstimateMeansNull() {
        assertNull(EffortParser.parse("Submit the report by Friday"))
        assertNull(EffortParser.parse("The meeting lasts 2 hours")) // no effort hint word
        assertNull(EffortParser.parse(""))
    }

    @Test fun absurdValuesAreClamped() {
        assertEquals(60 * 200, EffortParser.parse("it will take 99999 hours"))
        assertEquals(5, EffortParser.parse("about 1 minutes"))
    }

    // ---- calendar export round trip, kept here because it shares the same small fixtures ----

    private val zone = ZoneId.of("UTC")

    private fun item(id: String, title: String, due: DueSpec, kind: Kind = Kind.DEADLINE, notes: String = "", lifecycle: Lifecycle = Lifecycle.ACTIVE) =
        Obligation(id = id, title = title, notes = notes, kind = kind, due = due, lifecycle = lifecycle)

    @Test fun exportedCalendarCanBeReadBack() {
        val items = listOf(
            item("a", "Report, final; v2", DueSpec(LocalDateTime.of(2026, 11, 18, 23, 59), zone, Precision.DATE_TIME), notes = "Line one\nLine two"),
            item("b", "Registration", DueSpec(LocalDateTime.of(2026, 11, 20, 0, 0), zone, Precision.DATE_ONLY)),
            item("c", "Seminar", DueSpec(LocalDateTime.of(2026, 12, 2, 10, 0), zone, Precision.DATE_TIME), kind = Kind.EVENT),
            item("d", "Cancelled thing", DueSpec(LocalDateTime.of(2026, 12, 3, 10, 0), zone, Precision.DATE_TIME), lifecycle = Lifecycle.CANCELLED),
        )
        val text = IcsWriter.write(items, zone, Instant.parse("2026-10-14T10:00:00Z"))
        assertTrue(text.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(text.lines().all { it.length <= 76 })
        val back = Ics.parse(text, zone)
        assertEquals(listOf("Report, final; v2", "Registration", "Seminar"), back.map { it.title })
        assertEquals(LocalDateTime.of(2026, 11, 18, 23, 59), back[0].due.local)
        assertEquals("Line one\nLine two", back[0].description)
        assertEquals(Precision.DATE_ONLY, back[1].due.precision)
        assertTrue(back[0].isTodo && !back[2].isTodo)
    }

    @Test fun veryLongTitlesAreFoldedAndStillRead() {
        val title = "T".repeat(300)
        val text = IcsWriter.write(listOf(item("x", title, DueSpec(LocalDateTime.of(2026, 11, 18, 12, 0), zone, Precision.DATE_TIME))), zone)
        assertTrue(text.split("\r\n").all { it.toByteArray().size <= 75 })
        assertEquals(title, Ics.parse(text, zone).single().title)
    }
}
