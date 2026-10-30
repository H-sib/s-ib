package com.hsib.sib.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hsib.sib.core.model.Space
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.hsib.sib.core.seal.SealEntry
import com.hsib.sib.data.ThemeMode
import com.hsib.sib.domain.AppState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the main screens with made-up data so the design can be looked at without a phone. Images are written only
 * when the build is run with -Precord=true (they land in app/build/shots); otherwise this proves each screen composes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class ScreenShotTest {
    @get:Rule
    val rule = createComposeRule()

    private val noImport = ImportActions(paste = {}, photo = {}, scan = {}, file = {})

    private fun shot(name: String, mode: ThemeMode = ThemeMode.DARK, space: Space = Space.WORK, content: @Composable () -> Unit) {
        rule.setContent { SibTheme(mode, space) { Box(Modifier.fillMaxSize().background(SibTheme.colors.paper)) { content() } } }
        rule.mainClock.advanceTimeBy(2500)
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/$name.png")
    }

    private fun today(st: AppState) = @Composable {
        TodayScreen(st, noImport, {}, {}, {}, {}, {}, { _, _ -> }, {}, {})
    }

    @Test fun todaySoonDark() = shot("today-soon-dark", content = today(Fixtures.soon))

    @Test fun todaySoonLight() = shot("today-soon-light", ThemeMode.LIGHT, content = today(Fixtures.soon))

    @Test fun todayCalmDark() = shot("today-calm-dark", content = today(Fixtures.calm))

    @Test fun todayOverdueDark() = shot("today-overdue-dark", content = today(Fixtures.overdue))

    @Test fun todayEmptyDark() = shot("today-empty-dark", content = today(Fixtures.empty))

    @Test fun planDark() = shot("plan-dark") { PlanScreen(Fixtures.planned, {}, {}) }

    @Test fun planShortDark() = shot("plan-short-dark") { PlanScreen(Fixtures.short, {}, {}) }

    @Test fun reviewDark() = shot("review-dark") { ReviewScreen(Fixtures.review, { _, _ -> }, {}, {}, {}) }

    @Test fun reviewEmptyDark() = shot("review-empty-dark") { ReviewScreen(Fixtures.empty, { _, _ -> }, {}, {}, {}) }

    @Test fun runwayDark() = shot("runway-dark") { Column(Modifier.padding(24.dp)) { Runway(runwayItems(Fixtures.planned), Fixtures.today, rememberFmt(), {}) } }

    @Test fun loadDark() = shot("load-dark") {
        Column(Modifier.padding(24.dp)) { LoadHorizon(Fixtures.planned.plan, Fixtures.today, rememberFmt()); LoadLegend() }
    }

    @Test fun todayBlack() = shot("today-soon-black", ThemeMode.BLACK, content = today(Fixtures.soon))

    @Test fun todayNeon() = shot("today-soon-neon", ThemeMode.NEON, content = today(Fixtures.soon))

    @Test fun closeHomeBlack() = shot("close-home-black", ThemeMode.BLACK, Space.CLOSE) { CloseHomeScreen(Fixtures.withClose, {}, {}, {}) }

    @Test fun closeHomeNeon() = shot("close-home-neon", ThemeMode.NEON, Space.CLOSE) { CloseHomeScreen(Fixtures.withClose, {}, {}, {}) }

    @Test fun planNeon() = shot("plan-neon", ThemeMode.NEON) { PlanScreen(Fixtures.planned, {}, {}) }

    @Test fun todayCloseDark() = shot("today-close-dark", content = today(Fixtures.withClose))

    @Test fun closeHomeDark() = shot("close-home-dark", space = Space.CLOSE) { CloseHomeScreen(Fixtures.withClose, {}, {}, {}) }

    @Test fun closeHomeLight() = shot("close-home-light", ThemeMode.LIGHT, Space.CLOSE) { CloseHomeScreen(Fixtures.withClose, {}, {}, {}) }

    @Test fun onboardingDark() = shot("onboarding-dark") { Onboarding(Fixtures.today, { null }, {}, {}) }

    @Test fun promptsDark() = shot("prompts-dark") {
        Column(Modifier.padding(24.dp)) { PromptCard(true, {}); Spacer(Modifier.height(16.dp)); PromptCard(false, {}) }
    }

    @Test fun choicesDark() = shot("choices-dark") {
        Column(Modifier.padding(24.dp)) {
            Text("Effort", style = SibType.bodyStrong, color = SibTheme.colors.ink)
            Spacer(Modifier.height(8.dp))
            Choice(EffortChoices, 240, {})
            Spacer(Modifier.height(20.dp))
            Text("Importance", style = SibType.bodyStrong, color = SibTheme.colors.ink)
            Spacer(Modifier.height(8.dp))
            Choice(listOf("LOW" to "Low", "NORMAL" to "Normal", "HIGH" to "High", "CRITICAL" to "Critical"), "HIGH", {})
        }
    }

    @Test fun historyDark() = shot("history-dark") {
        val fmt = rememberFmt()
        fun e(f: String, o: String, n: String, actor: String = "USER", r: String = "edited") = SealEntry(0, 1_760_000_000L, f, o, n, actor, r, "", "")
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                e("due", "2026-10-16T00:00 UTC date_only", "2026-10-18T17:00 UTC date_time", r = "date changed"),
                e("importance", "NORMAL", "HIGH", r = "importance changed"),
                e("effort", "168/240/384", "300/420/660", r = "estimate changed"),
                e("status", "ACTIVE", "COMPLETED", r = "marked done"),
                e("created", "", "Signals lab report | 2026-10-16T00:00 UTC date_only", r = "created"),
            ).forEach { HistoryEntryCard(it, fmt, Fixtures.zone) }
        }
    }

    @Test fun allDark() = shot("all-dark") { AllScreen(Fixtures.soon, {}, {}) }

    @Test fun barDark() = shot("bar-dark") { SibBottomBar(Tab.TODAY, {}, 3) }

    @Test fun termsDark() = shot("terms-dark") { TermsScreen(false, null, {}, null) }

    @Test fun termsLight() = shot("terms-light", ThemeMode.LIGHT) { TermsScreen(false, null, {}, null) }

    @Test fun guideDark() = shot("guide-dark") { GuideScreen(false, {}) }

    @Test fun whatsNewDark() = shot("whatsnew-dark") {
        androidx.compose.foundation.layout.Box(Modifier.padding(24.dp)) { WhatsNewCard(WhatsNew.entries, {}) }
    }
}
