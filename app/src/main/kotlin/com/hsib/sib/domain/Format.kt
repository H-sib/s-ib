package com.hsib.sib.domain

import android.content.Context
import android.text.format.DateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Human wording for dates, times and durations. Date order, month names and 12 or 24 hour clocks come from the
 * phone's locale, never from hard coded patterns.
 */
class Fmt(context: Context, private val locale: Locale = Locale.getDefault()) {
    private val is24 = DateFormat.is24HourFormat(context)

    private fun fmt(skeleton: String) = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

    private val dayLong = fmt("EEEEdMMMM")
    private val dayShort = fmt("EEEdMMM")
    private val dayNoWeek = fmt("dMMM")
    private val dayYear = fmt("dMMMy")
    private val weekday = fmt("EEE")
    private val monthYear = fmt("MMMMy")
    private val time = fmt(if (is24) "Hm" else "hm")

    fun dayLong(d: LocalDate): String = dayLong.format(d)
    fun dayShort(d: LocalDate): String = dayShort.format(d)
    fun dayNoWeek(d: LocalDate): String = dayNoWeek.format(d)
    fun dayYear(d: LocalDate): String = dayYear.format(d)
    fun weekday(d: LocalDate): String = weekday.format(d)

    /** One letter for the month, in the phone's language, for tight spaces such as the year ribbon. */
    fun monthInitial(d: LocalDate): String = d.month.getDisplayName(TextStyle.NARROW_STANDALONE, locale)
    fun monthYear(d: LocalDate): String = monthYear.format(d)
    fun time(t: java.time.LocalTime): String = time.format(t)

    /** "Wed 18 Nov, 23:59" or "Wed 18 Nov" for date-only deadlines. */
    fun due(local: LocalDateTime, dateOnly: Boolean): String =
        if (dateOnly) dayShort.format(local.toLocalDate()) else dayShort.format(local) + ", " + time.format(local)

    fun minutes(min: Int): String = when {
        min < 60 -> "$min min"
        min % 60 == 0 -> "${min / 60} h"
        else -> "${min / 60} h ${min % 60} min"
    }

    fun range(a: LocalDate, b: LocalDate): String = if (a == b) dayNoWeek(a) else dayNoWeek(a) + " to " + dayNoWeek(b)

    /** "in 3 days", "tomorrow", "today", "2 days ago". Based on calendar days in [zone]. */
    fun daysFrom(today: LocalDate, day: LocalDate): String {
        val d = ChronoUnit.DAYS.between(today, day)
        return when {
            d == 0L -> "today"
            d == 1L -> "tomorrow"
            d == -1L -> "yesterday"
            d > 1 -> "in $d days"
            else -> "${-d} days ago"
        }
    }

    /** Countdown wording used in notifications: "in 5 h", "in 3 days", "1 day ago". */
    fun countdown(now: Instant, due: Instant, zone: ZoneId): String {
        val diff = Duration.between(now, due)
        val abs = diff.abs()
        val text = when {
            abs.toMinutes() < 60 -> "${abs.toMinutes().coerceAtLeast(1)} min"
            abs.toHours() < 24 -> "${abs.toHours()} h"
            else -> {
                val days = ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), due.atZone(zone).toLocalDate()).let { kotlin.math.abs(it) }
                if (days == 1L) "1 day" else "$days days"
            }
        }
        return if (diff.isNegative) "$text ago" else "in $text"
    }
}
