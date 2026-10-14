package com.hsib.sib.core.plan

import com.hsib.sib.core.model.Importance
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One thing to schedule. Work is measured in minutes; the planner works in 15 minute units. */
data class PlanTask(
    val id: String,
    val title: String,
    val remainingMin: Int,
    val safeRemainingMin: Int,
    val dueDay: LocalDate,
    val earliestDay: LocalDate,
    val importance: Importance,
)

/** How many minutes the user can give on each day. */
class Capacity(private val perWeekday: Map<DayOfWeek, Int>, private val overrides: Map<LocalDate, Int> = emptyMap()) {
    fun minutes(day: LocalDate): Int = (overrides[day] ?: perWeekday[day.dayOfWeek] ?: 0).coerceAtLeast(0)

    companion object {
        fun uniform(minutes: Int) = Capacity(DayOfWeek.values().associateWith { minutes })

        fun standard(weekdayMin: Int, weekendMin: Int, weekend: Set<DayOfWeek>, overrides: Map<LocalDate, Int> = emptyMap()) =
            Capacity(DayOfWeek.values().associateWith { if (it in weekend) weekendMin else weekdayMin }, overrides)
    }
}

data class PlanBlock(val day: LocalDate, val taskId: String, val minutes: Int)

sealed interface Remedy {
    data class MoreTimePerDay(val extraMinPerDay: Int, val fromDay: LocalDate, val toDay: LocalDate) : Remedy
    data class ExtendDeadline(val taskId: String, val byDays: Int) : Remedy
    data class ReduceScope(val taskId: String, val minutes: Int) : Remedy
}

/** Why the plan cannot work, found by a minimum cut: the tightest stretch of days and exactly how short it is. */
data class Shortfall(
    val fromDay: LocalDate,
    val toDay: LocalDate,
    val demandMin: Int,
    val capacityMin: Int,
    val missingMin: Int,
    val taskIds: List<String>,
    val remedies: List<Remedy>,
)

data class AtRisk(val taskId: String, val unscheduledMin: Int)

data class PlanResult(
    val feasible: Boolean,
    val blocks: List<PlanBlock>,
    val loadByDay: Map<LocalDate, Int>,
    val capacityByDay: Map<LocalDate, Int>,
    val peakMin: Int,
    val shortfall: Shortfall?,
    val atRisk: List<AtRisk>,
    /** Latest day work may begin and still finish on time even if the effort is the pessimistic estimate. Null when already behind. */
    val latestStart: Map<String, LocalDate?>,
)

/**
 * Preparation planner.
 *
 * The problem is preemptive scheduling with deadlines and variable daily capacity. Feasibility, leveling
 * and the explanation of shortfalls all come from one model: a bipartite flow from tasks to days.
 *  - total flow == total work  <=>  a schedule exists (checked against Hall's interval condition in tests);
 *  - the minimum cut names the tightest interval and the exact number of minutes missing;
 *  - binary search on a per-day cap gives the smallest possible busiest day;
 *  - latest safe start is found by searching over start days with full re-solves, so it accounts for every other task.
 */
object Planner {
    const val UNIT = 15
    const val MAX_HORIZON_DAYS = 400
    const val MAX_SAFE_START_TASKS = 150
    private const val INF = 1_000_000

    private class Instance(val demand: IntArray, val lo: IntArray, val hi: IntArray, val cap: IntArray) {
        val n: Int get() = demand.size
        val d: Int get() = cap.size
        fun withWindows(newLo: IntArray) = Instance(demand, newLo, hi, cap)
        fun withDemand(newDemand: IntArray) = Instance(newDemand, lo, hi, cap)
    }

    private class FlowModel(val inst: Instance, levelCap: Int = INF, prioritized: Boolean = false) {
        val n = inst.n
        val d = inst.d
        val src = 0
        val sink = n + d + 1
        val g = MaxFlow(n + d + 2)
        val srcEdge = IntArray(n)
        private val edgeTask = ArrayList<Int>()
        private val edgeDay = ArrayList<Int>()
        private val edgeId = ArrayList<Int>()

        init {
            for (i in 0 until n) srcEdge[i] = g.addEdge(src, 1 + i, if (prioritized) 0 else inst.demand[i])
            for (i in 0 until n) {
                if (inst.demand[i] <= 0) continue
                for (dd in inst.lo[i]..inst.hi[i]) {
                    if (inst.cap[dd] <= 0) continue
                    edgeTask += i; edgeDay += dd; edgeId += g.addEdge(1 + i, 1 + n + dd, INF)
                }
            }
            for (dd in 0 until d) g.addEdge(1 + n + dd, sink, minOf(inst.cap[dd], levelCap))
        }

        fun runAll(): Int = g.run(src, sink)

        fun raise(i: Int) {
            g.setCapacity(srcEdge[i], inst.demand[i])
            g.run(src, sink)
        }

        fun taskFlow(i: Int): Int = g.flowOn(srcEdge[i])

        fun total(): Int = (0 until n).sumOf { taskFlow(it) }

        /** Allocation matrix: units[task][day]. */
        fun allocation(): Array<IntArray> {
            val a = Array(n) { IntArray(d) }
            for (k in edgeId.indices) {
                val f = g.flowOn(edgeId[k])
                if (f > 0) a[edgeTask[k]][edgeDay[k]] += f
            }
            return a
        }
    }

    private fun feasible(inst: Instance): Boolean = FlowModel(inst).let { it.runAll(); it.total() == inst.demand.sum() }

    private fun ceilDiv(a: Int, b: Int) = (a + b - 1) / b

    fun plan(tasks: List<PlanTask>, capacity: Capacity, today: LocalDate): PlanResult {
        val active = tasks.filter { it.remainingMin > 0 }
        if (active.isEmpty()) {
            return PlanResult(true, emptyList(), emptyMap(), emptyMap(), 0, null, emptyList(), tasks.associate { it.id to null })
        }
        val last = minOf(active.maxOf { maxOf(it.dueDay, today) }, today.plusDays(MAX_HORIZON_DAYS.toLong()))
        val d = ChronoUnit.DAYS.between(today, last).toInt() + 1
        fun dayAt(i: Int) = today.plusDays(i.toLong())
        fun idx(date: LocalDate) = ChronoUnit.DAYS.between(today, date).toInt().coerceIn(0, d - 1)
        val capUnits = IntArray(d) { capacity.minutes(dayAt(it)) / UNIT }
        val n = active.size
        val lo = IntArray(n) { idx(maxOf(active[it].earliestDay, today)) }
        val hi = IntArray(n) { maxOf(lo[it], idx(maxOf(active[it].dueDay, today))) }
        val expUnits = IntArray(n) { ceilDiv(active[it].remainingMin, UNIT) }
        val safeUnits = IntArray(n) { ceilDiv(maxOf(active[it].safeRemainingMin, active[it].remainingMin), UNIT) }
        val full = Instance(expUnits, lo, hi, capUnits)

        val fullModel = FlowModel(full)
        fullModel.runAll()
        val isFeasible = fullModel.total() == expUnits.sum()

        val capacityByDay = (0 until d).associate { dayAt(it) to capUnits[it] * UNIT }

        if (!isFeasible) {
            return infeasiblePlan(active, full, fullModel, capacity, today, ::dayAt, capacityByDay)
        }

        val lss = latestStartIndices(active, full, safeUnits)
        val latest: Map<String, LocalDate?> = tasks.associate { t ->
            val k = active.indexOfFirst { it.id == t.id }
            t.id to (if (k >= 0 && lss[k] >= 0) dayAt(lss[k]) else null)
        }

        // Concentrate each task's work near the time it is really needed instead of smearing it over months.
        val lo2 = IntArray(n) { i ->
            if (lss[i] < 0) lo[i] else {
                val lead = hi[i] - lo[i]
                val buffer = (lead / 5).coerceIn(1, 10)
                maxOf(lo[i], lss[i] - buffer)
            }
        }
        var inst = full.withWindows(lo2)
        if (!feasible(inst)) inst = full

        val level = minimalPeak(inst)
        val model = FlowModel(inst, level)
        model.runAll()
        val alloc = model.allocation()
        balance(alloc, inst)
        return buildResult(true, active, alloc, inst, ::dayAt, capacityByDay, null, emptyList(), latest)
    }

    // ---- leveling ----------------------------------------------------------------------------

    private fun minimalPeak(inst: Instance): Int {
        val total = inst.demand.sum()
        var lo = maxOf(1, ceilDiv(total, inst.cap.count { it > 0 }.coerceAtLeast(1)))
        var hi = (inst.cap.maxOrNull() ?: 1).coerceAtLeast(1)
        if (lo > hi) lo = hi
        while (lo < hi) {
            val mid = (lo + hi) / 2
            val m = FlowModel(inst, mid)
            m.runAll()
            if (m.total() == total) hi = mid else lo = mid + 1
        }
        return hi
    }

    /** Moves single units from the busiest day to a quieter day whenever some task allows it. */
    private fun balance(alloc: Array<IntArray>, inst: Instance) {
        val d = inst.d
        val n = inst.n
        val load = IntArray(d)
        for (i in 0 until n) for (dd in 0 until d) load[dd] += alloc[i][dd]
        val stuck = BooleanArray(d)
        var guard = 200_000
        while (guard-- > 0) {
            var h = -1
            for (dd in 0 until d) if (!stuck[dd] && load[dd] > 0 && (h < 0 || load[dd] > load[h])) h = dd
            if (h < 0) return
            var moved = false
            val lows = (0 until d).filter { load[it] <= load[h] - 2 && load[it] < inst.cap[it] }.sortedBy { load[it] }
            outer@ for (l in lows) {
                for (i in 0 until n) {
                    if (alloc[i][h] > 0 && l >= inst.lo[i] && l <= inst.hi[i]) {
                        alloc[i][h]--; alloc[i][l]++; load[h]--; load[l]++
                        moved = true
                        break@outer
                    }
                }
            }
            if (!moved) stuck[h] = true else java.util.Arrays.fill(stuck, false)
        }
    }

    // ---- latest safe start -------------------------------------------------------------------

    /** For each task, the latest start index that keeps everything feasible under pessimistic effort; -1 when none. */
    private fun latestStartIndices(active: List<PlanTask>, full: Instance, safeUnits: IntArray): IntArray {
        val n = full.n
        val out = IntArray(n) { -1 }
        var demand = safeUnits
        var base = full.withDemand(demand)
        if (!feasible(base)) {
            demand = full.demand
            base = full
        }
        if (n > MAX_SAFE_START_TASKS) return out
        for (i in 0 until n) {
            // Cheap upper bound: walking backwards from the due day, when has this task alone collected enough capacity?
            var acc = 0
            var simple = full.hi[i]
            var dd = full.hi[i]
            while (dd >= full.lo[i]) {
                acc += full.cap[dd]
                simple = dd
                if (acc >= demand[i]) break
                dd--
            }
            if (acc < demand[i]) continue
            var lo = full.lo[i]
            var hi = simple
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                val newLo = full.lo.copyOf().also { it[i] = mid }
                if (feasible(Instance(demand, newLo, full.hi, full.cap))) lo = mid else hi = mid - 1
            }
            out[i] = lo
        }
        return out
    }

    // ---- infeasible: compressed plan, min cut explanation, remedies -------------------------

    private fun infeasiblePlan(
        active: List<PlanTask>,
        full: Instance,
        fullModel: FlowModel,
        capacity: Capacity,
        today: LocalDate,
        dayAt: (Int) -> LocalDate,
        capacityByDay: Map<LocalDate, Int>,
    ): PlanResult {
        val n = full.n
        // Priority order: importance first, then the earliest deadline.
        val order = (0 until n).sortedWith(
            compareByDescending<Int> { active[it].importance.weight }.thenBy { full.hi[it] }.thenBy { full.lo[it] },
        )
        val pm = FlowModel(full, INF, prioritized = true)
        for (i in order) pm.raise(i)
        val alloc = pm.allocation()
        balance(alloc, full)
        val atRisk = (0 until n).filter { pm.taskFlow(it) < full.demand[it] }
            .map { AtRisk(active[it].id, (full.demand[it] - pm.taskFlow(it)) * UNIT) }

        // Minimum cut of the unrestricted flow names the bottleneck.
        val reach = fullModel.g.reachable(fullModel.src)
        val tightTasks = (0 until n).filter { reach[1 + it] && full.demand[it] > 0 }
        val tightDays = (0 until full.d).filter { reach[1 + n + it] }
        val demandUnits = tightTasks.sumOf { full.demand[it] }
        val capUnits = tightDays.sumOf { full.cap[it] }
        val missing = (demandUnits - capUnits).coerceAtLeast(1)
        val fromIdx = tightDays.minOrNull() ?: tightTasks.minOf { full.lo[it] }
        val toIdx = maxOf(tightDays.maxOrNull() ?: 0, tightTasks.maxOfOrNull { full.hi[it] } ?: 0)

        val remedies = mutableListOf<Remedy>()
        // 1. A little more time on every day of the tight stretch.
        for (extra in 1..16) {
            val caps = full.cap.copyOf()
            for (dd in fromIdx..minOf(toIdx, full.d - 1)) caps[dd] += extra
            if (feasible(Instance(full.demand, full.lo, full.hi, caps))) {
                remedies += Remedy.MoreTimePerDay(extra * UNIT, dayAt(fromIdx), dayAt(minOf(toIdx, full.d - 1)))
                break
            }
        }
        // 2. Move the least important deadline in the tight set later.
        val weakest = tightTasks.minWithOrNull(compareBy<Int> { active[it].importance.weight }.thenByDescending { full.hi[it] })
        if (weakest != null) {
            var lo = 1
            var hi = 60
            fun feasibleWithExtension(k: Int): Boolean {
                val dNew = full.d + k
                val caps = IntArray(dNew) { if (it < full.d) full.cap[it] else capacity.minutes(dayAt(0).plusDays(it.toLong())) / UNIT }
                val his = full.hi.copyOf().also { it[weakest] = minOf(dNew - 1, it[weakest] + k) }
                return feasible(Instance(full.demand, full.lo, his, caps))
            }
            if (feasibleWithExtension(hi)) {
                while (lo < hi) {
                    val mid = (lo + hi) / 2
                    if (feasibleWithExtension(mid)) hi = mid else lo = mid + 1
                }
                remedies += Remedy.ExtendDeadline(active[weakest].id, hi)
            }
            // 3. Or drop scope from that task.
            remedies += Remedy.ReduceScope(active[weakest].id, minOf(missing, full.demand[weakest]) * UNIT)
        }

        val shortfall = Shortfall(
            fromDay = dayAt(fromIdx),
            toDay = dayAt(minOf(toIdx, full.d - 1)),
            demandMin = demandUnits * UNIT,
            capacityMin = capUnits * UNIT,
            missingMin = missing * UNIT,
            taskIds = tightTasks.map { active[it].id },
            remedies = remedies,
        )
        val latest: Map<String, LocalDate?> = active.associate { it.id to null }
        return buildResult(false, active, alloc, full, dayAt, capacityByDay, shortfall, atRisk, latest)
    }

    private fun buildResult(
        feasible: Boolean,
        active: List<PlanTask>,
        alloc: Array<IntArray>,
        inst: Instance,
        dayAt: (Int) -> LocalDate,
        capacityByDay: Map<LocalDate, Int>,
        shortfall: Shortfall?,
        atRisk: List<AtRisk>,
        latest: Map<String, LocalDate?>,
    ): PlanResult {
        val blocks = ArrayList<PlanBlock>()
        val load = HashMap<LocalDate, Int>()
        for (i in 0 until inst.n) for (dd in 0 until inst.d) {
            val u = alloc[i][dd]
            if (u > 0) {
                val day = dayAt(dd)
                blocks += PlanBlock(day, active[i].id, u * UNIT)
                load[day] = (load[day] ?: 0) + u * UNIT
            }
        }
        blocks.sortWith(compareBy<PlanBlock> { it.day }.thenBy { it.taskId })
        return PlanResult(feasible, blocks, load, capacityByDay, load.values.maxOrNull() ?: 0, shortfall, atRisk, latest)
    }
}
