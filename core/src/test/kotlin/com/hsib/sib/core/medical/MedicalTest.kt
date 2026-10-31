package com.hsib.sib.core.medical

import com.hsib.sib.core.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MedicalTest {
    private val phases = Courses.mbbs.phases
    private fun phase(n: Int) = phases[n - 1]

    @Test fun fourProfsWithTheListedSubjects() {
        assertEquals(4, phases.size)
        assertEquals(listOf("Anatomy", "Physiology", "Biochemistry"), phase(1).subjects)
        assertEquals(listOf("Pharmacology & Therapeutics", "Forensic Medicine & Toxicology"), phase(2).subjects)
        assertTrue("Community Medicine & Public Health" in phase(3).subjects)
        assertEquals(listOf("Medicine & Allied Subjects", "Surgery & Allied Subjects", "Obstetrics & Gynaecology"), phase(4).subjects)
    }

    @Test fun fourthProfHasNoItemsOrCardsButHasWardFinal() {
        val kinds = Medical.kinds(phase(4))
        assertFalse(Category.ITEM in kinds)
        assertFalse(Category.CARD in kinds)
        assertTrue(Category.WARD_FINAL in kinds)
        assertTrue(Category.CARD in Medical.kinds(phase(1)))
        assertTrue(phases.none { p -> Category.REPORT in Medical.kinds(p) })
        assertTrue(phases.all { p -> p.practicals.none { it.contains("Log book") } })
    }

    @Test fun examMonthsRunTheOtherWayForSupplementary() {
        val today = LocalDate.of(2026, 11, 10)
        assertEquals(LocalDate.of(2027, 5, 1), Medical.nextExamMonthStart(phase(4), today, supplementary = false))
        assertEquals(LocalDate.of(2027, 11, 1), Medical.nextExamMonthStart(phase(4), today, supplementary = true))
        assertEquals(LocalDate.of(2027, 5, 1), Medical.nextExamMonthStart(phase(1), today, supplementary = true))
    }

    @Test fun wholeExamsSplitIntoSubjectsAndTitlesAreClear() {
        assertEquals(phase(1).subjects, Medical.partsFor(Category.PROF, phase(1)))
        assertTrue(Medical.partsFor(Category.ITEM, phase(1)).isEmpty())
        assertEquals("Anatomy Card", Medical.title(Category.CARD, "Anatomy", phase(1)))
        assertEquals("1st Professional Exam", Medical.title(Category.PROF, null, phase(1)))
        assertEquals("3rd Professional Supplementary Exam", Medical.title(Category.SUPPLEMENTARY, null, phase(3)))
    }

    @Test fun aCourseSurvivesBeingSharedAsText() {
        val code = Courses.toCode(Courses.mbbs)
        assertTrue(code.length < 3000)
        val back = Courses.fromCode("  " + code.chunked(40).joinToString("\n") + "  ")
        assertEquals(Courses.mbbs, back)
    }

    @Test fun badCodesAreRefusedWithoutCrashing() {
        assertNull(Courses.fromCode("hello"))
        assertNull(Courses.fromCode(Courses.CODE_PREFIX + "!!!not base64!!!"))
        assertNull(Courses.fromCode(Courses.CODE_PREFIX + "A".repeat(Courses.MAX_CODE_CHARS + 5)))
    }

    @Test fun sharedCoursesAreCleanedAndLimited() {
        val messy = Course("  Evil\u0007 course  ", "x".repeat(500), List(30) { CoursePhase("P$it", subjects = List(100) { s -> "Subject $s" }, kinds = listOf("PROF", "NOT_A_KIND"), examMonth = 99) })
        val c = Courses.sanitize(messy)!!
        assertEquals("Evil course", c.name)
        assertEquals(10, c.phases.size)
        assertEquals(40, c.phases[0].subjects.size)
        assertEquals(listOf("PROF"), c.phases[0].kinds)
        assertEquals(12, c.phases[0].examMonth)
        assertTrue(c.field.length <= 40)
        assertNull(Courses.sanitize(Course("", "x", emptyList())))
        assertNotNull(Courses.sanitize(Course("A", "x", listOf(CoursePhase("1")))))
    }

    @Test fun answersBuildACourse() {
        val c = Courses.fromAnswers("IUB CSE", "Engineering", "Semester", 8, listOf(Category.ITEM, Category.TERM, Category.PROF), listOf(listOf("Maths", "Physics")), 12, 6)!!
        assertEquals(8, c.phases.size)
        assertEquals("Semester 1", c.phases[0].name)
        assertEquals(listOf("Maths", "Physics"), c.phases[0].subjects)
        assertEquals(listOf("ITEM", "TERM", "PROF"), c.phases[2].kinds)
    }
}

class CourseTemplatesTest {
    @Test fun everyTemplateIsAlreadyClean() {
        for (t in CourseTemplates.all) {
            assertEquals("${t.course.name} changes when cleaned", t.course, Courses.sanitize(t.course))
            assertTrue(t.blurb.isNotBlank())
        }
    }

    @Test fun thereAreStudyCoursesAndProfessionsAndNamesAreUnique() {
        assertTrue(CourseTemplates.group(CourseTemplates.STUDY).size >= 8)
        assertTrue(CourseTemplates.group(CourseTemplates.WORK).size >= 14)
        assertTrue(CourseTemplates.group(CourseTemplates.LIFE).size >= 10)
        assertEquals(CourseTemplates.all.size, CourseTemplates.all.map { it.course.name }.toSet().size)
    }

    @Test fun professionsUseTypesAndStudyCoursesUseExamKinds() {
        for (t in CourseTemplates.group(CourseTemplates.WORK) + CourseTemplates.group(CourseTemplates.LIFE)) {
            assertTrue(Courses.isWork(t.course))
            assertTrue(t.course.phases.all { it.types.isNotEmpty() && it.kinds.isEmpty() })
        }
        for (t in CourseTemplates.group(CourseTemplates.STUDY)) assertTrue(t.course.phases.all { it.kinds.isNotEmpty() })
    }

    @Test fun noScreenTextTalksAboutTemplates() {
        for (t in CourseTemplates.all) assertFalse(t.blurb, t.blurb.contains("template", true) || t.blurb.contains("starting point", true))
    }

    @Test fun everyTemplateCanBeShared() {
        for (t in CourseTemplates.all) assertEquals(t.course, Courses.fromCode(Courses.toCode(t.course)))
    }

    @Test fun medicalColoursOnlyForMedicalFields() {
        assertTrue(Courses.isMedical(Courses.mbbs))
        assertFalse(Courses.isMedical(CourseTemplates.all.first { it.course.name == "Corporate office" }.course))
        assertFalse(Courses.isMedical(CourseTemplates.all.first { it.course.name == "Engineering university" }.course))
    }
}
