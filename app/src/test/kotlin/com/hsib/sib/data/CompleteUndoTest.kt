package com.hsib.sib.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Lifecycle
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.model.Repeat
import com.hsib.sib.ui.TestReady
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/** Marking complete can be put back: a closed deadline is opened again, a repeating one gets its old date, and nothing else is overwritten. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CompleteUndoTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val zone = ZoneId.systemDefault()
    private fun due(days: Long) = DueSpec(LocalDate.now(zone).plusDays(days).atStartOfDay(), zone, Precision.DATE_ONLY)

    private fun birthday(id: String = "bday"): Obligation {
        val day = LocalDate.now(zone).plusDays(21)
        return Obligation(id = id, title = "Birthday", due = DueSpec(day.atStartOfDay(), zone, Precision.DATE_ONLY), repeat = Repeat.YEARLY, repeatAnchor = day)
    }

    @Test fun aRepeatingDateJumpsAYearAndUndoBringsTheOldDateBack() = runBlocking {
        val scope = CoroutineScope(Job())
        val ready = TestReady.create(context, scope)
        val o = birthday()
        ready.store.create(o, "USER", "test")
        ready.store.complete("bday", null, null)
        val after = ready.store.obligation("bday")!!
        assertEquals(Lifecycle.ACTIVE, after.lifecycle)
        assertEquals(o.due!!.local.plusYears(1), after.due!!.local)
        assertNotNull(ready.store.undoRecord("bday"))

        assertTrue(ready.store.undoComplete("bday"))
        assertEquals(o.due!!.local, ready.store.obligation("bday")!!.due!!.local)
        assertNull(ready.store.undoRecord("bday"))
        assertFalse(ready.store.undoComplete("bday"))
        scope.cancel()
    }

    @Test fun aClosedDeadlineIsOpenedAgainByUndo() = runBlocking {
        val scope = CoroutineScope(Job())
        val ready = TestReady.create(context, scope)
        ready.store.create(Obligation(id = "once", title = "Report", due = due(5)), "USER", "test")
        ready.store.complete("once", "sent it", null)
        assertEquals(Lifecycle.COMPLETED, ready.store.obligation("once")!!.lifecycle)

        assertTrue(ready.store.undoComplete("once"))
        val back = ready.store.obligation("once")!!
        assertEquals(Lifecycle.ACTIVE, back.lifecycle)
        assertNull(back.completedAt)
        assertNull(ready.store.undoRecord("once"))
        scope.cancel()
    }

    @Test fun undoNeverOverwritesADateTheOwnerChangedAfterwards() = runBlocking {
        val scope = CoroutineScope(Job())
        val ready = TestReady.create(context, scope)
        ready.store.create(birthday(), "USER", "test")
        ready.store.complete("bday", null, null)
        val moved = ready.store.obligation("bday")!!
        val mine = moved.copy(due = due(40))
        ready.store.save(mine, "USER", "date changed")

        assertFalse(ready.store.undoComplete("bday"))
        assertEquals(mine.due!!.local, ready.store.obligation("bday")!!.due!!.local)
        scope.cancel()
    }

    @Test fun reopenAndDeleteClearTheUndoRecord() = runBlocking {
        val scope = CoroutineScope(Job())
        val ready = TestReady.create(context, scope)
        ready.store.create(Obligation(id = "a", title = "A", due = due(5)), "USER", "test")
        ready.store.create(Obligation(id = "b", title = "B", due = due(5)), "USER", "test")
        ready.store.complete("a", null, null)
        ready.store.complete("b", null, null)
        assertNotNull(ready.store.undoRecord("a"))
        ready.store.reopen("a")
        assertNull(ready.store.undoRecord("a"))
        ready.store.delete("b")
        assertNull(ready.store.undoRecord("b"))
        scope.cancel()
    }

    @Test fun theNewSwitchesSurviveTheSettingsCodec() {
        val s = AppSettings(focusNotification = false, priorityColors = false, uiHidden = setOf("routinechip", "checklist", "files"))
        val back = SettingsCodec.decode(SettingsCodec.encode(s))
        assertFalse(back.focusNotification)
        assertFalse(back.priorityColors)
        assertEquals(setOf("routinechip", "checklist", "files"), back.uiHidden)
        assertTrue(SettingsCodec.decode(emptyMap()).focusNotification)
        assertTrue(SettingsCodec.decode(emptyMap()).priorityColors)
    }
}
