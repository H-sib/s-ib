package com.hsib.sib.core.extract

import com.hsib.sib.core.model.Candidate
import com.hsib.sib.core.model.Category
import com.hsib.sib.core.model.Relation

/**
 * Finds the suggestions that together look like an exam routine: three or more new, dated exams waiting at once, which is what
 * a photo or a scan of a timetable produces. The person is then asked whether to keep them as separate deadlines or as one
 * deadline with a part for each exam.
 */
object ExamRoutine {
    const val MIN_EXAMS = 3

    private val examLike = setOf(
        Category.EXAM, Category.ITEM, Category.CARD, Category.TERM, Category.PROF, Category.PRACTICAL, Category.WARD_FINAL, Category.SUPPLEMENTARY,
    )

    fun looksLikeExam(c: Candidate): Boolean =
        c.relation == Relation.NEW && c.due != null && (c.category in examLike || Regex("\\b(exam|test|viva|practical|quiz)\\b", RegexOption.IGNORE_CASE).containsMatchIn(c.title))

    /** The waiting suggestions that form a routine, or an empty list when there are fewer than [MIN_EXAMS]. */
    fun find(pending: List<Candidate>): List<Candidate> = pending.filter(::looksLikeExam).let { if (it.size >= MIN_EXAMS) it.sortedBy { c -> c.due!!.local } else emptyList() }
}
