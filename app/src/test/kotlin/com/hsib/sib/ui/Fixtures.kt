package com.hsib.sib.ui

import com.hsib.sib.core.model.Candidate
import com.hsib.sib.core.model.Category
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Effort
import com.hsib.sib.core.model.Flag
import com.hsib.sib.core.model.Importance
import com.hsib.sib.core.model.Kind
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.model.Reason
import com.hsib.sib.core.model.Relation
import com.hsib.sib.core.model.Repeat
import com.hsib.sib.core.model.SourceType
import com.hsib.sib.core.model.Space
import com.hsib.sib.core.model.Tier
import com.hsib.sib.core.plan.PlanBlock
import com.hsib.sib.core.plan.PlanResult
import com.hsib.sib.core.plan.Remedy
import com.hsib.sib.core.plan.Shortfall
import com.hsib.sib.core.urgency.UrgencyState
import com.hsib.sib.domain.AppState
import com.hsib.sib.domain.ObligationView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Made-up state for screenshot tests. Nothing here touches a database. */
object Fixtures {
    val zone: ZoneId = ZoneId.of("UTC")
    val today: LocalDate = LocalDate.of(2026, 10, 14)
    val now: Instant = today.atTime(9, 0).atZone(zone).toInstant()

    fun view(id: String, title: String, daysAway: Long, state: UrgencyState, pressure: Double = 0.5, category: Category = Category.ASSIGNMENT): ObligationView {
        val due = DueSpec(today.plusDays(daysAway).atTime(0, 0), zone, Precision.DATE_ONLY)
        val o = Obligation(id = id, title = title, category = category, due = due, importance = Importance.NORMAL, effort = Effort.fromLikely(240))
        return ObligationView(o, state, pressure, today.plusDays(daysAway - 2), due.instant(zone), due.dayIn(zone), 0, emptyList(), false)
    }

    fun state(vararg items: ObligationView): AppState = AppState.empty().copy(loaded = true, now = now, zone = zone, items = items.toList())

    val soon = state(
        view("a", "Signals lab report", 2, UrgencyState.DUE_SOON, 0.72),
        view("b", "Application essay", 6, UrgencyState.APPROACHING, 0.4),
        view("c", "Thesis chapter three", 19, UrgencyState.PLANNED, 0.2),
    )
    val calm = state(view("c", "Thesis chapter three", 19, UrgencyState.PLANNED, 0.2))
    val overdue = state(
        view("o", "Pay the electricity bill", -2, UrgencyState.OVERDUE, 1.0, Category.BILL),
        view("a", "Signals lab report", 2, UrgencyState.DUE_SOON, 0.72),
    )
    val empty = state()

    private fun planFor(blocks: List<PlanBlock>): PlanResult {
        val days = (0 until 28).map { today.plusDays(it.toLong()) }
        val load = days.associateWith { d -> blocks.filter { it.day == d }.sumOf { it.minutes } }
        val capacity = days.associateWith { d -> if (d.dayOfWeek.value >= 6) 60 else 120 }
        return PlanResult(true, blocks, load, capacity, load.values.maxOrNull() ?: 0, null, emptyList(), emptyMap())
    }

    val planned = soon.copy(
        plan = planFor(
            listOf(
                PlanBlock(today, "a", 90), PlanBlock(today.plusDays(1), "a", 90), PlanBlock(today.plusDays(1), "b", 30),
                PlanBlock(today.plusDays(3), "b", 120), PlanBlock(today.plusDays(4), "b", 90), PlanBlock(today.plusDays(9), "c", 100),
            ),
        ),
    )

    val short = planned.copy(
        plan = planned.plan.copy(
            feasible = false,
            shortfall = Shortfall(
                today, today.plusDays(2), 420, 300, 120, listOf("a", "b"),
                listOf(Remedy.MoreTimePerDay(40, today, today.plusDays(2)), Remedy.ExtendDeadline("a", 1), Remedy.ReduceScope("b", 60)),
            ),
        ),
    )

    fun candidate(id: String, title: String, tier: Tier, flags: Set<Flag> = emptySet(), confidence: Double = 0.93) = Candidate(
        id = id, title = title, category = Category.ASSIGNMENT, kind = Kind.DEADLINE,
        due = DueSpec(LocalDate.of(2026, 11, 2).atTime(17, 0), zone, Precision.DATE_TIME), fog = null, confidence = confidence, tier = tier,
        relation = Relation.NEW, relatedId = null, reasons = listOf(Reason("It says the report must be submitted before this time", 0.9)), flags = flags,
        sourceType = SourceType.PASTE, excerpt = "All project reports must be submitted before 2 November, 5 PM.",
    )

    val review = soon.copy(
        pending = listOf(candidate("r1", "Submit the project report", Tier.SUGGEST), candidate("r2", "Registration closes", Tier.MAYBE, setOf(Flag.AMBIGUOUS_ORDER))),
    )

    private fun closeView(id: String, title: String, daysAway: Long, category: Category, person: String, repeat: Repeat, since: LocalDate?, relation: String): ObligationView {
        val base = view(id, title, daysAway, UrgencyState.PLANNED, 0.0, category)
        val day = today.plusDays(daysAway)
        val o = base.o.copy(
            space = Space.CLOSE, effort = null, repeat = repeat, repeatAnchor = if (repeat == Repeat.NONE) null else day.minusYears(if (since != null) 4 else 0),
            person = person, relation = relation, since = since, leadDays = listOf(14, 3),
        )
        return base.copy(o = o)
    }

    val withClose = soon.copy(
        items = soon.items + listOf(
            closeView("x1", "Anniversary", 20, Category.ANNIVERSARY, "Sam", Repeat.YEARLY, LocalDate.of(2022, 11, 3), "Partner"),
            closeView("x2", "Date night", 3, Category.DATE_NIGHT, "Sam", Repeat.NONE, null, "Partner"),
            closeView("x3", "Birthday", 62, Category.BIRTHDAY, "Alex", Repeat.YEARLY, null, "Girlfriend"),
        ),
    )
}
