package com.hsib.sib.core.plan

import java.time.LocalDate

/** What accepting a suggestion would do to the schedule, found by planning with and without it. */
data class Impact(
    val addedMin: Int,
    val feasibleBefore: Boolean,
    val feasibleAfter: Boolean,
    val missingBeforeMin: Int,
    val missingAfterMin: Int,
    val peakBeforeMin: Int,
    val peakAfterMin: Int,
    val firstDay: LocalDate?,
    val lastDay: LocalDate?,
    /** Ids of tasks that become at risk only because of the new one. */
    val newlyAtRisk: List<String>,
) {
    val extraMissingMin: Int get() = (missingAfterMin - missingBeforeMin).coerceAtLeast(0)
    val collides: Boolean get() = extraMissingMin > 0 || (feasibleBefore && !feasibleAfter)
}

object ImpactPreview {
    fun compute(tasks: List<PlanTask>, capacity: Capacity, today: LocalDate, candidate: PlanTask): Impact {
        val before = Planner.plan(tasks, capacity, today)
        val after = Planner.plan(tasks + candidate, capacity, today)
        fun missing(r: PlanResult) = r.atRisk.sumOf { it.unscheduledMin }
        val mine = after.blocks.filter { it.taskId == candidate.id }
        val atRiskBefore = before.atRisk.map { it.taskId }.toSet()
        return Impact(
            addedMin = candidate.remainingMin,
            feasibleBefore = before.feasible,
            feasibleAfter = after.feasible,
            missingBeforeMin = missing(before),
            missingAfterMin = missing(after),
            peakBeforeMin = before.peakMin,
            peakAfterMin = after.peakMin,
            firstDay = mine.minOfOrNull { it.day },
            lastDay = mine.maxOfOrNull { it.day },
            newlyAtRisk = after.atRisk.map { it.taskId }.filter { it != candidate.id && it !in atRiskBefore },
        )
    }
}
