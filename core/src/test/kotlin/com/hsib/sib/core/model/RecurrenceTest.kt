package com.hsib.sib.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Random

class RecurrenceTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test fun weeklyAndFortnightly() {
        assertEquals(d(2026, 11, 2), Recurrence.nextOnOrAfter(d(2026, 10, 5), Repeat.WEEKLY, d(2026, 10, 30)))
        assertEquals(d(2026, 11, 2), Recurrence.nextOnOrAfter(d(2026, 10, 5), Repeat.FORTNIGHTLY, d(2026, 10, 30)))
        assertEquals(d(2026, 10, 19), Recurrence.nextOnOrAfter(d(2026, 10, 5), Repeat.FORTNIGHTLY, d(2026, 10, 14)))
    }

    @Test fun onTheDayItself() {
        assertEquals(d(2026, 10, 19), Recurrence.nextOnOrAfter(d(2026, 10, 5), Repeat.WEEKLY, d(2026, 10, 19)))
        assertEquals(d(2026, 10, 5), Recurrence.nextOnOrAfter(d(2026, 10, 5), Repeat.YEARLY, d(2026, 10, 5)))
        assertEquals(d(2026, 10, 5), Recurrence.nextOnOrAfter(d(2026, 10, 5), Repeat.YEARLY, d(2020, 1, 1)))
    }

    @Test fun monthlyOnThe31stReturnsToThe31stWithoutDrifting() {
        val anchor = d(2026, 1, 31)
        assertEquals(d(2026, 2, 28), Recurrence.nextOnOrAfter(anchor, Repeat.MONTHLY, d(2026, 2, 1)))
        assertEquals(d(2026, 3, 31), Recurrence.nextOnOrAfter(anchor, Repeat.MONTHLY, d(2026, 3, 1)))
        assertEquals(d(2026, 4, 30), Recurrence.nextOnOrAfter(anchor, Repeat.MONTHLY, d(2026, 4, 1)))
        assertEquals(d(2026, 5, 31), Recurrence.nextOnOrAfter(anchor, Repeat.MONTHLY, d(2026, 5, 1)))
    }

    @Test fun aLeapDayBirthdayStaysALeapDay() {
        val anchor = d(2024, 2, 29)
        assertEquals(d(2025, 2, 28), Recurrence.nextOnOrAfter(anchor, Repeat.YEARLY, d(2025, 1, 1)))
        assertEquals(d(2028, 2, 29), Recurrence.nextOnOrAfter(anchor, Repeat.YEARLY, d(2028, 1, 1)))
    }

    @Test fun neverReturnsADayBeforeTheRequestedOneAndNeverSkipsAnOccurrence() {
        val rnd = Random(21)
        repeat(2000) {
            val repeatKind = listOf(Repeat.WEEKLY, Repeat.FORTNIGHTLY, Repeat.MONTHLY, Repeat.YEARLY)[rnd.nextInt(4)]
            val anchor = d(2020, 1, 1).plusDays(rnd.nextInt(3000).toLong())
            val from = anchor.plusDays(rnd.nextInt(4000).toLong() - 100)
            val next = Recurrence.nextOnOrAfter(anchor, repeatKind, from)
            assertTrue("$repeatKind anchor=$anchor from=$from next=$next", !next.isBefore(from) || next == anchor)
            // The occurrence before the answer must be earlier than the requested day.
            var n = 0L
            while (Recurrence.occurrence(anchor, repeatKind, n) < next) n++
            if (n > 0) assertTrue(Recurrence.occurrence(anchor, repeatKind, n - 1) < from)
        }
    }

    @Test fun nonRepeatingStaysPut() {
        assertEquals(d(2026, 10, 5), Recurrence.nextOnOrAfter(d(2026, 10, 5), Repeat.NONE, d(2030, 1, 1)))
    }

    @Test fun togetherSpan() {
        val (y, m, day) = Recurrence.span(d(2023, 6, 20), d(2026, 10, 14))
        assertEquals(3, y); assertEquals(3, m); assertEquals(24, day)
    }

    @Test fun categoriesSplitCleanlyBetweenSpaces() {
        assertTrue(Category.work.none { it.close })
        assertTrue(Category.closeOnes.all { it.close })
        assertEquals(Category.values().size, Category.work.size + Category.closeOnes.size)
    }
}
