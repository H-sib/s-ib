package com.hsib.sib.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.hsib.sib.data.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the intro at fixed moments so its timing can be looked at without a phone. The images are written only when
 * the build is run with -Precord=true; otherwise this just proves every frame composes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class IntroShotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun frames(mode: ThemeMode, tag: String) {
        var t by mutableFloatStateOf(0f)
        rule.setContent { SibTheme(mode) { IntroScene(t) } }
        for ((name, at) in listOf("1-axis" to 0.2f, "2-deadline" to 0.38f, "3-travel" to 0.55f, "4-arc" to 0.72f, "5-name" to 0.88f, "6-final" to 1f)) {
            t = at
            rule.waitForIdle()
            rule.onRoot().captureRoboImage("build/shots/intro-$tag-$name.png")
        }
    }

    @Test
    fun lightFrames() = frames(ThemeMode.LIGHT, "light")

    @Test
    fun darkFrames() = frames(ThemeMode.DARK, "dark")
}
