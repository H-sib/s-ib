package com.hsib.sib.platform

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.hsib.sib.core.seal.SealEntry
import com.hsib.sib.data.LockScreenDetail
import com.hsib.sib.data.ThemeMode
import com.hsib.sib.domain.AppState
import com.hsib.sib.domain.Fmt
import com.hsib.sib.ui.Fixtures
import com.hsib.sib.ui.describeHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Notification reading, the notification bar dashboard and the readable history. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class ReaderAndDashboardTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    // ---- what is worth reading ----------------------------------------------------------------

    @Test
    fun aNotificationIsAssembledOnceWithoutRepeats() {
        val text = NotificationText.assemble("Prof. Rahman", "Report due Friday", "Report due Friday", listOf("Report due Friday", "Bring the draft"))
        assertEquals("Prof. Rahman\nReport due Friday\nBring the draft", text)
    }

    @Test
    fun aLongBodyReplacesTheShortOne() {
        val text = NotificationText.assemble("Title", "short", "the full message with the date 12 March", emptyList())
        assertTrue(text.contains("the full message"))
        assertFalse(text.contains("short"))
    }

    @Test
    fun textIsBounded() {
        assertEquals(NotificationText.MAX_CHARS, NotificationText.assemble("t", "x".repeat(50_000), null, emptyList()).length)
    }

    @Test
    fun chatterWithoutADateIsSkippedAndDeadlinesAreKept() {
        assertFalse(NotificationText.worthReading("haha ok see you"))
        assertFalse(NotificationText.worthReading("hi"))
        assertTrue(NotificationText.worthReading("Assignment due Friday at 5 PM"))
        assertTrue(NotificationText.worthReading("Quiz on 14 November"))
        assertTrue(NotificationText.worthReading("Meeting tomorrow morning"))
    }

    @Test
    fun onlyKnownChatAppsAreOffered() {
        assertTrue(KnownApps.all.all { (pkg, label) -> pkg.contains('.') && label.isNotBlank() })
        assertEquals(KnownApps.all.size, KnownApps.all.map { it.first }.toSet().size)
    }

    // ---- the history in words -----------------------------------------------------------------

    private fun entry(field: String, old: String, new: String, actor: String = "USER", reason: String = "edited") =
        SealEntry(0, 1_760_000_000L, field, old, new, actor, reason, "", "")

    @Test
    fun aDateChangeIsReadableNotRaw() {
        val line = describeHistory(entry("due", "2026-10-16T00:00 UTC date_only", "2026-10-18T17:00 UTC date_time", reason = "date changed"), Fmt(context))
        assertEquals("Date changed", line.title)
        val from = line.from!!
        val to = line.to!!
        assertFalse(from.contains("UTC") || from.contains("date_only") || from.contains("T00"))
        assertFalse(to.contains("UTC") || to.contains("date_time"))
        assertTrue(to.contains("5:00") || to.contains("17:00"))
        assertEquals("You", line.by)
    }

    @Test
    fun aNewDeadlineShowsItsTitleAndDueDate() {
        val line = describeHistory(entry("created", "", "Signals lab report | 2026-10-16T00:00 UTC date_only", reason = "created"), Fmt(context))
        assertEquals("Created", line.title)
        assertEquals("Signals lab report", line.from)
        assertTrue(line.to!!.startsWith("Due "))
        assertEquals("You", line.by)
    }

    @Test
    fun statusEffortAndPauseUseWords() {
        val fmt = Fmt(context)
        assertEquals("Marked done", describeHistory(entry("status", "ACTIVE", "COMPLETED"), fmt).title)
        assertEquals("Reopened", describeHistory(entry("status", "COMPLETED", "ACTIVE"), fmt).title)
        val effort = describeHistory(entry("effort", "168/240/384", "300/420/660"), fmt)
        assertEquals("Estimate changed", effort.title)
        assertEquals("4 h", effort.from)
        assertEquals("7 h", effort.to)
        assertEquals("Importance changed", describeHistory(entry("importance", "NORMAL", "HIGH"), fmt).title)
        assertEquals("High", describeHistory(entry("importance", "NORMAL", "HIGH"), fmt).to)
        assertEquals("Paused", describeHistory(entry("postponed", "none", "2026-11-01"), fmt).title)
        assertEquals("Resumed", describeHistory(entry("postponed", "2026-11-01", "none"), fmt).title)
        assertEquals("A suggestion you accepted", describeHistory(entry("due", "none", "2026-11-01T00:00 UTC date_only", "SUGGESTION", "changed after you accepted a suggestion"), fmt).by.substringBefore("  "))
    }

    // ---- the notification bar dashboard -------------------------------------------------------

    private fun render(rv: RemoteViews, name: String) {
        val density = context.resources.displayMetrics.density
        val width = (360 * density).toInt()
        val view = rv.apply(context, FrameLayout(context))
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("build/shots").mkdirs()
        FileOutputStream("build/shots/$name.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("$name drew nothing", view.measuredHeight > 0)
    }

    private fun themed(mode: ThemeMode): AppState = Fixtures.soon.copy(settings = Fixtures.soon.settings.copy(theme = mode))

    @Test
    fun theDashboardRendersInEveryTheme() {
        val notifier = Notifier(context)
        for ((mode, tag) in listOf(ThemeMode.DARK to "twilight", ThemeMode.BLACK to "black", ThemeMode.NEON to "neon", ThemeMode.LIGHT to "light")) {
            val views = notifier.dashboardViews(themed(mode))
            assertNotNull(views)
            render(views!!.small, "dashboard-small-$tag")
            render(views.big, "dashboard-big-$tag")
        }
    }

    @Test
    fun theDashboardNamesTheNextDeadlineAndCountsThem() {
        val views = Notifier(context).dashboardViews(Fixtures.soon)!!
        assertEquals("Signals lab report", views.heroTitle)
        assertEquals(3, views.count)
    }

    @Test
    fun nothingIsPinnedWhenNothingNeedsAttention() {
        val notifier = Notifier(context)
        assertNull(notifier.dashboard(Fixtures.empty))
        assertNull(notifier.dashboard(Fixtures.calm))
    }

    @Test
    fun theLockScreenVersionHidesTheTitleByDefault() {
        val n = Notifier(context).dashboard(Fixtures.soon)!!
        assertEquals(androidx.core.app.NotificationCompat.VISIBILITY_PRIVATE, n.visibility)
        assertNotNull(n.publicVersion)
        val shown = Notifier(context).dashboard(Fixtures.soon.copy(settings = Fixtures.soon.settings.copy(lockScreenDetail = LockScreenDetail.TITLES)))!!
        assertEquals(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC, shown.visibility)
    }

    @Test
    fun theCardOnlyStaysThroughClearAllWhenThePersonKeepsIt() {
        val normal = Notifier(context).dashboard(Fixtures.soon)!!
        assertEquals(0, normal.flags and android.app.Notification.FLAG_ONGOING_EVENT)
        val kept = Notifier(context).dashboard(Fixtures.soon.copy(settings = Fixtures.soon.settings.copy(todayPinned = true)))!!
        assertTrue(kept.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun swipingTheCardAwayHidesItForThatDeadlineUntilTomorrow() {
        val notifier = Notifier(context)
        val key = notifier.dashboardViews(Fixtures.soon)!!.hiddenKey
        assertNull(notifier.dashboard(Fixtures.soon.copy(settings = Fixtures.soon.settings.copy(dashboardHidden = key))))
        assertNotNull(notifier.dashboard(Fixtures.soon.copy(settings = Fixtures.soon.settings.copy(dashboardHidden = "another|key"))))
    }
}
