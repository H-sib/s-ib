package com.hsib.sib.ui

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hsib.sib.R
import com.hsib.sib.core.model.Space
import com.hsib.sib.data.ThemeMode

/**
 * The night palette: a deep violet-black base, layered surfaces, and one signal colour per state graded from calm to
 * urgent. Text is bright on purpose; hierarchy comes from size and weight, never from dimming. Every text pairing was
 * checked against WCAG AA (body text at 7:1 or better) in all four palettes; urgency is never carried by colour alone.
 * The names are kept from the first design so each component recolours itself: vermilion is the alert colour,
 * ochre the warning, verdigris the calm and done colour.
 */
@Immutable
class SibColors(
    val paper: Color,
    val raised: Color,
    val lifted: Color,
    val ink: Color,
    val inkSoft: Color,
    val rule: Color,
    val vermilion: Color,
    val ochre: Color,
    val verdigris: Color,
    val violet: Color,
    val vermilionWash: Color,
    val ochreWash: Color,
    val verdigrisWash: Color,
    val violetWash: Color,
    /** The signature gradient runs glowA to glowB; onGlow is the text colour that sits on it. */
    val glowA: Color,
    val glowB: Color,
    val onGlow: Color,
    /** The solid fill of primary buttons and chosen options, and the text colour that sits on it. */
    val action: Color,
    val onAction: Color,
    val dark: Boolean,
    /** How strongly hero cards glow: full in the colourful themes, restrained in Black. */
    val glowStrength: Float = 1f,
) {
    val glow: Brush get() = Brush.linearGradient(listOf(glowA, glowB))
}

private val DarkColors = SibColors(
    paper = Color(0xFF0D0B1F), raised = Color(0xFF171433), lifted = Color(0xFF221E47), ink = Color(0xFFF7F4FF), inkSoft = Color(0xFFCFC8F0),
    rule = Color(0xFF2D2857), vermilion = Color(0xFFFF7F6B), ochre = Color(0xFFFFC25C), verdigris = Color(0xFF55E8BA), violet = Color(0xFFA28FFF),
    vermilionWash = Color(0xFF3B1B30), ochreWash = Color(0xFF38290F), verdigrisWash = Color(0xFF123C3A), violetWash = Color(0xFF2A2255),
    glowA = Color(0xFF9B86FF), glowB = Color(0xFFFF8A70), onGlow = Color(0xFF0D0B1F), action = Color(0xFFF7F4FF), onAction = Color(0xFF0D0B1F), dark = true,
)

private val LightColors = SibColors(
    paper = Color(0xFFF3F0FF), raised = Color(0xFFFFFFFF), lifted = Color(0xFFE9E4FF), ink = Color(0xFF14112B), inkSoft = Color(0xFF3E3869),
    rule = Color(0xFFD6D0F2), vermilion = Color(0xFFC2331F), ochre = Color(0xFF855000), verdigris = Color(0xFF0C7556), violet = Color(0xFF5B3FD4),
    vermilionWash = Color(0xFFFFE2DC), ochreWash = Color(0xFFFFEBC4), verdigrisWash = Color(0xFFD3F4E8), violetWash = Color(0xFFE2DCFF),
    glowA = Color(0xFF9B86FF), glowB = Color(0xFFFF8A70), onGlow = Color(0xFF0D0B1F), action = Color(0xFF14112B), onAction = Color(0xFFF3F0FF), dark = false,
)

/** Close ones: the same tokens in plum and rose, with a plum-to-rose gradient. */
private val CloseDark = SibColors(
    paper = Color(0xFF190E1C), raised = Color(0xFF251528), lifted = Color(0xFF321C36), ink = Color(0xFFF8E9F1), inkSoft = Color(0xFFD9B9CE),
    rule = Color(0xFF45284A), vermilion = Color(0xFFFF8FAE), ochre = Color(0xFFF0C26B), verdigris = Color(0xFF74D1B5), violet = Color(0xFFD59BFF),
    vermilionWash = Color(0xFF40203A), ochreWash = Color(0xFF392C12), verdigrisWash = Color(0xFF173B31), violetWash = Color(0xFF35204F),
    glowA = Color(0xFFC27BFF), glowB = Color(0xFFFF8FB0), onGlow = Color(0xFF1A0F1F), action = Color(0xFFFF8FAE), onAction = Color(0xFF190E1C), dark = true,
)

private val CloseLight = SibColors(
    paper = Color(0xFFFFF1F5), raised = Color(0xFFFFFFFF), lifted = Color(0xFFFBDDE8), ink = Color(0xFF2A1226), inkSoft = Color(0xFF5A3355),
    rule = Color(0xFFEBC9D7), vermilion = Color(0xFFB01E4F), ochre = Color(0xFF80560A), verdigris = Color(0xFF2A6B58), violet = Color(0xFF8A2FC2),
    vermilionWash = Color(0xFFF8D8E3), ochreWash = Color(0xFFF2E3C4), verdigrisWash = Color(0xFFD8E8DF), violetWash = Color(0xFFEBD8FA),
    glowA = Color(0xFFC27BFF), glowB = Color(0xFFFF8FB0), onGlow = Color(0xFF1A0F1F), action = Color(0xFFB01E4F), onAction = Color(0xFFFFF1F5), dark = false,
)

/** Pure black with white: the base is #000000, text is white, and the accent is white on a cool grey gradient. */
private val BlackColors = SibColors(
    paper = Color(0xFF000000), raised = Color(0xFF0F0F11), lifted = Color(0xFF1A1A1E), ink = Color(0xFFFFFFFF), inkSoft = Color(0xFFD6D6DC),
    rule = Color(0xFF2B2B31), vermilion = Color(0xFFFF7062), ochre = Color(0xFFFFC95C), verdigris = Color(0xFF5CE8B6), violet = Color(0xFFECECF2),
    vermilionWash = Color(0xFF34140F), ochreWash = Color(0xFF33270A), verdigrisWash = Color(0xFF0C2F22), violetWash = Color(0xFF222226),
    glowA = Color(0xFFFFFFFF), glowB = Color(0xFFC4C8D4), onGlow = Color(0xFF000000), action = Color(0xFFFFFFFF), onAction = Color(0xFF000000), dark = true, glowStrength = 0.3f,
)

private val BlackClose = SibColors(
    paper = Color(0xFF000000), raised = Color(0xFF140A10), lifted = Color(0xFF22121B), ink = Color(0xFFFFFFFF), inkSoft = Color(0xFFE8D0DC),
    rule = Color(0xFF35202B), vermilion = Color(0xFFFF8FAE), ochre = Color(0xFFF0C26B), verdigris = Color(0xFF74D1B5), violet = Color(0xFFFFC2D6),
    vermilionWash = Color(0xFF3A1626), ochreWash = Color(0xFF33260C), verdigrisWash = Color(0xFF123329), violetWash = Color(0xFF2E1824),
    glowA = Color(0xFFFFE0EA), glowB = Color(0xFFFF9DB8), onGlow = Color(0xFF1A0B12), action = Color(0xFFFFD1E0), onAction = Color(0xFF1A0B12), dark = true, glowStrength = 0.3f,
)

/** Neon: almost black, with electric cyan and hot magenta doing the talking. */
private val NeonColors = SibColors(
    paper = Color(0xFF06060F), raised = Color(0xFF111122), lifted = Color(0xFF1B1B36), ink = Color(0xFFFFFFFF), inkSoft = Color(0xFFDAD7FF),
    rule = Color(0xFF2B2B52), vermilion = Color(0xFFFF5C9A), ochre = Color(0xFFFFD84D), verdigris = Color(0xFF5CFFA0), violet = Color(0xFF5CE1FF),
    vermilionWash = Color(0xFF3A0F26), ochreWash = Color(0xFF3A3208), verdigrisWash = Color(0xFF0C3A22), violetWash = Color(0xFF0B2D3D),
    glowA = Color(0xFF00E5FF), glowB = Color(0xFFFF3D9A), onGlow = Color(0xFF06060F), action = Color(0xFF00E5FF), onAction = Color(0xFF06060F), dark = true, glowStrength = 1f,
)

private val NeonClose = SibColors(
    paper = Color(0xFF0A0510), raised = Color(0xFF170B24), lifted = Color(0xFF251239), ink = Color(0xFFFFFFFF), inkSoft = Color(0xFFF2D9FF),
    rule = Color(0xFF3C2058), vermilion = Color(0xFFFF66B0), ochre = Color(0xFFFFC94D), verdigris = Color(0xFF4DF0C0), violet = Color(0xFFD88AFF),
    vermilionWash = Color(0xFF3D0F2B), ochreWash = Color(0xFF3A2D08), verdigrisWash = Color(0xFF0B3A2E), violetWash = Color(0xFF2D1050),
    glowA = Color(0xFFD17BFF), glowB = Color(0xFFFF5CA8), onGlow = Color(0xFF0A0510), action = Color(0xFFFF5CA8), onAction = Color(0xFF0A0510), dark = true, glowStrength = 1f,
)

/** The palette for a theme and space. SYSTEM is resolved by [SibTheme]; asked for directly it means Light. */
fun paletteFor(mode: ThemeMode, space: Space = Space.WORK): SibColors = when (mode) {
    ThemeMode.BLACK -> if (space == Space.CLOSE) BlackClose else BlackColors
    ThemeMode.NEON -> if (space == Space.CLOSE) NeonClose else NeonColors
    ThemeMode.DARK -> if (space == Space.CLOSE) CloseDark else DarkColors
    else -> if (space == Space.CLOSE) CloseLight else LightColors
}

val LocalSibColors = staticCompositionLocalOf { LightColors }

/** The theme actually in force (never SYSTEM), so a nested theme can keep the same look. */
val LocalThemeMode = staticCompositionLocalOf { ThemeMode.LIGHT }

/** True when the user asked for no motion in S-IB. The phone's own "remove animations" setting is honoured as well. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Whether animations should play: off when the user chose Reduce motion or the phone's Remove animations is on. */
@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    val reduced = LocalReduceMotion.current
    return remember(reduced) {
        !reduced && runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }.getOrDefault(true)
    }
}

object SibTheme {
    val colors: SibColors @Composable @ReadOnlyComposable get() = LocalSibColors.current
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun sora(weight: Int) = Font(
    R.font.sora, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Sora, everywhere: wide, geometric, with strong numerals that suit big countdowns. */
val Display = FontFamily(sora(400), sora(500), sora(600), sora(700), sora(800))

/** Tabular figures keep countdowns and times aligned so numbers do not jitter as they change. */
private const val TABULAR = "tnum"

object SibType {
    val hero = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 84.sp, lineHeight = 84.sp, letterSpacing = (-2).sp, fontFeatureSettings = TABULAR)
    val display = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp)
    val headline = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 29.sp, letterSpacing = (-0.3).sp)
    val title = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp, letterSpacing = (-0.1).sp)
    val body = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp, fontFeatureSettings = TABULAR)
    val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold)
    val small = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp, fontFeatureSettings = TABULAR)
    val label = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp, fontFeatureSettings = TABULAR)
    val number = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = TABULAR)
}

private fun scheme(c: SibColors): ColorScheme =
    (if (c.dark) darkColorScheme() else lightColorScheme()).copy(
        primary = c.violet, onPrimary = c.paper, secondary = c.vermilion, onSecondary = c.paper,
        background = c.paper, onBackground = c.ink, surface = c.paper, onSurface = c.ink,
        surfaceVariant = c.raised, onSurfaceVariant = c.inkSoft, outline = c.rule, outlineVariant = c.rule,
        error = c.vermilion, onError = c.paper, surfaceContainer = c.raised, surfaceContainerHigh = c.raised,
        surfaceContainerHighest = c.raised, surfaceContainerLow = c.paper, surfaceContainerLowest = c.paper,
        tertiary = c.verdigris, onTertiary = c.paper,
    )

private fun typography(c: SibColors): Typography = Typography(
    displayLarge = SibType.display, displayMedium = SibType.display, displaySmall = SibType.headline,
    headlineLarge = SibType.headline, headlineMedium = SibType.headline, headlineSmall = SibType.title,
    titleLarge = SibType.title, titleMedium = SibType.bodyStrong, titleSmall = SibType.small.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = SibType.body, bodyMedium = SibType.small, bodySmall = SibType.small.copy(fontSize = 13.sp),
    labelLarge = SibType.bodyStrong, labelMedium = SibType.label, labelSmall = SibType.label,
)

@Composable
fun SibTheme(mode: ThemeMode = ThemeMode.SYSTEM, space: Space = Space.WORK, content: @Composable () -> Unit) {
    val resolved = if (mode == ThemeMode.SYSTEM) (if (isSystemInDarkTheme()) ThemeMode.DARK else ThemeMode.LIGHT) else mode
    val colors = paletteFor(resolved, space)
    CompositionLocalProvider(LocalSibColors provides colors, LocalThemeMode provides resolved) {
        MaterialTheme(
            colorScheme = scheme(colors),
            typography = typography(colors),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(4.dp), medium = RoundedCornerShape(6.dp),
                large = RoundedCornerShape(8.dp), extraLarge = RoundedCornerShape(10.dp),
            ),
            content = content,
        )
    }
}
