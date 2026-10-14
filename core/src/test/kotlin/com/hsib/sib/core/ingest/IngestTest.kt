package com.hsib.sib.core.ingest

import com.hsib.sib.core.extract.Extractor
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.time.DateOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class IngestTest {
    private val utc = ZoneId.of("UTC")

    // ---- iCalendar ---------------------------------------------------------------------------

    private val sampleIcs = """
        BEGIN:VCALENDAR
        VERSION:2.0
        BEGIN:VEVENT
        UID:1@example
        SUMMARY:Midterm\, Hall B
        DTSTART;TZID=Europe/Berlin:20261105T090000
        DESCRIPTION:Bring ID\nand a pencil
        END:VEVENT
        BEGIN:VTODO
        SUMMARY:Submit essay
        DUE;VALUE=DATE:20261120
        END:VTODO
        BEGIN:VEVENT
        SUMMARY:Cancelled thing
        DTSTART:20261201T100000Z
        STATUS:CANCELLED
        END:VEVENT
        BEGIN:VEVENT
        SUMMARY:Folded long
         title continues
        DTSTART:20261203T100000
        END:VEVENT
        BEGIN:VEVENT
        SUMMARY:Broken date
        DTSTART:garbage
        END:VEVENT
        BEGIN:VEVENT
        SUMMARY:UTC meeting
        DTSTART:20261210T153000Z
        END:VEVENT
        END:VCALENDAR
    """.trimIndent().replace("\n", "\r\n")

    @Test fun icsItemsAreParsedAndBadOnesSkipped() {
        val items = Ics.parse(sampleIcs, utc)
        assertEquals(listOf("Midterm, Hall B", "Submit essay", "Folded longtitle continues", "UTC meeting"), items.map { it.title })
        val mid = items[0]
        assertEquals(LocalDateTime.of(2026, 11, 5, 9, 0), mid.due.local)
        assertEquals(ZoneId.of("Europe/Berlin"), mid.due.zone)
        assertEquals("Bring ID\nand a pencil", mid.description)
        val todo = items[1]
        assertTrue(todo.isTodo)
        assertEquals(Precision.DATE_ONLY, todo.due.precision)
        assertEquals(LocalDate.of(2026, 11, 20), todo.due.local.toLocalDate())
        assertTrue("floating times follow the device", items[2].due.floating)
        assertEquals(ZoneOffset.UTC, items[3].due.zone)
    }

    @Test fun icsRejectsOversizedInputAndNonsense() {
        assertTrue(Ics.parse("x".repeat(Ics.MAX_CHARS + 1), utc).isEmpty())
        assertTrue(Ics.parse("", utc).isEmpty())
        assertTrue(Ics.parse("BEGIN:VEVENT\nSUMMARY:no end", utc).isEmpty())
        assertTrue(Ics.parse("random text\nwith: colons", utc).isEmpty())
    }

    // ---- chat exports ------------------------------------------------------------------------

    private val android = """
        10/12/2026, 9:41 PM - Messages and calls are end-to-end encrypted. No one outside of this chat can read them.
        13/10/2026, 20:15 - Prof Rao: Reminder: the lab report is due Friday 16 October.
        13/10/2026, 20:16 - Sam: ok thanks
        14/10/2026, 08:02 - Prof Rao: Also the quiz on 22 October
        will cover chapters 1 to 4.
    """.trimIndent()

    @Test fun androidStyleExportIsParsedWithMultilineMessages() {
        val msgs = ChatExport.parse(android, utc, DateOrder.MDY)
        assertEquals(3, msgs.size)
        assertEquals("Prof Rao", msgs[0].sender)
        assertEquals(ZonedDateTime.of(2026, 10, 13, 20, 15, 0, 0, utc), msgs[0].time)
        assertTrue(msgs[2].text.contains("will cover chapters"))
    }

    @Test fun dayMonthOrderIsInferredFromTheFileItself() {
        // The 13th can only be a day, so despite the MDY default the file is day-first.
        val msgs = ChatExport.parse(android, utc, DateOrder.MDY)
        assertEquals(LocalDate.of(2026, 10, 14), msgs[2].time.toLocalDate())
        val us = "10/13/26, 8:15 PM - Sam: hello\n10/14/26, 12:05 AM - Sam: late"
        val m = ChatExport.parse(us, utc, DateOrder.DMY)
        assertEquals(LocalDate.of(2026, 10, 13), m[0].time.toLocalDate())
        assertEquals(20, m[0].time.hour)
        assertEquals(0, m[1].time.hour)
    }

    @Test fun iosStyleExportWithInvisibleMarks() {
        val ios = "[13/10/2026, 20:15:03] Prof Rao: ‎Deadline for the proposal: 30 October 2026\n[13/10/2026, 20:16:10] Sam: ‎ok"
        val msgs = ChatExport.parse(ios, utc, DateOrder.DMY)
        assertEquals(2, msgs.size)
        assertEquals("Prof Rao", msgs[0].sender)
        assertEquals(20, msgs[0].time.hour)
        assertTrue("the invisible mark must not leak into the text", msgs[0].text.trim().startsWith("Deadline"))
    }

    @Test fun scanFindsDeadlinesAndKeepsNoSenderNames() {
        val now = ZonedDateTime.of(2026, 10, 14, 10, 0, 0, 0, utc)
        val scan = ChatExport.scan(android, utc, DateOrder.DMY, Extractor(), now)
        val titles = scan.candidates.map { it.title.lowercase() }
        assertTrue("found $titles", titles.any { "lab report" in it })
        assertTrue("found $titles", titles.any { "quiz" in it })
        for (c in scan.candidates) {
            assertFalse(c.excerpt.contains("Prof Rao"))
            assertFalse(c.title.contains("Rao"))
        }
    }

    @Test fun relativeDatesAreResolvedAgainstWhenTheMessageWasSent() {
        val old = "05/10/2026, 20:00 - Sam: Please submit the form tomorrow"
        val now = ZonedDateTime.of(2026, 10, 14, 10, 0, 0, 0, utc)
        val scan = ChatExport.scan(old, utc, DateOrder.DMY, Extractor(), now)
        assertTrue("\"tomorrow\" in a message from 5 October is 6 October, which is over by now", scan.candidates.isEmpty())
        val fresh = "13/10/2026, 20:00 - Sam: Please submit the form tomorrow"
        val ok = ChatExport.scan(fresh, utc, DateOrder.DMY, Extractor(), now)
        assertEquals(LocalDate.of(2026, 10, 14), ok.candidates.single().due!!.local.toLocalDate())
    }

    @Test fun oldMessagesAreSkipped() {
        val text = "01/01/2026, 10:00 - Sam: Submit the report by 15 February 2027"
        val now = ZonedDateTime.of(2026, 10, 14, 10, 0, 0, 0, utc)
        val scan = ChatExport.scan(text, utc, DateOrder.DMY, Extractor(), now)
        assertEquals(0, scan.messagesConsidered)
        assertNull(scan.candidates.firstOrNull())
    }

    @Test fun garbageDoesNotCrash() {
        assertTrue(ChatExport.parse("", utc, DateOrder.DMY).isEmpty())
        assertTrue(ChatExport.parse("just some text\nwithout headers", utc, DateOrder.DMY).isEmpty())
        assertTrue(ChatExport.parse("99/99/9999, 99:99 - X: y", utc, DateOrder.DMY).isEmpty())
    }
}
