package com.hsib.sib.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hsib.sib.core.urgency.UrgencyState

/** Springs back when pressed and gives a light tick, so every button feels like a physical thing. */
fun Modifier.bounceClick(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val motion = rememberMotionEnabled()
    val scale by animateFloatAsState(if (pressed && motion) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = 650f), label = "press")
    val haptic = LocalHapticFeedback.current
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

enum class TileIcon { ADD, PASTE, PHOTO, SCAN, LIST, CLOSE, CHAT, PERSON }

/** Icons are drawn here instead of imported, so they stay crisp at any size and follow the palette. */
@Composable
fun TileGlyph(icon: TileIcon, color: Color, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    if (icon == TileIcon.CLOSE) {
        CloseGlyph(modifier, size, color)
        return
    }
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val sw = w * 0.1f
        val line = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (icon) {
            TileIcon.ADD -> {
                drawLine(color, Offset(w / 2, w * 0.2f), Offset(w / 2, w * 0.8f), sw * 1.15f, StrokeCap.Round)
                drawLine(color, Offset(w * 0.2f, w / 2), Offset(w * 0.8f, w / 2), sw * 1.15f, StrokeCap.Round)
            }
            TileIcon.PASTE -> {
                drawRoundRect(color, Offset(w * 0.2f, w * 0.18f), Size(w * 0.6f, w * 0.7f), CornerRadius(w * 0.1f), style = line)
                drawRoundRect(color, Offset(w * 0.36f, w * 0.1f), Size(w * 0.28f, w * 0.16f), CornerRadius(w * 0.05f), style = Stroke(sw))
                drawLine(color, Offset(w * 0.34f, w * 0.52f), Offset(w * 0.66f, w * 0.52f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * 0.34f, w * 0.68f), Offset(w * 0.56f, w * 0.68f), sw, StrokeCap.Round)
            }
            TileIcon.PHOTO -> {
                drawRoundRect(color, Offset(w * 0.12f, w * 0.2f), Size(w * 0.76f, w * 0.6f), CornerRadius(w * 0.1f), style = line)
                drawCircle(color, w * 0.07f, Offset(w * 0.34f, w * 0.38f))
                val hills = Path().apply { moveTo(w * 0.14f, w * 0.72f); lineTo(w * 0.42f, w * 0.5f); lineTo(w * 0.58f, w * 0.64f); lineTo(w * 0.68f, w * 0.56f); lineTo(w * 0.86f, w * 0.72f) }
                drawPath(hills, color, style = line)
            }
            TileIcon.SCAN -> {
                val a = w * 0.14f
                val b = w * 0.86f
                val k = w * 0.26f
                for ((x, y, dx, dy) in listOf(listOf(a, a, 1f, 1f), listOf(b, a, -1f, 1f), listOf(a, b, 1f, -1f), listOf(b, b, -1f, -1f))) {
                    drawLine(color, Offset(x, y), Offset(x + dx * k, y), sw, StrokeCap.Round)
                    drawLine(color, Offset(x, y), Offset(x, y + dy * k), sw, StrokeCap.Round)
                }
                drawLine(color, Offset(w * 0.28f, w / 2), Offset(w * 0.72f, w / 2), sw, StrokeCap.Round)
            }
            TileIcon.LIST -> {
                for (i in 0..2) {
                    val y = w * (0.28f + 0.22f * i)
                    drawCircle(color, sw * 0.7f, Offset(w * 0.2f, y))
                    drawLine(color, Offset(w * 0.36f, y), Offset(w * 0.84f, y), sw, StrokeCap.Round)
                }
            }
            TileIcon.CHAT -> {
                val bubble = Path().apply {
                    moveTo(w * 0.2f, w * 0.25f); lineTo(w * 0.8f, w * 0.25f); lineTo(w * 0.8f, w * 0.62f)
                    lineTo(w * 0.5f, w * 0.62f); lineTo(w * 0.32f, w * 0.8f); lineTo(w * 0.32f, w * 0.62f); lineTo(w * 0.2f, w * 0.62f); close()
                }
                drawPath(bubble, color, style = line)
                drawCircle(color, sw * 0.6f, Offset(w * 0.36f, w * 0.44f))
                drawCircle(color, sw * 0.6f, Offset(w * 0.5f, w * 0.44f))
                drawCircle(color, sw * 0.6f, Offset(w * 0.64f, w * 0.44f))
            }
            TileIcon.PERSON -> {
                drawCircle(color, w * 0.15f, Offset(w / 2, w * 0.33f), style = line)
                val shoulders = Path().apply { moveTo(w * 0.2f, w * 0.84f); cubicTo(w * 0.22f, w * 0.56f, w * 0.78f, w * 0.56f, w * 0.8f, w * 0.84f) }
                drawPath(shoulders, color, style = line)
            }
            TileIcon.CLOSE -> Unit
        }
    }
}

/**
 * A big square action: a coloured icon disc and one short word. The accent version carries the signature gradient and
 * is used for the single most important action on a screen.
 */
@Composable
fun ActionTile(label: String, icon: TileIcon, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Boolean = false) {
    val c = SibTheme.colors
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier.heightIn(min = 112.dp).clip(shape)
            .then(if (accent) Modifier.background(c.action) else Modifier.background(c.raised))
            .bounceClick(onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(if (accent) c.onAction.copy(alpha = 0.14f) else c.violetWash),
                contentAlignment = Alignment.Center,
            ) { TileGlyph(icon, if (accent) c.onAction else c.violet, size = 24.dp) }
            Text(tr(label), style = SibType.title, color = if (accent) c.onAction else c.ink)
        }
    }
}

/** The graded colours of a ring: calm violet to mint, then amber, then coral to hot pink as a date gets close. */
@Composable
fun ringBrush(state: UrgencyState?): Brush {
    val c = SibTheme.colors
    val pink = Color(0xFFFF4D8D)
    return when (state) {
        UrgencyState.OVERDUE, UrgencyState.DUE_TODAY, UrgencyState.DUE_SOON -> Brush.sweepGradient(listOf(c.vermilion, pink, c.vermilion))
        UrgencyState.APPROACHING, UrgencyState.START_SOON -> Brush.sweepGradient(listOf(c.ochre, c.vermilion, c.ochre))
        else -> Brush.sweepGradient(listOf(c.violet, c.verdigris, c.violet))
    }
}

/**
 * The big countdown: a ring that sweeps in as the date gets closer, with the number of days counting up inside it.
 * Empty (days = null) it shows a quiet full ring and a dash.
 */
@Composable
fun HeroRing(days: Long?, state: UrgencyState?, modifier: Modifier = Modifier, size: Dp = 210.dp, brushOverride: Brush? = null) {
    val c = SibTheme.colors
    val motion = rememberMotionEnabled()
    val brush = brushOverride ?: ringBrush(state)
    val target = if (days == null) 0f else (1f - days.coerceIn(0, 30) / 30f).coerceIn(0.04f, 1f)
    var go by remember { mutableStateOf(!motion) }
    LaunchedEffect(Unit) { go = true }
    val sweep by animateFloatAsState(if (go) target else 0f, tween(if (motion) 1300 else 0, easing = FastOutSlowInEasing), label = "heroSweep")
    val shown by animateFloatAsState(if (go) (days ?: 0).toFloat() else 0f, tween(if (motion) 1100 else 0, easing = FastOutSlowInEasing), label = "heroCount")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = this.size.minDimension * 0.075f
            val topLeft = Offset(sw / 2, sw / 2)
            val box = Size(this.size.width - sw, this.size.height - sw)
            drawArc(c.lifted, 0f, 360f, false, topLeft, box, style = Stroke(sw))
            if (days == null) {
                // Nothing due: a full calm ring with a tick.
                drawArc(Brush.sweepGradient(listOf(c.verdigris, c.violet, c.verdigris)), -90f, 360f, false, topLeft, box, style = Stroke(sw, cap = StrokeCap.Round))
                val w = this.size.width
                val tick = Path().apply { moveTo(w * 0.34f, w * 0.52f); lineTo(w * 0.46f, w * 0.64f); lineTo(w * 0.68f, w * 0.38f) }
                drawPath(tick, c.ink, style = Stroke(sw * 1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else if (sweep > 0f) {
                drawArc(brush, -90f, 360f * sweep, false, topLeft, box, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                days == null -> Unit
                days < 0L -> Text("Past due", style = SibType.display.copy(fontSize = 34.sp, lineHeight = 40.sp), color = c.vermilion)
                days == 0L -> Text("Today", style = SibType.display.copy(fontSize = 40.sp, lineHeight = 44.sp), color = c.ink)
                else -> {
                    Text(shown.toInt().coerceAtLeast(0).toString(), style = SibType.hero.copy(fontSize = (size.value * 0.4f).sp, lineHeight = (size.value * 0.4f).sp), color = c.ink)
                    Text(if (days == 1L) "day left" else "days left", style = SibType.bodyStrong, color = c.inkSoft)
                }
            }
        }
    }
}

/** A soft coloured glow behind a hero, so the card has depth instead of being a flat panel. */
fun Modifier.glowBackdrop(a: Color, b: Color, strength: Float = 1f): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(listOf(a.copy(alpha = 0.55f * strength), Color.Transparent), endY = size.height * 0.8f))
    drawCircle(Brush.radialGradient(listOf(b.copy(alpha = 0.40f * strength), Color.Transparent), center = Offset(size.width * 0.9f, 0f), radius = size.width * 0.75f), center = Offset(size.width * 0.9f, 0f), radius = size.width * 0.75f)
}

/** A small count with a word under it, for the strip of "how many" on the home page. */
@Composable
fun StatPill(count: Int, label: String, tone: Tone, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = SibTheme.colors
    val (bg, fg) = when (tone) {
        Tone.ALERT -> c.vermilionWash to c.vermilion
        Tone.WARN -> c.ochreWash to c.ochre
        Tone.GOOD -> c.verdigrisWash to c.verdigris
        Tone.NEUTRAL -> c.violetWash to c.violet
    }
    Row(
        modifier.clip(RoundedCornerShape(50)).background(bg).bounceClick(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics { contentDescription = "$count $label" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(count.toString(), style = SibType.number.copy(fontSize = 20.sp, lineHeight = 24.sp), color = fg)
        Spacer(Modifier.width(8.dp))
        Text(label, style = SibType.bodyStrong, color = fg)
    }
}

/** The one big call to action on a card: a solid pill that springs when pressed. */
@Composable
fun ActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = SibTheme.colors
    Box(
        modifier.heightIn(min = 54.dp).clip(RoundedCornerShape(50))
            .background(if (enabled) c.action else c.lifted)
            .bounceClick(enabled = enabled, onClick = onClick).padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { FitText(text, SibType.bodyStrong.copy(fontSize = 16.sp), if (enabled) c.onAction else c.inkSoft) }
}

/** Space that is meant to be empty. Named so the screens read as layout, not as magic numbers. */
@Composable
fun Gap(size: Dp = 24.dp) = Spacer(Modifier.height(size))

/**
 * An explanation that stays out of the way: a short question the reader can open. This is how the app keeps pages calm
 * while the full reason is still one tap away.
 */
@Composable
fun More(text: String, modifier: Modifier = Modifier, label: String = "Why?") {
    val c = SibTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().animateContentSize()) {
        Text(
            if (open) "Hide" else label, style = SibType.bodyStrong, color = c.violet,
            modifier = Modifier.clip(RoundedCornerShape(50)).bounceClick(role = Role.Button) { open = !open }.padding(vertical = 8.dp),
        )
        if (open) {
            Text(text, style = SibType.body, color = c.ink, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.raised).padding(18.dp))
            Spacer(Modifier.height(8.dp))
        }
    }
}
