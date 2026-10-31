package com.hsib.sib.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hsib.sib.core.model.Category
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Effort
import com.hsib.sib.core.model.FileLink
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Part
import com.hsib.sib.core.model.Precision
import com.hsib.sib.ui.TestReady
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NewFeaturesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val zone = ZoneId.systemDefault()

    private fun due(days: Long) = DueSpec(LocalDate.now(zone).plusDays(days).atStartOfDay(), zone, Precision.DATE_ONLY)

    @Test fun partsOwnPlanAndFilesSurviveTheDatabase() = runBlocking {
        val scope = CoroutineScope(Job())
        val ready = TestReady.create(context, scope)
        val day = LocalDate.now(zone).plusDays(2)
        val o = Obligation(
            id = "a", title = "Seven things", due = due(10), effort = Effort.fromLikely(240),
            parts = listOf(Part("One", true), Part("Two")), manualPlan = mapOf(day to 120), links = listOf(FileLink("notes.pdf", "content://x/1")),
        )
        ready.store.create(o, "USER", "test")
        val back = ready.store.obligation("a")!!
        assertEquals(o.parts, back.parts)
        assertEquals(mapOf(day to 120), back.manualPlan)
        assertEquals(o.links, back.links)
        scope.cancel()
    }

    @Test fun anOwnPlanIsKeptAsChosenAndOthersPlanAroundIt() = runBlocking {
        val scope = CoroutineScope(Job())
        val ready = TestReady.create(context, scope)
        val today = LocalDate.now(zone)
        val mine = Obligation(id = "m", title = "Mine", due = due(5), effort = Effort.fromLikely(600), manualPlan = mapOf(today.plusDays(1) to 90))
        val other = Obligation(id = "o", title = "Other", due = due(5), effort = Effort.fromLikely(240))
        val st = ready.engine.compute(listOf(mine, other), emptyList(), emptyList(), AppSettings(), 1.0)
        val mineBlocks = st.plan.blocks.filter { it.taskId == "m" }
        assertEquals(listOf(90), mineBlocks.map { it.minutes })
        assertEquals(today.plusDays(1), mineBlocks.single().day)
        assertTrue(st.plan.blocks.any { it.taskId == "o" })
        scope.cancel()
    }

    @Test fun classDiaryIsKeptAndRemoved() = runBlocking {
        val scope = CoroutineScope(Job())
        val ready = TestReady.create(context, scope)
        ready.store.addClassNote(ClassNote("n1", LocalDate.of(2026, 11, 2), "Anatomy", "Male genital", "Dr. A", "Lecture"))
        val notes = ready.store.classNotes.first()
        assertEquals("Male genital", notes.single().topic)
        ready.store.removeClassNote("n1")
        assertTrue(ready.store.classNotes.first().isEmpty())
        scope.cancel()
    }

    @Test fun theWeeklyRoutineIsKeptCopiedAndRemoved() = runBlocking {
        val scope = CoroutineScope(Job())
        val ready = TestReady.create(context, scope)
        ready.store.saveRoutineSlot(RoutineSlot("a", 6, "Lecture", "", "Anatomy", "Gallery 2", 8 * 60, 9 * 60))
        ready.store.saveRoutineSlot(RoutineSlot("b", 6, "Ward", "Morning", "Medicine", "", 10 * 60, 13 * 60))
        assertEquals(2, ready.store.routine.first().size)
        ready.store.replaceRoutineDay(7, ready.store.routine.first().filter { it.day == 6 })
        val sunday = ready.store.routine.first().filter { it.day == 7 }
        assertEquals(listOf("Anatomy", "Medicine"), sunday.map { it.subject })
        ready.store.removeRoutineSlot("a")
        assertEquals(1, ready.store.routine.first().count { it.day == 6 })
        scope.cancel()
    }

    @Test fun routineChoicesAndMotivationSwitchesSurviveTheSettingsCodec() {
        val s = AppSettings(routineDays = setOf(6, 7, 1, 5), routineLectures = 7, routineTutorials = 0, routineWards = 2, motivationOn = false, motivationOff = setOf("EXAM", "BILL"), medicalMode = true)
        val back = SettingsCodec.decode(SettingsCodec.encode(s))
        assertEquals(s.routineDays, back.routineDays)
        assertEquals(7, back.routineLectures)
        assertEquals(0, back.routineTutorials)
        assertEquals(2, back.routineWards)
        assertEquals(setOf("EXAM", "BILL"), back.motivationOff)
        assertEquals(false, back.motivationOn)
        assertEquals(true, back.medicalMode)
    }

    @Test fun savedCoursesSurviveTheSettingsCodec() {
        val mine = com.hsib.sib.core.medical.Courses.mbbs.copy(name = "IUB test")
        val s = AppSettings(courses = listOf(mine), courseActive = "IUB test")
        val back = SettingsCodec.decode(SettingsCodec.encode(s))
        assertEquals(listOf(mine), back.courses)
        assertEquals("IUB test", back.course.name)
        assertEquals("MBBS", AppSettings().course.name)
    }

    @Test fun medicalCategoriesSurviveTheDatabase() = runBlocking {
        val scope = CoroutineScope(Job())
        val ready = TestReady.create(context, scope)
        ready.store.create(Obligation(id = "p", title = "1st Prof exam", category = Category.PROF, due = due(30), effort = Effort.fromLikely(4800)), "USER", "test")
        assertEquals(Category.PROF, ready.store.obligation("p")!!.category)
        scope.cancel()
    }
}
