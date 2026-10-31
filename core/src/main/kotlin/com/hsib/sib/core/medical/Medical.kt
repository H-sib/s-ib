package com.hsib.sib.core.medical

import com.hsib.sib.core.model.Category
import java.time.LocalDate
import java.time.Month

/**
 * How an MBBS course is laid out, as told by a medical student (Dr. Muser). Every list is a starting point that the student can
 * change; colleges differ. Nothing here is a rule, it only saves typing.
 */
data class Prof(
    val number: Int,
    val name: String,
    val subjects: List<String>,
    /** The practical work and reports that belong to this prof, in the order a student meets them. */
    val practicals: List<String>,
    /** What a normal week holds, in words. */
    val week: String,
    val hasCards: Boolean,
    val hasItems: Boolean,
    val examMonth: Month,
    val supplementaryMonth: Month,
)

object Medical {
    val profs: List<Prof> = listOf(
        Prof(
            1, "1st Prof", listOf("Anatomy", "Physiology", "Biochemistry"),
            listOf("Written practical", "Assignment"),
            "usually 2 lectures and 2 tutorials a day", hasCards = true, hasItems = true, examMonth = Month.NOVEMBER, supplementaryMonth = Month.MAY,
        ),
        Prof(
            2, "2nd Prof", listOf("Forensic Medicine", "Pharmacology", "Microbiology", "Pathology"),
            listOf("Written practical", "Assignment", "Postmortem report", "Certificates", "Case report (Pharmacology)"),
            "usually 2 lectures, 1 tutorial and 1 ward or block class of 3 hours a day", hasCards = false, hasItems = true, examMonth = Month.NOVEMBER, supplementaryMonth = Month.MAY,
        ),
        Prof(
            3, "3rd Prof", listOf("Microbiology", "Pathology", "Community Medicine"),
            listOf("Written practical", "Assignment", "Day visit report", "RFST report", "Study tour report", "Case report (Pathology)", "Case report (Microbiology)"),
            "usually 2 lectures, 1 tutorial and 1 ward or block class a day", hasCards = false, hasItems = true, examMonth = Month.NOVEMBER, supplementaryMonth = Month.MAY,
        ),
        Prof(
            4, "4th Prof", listOf("Medicine", "Surgery", "Obstetrics and Gynaecology"),
            listOf("Short case", "Long case", "Case report"),
            "usually 5 lectures and 2 ward or block classes (morning and evening) a day, no tutorials", hasCards = false, hasItems = false, examMonth = Month.MAY, supplementaryMonth = Month.NOVEMBER,
        ),
    )

    fun prof(number: Int): Prof = profs.first { it.number == number }

    /**
     * The exam kinds a prof has, in the order the students use them. The 4th prof has no items or cards but has ward finals.
     * Logbooks and reports are left out for now: the final phase has details that are not known yet.
     */
    fun kinds(p: Prof): List<Category> = buildList {
        if (p.hasItems) add(Category.ITEM)
        add(Category.TERM)
        if (p.hasCards) add(Category.CARD)
        add(Category.PROF)
        add(Category.SUPPLEMENTARY)
        add(Category.PRACTICAL)
        if (p.number == 4) add(Category.WARD_FINAL)
    }

    /** The first day of the exam month that is still ahead. The student sets the exact day, since colleges publish their own. */
    fun nextExamMonthStart(p: Prof, today: LocalDate, supplementary: Boolean): LocalDate {
        val month = if (supplementary) p.supplementaryMonth else p.examMonth
        var d = LocalDate.of(today.year, month, 1)
        if (d.isBefore(today)) d = d.plusYears(1)
        return d
    }

    fun title(kind: Category, subject: String?, p: Prof?): String {
        val s = subject?.takeIf { it.isNotBlank() }
        val ordinal = p?.name?.substringBefore(' ')
        return when (kind) {
            Category.PROF -> if (ordinal != null) "$ordinal Professional Exam" else "Professional Exam"
            Category.SUPPLEMENTARY -> if (ordinal != null) "$ordinal Professional Supplementary Exam" else "Supplementary Exam"
            else -> listOfNotNull(s, kind.label).joinToString(" ")
        }
    }

    /** The parts a whole-prof or term deadline is split into: one per subject. */
    fun partsFor(kind: Category, p: Prof): List<String> =
        if (kind == Category.PROF || kind == Category.SUPPLEMENTARY || kind == Category.TERM) p.subjects else emptyList()

    /** The structure in plain words, for the guide and the setup screen. */
    const val STRUCTURE =
        "5 years of study and 1 year of internship. A prof holds 2 terms. In the 1st prof a term holds 3 cards. A card holds items, " +
            "which are weekly class tests (every 2 weeks in the 1st prof). Items come first, then the card, then the term, then the professional exam. " +
            "1st, 2nd and 3rd prof are in November and the 4th prof is in May. Supplementary exams for the failed run the other way round."
}

/** The most classes of each kind in one day. A phase can use fewer, never more; the student chooses the numbers. */
object RoutineLimits {
    const val LECTURES = 8
    const val TUTORIALS = 2
    const val WARDS = 2
}
