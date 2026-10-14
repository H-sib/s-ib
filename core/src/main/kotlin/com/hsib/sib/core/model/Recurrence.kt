package com.hsib.sib.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Repeating dates. Every occurrence is computed from the original day, not from the previous occurrence, so a
 * monthly date on the 31st returns to the 31st after a short month, and a 29 February birthday stays a 29 February.
 */
object Recurrence {
    fun occurrence(anchor: LocalDate, repeat: Repeat, n: Long): LocalDate = when (repeat) {
        Repeat.NONE -> anchor
        Repeat.WEEKLY -> anchor.plusWeeks(n)
        Repeat.FORTNIGHTLY -> anchor.plusWeeks(2 * n)
        Repeat.MONTHLY -> anchor.plusMonths(n)
        Repeat.YEARLY -> anchor.plusYears(n)
    }

    /** The first occurrence on or after [from]. For a non-repeating date this is the date itself. */
    fun nextOnOrAfter(anchor: LocalDate, repeat: Repeat, from: LocalDate): LocalDate {
        if (repeat == Repeat.NONE || !from.isAfter(anchor)) return anchor
        var n = when (repeat) {
            Repeat.WEEKLY -> ChronoUnit.WEEKS.between(anchor, from)
            Repeat.FORTNIGHTLY -> ChronoUnit.WEEKS.between(anchor, from) / 2
            Repeat.MONTHLY -> ChronoUnit.MONTHS.between(anchor, from)
            Repeat.YEARLY -> ChronoUnit.YEARS.between(anchor, from)
            Repeat.NONE -> 0
        }
        while (occurrence(anchor, repeat, n) < from) n++
        while (n > 0 && occurrence(anchor, repeat, n - 1) >= from) n--
        return occurrence(anchor, repeat, n)
    }

    /** Whole years, months and days between [from] and [to], for "together 2 years, 4 months". */
    fun span(from: LocalDate, to: LocalDate): Triple<Int, Int, Int> {
        val p = java.time.Period.between(from, to)
        return Triple(p.years, p.months, p.days)
    }
}
