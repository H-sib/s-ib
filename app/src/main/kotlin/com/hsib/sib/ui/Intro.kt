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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private const val DURATION_MS = 3000

private val Expo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val Smooth = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

/** Which word of "Start It Before" lights each letter of H-sib: the s, the i and the b are its initials. */
private val LetterWord = intArrayOf(-1, -1, 0, 1, 2)

/** Position of [t] inside the window [from, to], clamped to 0..1. */
private fun seg(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun mix(a: Float, b: Float, f: Float): Float = a + (b - a) * f

/** Overshoots slightly before settling, so something that lands feels like it has weight. */
private fun backOut(x: Float): Float {
    val c1 = 1.70158f
    val p = x - 1f
    return 1f + (c1 + 1f) * p * p * p + c1 * p * p
}

/**
 * The opening. A row of small dots, the days, rolls in like a wave. Then the dots become the H-sib mark: two of them grow into
 * the bars, three glide up into the rising diagonal between them (the time you have), and one arcs over and lands on top as the
 * date, with a soft squash and a ring. The name and "Start It Before" rise in underneath, the s, the i and the b lighting up.
 * The whole thing is one clock (progress 0 to 1) so every part stays in step and a tap can finish it at once.
 */
@Composable
fun IntroScreen(durationMs: Int = DURATION_MS, onFinished: () -> Unit) {
    val motion = rememberMotionEnabled()
    val progress = remember { Animatable(if (motion) 0f else 1f) }
    val scope = rememberCoroutineScope()
    var finished by remember { mutableStateOf(false) }
    val finish = { if (!finished) { finished = true; onFinished() } }

    LaunchedEffect(motion) {
        if (motion) progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
        delay(if (motion) 450 else 800)
        finish()
    }

    IntroScene(
        progress.value,
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
            scope.launch { progress.animateTo(1f, tween(160)); finish() }
        }.semantics { contentDescription = "H-sib Reminder. Start it before. Tap to continue." },
    )
}

/** One frame of the intro. Pure in [t] (0 to 1), which is what lets a screenshot test render any moment of it. */
@Composable
fun IntroScene(t: Float, modifier: Modifier = Modifier) {
    val c = SibTheme.colors

    val glowIn = Expo.transform(seg(t, 0.0f, 0.2f))
    BoxWithConstraints(modifier.fillMaxSize().background(c.paper)) {
        // The glow is painted once into its own layer and only faded in. Repainting full-screen gradients every frame cost most
        // of the frame time on a weak GPU (measured: 81 percent janky frames on the reference phone).
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = glowIn }.drawBehind {
                val centre = Offset(size.width / 2, size.height * 0.30f)
                drawCircle(Brush.radialGradient(listOf(c.glowA.copy(alpha = 0.34f * c.glowStrength), Color.Transparent), center = centre, radius = size.width * 0.62f), center = centre, radius = size.width * 0.62f)
                val second = Offset(size.width * 0.85f, size.height * 0.62f)
                drawCircle(Brush.radialGradient(listOf(c.glowB.copy(alpha = 0.18f * c.glowStrength), Color.Transparent), center = second, radius = size.width * 0.45f), center = second, radius = size.width * 0.45f)
            },
        )
        Canvas(Modifier.fillMaxSize()) {
            // The mark is drawn on the same 108 point grid as the app icon, so the dots land exactly where its parts are.
            val u = size.width * 0.44f / 108f
            val ox = size.width / 2f - 54f * u
            val oy = size.height * 0.30f - 47f * u
            fun px(x: Float) = ox + x * u
            fun py(y: Float) = oy + y * u
            val r0 = 2.6f * u
            val lineY = 56f

            // How far dot i has rolled in, and how high the wave lifts it as it passes.
            fun rollIn(i: Int) = backOut(seg(t, 0.04f + 0.018f * i, 0.14f + 0.018f * i))
            fun wave(i: Int) = sin(PI.toFloat() * seg(t, 0.14f + 0.014f * i, 0.30f + 0.014f * i)) * 3.2f
            fun dotX(i: Int) = 38f + (i - 2) * 8f

            // Dots 0, 1 and 8 flow into the bars and are gone.
            for (i in intArrayOf(0, 1, 8)) {
                val e = Expo.transform(seg(t, 0.34f + 0.02f * minOf(i, 2), 0.56f))
                val r = r0 * rollIn(i) * (1f - e)
                if (r > 0.3f) drawCircle(c.inkSoft.copy(alpha = 1f - e * 0.6f), r, Offset(px(mix(dotX(i), if (i == 8) 70f else 38f, e)), py(lineY - wave(i))))
            }

            // Dots 2 and 6 stretch into the two bars. The right one takes the gradient.
            val grow = Expo.transform(seg(t, 0.34f, 0.60f))
            for (side in 0..1) {
                val i = if (side == 0) 2 else 6
                val ap = rollIn(i)
                val bw = mix(2f * r0 * ap, 8f * u, grow)
                val bh = mix(2f * r0 * ap, 40f * u, grow)
                val topLeft = Offset(px(dotX(i)) - bw / 2f, py(lineY - (if (grow < 1f) wave(i) else 0f)) - bh / 2f)
                val corner = CornerRadius(bw / 2f)
                if (side == 0) {
                    drawRoundRect(lerp(c.inkSoft, c.ink, grow), topLeft, Size(bw, bh), corner)
                } else {
                    drawRoundRect(lerp(c.inkSoft, c.glowB, grow), topLeft, Size(bw, bh), corner)
                    drawRoundRect(Brush.verticalGradient(listOf(c.glowB, c.glowA), startY = py(36f), endY = py(76f)), topLeft, Size(bw, bh), corner, alpha = grow)
                }
            }

            // Dots 3, 4 and 5 glide up into the diagonal between the bars, growing as they climb.
            val targetX = floatArrayOf(49.2f, 54.8f, 60.9f)
            val targetY = floatArrayOf(57f, 52.5f, 48.8f)
            val targetR = floatArrayOf(2.2f, 2.8f, 3.4f)
            for (k in 0..2) {
                val i = 3 + k
                val e = Smooth.transform(seg(t, 0.42f + 0.03f * k, 0.68f + 0.03f * k))
                val colour = lerp(c.inkSoft, lerp(c.glowA, c.glowB, k / 2f), e)
                drawCircle(colour, mix(r0 * rollIn(i), targetR[k] * u, e), Offset(px(mix(dotX(i), targetX[k], e)), py(mix(lineY, targetY[k], e) - (if (e < 1f) wave(i) else 0f))))
            }

            // Dot 7 is the date. It arcs up and over and lands on the right bar with a soft squash, then sends out a ring.
            val fly = Smooth.transform(seg(t, 0.38f, 0.66f))
            val my = mix(lineY, 23.5f, fly) - 16f * sin(PI.toFloat() * fly) - (if (fly < 1f) wave(7) * (1f - fly) else 0f)
            val mr = mix(r0 * rollIn(7), 5f * u, fly)
            val squash = sin(PI.toFloat() * seg(t, 0.64f, 0.76f)) * 0.22f
            drawOval(
                lerp(c.inkSoft, c.ink, fly),
                Offset(px(mix(dotX(7), 70f, fly)) - mr * (1f + squash), py(my) - mr * (1f - squash)),
                Size(2f * mr * (1f + squash), 2f * mr * (1f - squash)),
            )
            val ring = seg(t, 0.66f, 0.94f)
            if (ring > 0f && ring < 1f) {
                drawCircle(c.glowB.copy(alpha = (1f - ring) * 0.5f), 5f * u + 26f * u * Expo.transform(ring), Offset(px(70f), py(23.5f)), style = Stroke(2.dp.toPx()))
            }
        }

        // The name resolves letter by letter under the mark; each initial pulses as its word appears below.
        Row(Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.43f), verticalAlignment = Alignment.Bottom) {
            "H-sib".forEachIndexed { i, ch ->
                val appear = Expo.transform(seg(t, 0.62f + 0.05f * i, 0.86f + 0.05f * i))
                val word = LetterWord[i]
                val pulse = if (word >= 0) sin(PI.toFloat() * seg(t, 0.80f + 0.06f * word, 0.96f + 0.04f * word)) else 0f
                val star = i >= 2
                Text(
                    ch.toString(),
                    style = SibType.hero.copy(
                        fontSize = 76.sp, lineHeight = 80.sp, letterSpacing = (-2).sp,
                        fontWeight = if (star) FontWeight.ExtraBold else FontWeight.Medium,
                    ),
                    color = if (star) lerp(c.glowA, c.glowB, (i - 2) / 2f) else c.ink.copy(alpha = 0.45f),
                    modifier = Modifier.graphicsLayer {
                        alpha = appear
                        translationY = (1f - appear) * 24.dp.toPx()
                        val s = 1f + 0.12f * pulse
                        scaleX = s
                        scaleY = s
                    },
                )
            }
        }

        val sub = Expo.transform(seg(t, 0.78f, 0.94f))
        Text(
            "REMINDER", style = SibType.label.copy(letterSpacing = 6.sp, fontSize = 14.sp), color = c.inkSoft,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.43f + 96.dp).graphicsLayer { alpha = sub },
        )

        Row(Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.78f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Start", "It", "Before").forEachIndexed { i, word ->
                val appear = Expo.transform(seg(t, 0.80f + 0.06f * i, 0.95f + 0.04f * i))
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
