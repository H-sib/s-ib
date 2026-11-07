package com.hsib.sib.core.model

import com.hsib.sib.core.backup.ObligationDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartsTest {
    @Test fun partsSurviveEncodingWithNamesAndDoneMarks() {
        val parts = listOf(Part("Essay | draft", true), Part("Lab report"), Part("Quiz 3", true))
        assertEquals(parts, Part.decode(Part.encode(parts)))
    }

    @Test fun noPartsIsAnEmptyList() {
        assertTrue(Part.decode("").isEmpty())
        assertEquals("", Part.encode(emptyList()))
    }

    @Test fun doneCountFollowsTheMarks() {
        val o = Obligation(id = "a", title = "Seven things", parts = List(7) { Part("Part ${it + 1}", it < 2) })
        assertEquals(2, o.partsDone)
    }

    @Test fun priorityAndDoneSurviveEncoding() {
        val parts = listOf(Part("Anatomy", false, Priority.HIGH), Part("Physiology", true, Priority.MEDIUM), Part("Biochem | notes", false, Priority.LOW), Part("Other"))
        assertEquals(parts, Part.decode(Part.encode(parts)))
    }

    @Test fun linesFromBeforePrioritiesStillRead() {
        assertEquals(listOf(Part("Old one", true), Part("Old two", false)), Part.decode("1|Old one\n0|Old two"))
    }

    @Test fun moveKeepsMarksAndClampsTheTarget() {
        val parts = listOf(Part("A"), Part("B", true), Part("C", false, Priority.HIGH))
        assertEquals(listOf("B", "A", "C"), Part.move(parts, 0, 1).map { it.name })
        assertEquals(listOf("C", "A", "B"), Part.move(parts, 2, -5).map { it.name })
        assertEquals(listOf("B", "C", "A"), Part.move(parts, 0, 99).map { it.name })
        assertEquals(parts, Part.move(parts, 7, 0))
        assertEquals(true, Part.move(parts, 1, 0)[0].done)
        assertEquals(Priority.HIGH, Part.move(parts, 2, 0)[0].priority)
    }

    @Test fun removeTakesOutOnlyThatTopic() {
        val parts = listOf(Part("A"), Part("B", true), Part("C"))
        assertEquals(listOf(Part("A"), Part("C")), Part.remove(parts, 1))
        assertEquals(parts, Part.remove(parts, 3))
        assertEquals(emptyList<Part>(), Part.remove(listOf(Part("Only")), 0))
    }

    @Test fun backupKeepsPriorities() {
        val o = Obligation(id = "a", title = "T", parts = listOf(Part("One", true, Priority.LOW), Part("Two", false, Priority.HIGH)))
        assertEquals(o.parts, ObligationDto.from(o).toModel().parts)
    }

    @Test fun backupKeepsParts() {
        val o = Obligation(id = "a", title = "T", parts = listOf(Part("One", true), Part("Two")))
        assertEquals(o.parts, ObligationDto.from(o).toModel().parts)
    }
}
