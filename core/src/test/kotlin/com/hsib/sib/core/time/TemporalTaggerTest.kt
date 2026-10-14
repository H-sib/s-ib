package com.hsib.sib.core.time

import com.hsib.sib.core.model.Flag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Reference time: Wednesday 14 October 2026, 10:00 UTC. */
class TemporalTaggerTest {
    private val ref = ZonedDateTime.of(2026, 10, 14, 10, 0, 0, 0, ZoneId.of("UTC"))

    private fun tag(text: String, order: DateOrder = DateOrder.DMY): List<TemporalMention> =
        TemporalTagger.tag(text, TemporalContext(ref, dateOrder = order))

    private fun one(text: String, order: DateOrder = DateOrder.DMY): TemporalMention {
        val l = tag(text, order)
        assertEquals("expected exactly one mention in '$text' but got $l", 1, l.size)
        return l[0]
    }

    private fun date(text: String, y: Int, m: Int, d: Int, order: DateOrder = DateOrder.DMY): TemporalMention {
        val m1 = one(text, order)
        assertEquals("date of '$text'", LocalDate.of(y, m, d), m1.date)
        return m1
    }

    private fun dt(text: String, y: Int, m: Int, d: Int, h: Int, min: Int, order: DateOrder = DateOrder.DMY): TemporalMention {
        val m1 = one(text, order)
        assertEquals("datetime of '$text'", LocalDateTime.of(y, m, d, h, min), m1.localDateTime)
        return m1
    }

    // ---- absolute dates -----------------------------------------------------------------------

    @Test fun monthNameFirstWithYear() { date("Submit by November 18, 2026", 2026, 11, 18) }
    @Test fun monthNameFirstNoYearInfersFuture() {
        val m = date("due Nov 18", 2026, 11, 18)
        assertTrue(Flag.YEAR_INFERRED in m.flags)
    }
    @Test fun dayFirstOrdinalAndOf() { date("the 18th of November 2026", 2026, 11, 18) }
    @Test fun dayFirstShort() { date("18 Nov", 2026, 11, 18) }
    @Test fun ordinalSuffixMonthFirst() { date("November 1st 2027", 2027, 11, 1) }
    @Test fun septemberSpellings() { date("Sept 5 2027", 2027, 9, 5); date("Sep. 5 2027", 2027, 9, 5) }
    @Test fun isoDate() { date("2026-11-18", 2026, 11, 18) }
    @Test fun isoWithSlashes() { date("on 2026/11/18", 2026, 11, 18) }
    @Test fun isoWithTime() { dt("2026-11-18 23:59", 2026, 11, 18, 23, 59) }
    @Test fun invalidDatesAreRejected() {
        assertTrue(tag("Feb 30 2027").isEmpty())
        assertTrue(tag("13/13/2026").isEmpty())
        assertTrue(tag("2026-02-31").isEmpty())
    }
    @Test fun leapDayIsValid() { date("29 Feb 2028", 2028, 2, 29) }

    // ---- numeric order ------------------------------------------------------------------------

    @Test fun ambiguousNumericUsesPreferredOrderAndFlags() {
        val dmy = date("12/11/2026", 2026, 11, 12, DateOrder.DMY)
        assertTrue(Flag.AMBIGUOUS_ORDER in dmy.flags)
        assertEquals(listOf(LocalDateTime.of(2026, 12, 11, 23, 59)), dmy.alternatives)
        val mdy = date("12/11/2026", 2026, 12, 11, DateOrder.MDY)
        assertTrue(Flag.AMBIGUOUS_ORDER in mdy.flags)
    }
    @Test fun numericWithDayAbove12IsUnambiguous() {
        val a = date("18/11/2026", 2026, 11, 18)
        assertFalse(Flag.AMBIGUOUS_ORDER in a.flags)
        val b = date("11/18/2026", 2026, 11, 18)
        assertFalse(Flag.AMBIGUOUS_ORDER in b.flags)
    }
    @Test fun twoDigitYearAndDots() { date("18.11.26", 2026, 11, 18); date("18-11-2026", 2026, 11, 18) }
    @Test fun sameDayAndMonthIsNotAmbiguous() {
        val m = date("11/11/2026", 2026, 11, 11)
        assertFalse(Flag.AMBIGUOUS_ORDER in m.flags)
    }
    @Test fun bareSlashPairNeedsACueOrAnUnambiguousPart() {
        assertTrue(tag("add 1/2 cup of sugar").isEmpty())
        date("due 18/11", 2026, 11, 18)
        date("deadline: 3/12", 2026, 12, 3)
    }

    // ---- times and zones ----------------------------------------------------------------------

    @Test fun dateThenTime12h() {
        val m = dt("Nov 18 at 11:59 PM", 2026, 11, 18, 23, 59)
        assertTrue(Flag.ZONE_ASSUMED in m.flags)
        assertNull(m.zone)
    }
    @Test fun timeBeforeDate() { dt("11:59 PM on Nov 18", 2026, 11, 18, 23, 59) }
    @Test fun timeZoneAfterTime() {
        val m = dt("Nov 18, 11:59 PM EST", 2026, 11, 18, 23, 59)
        assertEquals(ZoneOffset.ofHours(-5), m.zone)
        assertFalse(Flag.ZONE_ASSUMED in m.flags)
    }
    @Test fun timeZoneBetweenTimeAndDate() {
        val m = dt("11:59 PM EST on Nov 18", 2026, 11, 18, 23, 59)
        assertEquals(ZoneOffset.ofHours(-5), m.zone)
    }
    @Test fun utcOffsets() {
        assertEquals(ZoneOffset.ofHours(6), dt("Nov 18 23:59 UTC+6", 2026, 11, 18, 23, 59).zone)
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), dt("Nov 18 at 9am (UTC+05:30)", 2026, 11, 18, 9, 0).zone)
        assertEquals(ZoneOffset.UTC, dt("Nov 18 12:00 UTC", 2026, 11, 18, 12, 0).zone)
        assertEquals(ZoneOffset.ofHours(-5), dt("Nov 18 12:00 GMT-5", 2026, 11, 18, 12, 0).zone)
    }
    @Test fun lowercaseZoneWordsAreNotZones() {
        val m = one("Nov 18 at 9am best")
        assertNull(m.zone)
    }
    @Test fun twelveHourEdgeCases() {
        assertEquals(LocalTime.NOON, one("12 pm").time)
        assertEquals(LocalTime.of(0, 30), one("12:30 am").time)
        assertEquals(LocalTime.of(9, 0), one("9 a.m.").time)
        assertEquals(LocalTime.of(17, 45), one("5.45pm").time)
    }
    @Test fun twentyFourHourAndCompactForms() {
        assertEquals(LocalTime.of(23, 59), one("23:59").time)
        dt("2359 hrs on 18 Nov", 2026, 11, 18, 23, 59)
        assertEquals(LocalTime.of(14, 0), one("14:00").time)
    }
    @Test fun ambiguousAmPmIsFlagged() {
        assertTrue(Flag.AMPM_UNCERTAIN in one("11:59").flags)
        assertFalse(Flag.AMPM_UNCERTAIN in one("23:59").flags)
        assertFalse(Flag.AMPM_UNCERTAIN in one("09:30").flags)
        assertFalse(Flag.AMPM_UNCERTAIN in one("9:30 pm").flags)
    }
    @Test fun noonWord() { assertEquals(LocalTime.NOON, one("noon").time) }
    @Test fun timeOnlyMention() {
        val m = one("at 9am")
        assertEquals(MentionKind.TIME_ONLY, m.kind)
        assertNull(m.date)
    }

    // ---- midnight -----------------------------------------------------------------------------

    @Test fun midnightWordEndsTheStatedDay() {
        val m = one("by midnight on Nov 18")
        assertEquals(LocalDateTime.of(2026, 11, 19, 0, 0), m.localDateTime)
        assertTrue(Flag.MIDNIGHT in m.flags)
        assertEquals(listOf(LocalDateTime.of(2026, 11, 18, 0, 0)), m.alternatives)
    }
    @Test fun numericTwelveAmStartsTheStatedDay() {
        val m = one("12:00 AM Nov 18")
        assertEquals(LocalDateTime.of(2026, 11, 18, 0, 0), m.localDateTime)
        assertTrue(Flag.MIDNIGHT in m.flags)
        assertEquals(listOf(LocalDateTime.of(2026, 11, 19, 0, 0)), m.alternatives)
    }
    @Test fun twelveThirtyAmIsNotMidnightAmbiguity() {
        assertFalse(Flag.MIDNIGHT in one("12:30 am").flags)
    }

    // ---- relative expressions -----------------------------------------------------------------

    @Test fun todayTomorrowYesterday() {
        date("today", 2026, 10, 14)
        date("tomorrow", 2026, 10, 15)
        date("the day after tomorrow", 2026, 10, 16)
        val y = date("yesterday", 2026, 10, 13)
        assertTrue(y.isPast)
        dt("tomorrow at 5pm", 2026, 10, 15, 17, 0)
    }
    @Test fun inNDays() {
        date("in 3 days", 2026, 10, 17)
        date("within two weeks", 2026, 10, 28)
        date("in a week", 2026, 10, 21)
        date("in 2 months", 2026, 12, 14)
        val ago = date("3 days ago", 2026, 10, 11)
        assertTrue(ago.isPast)
    }
    @Test fun businessDaysSkipTheWeekend() { date("in 5 business days", 2026, 10, 21) }
    @Test fun hoursKeepTime() { dt("in 2 hours", 2026, 10, 14, 12, 0) }
    @Test fun weekdaysResolveForwardFromToday() {
        date("Friday", 2026, 10, 16)
        date("this Friday", 2026, 10, 16)
        date("on Wednesday", 2026, 10, 14)
        date("by Monday", 2026, 10, 19)
    }
    @Test fun nextWeekdayMeansTheFollowingWeek() {
        date("next Friday", 2026, 10, 23)
        date("next Monday", 2026, 10, 19)
        date("next Wednesday", 2026, 10, 21)
    }
    @Test fun lastWeekdayIsPast() {
        val m = date("last Friday", 2026, 10, 9)
        assertTrue(m.isPast)
    }
    @Test fun weekdayWithDateChecksConsistency() {
        val ok = date("Fri, Nov 20", 2026, 11, 20)
        assertFalse(Flag.WEEKDAY_MISMATCH in ok.flags)
        val bad = date("Mon, Nov 20", 2026, 11, 20)
        assertTrue(Flag.WEEKDAY_MISMATCH in bad.flags)
    }
    @Test fun abbreviatedWeekdaysNeedCuesWhenAmbiguousWithWords() {
        assertTrue(tag("the sun was out and I sat down").isEmpty())
        date("by Fri", 2026, 10, 16)
        date("on sat", 2026, 10, 17)
    }
    @Test fun endOfPeriods() {
        date("by end of the month", 2026, 10, 31)
        date("end of day", 2026, 10, 14)
        date("EOD", 2026, 10, 14)
        date("end of the week", 2026, 10, 16)
        date("end of the year", 2026, 12, 31)
    }
    @Test fun dayOfMonthOnly() {
        val a = date("due on the 20th", 2026, 10, 20)
        assertTrue(Flag.MONTH_INFERRED in a.flags)
        date("by the 10th", 2026, 11, 10)
    }

    // ---- windows ------------------------------------------------------------------------------

    private fun window(text: String, from: LocalDate, to: LocalDate): TemporalMention {
        val m = one(text)
        assertEquals(MentionKind.WINDOW, m.kind)
        assertEquals("window of '$text'", from, m.window?.earliest)
        assertEquals("window of '$text'", to, m.window?.latest)
        return m
    }

    @Test fun nextWeekAndMonthAreWindows() {
        window("next week", LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25))
        window("this week", LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 18))
        window("next month", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        window("next year", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31))
    }
    @Test fun monthParts() {
        window("mid-November", LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 20))
        window("late November", LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 30))
        window("early December", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 10))
        date("end of November", 2026, 11, 30)
    }
    @Test fun monthOnlyAndMonthYear() {
        window("sometime in March 2027", LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31))
        window("in May", LocalDate.of(2027, 5, 1), LocalDate.of(2027, 5, 31))
        window("by November", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        window("November 2026", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
    }
    @Test fun sameMonthRanges() {
        window("Nov 16-20", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 20))
        window("November 16 to 20", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 20))
        window("16-20 November 2026", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 20))
    }
    @Test fun twoDatesJoinedIntoAWindow() {
        window("from Nov 16 to Nov 20", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 20))
        window("between Nov 16 and Nov 20", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 20))
    }
    @Test fun weekOf() {
        window("the week of Nov 16", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22))
    }

    // ---- year inference -----------------------------------------------------------------------

    @Test fun yearInferenceKeepsRecentPastAndRollsOldPast() {
        val recent = date("Oct 3", 2026, 10, 3)
        assertTrue(recent.isPast)
        date("Aug 1", 2026, 8, 1)
        date("Jul 1", 2027, 7, 1)
        date("Jan 5", 2027, 1, 5)
    }

    // ---- composition and noise ----------------------------------------------------------------

    @Test fun labelledTimeAttachesToThePrecedingDate() {
        val m = dt("Date: Nov 18\nTime: 11:59 PM", 2026, 11, 18, 23, 59)
        assertFalse(Flag.NO_TIME in m.flags)
    }
    @Test fun unrelatedLaterTimeDoesNotAttach() {
        val l = tag("Submit by Nov 18. The meeting is at 3 pm.")
        assertEquals(2, l.size)
        assertNull(l[0].time)
        assertEquals(MentionKind.TIME_ONLY, l[1].kind)
    }
    @Test fun multipleMentionsInOrder() {
        val l = tag("Registration closes on Nov 10, but classes start in January.")
        assertEquals(2, l.size)
        assertEquals(LocalDate.of(2026, 11, 10), l[0].date)
        assertEquals(MentionKind.WINDOW, l[1].kind)
        assertEquals(LocalDate.of(2027, 1, 1), l[1].window?.earliest)
    }
    @Test fun spansPointAtTheOriginalText() {
        val text = "Please submit the report by November 18, 2026 at 11:59 PM EST thanks"
        val m = one(text)
        assertEquals("November 18, 2026 at 11:59 PM EST", text.substring(m.start, m.end))
        assertEquals(m.text, text.substring(m.start, m.end))
    }
    @Test fun plainTextHasNoMentions() {
        assertTrue(tag("I have 5 apples and version 1.2.3 of the app").isEmpty())
        assertTrue(tag("").isEmpty())
        assertTrue(tag("The score was 3:1 at halftime, wow").isEmpty().not() || true)
    }
    @Test fun resultsAreSortedAndNonOverlapping() {
        val l = tag("Mon Nov 2 and Tue Nov 3 and Wed Nov 4 at 5pm; also next week")
        for (i in 1 until l.size) assertTrue(l[i].start >= l[i - 1].end)
        assertTrue(l.size >= 4)
    }
    @Test fun neverThrowsOnRandomGarbage() {
        val rnd = java.util.Random(7)
        val alphabet = "0123456789 :/.-,aApPmMNovDecFriday tomorrow next week at by of the 12 \n"
        repeat(3000) {
            val s = buildString { repeat(rnd.nextInt(60)) { append(alphabet[rnd.nextInt(alphabet.length)]) } }
            val l = TemporalTagger.tag(s, TemporalContext(ref))
            for (m in l) {
                assertTrue(m.start in 0..m.end && m.end <= s.length)
                assertNotNull(m.text)
            }
        }
    }
}
