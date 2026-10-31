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
            "2 lectures and 2 tutorials a week", hasCards = true, hasItems = true, examMonth = Month.NOVEMBER, supplementaryMonth = Month.MAY,
        ),
        Prof(
            2, "2nd Prof", listOf("Forensic Medicine", "Pharmacology", "Microbiology", "Pathology"),
            listOf("Written practical", "Assignment", "Postmortem report", "Certificates", "Case report (Pharmacology)"),
            "2 lectures, 1 tutorial and 1 ward round of 3 hours a week", hasCards = false, hasItems = true, examMonth = Month.NOVEMBER, supplementaryMonth = Month.MAY,
        ),
        Prof(
            3, "3rd Prof", listOf("Microbiology", "Pathology", "Community Medicine"),
            listOf("Written practical", "Assignment", "Day visit report", "RFST report", "Study tour report", "Case report (Pathology)", "Case report (Microbiology)"),
            "2 lectures, 1 tutorial and 1 ward round a week", hasCards = false, hasItems = true, examMonth = Month.NOVEMBER, supplementaryMonth = Month.MAY,
        ),
        Prof(
            4, "4th Prof", listOf("Medicine", "Surgery", "Obstetrics and Gynaecology"),
            listOf("Short case", "Long case", "Case report", "Log book"),
            "5 lectures and 2 ward rounds (morning and evening) a week", hasCards = false, hasItems = false, examMonth = Month.MAY, supplementaryMonth = Month.NOVEMBER,
        ),
    )

    fun prof(number: Int): Prof = profs.first { it.number == number }

    /** The exam kinds a prof has, as categories. The 4th prof has terms, practicals and ward finals only. */
    fun kinds(p: Prof): List<Category> = buildList {
        if (p.hasItems) add(Category.ITEM)
        if (p.hasCards) add(Category.CARD)
        add(Category.TERM)
        if (p.number == 4) add(Category.WARD_FINAL)
        add(Category.PRACTICAL)
        add(Category.PROF)
        add(Category.SUPPLEMENTARY)
        add(Category.REPORT)
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
        return when (kind) {
            Category.PROF -> if (p != null) "${p.name} exam" else "Prof exam"
            Category.SUPPLEMENTARY -> if (p != null) "${p.name} supplementary" else "Supplementary exam"
            else -> listOfNotNull(s, kind.label.substringBefore(" (")).joinToString(" ")
        }
    }

    /** The parts a whole-prof or term deadline is split into: one per subject. */
    fun partsFor(kind: Category, p: Prof): List<String> =
        if (kind == Category.PROF || kind == Category.SUPPLEMENTARY || kind == Category.TERM) p.subjects else emptyList()

    /** The structure in plain words, for the guide and the setup screen. */
    const val STRUCTURE =
        "5 years of study and 1 year of internship. A prof holds 2 terms. In the 1st prof a term holds 3 cards. A card holds items, " +
            "which are weekly class tests (every 2 weeks in the 1st prof). Items come first, then the card exam, then the term exam, then the prof exam. " +
            "1st, 2nd and 3rd prof are in November and the 4th prof is in May. Supplementary exams for the failed run the other way round."
}
