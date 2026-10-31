package com.hsib.sib.core.urgency

import com.hsib.sib.core.model.Lifecycle
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.model.Space
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class ReminderKind(val protected: Boolean, val base: Double) {
    START_NUDGE(false, 0.5),
    APPROACHING(false, 0.5),
    DUE_SOON(false, 0.8),
    DUE_MORNING(true, 0.9),
    DUE_FINAL(true, 1.0),
    OVERDUE_CHECK(false, 0.6),
    /** A preparation nudge some days before an occasion: choose the gift, book the table. */
    LEAD(true, 0.8),
}

data class ReminderSettings(
    val morning: LocalTime = LocalTime.of(8, 30),
    val quietStart: LocalTime = LocalTime.of(22, 0),
    val quietEnd: LocalTime = LocalTime.of(7, 30),
    /** Interrupting reminders per day, not counting reminders for things due that day. */
    val dailyBudget: Int = 2,
    val finalLeadMinutes: Int = 120,
    val remindOverdue: Boolean = true,
)

data class ReminderSpec(
    val obligationId: String,
    val kind: ReminderKind,
    val fireAt: Instant,
    val score: Double,
) {
    val dedupeKey: String get() = "$obligationId|${kind.name}|${fireAt.epochSecond / 60}"
}

data class BudgetResult(val scheduled: List<ReminderSpec>, val digestOnly: List<ReminderSpec>)

/**
 * Turns deadlines into a small set of reminders that earn their interruption.
 * Reminders are derived data: they are recomputed from the deadline whenever anything changes, so a changed
 * deadline can never leave an old reminder behind.
 */
object ReminderPlanner {
    val DEFAULT_LEADS = listOf(7, 6, 5)

    fun inQuiet(t: LocalTime, s: ReminderSettings): Boolean =
        if (s.quietStart.isAfter(s.quietEnd)) !t.isBefore(s.quietStart) || t.isBefore(s.quietEnd)
        else !t.isBefore(s.quietStart) && t.isBefore(s.quietEnd)

    /** Moves a time out of quiet hours without ever pushing it past [latest]. Returns null when it cannot be placed. */
    private fun clearOfQuiet(at: ZonedDateTime, s: ReminderSettings, latest: Instant?, now: Instant): ZonedDateTime? {
        if (!inQuiet(at.toLocalTime(), s)) return at
        var forward = at.with(s.quietEnd)
        if (forward.isBefore(at)) forward = forward.plusDays(1)
        if (latest == null || forward.toInstant().isBefore(latest.minus(Duration.ofMinutes(30)))) return forward
        // Too close to the deadline: go back to just before quiet hours began.
        var back = at.with(s.quietStart).minusMinutes(30)
        if (back.isAfter(at)) back = back.minusDays(1)
        return if (back.toInstant().isAfter(now)) back else null
    }

    fun specs(
        o: Obligation,
        latestStart: LocalDate?,
        s: ReminderSettings,
        now: Instant,
        zone: ZoneId,
        fogTentative: Boolean = false,
    ): List<ReminderSpec> {
        if (o.lifecycle != Lifecycle.ACTIVE) return emptyList()
        val dueSpec = o.due ?: return emptyList()
        val due = dueSpec.instant(zone)
        val dueZ = due.atZone(zone)
        val weight = o.importance.weight / 2.0
        val out = mutableListOf<ReminderSpec>()

        fun add(kind: ReminderKind, at: ZonedDateTime?, latest: Instant? = due) {
            if (at == null) return
            val placed = clearOfQuiet(at, s, latest, now) ?: return
            val inst = placed.toInstant()
            if (!inst.isAfter(now)) return
            out += ReminderSpec(o.id, kind, inst, kind.base * weight)
        }

        if (o.space == Space.CLOSE) {
            // Occasions are not work: no start pressure and no "past due" chasing. Just well-timed nudges to prepare,
            // a morning reminder on the day, and a nudge before a timed plan such as a dinner.
            for (days in o.leadDays.filter { it > 0 }.distinct()) {
                add(ReminderKind.LEAD, dueZ.toLocalDate().minusDays(days.toLong()).atTime(s.morning).atZone(zone))
            }
            val morningOfDay = dueZ.toLocalDate().atTime(s.morning).atZone(zone)
            if (dueSpec.precision == Precision.DATE_ONLY || morningOfDay.toInstant().isBefore(due.minus(Duration.ofMinutes(90)))) {
                add(ReminderKind.DUE_MORNING, morningOfDay, latest = due)
            }
            if (dueSpec.precision == Precision.DATE_TIME) add(ReminderKind.DUE_FINAL, dueZ.minusMinutes(s.finalLeadMinutes.toLong()))
            return out.groupBy { it.kind to it.fireAt.epochSecond / 60 }.map { it.value.first() }.sortedBy { it.fireAt }
        }

        // A tentative date only gets a gentle start nudge; stronger reminders wait until it is confirmed.
        latestStart?.let { ls ->
            if (ls.isBefore(dueZ.toLocalDate())) add(ReminderKind.START_NUDGE, ls.atTime(s.morning).atZone(zone))
        }
        if (fogTentative) return out

        // Early reminders. The person's own days win; with none chosen a deadline gets a calm nudge 7, 6 and 5 days ahead.
        for (days in o.leadDays.filter { it > 0 }.distinct().ifEmpty { DEFAULT_LEADS }) {
            add(ReminderKind.LEAD, dueZ.toLocalDate().minusDays(days.toLong()).atTime(s.morning).atZone(zone))
        }
        add(ReminderKind.APPROACHING, dueZ.minusDays(3).toLocalDate().atTime(s.morning).atZone(zone))
        add(ReminderKind.DUE_SOON, dueZ.minusHours(24))
        // A morning nudge only makes sense when the deadline is still a while away that morning.
        val morning = dueZ.toLocalDate().atTime(s.morning).atZone(zone)
        if (morning.toInstant().isBefore(due.minus(Duration.ofMinutes(45)))) add(ReminderKind.DUE_MORNING, morning, latest = due)
        if (dueSpec.precision == Precision.DATE_TIME) {
            add(ReminderKind.DUE_FINAL, dueZ.minusMinutes(s.finalLeadMinutes.toLong()))
        }
        if (s.remindOverdue) add(ReminderKind.OVERDUE_CHECK, dueZ.toLocalDate().plusDays(1).atTime(s.morning).atZone(zone), latest = null)
        // Drop duplicates that collapsed onto the same minute (for example a very close deadline).
        return out.groupBy { it.kind to it.fireAt.epochSecond / 60 }.map { it.value.first() }.sortedBy { it.fireAt }
    }

    /**
     * Daily interruption budget. Reminders for things due that day are protected; the rest compete for the
     * remaining slots by score. Losers are not lost: they appear in the daily digest instead.
     */
    fun applyBudget(specs: List<ReminderSpec>, s: ReminderSettings, zone: ZoneId): BudgetResult {
        val scheduled = mutableListOf<ReminderSpec>()
        val digest = mutableListOf<ReminderSpec>()
        for ((_, day) in specs.groupBy { it.fireAt.atZone(zone).toLocalDate() }) {
            val protectedOnes = day.filter { it.kind.protected }
            val rest = day.filter { !it.kind.protected }.sortedByDescending { it.score }
            scheduled += protectedOnes
            scheduled += rest.take(s.dailyBudget)
            digest += rest.drop(s.dailyBudget)
        }
        return BudgetResult(scheduled.sortedBy { it.fireAt }, digest.sortedBy { it.fireAt })
    }
}
