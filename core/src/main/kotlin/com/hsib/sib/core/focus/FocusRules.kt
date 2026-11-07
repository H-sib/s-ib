package com.hsib.sib.core.focus

/**
 * Rules for a focus session, kept free of Android so they can be tested. A session is only a start time; the minutes are worked out
 * when it stops. A session that has run for hours was almost certainly forgotten, so it is never added without asking.
 */
object FocusRules {
    /** From this many minutes a session counts as long and the person is asked what they really worked. */
    const val LONG_MIN = 180

    fun elapsedMin(startedAt: Long, now: Long): Int = ((now - startedAt) / 60_000L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    fun isLong(minutes: Int): Boolean = minutes >= LONG_MIN

    /** The number put in the box when a long session stops: the time that ran, but never more than a long working stretch. */
    fun suggestedMin(minutes: Int): Int = minutes.coerceIn(0, LONG_MIN)

    /** A short plain length such as "45 min", "3 h 20 min" or "2 days 3 h". */
    fun describe(minutes: Int): String {
        val m = minutes.coerceAtLeast(0)
        val days = m / 1440
        val hours = (m % 1440) / 60
        val mins = m % 60
        return when {
            days > 0 -> (if (days == 1) "1 day" else "$days days") + if (hours > 0) " $hours h" else ""
            hours > 0 -> if (mins > 0) "$hours h $mins min" else "$hours h"
            else -> "$mins min"
        }
    }

    /** Reads what the box holds. Anything that is not a whole number of minutes counts as nothing; the result is never above a week. */
    fun parseMinutes(text: String): Int = text.trim().toIntOrNull()?.coerceIn(0, 7 * 1440) ?: 0
}
