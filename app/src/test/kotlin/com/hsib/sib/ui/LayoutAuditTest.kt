package com.hsib.sib.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.hsib.sib.core.model.Space
import com.hsib.sib.data.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The clipping audit (APP_QUALITY_STANDARD.txt, P9 and hard stop H11): the main screens are drawn 360 dp wide at font scale
 * 1.0, 1.3 and 2.0, and every piece of text is asked whether it was cut off (ellipsis or clipped) or ran past its box. A cut
 * off word is a defect: the person cannot read what the app says. Findings list the screen, the scale and the words.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi", application = Application::class)
class LayoutAuditTest {
    @get:Rule
    val rule = createComposeRule()

    private val noImport = ImportActions(paste = {}, photo = {}, scan = {}, file = {})
    private val st = Fixtures.soon
    private val diaryFixture = (0 until 90).map { i ->
        com.hsib.sib.core.medical.DiaryEntry("d$i", java.time.LocalDate.of(2026, 11, 1).minusDays((i / 2).toLong()), listOf("Anatomy", "Physiology", "Biochemistry")[i % 3],
            "Topic ${i % 11}", "Dr. " + listOf("Rahman", "Akter", "Hossain", "Karim")[i % 4], listOf("Lecture", "Lecture", "Tutorial", "Ward")[i % 4])
    }

    private fun problems(label: String): List<String> {
        val out = ArrayList<String>()
        val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
        for (n in nodes) {
            val results = ArrayList<TextLayoutResult>()
            n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            val r = results.firstOrNull() ?: continue
            // didOverflowWidth is true for ordinary wrapped text in this test setup, so only the two reliable signals are used:
            // text cut at its height (maxLines or a fixed box) and a line ended with an ellipsis.
            val ellipsized = (0 until r.lineCount).any { r.isLineEllipsized(it) }
            val text = r.layoutInput.text.text
            val broken = (0 until r.lineCount - 1).any { i ->
                val end = r.getLineEnd(i, visibleEnd = false)
                end in 1 until text.length && !text[end - 1].isWhitespace() && !text[end].isWhitespace() && text[end - 1] != '-' && text[end - 1] != '/'
            }
            if (broken) out += "$label: \"${text.take(48)}\" breaks a word across two lines"
            if (r.didOverflowHeight || ellipsized) {
                out += "$label: \"${r.layoutInput.text.text.take(48)}\" is cut off (${if (ellipsized) "dots" else "height"})"
            }
        }
        return out
    }

    private fun audit(scale: Float) {
        val ready = TestReady.create(ApplicationProvider.getApplicationContext(), CoroutineScope(Dispatchers.Unconfined))
        runBlocking { Fixtures.soon.items.forEach { ready.store.create(it.o) } }
        val screens: List<Pair<String, @androidx.compose.runtime.Composable () -> Unit>> = listOf(
            "today" to { TodayScreen(st, noImport, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}) },
            "all" to { AllScreen(st, {}, {}) },
            "plan" to { PlanScreen(Fixtures.planned, {}, {}) },
            "review" to { ReviewScreen(Fixtures.review, { _, _ -> }, {}, {}, {}) },
            "terms" to { TermsScreen(false, null, {}, null) },
            "guide" to { GuideScreen(false, {}) },
            "settings" to { SettingsHome(st) {} },
            "add" to { AddScreen(Fixtures.empty, ready, noImport, {}) {} },
            "detail" to { DetailScreen("a", st, ready, {}, {}) },
            "appearance" to { AppearanceSettings(st, ready) {} },
            "diary" to { ClassDiaryScreen(st, ready) {} },
            "diary charts" to { Page("Class diary", {}) { DiaryCharts(diaryFixture, java.time.LocalDate.of(2026, 11, 1), rememberFmt()) } },
            "diary tables" to { Page("Class diary", {}) { DiaryTables(diaryFixture) } },
            "routine" to { ClassRoutineScreen(st, ready) {} },
        )
        var which by mutableIntStateOf(0)
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, scale)) {
                SibTheme(ThemeMode.DARK, Space.WORK) { Box(Modifier.fillMaxSize().background(SibTheme.colors.paper)) { screens[which].second() } }
            }
        }
        val found = ArrayList<String>()
        for (i in screens.indices) {
            which = i
            rule.mainClock.advanceTimeBy(800)
            rule.waitForIdle()
            found += problems("${screens[i].first} at $scale")
        }
        assertTrue("text is cut off:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test fun fontScale1() = audit(1.0f)

    @Test fun fontScale1point3() = audit(1.3f)

    @Test fun fontScale2() = audit(2.0f)
}
