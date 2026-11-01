package com.hsib.sib.core.medical

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class DiaryStatsTest {
    private var n = 0
    private fun e(day: String, subject: String, topic: String, faculty: String, kind: String = "Lecture") =
        DiaryEntry("id${n++}", LocalDate.parse(day), subject, topic, faculty, kind)

    private val today = LocalDate.parse("2026-11-04") // a Wednesday

    @Test fun daysAreCountedByKindAndEmptyDaysStay() {
        val list = listOf(e("2026-11-04", "Anatomy", "Heart", "Dr A"), e("2026-11-04", "Anatomy", "Lung", "Dr A", "Tutorial"), e("2026-11-02", "Physiology", "Nerve", "Dr B", "Ward"))
        val b = DiaryStats.buckets(list, DiarySpan.DAY, today)
        assertEquals(14, b.size)
        assertEquals(today, b.last().start)
        assertEquals(1, b.last().lectures); assertEquals(1, b.last().tutorials); assertEquals(2, b.last().total)
        assertEquals(1, b.first { it.start == LocalDate.parse("2026-11-02") }.wards)
        assertEquals(0, b.first { it.start == LocalDate.parse("2026-11-03") }.total)
    }

    @Test fun weeksStartOnSaturday() {
        // Saturday 31 October to Friday 6 November is one week; Wednesday 4 November belongs to it.
        val list = listOf(e("2026-10-31", "A", "t1", "x"), e("2026-11-04", "A", "t2", "x"), e("2026-10-30", "A", "t3", "x"))
        val b = DiaryStats.buckets(list, DiarySpan.WEEK, today)
        assertEquals(12, b.size)
        assertEquals(LocalDate.parse("2026-10-31"), b.last().start)
        assertEquals(2, b.last().total)
        assertEquals(1, b[b.size - 2].total)
        assertTrue(DiaryStats.startOf(LocalDate.parse("2026-11-07"), DiarySpan.WEEK, DayOfWeek.SATURDAY) == LocalDate.parse("2026-11-07"))
    }

    @Test fun monthsAndYears() {
        val list = listOf(e("2026-11-01", "A", "t", "x"), e("2026-10-31", "A", "t", "x"), e("2025-03-05", "A", "t", "x"))
        val m = DiaryStats.buckets(list, DiarySpan.MONTH, today)
        assertEquals(12, m.size)
        assertEquals(1, m.last().total); assertEquals(1, m[10].total)
        val y = DiaryStats.buckets(list, DiarySpan.YEAR, today)
        assertEquals(listOf(2025, 2026), y.map { it.start.year })
        assertEquals(listOf(1, 2), y.map { it.total })
    }

    @Test fun sameAndDifferentFacultyPerTopicIgnoreSpelling() {
        val list = listOf(
            e("2026-11-01", "Anatomy", "Heart", "Dr A"), e("2026-11-02", "anatomy ", "heart", "dr a"), e("2026-11-03", "Anatomy", "Heart", "Dr B"),
            e("2026-11-03", "Anatomy", "Lung", "Dr B"), e("2026-11-04", "Anatomy", "Lung", ""),
        )
        val rows = DiaryStats.topics(list)
        val heart = rows.first { it.topic == "Heart" }
        assertEquals(3, heart.classes); assertEquals(2, heart.sameFaculty); assertEquals(1, heart.otherFaculty)
        assertEquals(listOf("Dr A" to 2, "Dr B" to 1), heart.faculties)
        val lung = rows.first { it.topic == "Lung" }
        assertEquals(2, lung.classes); assertEquals(1, lung.sameFaculty); assertEquals(0, lung.otherFaculty)
        assertEquals(1, DiaryStats.withoutFaculty(list))
        assertEquals(2, rows.size)
    }

    @Test fun facultyTable() {
        val list = listOf(e("2026-11-01", "Anatomy", "Heart", "Dr A"), e("2026-11-02", "Physiology", "Nerve", "Dr A"), e("2026-11-02", "Physiology", "Nerve", "dr a"), e("2026-11-03", "Anatomy", "Lung", "Dr B"))
        val f = DiaryStats.faculties(list)
        assertEquals("Dr A", f[0].faculty); assertEquals(3, f[0].classes); assertEquals(listOf("Anatomy", "Physiology"), f[0].subjects); assertEquals(2, f[0].topics)
        assertEquals(1, f[1].classes)
        assertEquals(listOf("Heart", "Lung"), DiaryStats.topicNames(list, "anatomy").sorted())
    }

    @Test fun streakAndDays() {
        val list = listOf(e("2026-11-04", "A", "t", "x"), e("2026-11-03", "A", "t", "x"), e("2026-11-01", "A", "t", "x"))
        assertEquals(2, DiaryStats.streakEndingAt(list, today))
        assertEquals(3, DiaryStats.daysWritten(list))
        assertEquals(4, DiaryStats.daysSpan(list))
        assertEquals(0, DiaryStats.streakEndingAt(emptyList(), today))
    }
}
