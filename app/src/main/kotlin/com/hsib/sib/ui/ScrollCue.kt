package com.hsib.sib.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val CueWords = listOf("more below", "keep going", "psst, more below", "there is more")

/**
 * A faint hint that a page goes on below: a soft fade at the bottom edge, a few words and a small arrow that bobs three times.
 * It shows once the page has been still for a moment, leaves while you scroll, and is gone at the end of the page. It plays in
 * short bursts, never in a loop, so an idle screen costs nothing. It is only drawn: it takes no touch and a screen reader
 * does not see it. With Reduce motion it appears without moving. Put it before verticalScroll so it stays on the viewport.
 */
fun Modifier.scrollCue(state: ScrollState): Modifier = composed {
    val c = SibTheme.colors
    val motion = rememberMotionEnabled()
    val measurer = rememberTextMeasurer()
    val style = SibType.small.copy(fontSize = 12.sp, letterSpacing = 0.6.sp)
    val words = remember(style) { CueWords.map { measurer.measure(it, style) } }
    val shown = remember { Animatable(0f) }
    val nudge = remember { Animatable(0f) }
    val round = remember { mutableIntStateOf(0) }
    val more = state.canScrollForward
    val moving = state.isScrollInProgress
    LaunchedEffect(more, moving) {
        nudge.snapTo(0f)
        if (!more || moving) {
            if (moving && shown.value > 0f) round.intValue++
            if (motion) shown.animateTo(0f, tween(220)) else shown.snapTo(0f)
            return@LaunchedEffect
        }
        delay(if (round.intValue == 0) 900L else 1300L)
        if (motion) shown.animateTo(1f, tween(500)) else shown.snapTo(1f)
        if (motion) repeat(3) {
            nudge.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
            nudge.animateTo(0f, tween(480, easing = FastOutSlowInEasing))
        }
    }
    drawWithContent {
        drawContent()
        val a = shown.value
        if (a <= 0.01f) return@drawWithContent
        val t = nudge.value
        val fade = 96.dp.toPx()
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent, 0.4f to c.paper.copy(alpha = 0.75f * a), 1f to c.paper.copy(alpha = 0.95f * a),
                startY = size.height - fade, endY = size.height,
            ),
            topLeft = Offset(0f, size.height - fade), size = Size(size.width, fade),
        )
        val word = words[round.intValue % words.size]
        drawText(word, c.inkSoft, Offset((size.width - word.size.width) / 2f, size.height - 46.dp.toPx()), alpha = a * (0.55f + 0.3f * t))
        val cx = size.width / 2f
        val cy = size.height - 20.dp.toPx() + 6.dp.toPx() * t
        val arrow = Path().apply { moveTo(cx - 7.dp.toPx(), cy - 2.dp.toPx()); lineTo(cx, cy + 2.dp.toPx()); lineTo(cx + 7.dp.toPx(), cy - 2.dp.toPx()) }
        drawPath(arrow, c.violet.copy(alpha = 0.85f * a), style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** verticalScroll for a whole page, with the hint that there is more below. */
fun Modifier.cuedVerticalScroll(): Modifier = composed {
    val state = rememberScrollState()
    scrollCue(state).verticalScroll(state)
}
