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

    @Test fun backupKeepsParts() {
        val o = Obligation(id = "a", title = "T", parts = listOf(Part("One", true), Part("Two")))
        assertEquals(o.parts, ObligationDto.from(o).toModel().parts)
    }
}
