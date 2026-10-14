package com.hsib.sib.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private const val DURATION_MS = 2800

private val Expo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val Smooth = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

/** Which word of "Start It Before" lights each letter of S-IB; the hyphen has none. */
private val LetterWord = intArrayOf(0, -1, 1, 2)

/** Position of [t] inside the window [from, to], clamped to 0..1. */
private fun seg(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)

/** Overshoots slightly before settling, so a dropped marker feels like it has weight. */
private fun backOut(x: Float): Float {
    val c1 = 1.70158f
    val p = x - 1f
    return 1f + (c1 + 1f) * p * p * p + c1 * p * p
}

/**
 * The opening: a time axis draws itself, the deadline lands on it, a start marker travels to the safe day before it
 * and a dashed arc joins the two, then the name resolves with "Start It Before" lighting up its own initials. The whole
 * thing is one clock (progress 0 to 1) so every part stays in step and a tap can finish it at once.
 */
@Composable
fun IntroScreen(onFinished: () -> Unit) {
    val motion = rememberMotionEnabled()
    val progress = remember { Animatable(if (motion) 0f else 1f) }
    val scope = rememberCoroutineScope()
    var finished by remember { mutableStateOf(false) }
    val finish = { if (!finished) { finished = true; onFinished() } }

    LaunchedEffect(motion) {
        if (motion) progress.animateTo(1f, tween(DURATION_MS, easing = LinearEasing))
        delay(if (motion) 450 else 800)
        finish()
    }

    IntroScene(
        progress.value,
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
            scope.launch { progress.animateTo(1f, tween(160)); finish() }
        }.semantics { contentDescription = "S-IB. Start It Before. Tap to continue." },
    )
}

/** One frame of the intro. Pure in [t] (0 to 1), which is what lets a screenshot test render any moment of it. */
@Composable
fun IntroScene(t: Float, modifier: Modifier = Modifier) {
    val c = SibTheme.colors
    val measurer = rememberTextMeasurer()
    val startLabel = remember(c) { measurer.measure("START", SibType.label.copy(color = c.inkSoft)) }
    val dueLabel = remember(c) { measurer.measure("DUE", SibType.label.copy(color = c.glowB)) }

    val glowIn = Expo.transform(seg(t, 0.25f, 0.85f))
    BoxWithConstraints(
        modifier.fillMaxSize().background(c.paper).drawBehind {
            // A soft violet glow behind the name that grows in as the title resolves.
            drawCircle(
                Brush.radialGradient(listOf(c.glowA.copy(alpha = 0.34f * glowIn * c.glowStrength), Color.Transparent), center = Offset(size.width / 2, size.height * 0.28f), radius = size.width * 0.9f),
                center = Offset(size.width / 2, size.height * 0.28f), radius = size.width * 0.9f,
            )
            drawCircle(
                Brush.radialGradient(listOf(c.glowB.copy(alpha = 0.18f * glowIn * c.glowStrength), Color.Transparent), center = Offset(size.width * 0.85f, size.height * 0.62f), radius = size.width * 0.7f),
                center = Offset(size.width * 0.85f, size.height * 0.62f), radius = size.width * 0.7f,
            )
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val y0 = h * 0.56f
            val x0 = w * 0.10f
            val x1 = w * 0.90f
            val stroke = 2.dp.toPx()
            val markerY = y0 - 46.dp.toPx()
            val xs = x0 + (x1 - x0) * 0.30f
            val xd = x0 + (x1 - x0) * 0.82f

            // 1. The axis draws itself, and each tick rises as the line passes it.
            val axis = Smooth.transform(seg(t, 0f, 0.30f))
            drawLine(c.glow, Offset(x0, y0), Offset(x0 + (x1 - x0) * axis, y0), stroke * 1.4f, StrokeCap.Round)
            val ticks = 24
            for (i in 0..ticks) {
                val f = i / ticks.toFloat()
                val grow = Expo.transform(seg(axis, f, f + 0.1f))
                val major = i % 6 == 0
                val len = (if (major) 14.dp else 7.dp).toPx() * grow
                drawLine(if (major) c.inkSoft else c.rule, Offset(x0 + (x1 - x0) * f, y0), Offset(x0 + (x1 - x0) * f, y0 + len), if (major) stroke else 1.5.dp.toPx(), StrokeCap.Round)
            }

            // 2. The deadline lands, then sends out a ring.
            val drop = seg(t, 0.24f, 0.44f)
            if (drop > 0f) {
                val r = 9.dp.toPx() * backOut(drop)
                drawLine(c.glowB, Offset(xd, y0), Offset(xd, y0 - (y0 - markerY) * Expo.transform(drop)), stroke, StrokeCap.Round)
                drawCircle(c.glowB, r, Offset(xd, markerY))
                val ring = seg(t, 0.42f, 0.72f)
                if (ring > 0f && ring < 1f) {
                    drawCircle(c.glowB.copy(alpha = (1f - ring) * 0.55f), 9.dp.toPx() + 30.dp.toPx() * Expo.transform(ring), Offset(xd, markerY), style = Stroke(stroke))
                }
            }

            // 3. The start marker travels to the safe day, leaving a faint trail.
            val appear = seg(t, 0.34f, 0.42f)
            if (appear > 0f) {
                val travel = Smooth.transform(seg(t, 0.38f, 0.64f))
                val x = x0 + (xs - x0) * travel
                drawLine(c.rule.copy(alpha = appear), Offset(x0, markerY), Offset(x, markerY), stroke, StrokeCap.Round)
                drawLine(c.glowA.copy(alpha = appear), Offset(x, markerY), Offset(x, y0), stroke, StrokeCap.Round)
                drawCircle(c.paper, 9.dp.toPx(), Offset(x, markerY))
                drawCircle(c.glowA.copy(alpha = appear), 9.dp.toPx(), Offset(x, markerY), style = Stroke(stroke * 1.4f))
            }

            // 4. A dashed arc joins them: the time you have before the date.
            val arc = Smooth.transform(seg(t, 0.62f, 0.84f))
            if (arc > 0f) {
                val path = Path().apply {
                    moveTo(xs, markerY - 12.dp.toPx())
                    quadraticTo((xs + xd) / 2f, markerY - 122.dp.toPx(), xd, markerY - 12.dp.toPx())
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val part = Path()
                measure.getSegment(0f, measure.length * arc, part, true)
                drawPath(part, Brush.linearGradient(listOf(c.glowA, c.glowB), start = Offset(xs, 0f), end = Offset(xd, 0f)), style = Stroke(stroke * 1.3f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 9.dp.toPx()))))
            }

            val label = seg(t, 0.64f, 0.78f)
            if (label > 0f) {
                drawText(startLabel, topLeft = Offset(xs - startLabel.size.width / 2f, y0 + 24.dp.toPx()), alpha = label)
                drawText(dueLabel, topLeft = Offset(xd - dueLabel.size.width / 2f, y0 + 24.dp.toPx()), alpha = label)
            }
        }

        // 5. The name resolves letter by letter; each initial pulses as its word appears below.
        Row(Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.17f)) {
            "S-IB".forEachIndexed { i, ch ->
                val appear = Expo.transform(seg(t, 0.50f + 0.06f * i, 0.74f + 0.06f * i))
                val word = LetterWord[i]
                val pulse = if (word >= 0) sin(PI.toFloat() * seg(t, 0.74f + 0.07f * word, 0.90f + 0.07f * word)) else 0f
                Text(
                    ch.toString(), style = SibType.hero.copy(fontSize = 80.sp, lineHeight = 84.sp, letterSpacing = (-2).sp).let { if (ch == '-') it.copy(brush = c.glow) else it }, color = if (ch == '-') Color.Unspecified else c.ink,
                    modifier = Modifier.graphicsLayer {
                        alpha = appear
                        translationY = (1f - appear) * 26.dp.toPx()
                        val s = 1f + 0.12f * pulse
                        scaleX = s
                        scaleY = s
                    },
                )
            }
        }

        Row(Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.72f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Start", "It", "Before").forEachIndexed { i, word ->
                val appear = Expo.transform(seg(t, 0.74f + 0.07f * i, 0.90f + 0.07f * i))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = c.glowB, fontWeight = FontWeight.ExtraBold)) { append(word.take(1)) }
                        append(word.drop(1))
                    },
                    style = SibType.title.copy(fontSize = 19.sp), color = c.ink,
                    modifier = Modifier.graphicsLayer { alpha = appear; translationY = (1f - appear) * 12.dp.toPx() },
                )
            }
        }
    }
}
