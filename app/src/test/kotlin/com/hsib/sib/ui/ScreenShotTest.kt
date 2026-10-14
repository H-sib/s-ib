package com.hsib.sib.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hsib.sib.core.model.Space
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
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
        TodayScreen(st, noImport, {}, {}, {}, {}, {}, { _, _ -> }, {})
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

    @Test fun allDark() = shot("all-dark") { AllScreen(Fixtures.soon, {}, {}) }

    @Test fun barDark() = shot("bar-dark") { SibBottomBar(Tab.TODAY, {}, 3) }
}
