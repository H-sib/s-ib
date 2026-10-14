package com.hsib.sib.core.plan

import com.hsib.sib.core.model.Importance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Random

class PlannerTest {
    private val today = LocalDate.of(2026, 10, 14)

    private fun task(id: String, minutes: Int, dueInDays: Int, importance: Importance = Importance.NORMAL, safe: Int = minutes, start: Int = 0) =
        PlanTask(id, id, minutes, safe, today.plusDays(dueInDays.toLong()), today.plusDays(start.toLong()), importance)

    // ---- max flow ----------------------------------------------------------------------------

    @Test fun maxFlowKnownGraph() {
        // Classic example with maximum flow 23.
        val g = MaxFlow(6)
        g.addEdge(0, 1, 16); g.addEdge(0, 2, 13); g.addEdge(1, 2, 10); g.addEdge(2, 1, 4)
        g.addEdge(1, 3, 12); g.addEdge(3, 2, 9); g.addEdge(2, 4, 14); g.addEdge(4, 3, 7)
        g.addEdge(3, 5, 20); g.addEdge(4, 5, 4)
        assertEquals(23, g.run(0, 5))
    }

    @Test fun maxFlowCanBeRaisedIncrementally() {
        val g = MaxFlow(4)
        val a = g.addEdge(0, 1, 0)
        val b = g.addEdge(0, 2, 0)
        g.addEdge(1, 3, 5)
        g.addEdge(2, 3, 5)
        g.setCapacity(a, 4)
        assertEquals(4, g.run(0, 3))
        g.setCapacity(b, 4)
        assertEquals(4, g.run(0, 3))
        assertEquals(4, g.flowOn(a))
        assertEquals(4, g.flowOn(b))
        // Raising past what the sink can take only adds what fits: both paths end in an edge of capacity 5.
        g.setCapacity(b, 9)
        assertEquals(1, g.run(0, 3))
        assertEquals(5, g.flowOn(b))
    }

    @Test fun maxFlowSurvivesLongAugmentingPaths() {
        // A long chain forces a deep residual search; an iterative solver must not overflow the stack.
        val n = 30000
        val g = MaxFlow(n)
        for (i in 0 until n - 1) g.addEdge(i, i + 1, 3)
        assertEquals(3, g.run(0, n - 1))
    }

    // ---- the worked example from the proposal -----------------------------------------------

    @Test fun workedExampleIsNineHoursShort() {
        val cap = Capacity.uniform(180)
        val a = task("A", 480, 3)
        val b = task("B", 600, 5)
        val c = task("C", 720, 6)
        val r = Planner.plan(listOf(a, b, c), cap, today)
        assertFalse(r.feasible)
        val s = r.shortfall!!
        assertEquals(540, s.missingMin)
        assertEquals(1800, s.demandMin)
        assertEquals(1260, s.capacityMin)
        assertEquals(today, s.fromDay)
        assertEquals(today.plusDays(6), s.toDay)
        assertEquals(setOf("A", "B", "C"), s.taskIds.toSet())
        // Equal importance: earlier deadlines are protected, the last task is the one at risk.
        assertEquals(listOf(AtRisk("C", 540)), r.atRisk)
        val extra = s.remedies.filterIsInstance<Remedy.MoreTimePerDay>().single()
        assertEquals(90, extra.extraMinPerDay)
        assertEquals(Remedy.ExtendDeadline("C", 3), s.remedies.filterIsInstance<Remedy.ExtendDeadline>().single())
        assertEquals(Remedy.ReduceScope("C", 540), s.remedies.filterIsInstance<Remedy.ReduceScope>().single())
        // The compressed plan never exceeds capacity.
        for ((day, load) in r.loadByDay) assertTrue(load <= cap.minutes(day))
        assertEquals(1260, r.blocks.sumOf { it.minutes })
    }

    @Test fun moreImportantTaskIsProtectedWhenTimeRunsOut() {
        val cap = Capacity.uniform(120)
        val low = task("low", 600, 4, Importance.LOW)
        val high = task("high", 600, 4, Importance.CRITICAL)
        val r = Planner.plan(listOf(low, high), cap, today)
        assertFalse(r.feasible)
        val scheduledHigh = r.blocks.filter { it.taskId == "high" }.sumOf { it.minutes }
        assertEquals(600, scheduledHigh)
        assertEquals("low", r.atRisk.single().taskId)
    }

    // ---- feasible plans -----------------------------------------------------------------------

    @Test fun singleTaskIsScheduledWithinCapacity() {
        val cap = Capacity.uniform(120)
        val r = Planner.plan(listOf(task("t", 360, 10, safe = 576)), cap, today)
        assertTrue(r.feasible)
        assertEquals(360, r.blocks.sumOf { it.minutes })
        for ((day, load) in r.loadByDay) assertTrue(load <= cap.minutes(day))
        assertTrue(r.blocks.all { !it.day.isAfter(today.plusDays(10)) })
        assertNull(r.shortfall)
    }

    @Test fun latestSafeStartUsesPessimisticEffort() {
        // 576 min = 39 units at 8 units per day needs 5 days, so work must begin 4 days before the due day at the latest.
        val r = Planner.plan(listOf(task("t", 360, 10, safe = 576)), Capacity.uniform(120), today)
        assertEquals(today.plusDays(6), r.latestStart["t"])
    }

    @Test fun latestSafeStartAccountsForOtherTasks() {
        val cap = Capacity.uniform(120)
        // Task b can only be worked on in the last four days, so it eats most of the late capacity task a would want.
        val alone = Planner.plan(listOf(task("a", 240, 8)), cap, today).latestStart["a"]!!
        val crowded = Planner.plan(listOf(task("a", 240, 8), task("b", 360, 8, start = 5)), cap, today).latestStart["a"]!!
        assertEquals(today.plusDays(7), alone)
        // Window [s, D8] must hold task a (16 units) plus b (24 units, confined to D5..D8): 5 days is the last start that fits.
        assertEquals(today.plusDays(4), crowded)
    }

    @Test fun workIsConcentratedNearWhenItIsNeededAndLevelled() {
        val r = Planner.plan(listOf(task("t", 240, 8, safe = 384)), Capacity.uniform(120), today)
        assertTrue(r.feasible)
        assertEquals(240, r.blocks.sumOf { it.minutes })
        assertTrue("peak should be spread, was ${r.peakMin}", r.peakMin <= 60)
        assertTrue(r.blocks.map { it.day }.distinct().size >= 4)
        assertTrue("nothing needs to start today for an 8 day task", r.blocks.none { it.day == today })
    }

    @Test fun twoTasksWithTheSameWindowShareDaysEvenly() {
        val r = Planner.plan(listOf(task("a", 300, 5), task("b", 300, 5)), Capacity.uniform(240), today)
        assertTrue(r.feasible)
        val loads = r.loadByDay.values
        assertTrue("loads should differ by at most one unit: $loads", loads.max() - loads.min() <= 15 || loads.size < 6)
    }

    @Test fun weekendsWithNoCapacityAreSkipped() {
        val weekend = setOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY)
        val cap = Capacity.standard(120, 0, weekend)
        val r = Planner.plan(listOf(task("t", 480, 12)), cap, today)
        assertTrue(r.feasible)
        assertTrue(r.blocks.none { it.day.dayOfWeek in weekend })
    }

    @Test fun overdueTaskCompetesForTodayOnly() {
        val r = Planner.plan(listOf(task("late", 60, -3)), Capacity.uniform(120), today)
        assertTrue(r.feasible)
        assertTrue(r.blocks.all { it.day == today })
    }

    @Test fun overdueTaskThatCannotFitIsReportedAsAtRisk() {
        val r = Planner.plan(listOf(task("late", 600, -3)), Capacity.uniform(120), today)
        assertFalse(r.feasible)
        assertEquals(480, r.atRisk.single().unscheduledMin)
    }

    @Test fun finishedTasksProduceNoWork() {
        val r = Planner.plan(listOf(task("done", 0, 5)), Capacity.uniform(120), today)
        assertTrue(r.feasible)
        assertTrue(r.blocks.isEmpty())
        assertNull(r.latestStart["done"])
    }

    @Test fun postponedTasksDoNotStartBeforeTheirEarliestDay() {
        val r = Planner.plan(listOf(task("later", 240, 10, start = 6)), Capacity.uniform(120), today)
        assertTrue(r.feasible)
        assertTrue(r.blocks.all { !it.day.isBefore(today.plusDays(6)) })
    }

    // ---- correctness against an independent oracle -----------------------------------------

    /** Hall's condition for interval windows: for every interval, demand inside it must fit its capacity. */
    private fun hallFeasible(demands: IntArray, lo: IntArray, hi: IntArray, cap: IntArray): Boolean {
        val d = cap.size
        for (a in 0 until d) for (b in a until d) {
            var demand = 0
            for (i in demands.indices) if (lo[i] >= a && hi[i] <= b) demand += demands[i]
            var capacity = 0
            for (x in a..b) capacity += cap[x]
            if (demand > capacity) return false
        }
        return true
    }

    @Test fun feasibilityMatchesHallsConditionOnRandomInstances() {
        val rnd = Random(2026)
        var feasibleSeen = 0
        var infeasibleSeen = 0
        repeat(600) {
            val days = 4 + rnd.nextInt(10)
            val n = 1 + rnd.nextInt(6)
            val capUnits = IntArray(days) { rnd.nextInt(9) }
            val capacity = Capacity(java.time.DayOfWeek.values().associateWith { 0 }, (0 until days).associate { today.plusDays(it.toLong()) to capUnits[it] * 15 })
            val demands = IntArray(n) { 1 + rnd.nextInt(14) }
            val lo = IntArray(n) { rnd.nextInt(days) }
            val hi = IntArray(n) { lo[it] + rnd.nextInt(days - lo[it]) }
            // The last day must be covered so the planner's horizon equals the oracle's.
            hi[0] = days - 1
            val tasks = (0 until n).map {
                PlanTask("t$it", "t$it", demands[it] * 15, demands[it] * 15, today.plusDays(hi[it].toLong()), today.plusDays(lo[it].toLong()), Importance.NORMAL)
            }
            val expected = hallFeasible(demands, lo, hi, capUnits)
            val r = Planner.plan(tasks, capacity, today)
            assertEquals("instance demands=${demands.toList()} lo=${lo.toList()} hi=${hi.toList()} cap=${capUnits.toList()}", expected, r.feasible)
            if (expected) {
                feasibleSeen++
                assertEquals(demands.sum() * 15, r.blocks.sumOf { it.minutes })
                for ((day, load) in r.loadByDay) assertTrue(load <= capacity.minutes(day))
                for (b in r.blocks) {
                    val i = b.taskId.removePrefix("t").toInt()
                    assertTrue("block outside window", !b.day.isBefore(today.plusDays(lo[i].toLong())) && !b.day.isAfter(today.plusDays(hi[i].toLong())))
                }
            } else {
                infeasibleSeen++
                val s = r.shortfall!!
                assertTrue(s.missingMin > 0)
                assertEquals(s.demandMin - s.capacityMin, s.missingMin)
            }
        }
        assertTrue("generator should produce both outcomes ($feasibleSeen / $infeasibleSeen)", feasibleSeen > 50 && infeasibleSeen > 50)
    }

    @Test fun shortfallEqualsTotalDemandMinusMaxFlow() {
        val rnd = Random(5)
        repeat(200) {
            val n = 2 + rnd.nextInt(5)
            val tasks = (0 until n).map { task("t$it", 15 * (4 + rnd.nextInt(30)), rnd.nextInt(8)) }
            val r = Planner.plan(tasks, Capacity.uniform(90), today)
            val scheduled = r.blocks.sumOf { it.minutes }
            val demand = tasks.sumOf { it.remainingMin }
            if (!r.feasible) assertEquals(demand - scheduled, r.atRisk.sumOf { it.unscheduledMin })
            else assertEquals(demand, scheduled)
        }
    }

    // ---- scale ---------------------------------------------------------------------------------

    @Test fun threeHundredTasksOverFourMonthsPlanQuickly() {
        val rnd = Random(9)
        val tasks = (0 until 300).map { task("t$it", 15 * (4 + rnd.nextInt(28)), 5 + rnd.nextInt(115)) }
        val started = System.nanoTime()
        val r = Planner.plan(tasks, Capacity.uniform(360), today)
        val ms = (System.nanoTime() - started) / 1_000_000
        println("planned 300 tasks over 120 days in $ms ms (feasible=${r.feasible})")
        assertTrue("took $ms ms", ms < 30_000)
        assertTrue(r.blocks.isNotEmpty())
        for ((day, load) in r.loadByDay) assertTrue(load <= 360 || day.isBefore(today))
    }
}
