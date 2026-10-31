package com.hsib.sib.core.medical

import com.hsib.sib.core.model.Category
import java.time.LocalDate
import java.time.Month

/**
 * Helpers for a course's phases: which exam kinds a phase has, what to call a deadline, and when its exam month next comes.
 * The built in MBBS course is told by a medical student (Dr. Muser); every list is only a starting point, because colleges differ
 * and the student can change anything in Settings.
 */
object Medical {
    /** The exam kinds a phase has, in the order the students use them. Unknown names are ignored. */
    fun kinds(p: CoursePhase): List<Category> = p.kinds.mapNotNull { n -> Category.medical.firstOrNull { it.name == n } }

    /** The first day of the exam month that is still ahead. The student sets the exact day, since colleges publish their own. */
    fun nextExamMonthStart(p: CoursePhase, today: LocalDate, supplementary: Boolean): LocalDate {
        val month = Month.of((if (supplementary) p.supplementaryMonth else p.examMonth).coerceIn(1, 12))
        var d = LocalDate.of(today.year, month, 1)
        if (d.isBefore(today)) d = d.plusYears(1)
        return d
    }

    fun title(kind: Category, subject: String?, p: CoursePhase?): String {
        val s = subject?.takeIf { it.isNotBlank() }
        return when (kind) {
            Category.PROF -> p?.examName?.takeIf { it.isNotBlank() } ?: "Professional Exam"
            Category.SUPPLEMENTARY -> p?.examName?.takeIf { it.isNotBlank() }?.let { it.removeSuffix(" Exam") + " Supplementary Exam" } ?: "Supplementary Exam"
            else -> listOfNotNull(s, kind.label).joinToString(" ")
        }
    }

    /** The checklist a whole-phase or term deadline is split into: one item for each subject. */
    fun partsFor(kind: Category, p: CoursePhase): List<String> =
        if (kind == Category.PROF || kind == Category.SUPPLEMENTARY || kind == Category.TERM) p.subjects else emptyList()

    /** The structure of the built in course in plain words, for the guide and the setup screen. */
    const val STRUCTURE =
        "Phase I is 1.5 years, phase II 1 year, phase III 1 year and the final phase 1.5 years, then 1 year of internship. 5 years of study " +
            "and 1 year of internship. A prof holds 2 terms. In the 1st prof a term holds 3 cards. A card holds items, " +
            "which are weekly class tests (every 2 weeks in the 1st prof). Items come first, then the card, then the term, then the professional exam. " +
            "1st, 2nd and 3rd prof are in November and the 4th prof is in May. Supplementary exams for the failed run the other way round."
}

/** The most classes of each kind in one day. A phase can use fewer, never more; the student chooses the numbers. */
object RoutineLimits {
    const val LECTURES = 8
    const val TUTORIALS = 2
    const val WARDS = 2
}
