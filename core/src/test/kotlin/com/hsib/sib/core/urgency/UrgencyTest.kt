package com.hsib.sib.core.urgency

import com.hsib.sib.core.model.Category
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Importance
import com.hsib.sib.core.model.Lifecycle
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.plan.EstimateCalibration
import com.hsib.sib.core.plan.PlanBlock
import com.hsib.sib.core.plan.PlanTemplates
import com.hsib.sib.core.plan.RhythmTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Random

class UrgencyTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-14T10:00:00Z")

    private fun input(
        dueIn: Duration? = Duration.ofDays(20),
        lifecycle: Lifecycle = Lifecycle.ACTIVE,
        postponed: LocalDate? = null,
        progress: Int = 0,
        latestStart: LocalDate? = null,
        pressure: Double = 0.1,
        at: Instant = now,
    ) = UrgencyInput(at, zone, dueIn?.let { at.plus(it) }, lifecycle, postponed, progress, latestStart, pressure)

    // ---- states -------------------------------------------------------------------------------

    @Test fun lifecycleAndPostponementWinFirst() {
        assertEquals(UrgencyState.DONE, Urgency.evaluate(input(lifecycle = Lifecycle.COMPLETED), null))
        assertEquals(UrgencyState.DONE, Urgency.evaluate(input(lifecycle = Lifecycle.CANCELLED), null))
        assertEquals(UrgencyState.MOVED, Urgency.evaluate(input(postponed = LocalDate.of(2026, 10, 20)), null))
        assertEquals(UrgencyState.PLANNED, Urgency.evaluate(input(postponed = LocalDate.of(2026, 10, 14)), null))
    }

    @Test fun theChainEscalatesWithTime() {
        assertEquals(UrgencyState.PLANNED, Urgency.evaluate(input(dueIn = Duration.ofDays(20)), null))
        assertEquals(UrgencyState.APPROACHING, Urgency.evaluate(input(dueIn = Duration.ofHours(60)), null))
        assertEquals(UrgencyState.DUE_SOON, Urgency.evaluate(input(dueIn = Duration.ofHours(20)), null))
        assertEquals(UrgencyState.DUE_TODAY, Urgency.evaluate(input(dueIn = Duration.ofHours(5)), null))
        assertEquals(UrgencyState.OVERDUE, Urgency.evaluate(input(dueIn = Duration.ofHours(-1)), null))
    }

    @Test fun pressureAloneCanEscalate() {
        assertEquals(UrgencyState.DUE_SOON, Urgency.evaluate(input(dueIn = Duration.ofDays(10), pressure = 0.9), null))
        assertEquals(UrgencyState.APPROACHING, Urgency.evaluate(input(dueIn = Duration.ofDays(10), pressure = 0.7), null))
    }

    @Test fun startWindowAndProgress() {
        assertEquals(UrgencyState.START_SOON, Urgency.evaluate(input(latestStart = LocalDate.of(2026, 10, 16)), null))
        assertEquals(UrgencyState.PLANNED, Urgency.evaluate(input(latestStart = LocalDate.of(2026, 10, 25)), null))
        assertEquals(UrgencyState.IN_PROGRESS, Urgency.evaluate(input(progress = 30, latestStart = LocalDate.of(2026, 10, 16)), null))
    }

    @Test fun openEndedItemsHaveNoClock() {
        assertEquals(UrgencyState.PLANNED, Urgency.evaluate(input(dueIn = null), null))
        assertEquals(UrgencyState.IN_PROGRESS, Urgency.evaluate(input(dueIn = null, progress = 10), null))
    }

    @Test fun hysteresisPreventsFlickerAroundTheBoundary() {
        // 26 hours left is above the 24 hour entry threshold but inside the exit margin.
        val stays = Urgency.evaluate(input(dueIn = Duration.ofHours(26), pressure = 0.3), UrgencyState.DUE_SOON)
        assertEquals(UrgencyState.DUE_SOON, stays)
        // From a fresh start the same input is only approaching.
        assertEquals(UrgencyState.APPROACHING, Urgency.evaluate(input(dueIn = Duration.ofHours(26), pressure = 0.3), UrgencyState.APPROACHING))
        // Clearly outside the margin it does drop.
        assertEquals(UrgencyState.APPROACHING, Urgency.evaluate(input(dueIn = Duration.ofHours(40), pressure = 0.2), UrgencyState.DUE_SOON))
        // Time driven states never linger.
        assertEquals(UrgencyState.PLANNED, Urgency.evaluate(input(dueIn = Duration.ofDays(30)), UrgencyState.OVERDUE))
    }

    @Test fun stateNeverFlipsTwiceInsideTheHysteresisBand() {
        // Walk hours-to-deadline up and down across the 24 hour boundary and count transitions.
        val seq = listOf(30.0, 25.0, 23.0, 25.0, 23.5, 26.0, 29.0, 31.0, 40.0)
        var state: UrgencyState? = null
        var flips = 0
        for (h in seq) {
            val next = Urgency.evaluate(input(dueIn = Duration.ofMinutes((h * 60).toLong()), pressure = 0.2), state)
            if (state != null && next != state) flips++
            state = next
        }
        assertTrue("flips=$flips", flips <= 2)
    }

    @Test fun pressureFunction() {
        assertEquals(0.0, Urgency.pressure(0, 100), 1e-9)
        assertEquals(0.5, Urgency.pressure(60, 120), 1e-9)
        assertEquals(2.0, Urgency.pressure(60, 0), 1e-9)
        assertEquals(2.0, Urgency.pressure(600, 10), 1e-9)
    }

    // ---- reminders ----------------------------------------------------------------------------

    private fun deadline(due: LocalDateTime, precision: Precision = Precision.DATE_TIME, importance: Importance = Importance.NORMAL, id: String = "o1") = Obligation(
        id = id, title = "Report", category = Category.ASSIGNMENT, importance = importance,
        due = DueSpec(due, zone, precision),
    )

    private val settings = ReminderSettings()

    @Test fun aTimedDeadlineGetsAFewWellPlacedReminders() {
        val specs = ReminderPlanner.specs(deadline(LocalDateTime.of(2026, 11, 18, 23, 59)), LocalDate.of(2026, 11, 10), settings, now, zone)
        val byKind = specs.associateBy { it.kind }
        assertEquals(ZonedDateTime.of(2026, 11, 10, 8, 30, 0, 0, zone).toInstant(), byKind[ReminderKind.START_NUDGE]!!.fireAt)
        assertEquals(ZonedDateTime.of(2026, 11, 15, 8, 30, 0, 0, zone).toInstant(), byKind[ReminderKind.APPROACHING]!!.fireAt)
        // 24 hours before 23:59 would be in quiet hours, so it moves to the morning, before the deadline.
        assertEquals(ZonedDateTime.of(2026, 11, 18, 7, 30, 0, 0, zone).toInstant(), byKind[ReminderKind.DUE_SOON]!!.fireAt)
        assertEquals(ZonedDateTime.of(2026, 11, 18, 8, 30, 0, 0, zone).toInstant(), byKind[ReminderKind.DUE_MORNING]!!.fireAt)
        assertEquals(ZonedDateTime.of(2026, 11, 18, 21, 59, 0, 0, zone).toInstant(), byKind[ReminderKind.DUE_FINAL]!!.fireAt)
        assertEquals(ZonedDateTime.of(2026, 11, 19, 8, 30, 0, 0, zone).toInstant(), byKind[ReminderKind.OVERDUE_CHECK]!!.fireAt)
    }

    @Test fun noReminderEverLandsInQuietHoursOrPastTheDeadline() {
        val rnd = Random(3)
        repeat(500) {
            val due = LocalDateTime.of(2026, 10, 15, 0, 0).plusMinutes(rnd.nextInt(60 * 24 * 40).toLong())
            val o = deadline(due, if (rnd.nextBoolean()) Precision.DATE_TIME else Precision.DATE_ONLY)
            val dueInstant = o.due!!.instant(zone)
            for (s in ReminderPlanner.specs(o, null, settings, now, zone)) {
                assertFalse("quiet hours violated by ${s.kind} at ${s.fireAt} for due $due", ReminderPlanner.inQuiet(s.fireAt.atZone(zone).toLocalTime(), settings))
                assertTrue(s.fireAt.isAfter(now))
                if (s.kind != ReminderKind.OVERDUE_CHECK) assertTrue("${s.kind} after the deadline", !s.fireAt.isAfter(dueInstant))
            }
        }
    }

    @Test fun earlyMorningDeadlineNeverGetsPushedPastItself() {
        val o = deadline(LocalDateTime.of(2026, 11, 18, 7, 0))
        val specs = ReminderPlanner.specs(o, null, settings, now, zone)
        val final = specs.first { it.kind == ReminderKind.DUE_FINAL }
        assertTrue(final.fireAt.isBefore(o.due!!.instant(zone)))
        assertEquals(ZonedDateTime.of(2026, 11, 17, 21, 30, 0, 0, zone).toInstant(), final.fireAt)
    }

    @Test fun tentativeAndFinishedItemsStayQuiet() {
        val o = deadline(LocalDateTime.of(2026, 11, 18, 23, 59))
        val tentative = ReminderPlanner.specs(o, LocalDate.of(2026, 11, 10), settings, now, zone, fogTentative = true)
        assertEquals(listOf(ReminderKind.START_NUDGE), tentative.map { it.kind })
        assertTrue(ReminderPlanner.specs(o.copy(lifecycle = Lifecycle.COMPLETED), null, settings, now, zone).isEmpty())
        assertTrue(ReminderPlanner.specs(o.copy(due = null), null, settings, now, zone).isEmpty())
    }

    @Test fun dateOnlyDeadlinesGetNoClockTimeFinalReminder() {
        val specs = ReminderPlanner.specs(deadline(LocalDateTime.of(2026, 11, 18, 0, 0), Precision.DATE_ONLY), null, settings, now, zone)
        assertFalse(specs.any { it.kind == ReminderKind.DUE_FINAL })
        assertTrue(specs.any { it.kind == ReminderKind.DUE_MORNING })
    }

    @Test fun dailyBudgetKeepsProtectedRemindersAndDemotesTheRest() {
        val day = Instant.parse("2026-11-10T08:30:00Z")
        val specs = listOf(
            ReminderSpec("a", ReminderKind.APPROACHING, day, 0.9),
            ReminderSpec("b", ReminderKind.START_NUDGE, day.plusSeconds(60), 0.2),
            ReminderSpec("c", ReminderKind.START_NUDGE, day.plusSeconds(120), 0.7),
            ReminderSpec("d", ReminderKind.OVERDUE_CHECK, day.plusSeconds(180), 0.5),
            ReminderSpec("e", ReminderKind.DUE_MORNING, day.plusSeconds(240), 0.1),
            ReminderSpec("f", ReminderKind.DUE_FINAL, day.plusSeconds(300), 0.1),
        )
        val r = ReminderPlanner.applyBudget(specs, settings, zone)
        assertEquals(setOf("a", "c", "e", "f"), r.scheduled.map { it.obligationId }.toSet())
        assertEquals(setOf("d", "b"), r.digestOnly.map { it.obligationId }.toSet())
    }

    @Test fun dedupeKeyIsStableAndUnique() {
        val o = deadline(LocalDateTime.of(2026, 11, 18, 23, 59))
        val a = ReminderPlanner.specs(o, null, settings, now, zone)
        val b = ReminderPlanner.specs(o, null, settings, now, zone)
        assertEquals(a.map { it.dedupeKey }, b.map { it.dedupeKey })
        assertEquals(a.size, a.map { it.dedupeKey }.distinct().size)
    }

    @Test fun changingTheDeadlineChangesEveryReminderKey() {
        val a = ReminderPlanner.specs(deadline(LocalDateTime.of(2026, 11, 18, 23, 59)), null, settings, now, zone).map { it.dedupeKey }.toSet()
        val b = ReminderPlanner.specs(deadline(LocalDateTime.of(2026, 11, 25, 23, 59)), null, settings, now, zone).map { it.dedupeKey }.toSet()
        assertTrue("old reminders must not survive a moved deadline", a.intersect(b).isEmpty())
    }

    // ---- plan steps, calibration, rhythm ----------------------------------------------------

    private fun blocks(days: Int, minutes: Int) = (0 until days).map { PlanBlock(LocalDate.of(2026, 10, 14).plusDays(it.toLong()), "t", minutes) }

    @Test fun stepsFollowTheCumulativeEffortOfThePlan() {
        val b = blocks(10, 60)
        val steps = PlanTemplates.steps(Category.ASSIGNMENT, b)
        assertEquals(5, steps.size)
        assertEquals(600, steps.sumOf { it.minutes })
        assertEquals(steps.map { it.targetDay }, steps.map { it.targetDay }.sorted())
        assertEquals(b.last().day, steps.last().targetDay)
        assertTrue(steps.all { it.minutes > 0 })
    }

    @Test fun veryShortPlansDropOptionalStagesAndNeverInventTime() {
        val steps = PlanTemplates.steps(Category.EXAM, blocks(1, 45))
        assertEquals(45, steps.sumOf { it.minutes })
        assertTrue(steps.size <= 3)
        val tiny = PlanTemplates.steps(Category.ASSIGNMENT, blocks(1, 15))
        assertEquals(1, tiny.size)
        assertTrue(PlanTemplates.steps(Category.ASSIGNMENT, emptyList()).isEmpty())
    }

    @Test fun everyCategoryHasATemplate() {
        for (c in Category.values()) {
            val s = PlanTemplates.steps(c, blocks(6, 60))
            assertEquals(360, s.sumOf { it.minutes })
        }
    }

    @Test fun estimateCalibrationLearnsOnlyFromEnoughHistory() {
        assertEquals(1.0, EstimateCalibration.multiplier(emptyList()), 1e-9)
        assertEquals(1.0, EstimateCalibration.multiplier(listOf(60 to 120, 60 to 120)), 1e-9)
        val m = EstimateCalibration.multiplier(List(6) { 60 to 90 })
        assertTrue("multiplier=$m", m in 1.2..1.5)
        assertTrue(EstimateCalibration.multiplier(List(50) { 60 to 6000 }) <= 2.5)
        assertTrue(EstimateCalibration.multiplier(List(50) { 600 to 6 }) >= 0.7)
    }

    @Test fun rhythmLearnsTheUsualOpeningHour() {
        val r = RhythmTracker()
        assertEquals(8, r.bestHour(8))
        repeat(20) { r.record(9) }
        repeat(3) { r.record(7) }
        assertEquals(9, r.bestHour(8))
        val restored = RhythmTracker.parse(r.serialize())
        assertEquals(9, restored.bestHour(8))
        assertEquals(8, RhythmTracker.parse("garbage").bestHour(8))
    }
}

class CloseOnesReminderTest {
    private val zone = java.time.ZoneId.of("UTC")
    private val now = java.time.Instant.parse("2026-10-14T10:00:00Z")
    private val settings = ReminderSettings()

    private fun occasion(date: java.time.LocalDate, leads: List<Int>) = Obligation(
        id = "anniv", title = "Anniversary with Maya", category = Category.ANNIVERSARY, space = com.hsib.sib.core.model.Space.CLOSE,
        due = DueSpec(date.atStartOfDay(), zone, Precision.DATE_ONLY), leadDays = leads,
        repeat = com.hsib.sib.core.model.Repeat.YEARLY,
    )

    @Test fun leadDayNudgesLandOnTheRightMorningsAndNothingFeelsLikeWork() {
        val specs = ReminderPlanner.specs(occasion(java.time.LocalDate.of(2026, 11, 20), listOf(21, 7, 1)), null, settings, now, zone)
        val kinds = specs.map { it.kind }
        assertEquals(3, kinds.count { it == ReminderKind.LEAD })
        assertTrue(ReminderKind.DUE_MORNING in kinds)
        assertFalse(ReminderKind.START_NUDGE in kinds || ReminderKind.OVERDUE_CHECK in kinds || ReminderKind.APPROACHING in kinds)
        val leadDates = specs.filter { it.kind == ReminderKind.LEAD }.map { it.fireAt.atZone(zone).toLocalDate() }
        assertEquals(listOf(java.time.LocalDate.of(2026, 10, 30), java.time.LocalDate.of(2026, 11, 13), java.time.LocalDate.of(2026, 11, 19)), leadDates)
        assertTrue(specs.all { it.fireAt.atZone(zone).toLocalTime() == settings.morning })
    }

    @Test fun nudgesThatAreAlreadyInThePastAreDropped() {
        val specs = ReminderPlanner.specs(occasion(java.time.LocalDate.of(2026, 10, 16), listOf(21, 7, 1)), null, settings, now, zone)
        assertEquals(1, specs.count { it.kind == ReminderKind.LEAD })
    }

    @Test fun leadNudgesAreProtectedFromTheDailyBudget() {
        val specs = ReminderPlanner.specs(occasion(java.time.LocalDate.of(2026, 11, 20), listOf(7)), null, settings.copy(dailyBudget = 0), now, zone)
        val b = ReminderPlanner.applyBudget(specs, settings.copy(dailyBudget = 0), zone)
        assertTrue(b.scheduled.any { it.kind == ReminderKind.LEAD })
    }
}
