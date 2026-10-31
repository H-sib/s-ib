package com.hsib.sib.core.medical

import com.hsib.sib.core.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MedicalTest {
    @Test fun fourProfsWithTheRightSubjects() {
        assertEquals(4, Medical.profs.size)
        assertEquals(listOf("Anatomy", "Physiology", "Biochemistry"), Medical.prof(1).subjects)
        assertTrue("Community Medicine" in Medical.prof(3).subjects)
    }

    @Test fun fourthProfHasNoItemsOrCardsButHasWardFinal() {
        val kinds = Medical.kinds(Medical.prof(4))
        assertFalse(Category.ITEM in kinds)
        assertFalse(Category.CARD in kinds)
        assertTrue(Category.WARD_FINAL in kinds)
        assertTrue(Category.CARD in Medical.kinds(Medical.prof(1)))
        assertTrue(Medical.profs.none { p -> Category.REPORT in Medical.kinds(p) })
        assertTrue(Medical.profs.all { p -> p.practicals.none { it.contains("Log book") } })
    }

    @Test fun examMonthsRunTheOtherWayForSupplementary() {
        val today = LocalDate.of(2026, 11, 10)
        assertEquals(LocalDate.of(2027, 5, 1), Medical.nextExamMonthStart(Medical.prof(4), today, supplementary = false))
        assertEquals(LocalDate.of(2027, 11, 1), Medical.nextExamMonthStart(Medical.prof(4), today, supplementary = true))
        assertEquals(LocalDate.of(2027, 5, 1), Medical.nextExamMonthStart(Medical.prof(1), today, supplementary = true))
    }

    @Test fun wholeExamsSplitIntoSubjects() {
        assertEquals(Medical.prof(1).subjects, Medical.partsFor(Category.PROF, Medical.prof(1)))
        assertTrue(Medical.partsFor(Category.ITEM, Medical.prof(1)).isEmpty())
        assertEquals("Anatomy Card", Medical.title(Category.CARD, "Anatomy", Medical.prof(1)))
        assertEquals("1st Professional Exam", Medical.title(Category.PROF, null, Medical.prof(1)))
        assertEquals("3rd Professional Supplementary Exam", Medical.title(Category.SUPPLEMENTARY, null, Medical.prof(3)))
    }
}
