package com.hsib.sib.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.hsib.sib.Ready
import com.hsib.sib.core.model.Space
import com.hsib.sib.data.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The same idea as [ScreenShotTest], for screens that talk to the store. They run on an in-memory database. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class ReadyShotTest {
    @get:Rule
    val rule = createComposeRule()

    private val noImport = ImportActions(paste = {}, photo = {}, scan = {}, file = {})

    private fun shot(name: String, mode: ThemeMode = ThemeMode.DARK, space: Space = Space.WORK, content: @Composable (Ready) -> Unit) {
        val ready = TestReady.create(ApplicationProvider.getApplicationContext(), CoroutineScope(Dispatchers.Unconfined))
        runBlocking { Fixtures.soon.items.forEach { ready.store.create(it.o) } }
        rule.setContent { SibTheme(mode, space) { Box(Modifier.fillMaxSize().background(SibTheme.colors.paper)) { content(ready) } } }
        rule.mainClock.advanceTimeBy(2500)
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/$name.png")
    }

    private val st = Fixtures.soon

    @Test fun detailDark() = shot("detail-dark") { DetailScreen("a", st, it, {}, {}) }

    @Test fun addDark() = shot("add-dark") { AddScreen(Fixtures.empty, it, noImport, {}) {} }

    @Test fun settingsHomeDark() = shot("settings-home-dark") { SettingsHome(st) {} }

    @Test fun remindersDark() = shot("settings-reminders-dark") { RemindersSettings(st, it) {} }

    @Test fun securityDark() = shot("settings-security-dark") { SecuritySettings(st, it, {}, {}) }

    @Test fun appearanceDark() = shot("settings-appearance-dark") { AppearanceSettings(st.copy(settings = st.settings.copy(theme = ThemeMode.DARK)), it) {} }

    @Test fun aboutDark() = shot("settings-about-dark") { AboutScreen {} }

    @Test fun closeDetailDark() = shot("close-detail-dark", space = Space.CLOSE) { CloseDetailScreen("x1", Fixtures.withClose, it, {}, {}, {}) }

    @Test fun closeEditorDark() = shot("close-editor-dark", space = Space.CLOSE) { CloseEditorScreen(null, "anniversary", Fixtures.withClose, it, {}) {} }
}
