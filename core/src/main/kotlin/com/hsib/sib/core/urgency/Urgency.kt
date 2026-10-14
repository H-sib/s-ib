package com.hsib.sib.core.urgency

import com.hsib.sib.core.model.Lifecycle
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Where a deadline stands. The order of the chain from PLANNED to OVERDUE is the order of escalation.
 * Every state has a label, a glyph and a position in the interface so colour is never the only signal.
 */
enum class UrgencyState(val label: String) {
    REVIEW("Review"),
    PLANNED("Planned"),
    START_SOON("Start soon"),
    IN_PROGRESS("In progress"),
    APPROACHING("Approaching"),
    DUE_SOON("Due soon"),
    DUE_TODAY("Due today"),
    OVERDUE("Overdue"),
    DONE("Done"),
    MOVED("Moved"),
    ;

    /** Position in the escalation chain, or -1 for states outside it. */
    val rank: Int get() = when (this) {
        PLANNED -> 0
        START_SOON -> 1
        IN_PROGRESS -> 2
        APPROACHING -> 3
        DUE_SOON -> 4
        DUE_TODAY -> 5
        OVERDUE -> 6
        else -> -1
    }
}

data class UrgencyInput(
    val now: Instant,
    val zone: ZoneId,
    val due: Instant?,
    val lifecycle: Lifecycle,
    val postponedUntil: LocalDate?,
    val progressMin: Int,
    /** Latest day work may begin (from the planner), or null when unknown. */
    val latestStart: LocalDate?,
    /** Remaining work divided by the time available before the deadline. At or above 1.0 there is not enough time. */
    val pressure: Double,
)

object Urgency {
    const val DUE_SOON_HOURS = 24.0
    const val APPROACHING_HOURS = 72.0
    const val DUE_SOON_PRESSURE = 0.85
    const val APPROACHING_PRESSURE = 0.6

    /** Pressure = remaining work / available time. With no time left and work to do it saturates at 2. */
    fun pressure(remainingMin: Int, availableMin: Int): Double = when {
        remainingMin <= 0 -> 0.0
        availableMin <= 0 -> 2.0
        else -> (remainingMin.toDouble() / availableMin).coerceAtMost(2.0)
    }

    private fun raw(i: UrgencyInput): UrgencyState {
        if (i.lifecycle != Lifecycle.ACTIVE) return UrgencyState.DONE
        val today = i.now.atZone(i.zone).toLocalDate()
        if (i.postponedUntil != null && today.isBefore(i.postponedUntil)) return UrgencyState.MOVED
        val due = i.due ?: return if (i.progressMin > 0) UrgencyState.IN_PROGRESS else UrgencyState.PLANNED
        val hoursLeft = Duration.between(i.now, due).toMinutes() / 60.0
        if (hoursLeft < 0) return UrgencyState.OVERDUE
        if (due.atZone(i.zone).toLocalDate() == today) return UrgencyState.DUE_TODAY
        if (hoursLeft < DUE_SOON_HOURS || i.pressure > DUE_SOON_PRESSURE) return UrgencyState.DUE_SOON
        if (hoursLeft < APPROACHING_HOURS || i.pressure > APPROACHING_PRESSURE) return UrgencyState.APPROACHING
        if (i.progressMin > 0) return UrgencyState.IN_PROGRESS
        val ls = i.latestStart
        if (ls != null && !today.isBefore(ls.minusDays(3))) return UrgencyState.START_SOON
        return UrgencyState.PLANNED
    }

    /**
     * Current state with hysteresis: a state is entered at one threshold and left at a slightly looser one,
     * so the interface does not flicker around a boundary. Time driven states (due today, overdue) never linger.
     */
    fun evaluate(i: UrgencyInput, previous: UrgencyState?): UrgencyState {
        val r = raw(i)
        if (previous == null || previous.rank < 0 || r.rank < 0 || r.rank >= previous.rank) return r
        val due = i.due ?: return r
        val hoursLeft = Duration.between(i.now, due).toMinutes() / 60.0
        val today = i.now.atZone(i.zone).toLocalDate()
        return when (previous) {
            UrgencyState.DUE_SOON -> if (hoursLeft <= DUE_SOON_HOURS + 6 || i.pressure >= DUE_SOON_PRESSURE - 0.1) previous else r
            UrgencyState.APPROACHING -> if (hoursLeft <= APPROACHING_HOURS + 12 || i.pressure >= APPROACHING_PRESSURE - 0.1) previous else r
            UrgencyState.START_SOON -> {
                val ls = i.latestStart
                if (ls != null && !today.isBefore(ls.minusDays(4)) && r.rank < previous.rank) previous else r
            }
            else -> r
        }
    }
}
