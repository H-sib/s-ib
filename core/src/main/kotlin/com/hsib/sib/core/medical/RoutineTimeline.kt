package com.hsib.sib.core.medical

/**
 * Small calculations behind the class routine screen, kept free of Android so they can be tested.
 * Times are minutes since midnight; a class is a pair of (start, end).
 */
object RoutineTimeline {
    /** The gaps between one class and the next, for classes already sorted by start. A class that overlaps or touches the next gives 0. */
    fun breaks(times: List<Pair<Int, Int>>): List<Int> =
        times.zipWithNext { a, b -> (b.first - a.second).coerceAtLeast(0) }

    /** Earliest start and latest end of the day, or null when there are no classes. */
    fun span(times: List<Pair<Int, Int>>): Pair<Int, Int>? =
        if (times.isEmpty()) null else times.minOf { it.first } to times.maxOf { it.second }

    /** The class that is on now or comes next: the first whose end is after [nowMin]. Null when the day is over or has no classes. */
    fun nextIndex(times: List<Pair<Int, Int>>, nowMin: Int): Int? =
        times.indices.firstOrNull { times[it].second > nowMin }

    /** A short break label such as "45 min break" or "1 h 15 min break"; null for a gap too short to be worth a label. */
    fun breakLabel(minutes: Int): String? = when {
        minutes < 10 -> null
        minutes < 60 -> "$minutes min break"
        minutes % 60 == 0 -> "${minutes / 60} h break"
        else -> "${minutes / 60} h ${minutes % 60} min break"
    }

    /**
     * The hours the week map shows: from the hour before the earliest class to the hour after the latest, at least [minHours] long,
     * and never outside the day. [times] is every class of the week.
     */
    fun mapHours(times: List<Pair<Int, Int>>, minHours: Int = 6): IntRange {
        val span = span(times) ?: return 8..(8 + minHours)
        var from = (span.first / 60).coerceIn(0, 23)
        var to = ((span.second + 59) / 60).coerceIn(1, 24)
        if (to - from < minHours) {
            to = (from + minHours).coerceAtMost(24)
            from = (to - minHours).coerceAtLeast(0)
        }
        return from..to
    }
}
