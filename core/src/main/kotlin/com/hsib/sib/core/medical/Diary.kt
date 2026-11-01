package com.hsib.sib.core.medical

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** One class written in the diary. */
data class DiaryEntry(val id: String, val day: LocalDate, val subject: String, val topic: String, val faculty: String, val kind: String)

enum class DiarySpan { DAY, WEEK, MONTH, YEAR }

/** One bar of the chart: the first day it covers and how many classes of each kind fall in it. */
data class DiaryBucket(val start: LocalDate, val span: DiarySpan, val lectures: Int, val tutorials: Int, val wards: Int) {
    val total: Int get() = lectures + tutorials + wards
}

/** One teacher: how many classes, in how many subjects and on how many topics. */
data class FacultyRow(val faculty: String, val classes: Int, val subjects: List<String>, val topics: Int)

/**
 * One topic: how many classes it had in all, how many came from the teacher who taught it most (the same faculty), how many
 * from the others, and the teachers with their counts.
 */
data class TopicRow(
    val subject: String, val topic: String, val classes: Int,
    val sameFaculty: Int, val otherFaculty: Int, val faculties: List<Pair<String, Int>>,
)

/** Plain counting over the diary. No Android, so every number can be tested. Spelling and capital letters do not split a group. */
object DiaryStats {
    /** The key two spellings share: lower case, single spaces. */
    fun norm(s: String): String = s.trim().lowercase().replace(Regex("\\s+"), " ")

    fun kindOf(kind: String): Int = when (kind) { "Tutorial" -> 1; "Ward" -> 2; else -> 0 }

    fun startOf(day: LocalDate, span: DiarySpan, weekStart: DayOfWeek = DayOfWeek.SATURDAY): LocalDate = when (span) {
        DiarySpan.DAY -> day
        DiarySpan.WEEK -> day.with(TemporalAdjusters.previousOrSame(weekStart))
        DiarySpan.MONTH -> day.withDayOfMonth(1)
        DiarySpan.YEAR -> day.withDayOfYear(1)
    }

    private fun next(start: LocalDate, span: DiarySpan): LocalDate = when (span) {
        DiarySpan.DAY -> start.plusDays(1)
        DiarySpan.WEEK -> start.plusWeeks(1)
        DiarySpan.MONTH -> start.plusMonths(1)
        DiarySpan.YEAR -> start.plusYears(1)
    }

    /** How many bars a chart shows for each span: two weeks of days, 12 weeks, 12 months, and every year up to 8. */
    fun barCount(span: DiarySpan, entries: List<DiaryEntry>, today: LocalDate): Int = when (span) {
        DiarySpan.DAY -> 14
        DiarySpan.WEEK -> 12
        DiarySpan.MONTH -> 12
        DiarySpan.YEAR -> {
            val first = entries.minOfOrNull { it.day.year } ?: today.year
            (today.year - first + 1).coerceIn(1, 8)
        }
    }

    /** The chart bars, oldest first, ending with the span that holds [today]. Empty spans are kept so gaps are visible. */
    fun buckets(entries: List<DiaryEntry>, span: DiarySpan, today: LocalDate, weekStart: DayOfWeek = DayOfWeek.SATURDAY): List<DiaryBucket> {
        val n = barCount(span, entries, today)
        var start = startOf(today, span, weekStart)
        val starts = ArrayList<LocalDate>()
        repeat(n) {
            starts += start
            start = when (span) {
                DiarySpan.DAY -> start.minusDays(1)
                DiarySpan.WEEK -> start.minusWeeks(1)
                DiarySpan.MONTH -> start.minusMonths(1)
                DiarySpan.YEAR -> start.minusYears(1)
            }
        }
        starts.reverse()
        val counts = HashMap<LocalDate, IntArray>()
        for (e in entries) counts.getOrPut(startOf(e.day, span, weekStart)) { IntArray(3) }[kindOf(e.kind)]++
        return starts.map { s -> val c = counts[s] ?: IntArray(3); DiaryBucket(s, span, c[0], c[1], c[2]) }
    }

    /** Days between the first and last entry that have no entry, to show how steadily the diary is kept. */
    fun daysWritten(entries: List<DiaryEntry>): Int = entries.map { it.day }.distinct().size

    fun streakEndingAt(entries: List<DiaryEntry>, today: LocalDate): Int {
        val days = entries.map { it.day }.toSet()
        var d = if (today in days) today else today.minusDays(1)
        var n = 0
        while (d in days) { n++; d = d.minusDays(1) }
        return n
    }

    fun faculties(entries: List<DiaryEntry>): List<FacultyRow> {
        val shown = HashMap<String, String>()
        for (e in entries) if (e.faculty.isNotBlank()) shown.putIfAbsent(norm(e.faculty), e.faculty.trim())
        return entries.filter { it.faculty.isNotBlank() }.groupBy { norm(it.faculty) }.map { (k, l) ->
            FacultyRow(shown.getValue(k), l.size, l.map { it.subject.trim() }.filter { it.isNotEmpty() }.distinctBy(::norm).sorted(), l.map { norm(it.subject) + "|" + norm(it.topic) }.distinct().size)
        }.sortedWith(compareByDescending<FacultyRow> { it.classes }.thenBy { it.faculty.lowercase() })
    }

    /** Classes with no faculty written, so the person can see what the table leaves out. */
    fun withoutFaculty(entries: List<DiaryEntry>): Int = entries.count { it.faculty.isBlank() }

    fun topics(entries: List<DiaryEntry>, subject: String? = null): List<TopicRow> {
        val pool = if (subject == null) entries else entries.filter { norm(it.subject) == norm(subject) }
        val shownSubject = HashMap<String, String>()
        val shownTopic = HashMap<String, String>()
        val shownFaculty = HashMap<String, String>()
        for (e in pool) {
            shownSubject.putIfAbsent(norm(e.subject), e.subject.trim())
            shownTopic.putIfAbsent(norm(e.subject) + "|" + norm(e.topic), e.topic.trim())
            if (e.faculty.isNotBlank()) shownFaculty.putIfAbsent(norm(e.faculty), e.faculty.trim())
        }
        return pool.groupBy { norm(it.subject) + "|" + norm(it.topic) }.map { (k, l) ->
            val per = l.filter { it.faculty.isNotBlank() }.groupBy { norm(it.faculty) }.map { (f, x) -> shownFaculty.getValue(f) to x.size }
                .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
            val same = per.firstOrNull()?.second ?: 0
            val known = per.sumOf { it.second }
            TopicRow(shownSubject.getValue(norm(l.first().subject)), shownTopic.getValue(k), l.size, same, known - same, per)
        }.sortedWith(compareByDescending<TopicRow> { it.classes }.thenBy { it.topic.lowercase() })
    }

    fun subjects(entries: List<DiaryEntry>): List<String> {
        val shown = LinkedHashMap<String, String>()
        for (e in entries) if (e.subject.isNotBlank()) shown.putIfAbsent(norm(e.subject), e.subject.trim())
        return shown.values.sorted()
    }

    /** Teacher names already used, most used first, for quick chips. This keeps one spelling per person. */
    fun facultyNames(entries: List<DiaryEntry>): List<String> = faculties(entries).map { it.faculty }

    fun topicNames(entries: List<DiaryEntry>, subject: String): List<String> = topics(entries, subject).map { it.topic }

    fun daysSpan(entries: List<DiaryEntry>): Long = if (entries.isEmpty()) 0 else ChronoUnit.DAYS.between(entries.minOf { it.day }, entries.maxOf { it.day }) + 1
}
