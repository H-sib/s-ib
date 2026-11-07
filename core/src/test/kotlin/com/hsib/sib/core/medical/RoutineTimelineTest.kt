package com.hsib.sib.core.medical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutineTimelineTest {
    private val day = listOf(8 * 60 to 9 * 60, 9 * 60 + 45 to 10 * 60 + 45, 10 * 60 + 45 to 12 * 60, 14 * 60 to 15 * 60 + 30)

    @Test fun breaksAreTheGapsBetweenClasses() {
        assertEquals(listOf(45, 0, 120), RoutineTimeline.breaks(day))
        assertEquals(emptyList<Int>(), RoutineTimeline.breaks(emptyList()))
        assertEquals(emptyList<Int>(), RoutineTimeline.breaks(listOf(60 to 120)))
    }

    @Test fun anOverlapNeverGivesANegativeBreak() {
        assertEquals(listOf(0), RoutineTimeline.breaks(listOf(60 to 150, 120 to 180)))
    }

    @Test fun spanIsFirstStartToLastEnd() {
        assertEquals(8 * 60 to 15 * 60 + 30, RoutineTimeline.span(day))
        assertNull(RoutineTimeline.span(emptyList()))
    }

    @Test fun nextClassIsTheOneOnNowOrComingUp() {
        assertEquals(0, RoutineTimeline.nextIndex(day, 7 * 60))
        assertEquals(0, RoutineTimeline.nextIndex(day, 8 * 60 + 30))
        assertEquals(1, RoutineTimeline.nextIndex(day, 9 * 60 + 10))
        assertEquals(3, RoutineTimeline.nextIndex(day, 13 * 60))
        assertNull(RoutineTimeline.nextIndex(day, 16 * 60))
        assertNull(RoutineTimeline.nextIndex(emptyList(), 0))
    }

    @Test fun breakLabelsAreShortAndPlain() {
        assertNull(RoutineTimeline.breakLabel(0))
        assertNull(RoutineTimeline.breakLabel(9))
        assertEquals("45 min break", RoutineTimeline.breakLabel(45))
        assertEquals("2 h break", RoutineTimeline.breakLabel(120))
        assertEquals("1 h 15 min break", RoutineTimeline.breakLabel(75))
    }

    @Test fun theWeekMapCoversTheClassesWithRoomAround() {
        assertEquals(8..16, RoutineTimeline.mapHours(day, 6))
        assertEquals(8..14, RoutineTimeline.mapHours(emptyList(), 6))
        assertEquals(9..15, RoutineTimeline.mapHours(listOf(9 * 60 to 10 * 60), 6))
        assertEquals(18..24, RoutineTimeline.mapHours(listOf(23 * 60 to 23 * 60 + 50), 6))
    }
}
