package com.hsib.sib.core.plan

import com.hsib.sib.core.model.Category
import java.time.LocalDate
import kotlin.math.roundToInt

/** A suggested checkpoint inside a plan. Always a suggestion, never a real deadline. */
data class Step(val title: String, val minutes: Int, val targetDay: LocalDate)

/**
 * Stage templates per category. Stages are shares of the total effort, not fixed durations, so the same
 * template stretches over three months or compresses into two days. When there is too little time for every
 * stage, the least essential ones are dropped first.
 */
object PlanTemplates {
    private data class Stage(val title: String, val share: Double, val essential: Boolean)

    private val assignment = listOf(
        Stage("Understand the requirements and gather sources", 0.15, false),
        Stage("Outline and structure", 0.15, false),
        Stage("Write the first draft", 0.40, true),
        Stage("Revise and verify", 0.20, true),
        Stage("Final check and submit", 0.10, true),
    )
    private val exam = listOf(
        Stage("Map the syllabus and set priorities", 0.10, false),
        Stage("Core revision", 0.40, true),
        Stage("Practice questions and past papers", 0.30, true),
        Stage("Timed practice", 0.10, false),
        Stage("Light review and logistics", 0.10, true),
    )
    private val application = listOf(
        Stage("Check eligibility and requirements", 0.10, false),
        Stage("Gather documents", 0.30, true),
        Stage("Draft statements and forms", 0.30, true),
        Stage("Review everything", 0.20, false),
        Stage("Submit before the last date", 0.10, true),
    )
    private val meeting = listOf(
        Stage("Review the agenda", 0.30, false),
        Stage("Prepare materials", 0.50, true),
        Stage("Final check", 0.20, true),
    )
    private val bill = listOf(
        Stage("Check the amount and funds", 0.40, false),
        Stage("Pay", 0.60, true),
    )
    private val generic = listOf(
        Stage("Plan the work", 0.20, false),
        Stage("Do the work", 0.60, true),
        Stage("Review and finish", 0.20, true),
    )

    private fun stagesFor(c: Category) = when (c) {
        Category.ASSIGNMENT -> assignment
        Category.EXAM -> exam
        Category.APPLICATION -> application
        Category.MEETING, Category.APPOINTMENT -> meeting
        Category.BILL -> bill
        else -> generic
    }

    /**
     * Steps for one task from its planned blocks. Target days are where the cumulative planned effort
     * crosses each stage boundary, so the steps follow the plan instead of fighting it.
     */
    fun steps(category: Category, blocks: List<PlanBlock>): List<Step> {
        val sorted = blocks.sortedBy { it.day }
        val total = sorted.sumOf { it.minutes }
        if (total <= 0) return emptyList()
        var stages = stagesFor(category)
        // Each stage needs at least one planning unit; drop optional stages, least valuable (earliest listed) first.
        while (stages.size > 1 && total < stages.size * Planner.UNIT) {
            val drop = stages.firstOrNull { !it.essential } ?: break
            stages = stages - drop
        }
        if (stages.size == 1 || total < Planner.UNIT * 2) return listOf(Step("Do it", total, sorted.last().day))
        val shareSum = stages.sumOf { it.share }
        var cumulativeShare = 0.0
        var assigned = 0
        val out = ArrayList<Step>()
        stages.forEachIndexed { i, st ->
            cumulativeShare += st.share / shareSum
            val boundary = (cumulativeShare * total).roundToInt().coerceAtLeast(assigned)
            val minutes = if (i == stages.size - 1) total - assigned else boundary - assigned
            var acc = 0
            var day = sorted.last().day
            for (b in sorted) {
                acc += b.minutes
                if (acc >= boundary) { day = b.day; break }
            }
            out += Step(st.title, minutes.coerceAtLeast(0), day)
            assigned += minutes.coerceAtLeast(0)
        }
        return out
    }
}

/** Learns how far off the user's own estimates run, on this device only. */
object EstimateCalibration {
    /** Factor to scale new estimates by, from pairs of (estimated likely minutes, actual minutes spent). */
    fun multiplier(history: List<Pair<Int, Int>>): Double {
        val ratios = history.filter { it.first > 0 && it.second > 0 }.map { it.second.toDouble() / it.first }.sorted()
        if (ratios.size < 3) return 1.0
        val median = if (ratios.size % 2 == 1) ratios[ratios.size / 2] else (ratios[ratios.size / 2 - 1] + ratios[ratios.size / 2]) / 2
        val n = ratios.size.toDouble()
        val shrunk = 1.0 + (median - 1.0) * (n / (n + 3.0))
        return shrunk.coerceIn(0.7, 2.5)
    }
}

/** Learns which hour the user usually opens the app, so the daily digest arrives when it will be seen. */
class RhythmTracker(private val counts: DoubleArray = DoubleArray(24)) {
    fun record(hour: Int) {
        for (i in counts.indices) counts[i] *= 0.97
        counts[hour.coerceIn(0, 23)] += 1.0
    }

    /** Best digest hour in [from, to], or [default] until there is enough history. */
    fun bestHour(default: Int, from: Int = 6, to: Int = 11): Int {
        if (counts.sum() < 8.0) return default
        return (from..to).maxByOrNull { counts[it] } ?: default
    }

    fun serialize(): String = counts.joinToString(",") { "%.3f".format(java.util.Locale.ROOT, it) }

    companion object {
        fun parse(s: String?): RhythmTracker {
            val parts = s?.split(",")?.mapNotNull { it.toDoubleOrNull() }
            return RhythmTracker(if (parts != null && parts.size == 24) parts.toDoubleArray() else DoubleArray(24))
        }
    }
}
