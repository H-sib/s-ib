package com.hsib.sib.domain

import com.hsib.sib.core.model.Candidate
import com.hsib.sib.core.model.Lifecycle
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Space
import com.hsib.sib.core.plan.Capacity
import com.hsib.sib.core.plan.EstimateCalibration
import com.hsib.sib.core.plan.PlanBlock
import com.hsib.sib.core.plan.PlanResult
import com.hsib.sib.core.plan.PlanTask
import com.hsib.sib.core.plan.PlanTemplates
import com.hsib.sib.core.plan.Planner
import com.hsib.sib.core.plan.Step
import com.hsib.sib.core.urgency.Urgency
import com.hsib.sib.core.urgency.UrgencyInput
import com.hsib.sib.core.urgency.UrgencyState
import com.hsib.sib.data.AppSettings
import com.hsib.sib.data.SettingsStore
import com.hsib.sib.data.StoredCandidate
import com.hsib.sib.data.Store
import com.hsib.sib.data.UserStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** One deadline as the interface needs to show it. Everything here is derived, nothing is stored. */
data class ObligationView(
    val o: Obligation,
    val urgency: UrgencyState,
    val pressure: Double,
    val latestStart: LocalDate?,
    val dueInstant: Instant?,
    val planDay: LocalDate?,
    val atRiskMin: Int,
    val blocks: List<PlanBlock>,
    val tentative: Boolean,
)

data class AppState(
    val loaded: Boolean,
    val now: Instant,
    val zone: ZoneId,
    val settings: AppSettings,
    val items: List<ObligationView>,
    val plan: PlanResult,
    val planTasks: List<PlanTask>,
    val capacity: Capacity,
    val pending: List<Candidate>,
    val snoozedCount: Int,
    val steps: Map<String, List<UserStep>>,
    val suggestedSteps: Map<String, List<Step>>,
    val calibration: Double,
) {
    /** Work and study deadlines. Everything that feeds the widget, tile, summaries and the plan comes from here. */
    val active: List<ObligationView> get() = items.filter { it.o.lifecycle == Lifecycle.ACTIVE && it.o.space == Space.WORK }

    /** The private Close ones space: occasions for the people who matter. Kept apart from the work picture on purpose. */
    val close: List<ObligationView> get() = items.filter { it.o.lifecycle == Lifecycle.ACTIVE && it.o.space == Space.CLOSE }
        .sortedBy { it.dueInstant ?: Instant.MAX }
    val today: LocalDate get() = now.atZone(zone).toLocalDate()

    companion object {
        fun empty(settings: AppSettings = AppSettings()) = AppState(
            false, Instant.EPOCH, ZoneId.systemDefault(), settings, emptyList(),
            PlanResult(true, emptyList(), emptyMap(), emptyMap(), 0, null, emptyList(), emptyMap()),
            emptyList(), Capacity.uniform(60), emptyList(), 0, emptyMap(), emptyMap(), 1.0,
        )
    }
}

/**
 * Turns stored deadlines into the live picture: calibrated effort, a feasible plan, the latest safe start of every
 * task, pressure, and an urgency state per deadline. Pure derivation; it only reads.
 */
class Engine(
    private val store: Store,
    private val settings: SettingsStore,
    scope: CoroutineScope,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val previousStates = HashMap<String, UrgencyState>()
    private val calibration = MutableStateFlow(1.0)

    private fun ticker(): Flow<Unit> = flow { while (true) { emit(Unit); delay(60_000) } }

    val state: StateFlow<AppState> = combine(
        store.obligations, store.candidates, store.steps, settings.flow, calibration, ticker(),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        compute(values[0] as List<Obligation>, values[1] as List<StoredCandidate>, values[2] as List<UserStep>, values[3] as AppSettings, values[4] as Double)
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, AppState.empty())

    /** Re-reads how far the user's estimates run from reality. Called after something is completed. */
    suspend fun refreshCalibration() {
        calibration.value = EstimateCalibration.multiplier(store.completedEstimates())
    }

    /** One-shot state for receivers and workers that run without the live collector. */
    suspend fun snapshot(): AppState = compute(
        store.allObligations(), store.storedCandidates(), store.allSteps(), settings.current(),
        EstimateCalibration.multiplier(store.completedEstimates()),
    )

    fun capacityFor(s: AppSettings) = Capacity.standard(s.weekdayMinutes, s.weekendMinutes, s.weekend)

    fun compute(
        all: List<Obligation>,
        stored: List<StoredCandidate>,
        userSteps: List<UserStep>,
        s: AppSettings,
        multiplier: Double,
    ): AppState {
        val nowInstant = clock.instant()
        val zone = clock.zone
        val today = nowInstant.atZone(zone).toLocalDate()

        fun remaining(o: Obligation): Pair<Int, Int> {
            val e = o.effort ?: return 0 to 0
            val exp = (e.expectedMin * multiplier).toInt()
            val safe = (e.safeMin * multiplier).toInt()
            return (exp - o.progressMin).coerceAtLeast(0) to (safe - o.progressMin).coerceAtLeast(0)
        }

        // A deadline with its own plan keeps exactly the hours the person chose. Those hours come off the day's time for the rest.
        val manual = all.filter { it.lifecycle == Lifecycle.ACTIVE && it.space == Space.WORK && it.manualPlan.isNotEmpty() }
        val manualByDay = manual.flatMap { o -> o.manualPlan.filter { !it.key.isBefore(today) }.map { (d, m) -> d to m } }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        val capacity = Capacity.standard(s.weekdayMinutes, s.weekendMinutes, s.weekend, manualByDay.mapValues { (d, m) -> (capacityFor(s).minutes(d) - m).coerceAtLeast(0) })
        val manualIds = manual.map { it.id }.toSet()

        val planTasks = all.filter { it.id !in manualIds && it.lifecycle == Lifecycle.ACTIVE && it.space == Space.WORK && it.planDay(zone) != null && it.effort != null }.map { o ->
            val (exp, safe) = remaining(o)
            PlanTask(
                id = o.id, title = o.title, remainingMin = exp, safeRemainingMin = safe,
                dueDay = o.planDay(zone)!!, earliestDay = maxOf(today, o.postponedUntil ?: today), importance = o.importance,
            )
        }
        val autoPlan = Planner.plan(planTasks, capacity, today)
        val manualBlocks = manual.flatMap { o -> o.manualPlan.filter { !it.key.isBefore(today) && it.value > 0 }.map { (d, m) -> PlanBlock(d, o.id, m) } }
        val plan = autoPlan.copy(
            blocks = (autoPlan.blocks + manualBlocks).sortedBy { it.day },
            loadByDay = (autoPlan.loadByDay.keys + manualByDay.keys).associateWith { (autoPlan.loadByDay[it] ?: 0) + (manualByDay[it] ?: 0) },
            capacityByDay = (autoPlan.capacityByDay.keys + manualByDay.keys).associateWith { (autoPlan.capacityByDay[it] ?: 0) + (manualByDay[it] ?: 0) },
        )
        val blocksBy = plan.blocks.groupBy { it.taskId }
        val atRisk = plan.atRisk.associate { it.taskId to it.unscheduledMin }

        val views = all.map { o ->
            val planDay = o.planDay(zone)
            val tentative = o.fog != null
            val fog = o.fog
            val dueSpec = o.due
            val dueInstant: Instant? = when {
                fog != null -> fog.earliest.atTime(LocalTime.of(23, 59)).atZone(zone).toInstant()
                dueSpec != null -> dueSpec.instant(zone)
                else -> null
            }
            val (rem, _) = remaining(o)
            val available = if (planDay == null) 0 else generateSequence(today) { it.plusDays(1) }
                .takeWhile { !it.isAfter(planDay) }.take(400).sumOf { capacity.minutes(it) }
            val pressure = Urgency.pressure(rem, available)
            val input = UrgencyInput(nowInstant, zone, dueInstant, o.lifecycle, o.postponedUntil, o.progressMin, plan.latestStart[o.id], pressure)
            val urgency = Urgency.evaluate(input, previousStates[o.id])
            previousStates[o.id] = urgency
            ObligationView(o, urgency, pressure, plan.latestStart[o.id], dueInstant, planDay, atRisk[o.id] ?: 0, blocksBy[o.id].orEmpty(), tentative)
        }.sortedWith(
            compareByDescending<ObligationView> { it.urgency.rank }.thenBy { it.dueInstant ?: Instant.MAX },
        )

        val stepsBy = userSteps.groupBy { it.obligationId }
        val suggested = views.filter { it.o.lifecycle == Lifecycle.ACTIVE && it.o.space == Space.WORK && it.blocks.isNotEmpty() && stepsBy[it.o.id].isNullOrEmpty() }
            .associate { it.o.id to PlanTemplates.steps(it.o.category, it.blocks) }

        val nowMs = nowInstant
        val pending = stored.filter { it.status == "PENDING" || (it.snoozeUntil != null && !it.snoozeUntil.isAfter(nowMs)) }.map { it.candidate }
        return AppState(
            true, nowInstant, zone, s, views, plan, planTasks, capacity, pending,
            stored.size - pending.size, stepsBy, suggested, multiplier,
        )
    }
}
