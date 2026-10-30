package com.hsib.sib.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hsib.sib.core.urgency.UrgencyState
import com.hsib.sib.domain.Fmt
import com.hsib.sib.domain.ObligationView
import java.time.Instant

@Composable
fun stateColor(state: UrgencyState): Color {
    val c = SibTheme.colors
    return when (state) {
        UrgencyState.REVIEW, UrgencyState.PLANNED, UrgencyState.MOVED -> c.violet
        UrgencyState.START_SOON, UrgencyState.APPROACHING -> c.ochre
        UrgencyState.IN_PROGRESS, UrgencyState.DONE -> c.verdigris
        UrgencyState.DUE_SOON, UrgencyState.DUE_TODAY, UrgencyState.OVERDUE -> c.vermilion
    }
}

/**
 * One glyph per state. The shape alone says what state a deadline is in (open ring, half ring, arc, tick ring, solid
 * disc, broken ring, tick, arrow ring); colour only reinforces it.
 */
@Composable
fun UrgencyGlyph(state: UrgencyState, modifier: Modifier = Modifier, size: Dp = 22.dp, progress: Float = 0f) {
    val color = stateColor(state)
    Canvas(modifier.size(size).semantics { contentDescription = state.label }) {
        val sw = this.size.minDimension * 0.11f
        val r = (this.size.minDimension - sw) / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val box = Size(r * 2, r * 2)
        val tl = Offset(c.x - r, c.y - r)
        fun ring() = drawCircle(color, r, c, style = Stroke(sw))
        when (state) {
            UrgencyState.REVIEW -> drawCircle(color, r, c, style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(sw * 1.7f, sw * 1.5f))))
            UrgencyState.PLANNED -> ring()
            UrgencyState.START_SOON -> { ring(); drawArc(color, 90f, 180f, true, tl, box) }
            UrgencyState.IN_PROGRESS -> { ring(); drawArc(color, -90f, 360f * progress.coerceIn(0.1f, 1f), true, tl, box) }
            UrgencyState.APPROACHING -> { ring(); drawArc(color, -90f, 270f, true, tl, box) }
            UrgencyState.DUE_SOON -> {
                ring()
                drawLine(color, Offset(c.x, c.y), Offset(c.x, c.y - r * 0.8f), strokeWidth = sw * 1.3f, cap = StrokeCap.Round)
                drawLine(color, Offset(c.x, c.y), Offset(c.x + r * 0.5f, c.y + r * 0.3f), strokeWidth = sw * 1.3f, cap = StrokeCap.Round)
            }
            UrgencyState.DUE_TODAY -> drawCircle(color, r + sw / 2f, c)
            UrgencyState.OVERDUE -> {
                val gap = 28f
                for (i in 0 until 3) drawArc(color, -90f + i * 120f + gap / 2, 120f - gap, false, tl, box, style = Stroke(sw, cap = StrokeCap.Butt))
            }
            UrgencyState.DONE -> {
                val p = Path().apply {
                    moveTo(c.x - r * 0.62f, c.y + r * 0.02f); lineTo(c.x - r * 0.15f, c.y + r * 0.5f); lineTo(c.x + r * 0.7f, c.y - r * 0.5f)
                }
                drawPath(p, color, style = Stroke(sw * 1.3f, cap = StrokeCap.Round))
            }
            UrgencyState.MOVED -> {
                ring()
                drawLine(color, Offset(c.x - r * 0.45f, c.y), Offset(c.x + r * 0.45f, c.y), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(c.x + r * 0.05f, c.y - r * 0.4f), Offset(c.x + r * 0.45f, c.y), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(c.x + r * 0.05f, c.y + r * 0.4f), Offset(c.x + r * 0.45f, c.y), strokeWidth = sw, cap = StrokeCap.Round)
            }
        }
    }
}

fun pressureWords(p: Double) = when {
    p <= 0.0 -> "Nothing left to do"
    p < 0.35 -> "Plenty of room"
    p < 0.6 -> "Comfortable"
    p < 0.85 -> "Getting tight"
    p < 1.0 -> "Very tight"
    else -> "Not enough time at your usual pace"
}

/** Work remaining against time remaining. Always paired with words, never colour alone. */
@Composable
fun PressureBar(pressure: Double, state: UrgencyState, modifier: Modifier = Modifier) {
    val c = SibTheme.colors
    val fill = stateColor(state)
    Canvas(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
        drawRect(c.lifted)
        val w = (size.width * pressure.coerceIn(0.0, 1.0).toFloat()).let { if (pressure > 0 && it < 6f) 6f else it }
        drawRect(fill, size = Size(w, size.height))
        if (pressure >= 1.0) {
            var x = size.width - size.height * 6
            while (x < size.width) {
                drawLine(c.paper, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.5f)
                x += size.height * 1.4f
            }
        }
    }
}

/** Rows are separated by space, not by lines. Kept under the old name so existing screens stay valid. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxWidth().height(8.dp))
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, count: Int? = null) {
    val c = SibTheme.colors
    Row(modifier.fillMaxWidth().padding(top = 36.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = SibType.title, color = c.ink)
        if (count != null) {
            Spacer(Modifier.width(10.dp))
            Text(count.toString(), style = SibType.label, color = c.violet, modifier = Modifier.clip(RoundedCornerShape(50)).background(c.violetWash).padding(horizontal = 10.dp, vertical = 3.dp))
        }
    }
}

enum class Tone { NEUTRAL, WARN, ALERT, GOOD }

@Composable
fun Chip(text: String, tone: Tone = Tone.NEUTRAL, modifier: Modifier = Modifier) {
    val c = SibTheme.colors
    val (bg, fg) = when (tone) {
        Tone.NEUTRAL -> c.lifted to c.ink
        Tone.WARN -> c.ochreWash to c.ochre
        Tone.ALERT -> c.vermilionWash to c.vermilion
        Tone.GOOD -> c.verdigrisWash to c.verdigris
    }
    Text(text, style = SibType.label, color = fg, modifier = modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 5.dp))
}

enum class ButtonKind { PRIMARY, QUIET, DANGER, TEXT }

@Composable
fun SibButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: ButtonKind = ButtonKind.PRIMARY, enabled: Boolean = true, compact: Boolean = false) {
    val c = SibTheme.colors
    val shape = RoundedCornerShape(50)
    val (fill, fg) = when {
        !enabled -> Brush.linearGradient(listOf(c.lifted, c.lifted)) to c.inkSoft
        kind == ButtonKind.PRIMARY -> SolidColor(c.action) to c.onAction
        kind == ButtonKind.QUIET -> Brush.linearGradient(listOf(c.lifted, c.lifted)) to c.ink
        kind == ButtonKind.DANGER -> Brush.linearGradient(listOf(c.vermilionWash, c.vermilionWash)) to c.vermilion
        else -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)) to c.violet
    }
    Box(
        modifier.heightIn(min = if (compact) 46.dp else 52.dp).clip(shape).background(fill).bounceClick(enabled = enabled, onClick = onClick).padding(horizontal = if (compact) 12.dp else if (kind == ButtonKind.TEXT) 8.dp else 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = SibType.bodyStrong, color = fg, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboard: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    onDone: (() -> Unit)? = null,
    mono: Boolean = false,
) {
    val c = SibTheme.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.fillMaxWidth()) {
        Text(label, style = SibType.bodyStrong, color = c.ink)
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = value, onValueChange = onChange,
            textStyle = (if (mono) SibType.body.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) else SibType.body).copy(color = c.ink),
            cursorBrush = SolidColor(c.violet),
            singleLine = singleLine, minLines = minLines,
            keyboardOptions = keyboard,
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).onFocusChanged { focused = it.isFocused }.semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(
                    Modifier.clip(shape).background(c.lifted).border(if (focused) 2.dp else 1.dp, if (focused) c.violet else c.rule, shape).padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = SibType.body, color = c.inkSoft)
                    inner()
                }
            },
        )
    }
}

/** A row of text choices with a rule under the selected one. */
/**
 * A choice among a few options. Short sets show as one segmented pill. Sets that would not fit without cutting a word
 * (five options, or long labels) show as chips that wrap onto more lines, so a label is never shortened to "30...".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Choice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = SibTheme.colors
    val fitsInOneRow = options.size <= 4 && options.sumOf { it.second.length } <= 24 && androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.15f
    if (!fitsInOneRow) {
        FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((value, label) in options) SelectChip(label, value == selected, { onSelect(value) })
        }
        return
    }
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(c.lifted).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((value, label) in options) {
            val on = value == selected
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(50))
                    .then(if (on) Modifier.background(c.action) else Modifier)
                    .bounceClick(role = Role.RadioButton) { onSelect(value) }
                    .semantics { stateDescription = if (on) "Selected" else "Not selected" }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = SibType.bodyStrong.copy(fontSize = 14.sp), color = if (on) c.onAction else c.ink, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** The effort choices used wherever the amount of work is asked, spelled out so nothing is abbreviated into a puzzle. */
val EffortChoices = listOf(30 to "30 min", 120 to "2 hours", 240 to "4 hours", 480 to "1 day", 960 to "2 days")

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = SibTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 60.dp)
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = SibType.bodyStrong, color = if (enabled) c.ink else c.inkSoft)
            if (subtitle != null) Text(subtitle, style = SibType.small, color = c.inkSoft)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onGlow, checkedTrackColor = c.violet, checkedBorderColor = c.violet,
                uncheckedThumbColor = c.inkSoft, uncheckedTrackColor = c.lifted, uncheckedBorderColor = c.rule,
            ),
        )
    }
}

/** A row that opens another layer: a soft card with a chevron. */
@Composable
fun NavRow(title: String, subtitle: String?, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: String? = null) {
    val c = SibTheme.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.raised).bounceClick(onClick = onClick).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = SibType.title, color = c.ink)
            if (subtitle != null) Text(subtitle, style = SibType.small, color = c.inkSoft)
        }
        if (trailing != null) { Text(trailing, style = SibType.bodyStrong, color = c.violet); Spacer(Modifier.width(8.dp)) }
        Canvas(Modifier.size(10.dp, 14.dp)) {
            val p = Path().apply { moveTo(1f, 1f); lineTo(size.width - 1f, size.height / 2); lineTo(1f, size.height - 1f) }
            drawPath(p, c.violet, style = Stroke(2.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun BackArrow(onClick: () -> Unit) {
    val c = SibTheme.colors
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(c.lifted).bounceClick(onClick = onClick).semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val p = Path().apply { moveTo(size.width * 0.64f, 2f); lineTo(size.width * 0.2f, size.height / 2); lineTo(size.width * 0.64f, size.height - 2f) }
            drawPath(p, c.ink, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A full screen with the paper background, a back arrow and a serif title. */
/** A full screen on the night base, a round back button and a clear title. Generous side padding keeps it calm. */
@Composable
fun Page(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    scroll: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = SibTheme.colors
    Column(
        modifier.fillMaxSize().background(c.paper)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) BackArrow(onBack) else Spacer(Modifier.width(8.dp))
            Text(title, style = SibType.headline, color = c.ink, modifier = Modifier.weight(1f).padding(start = 12.dp))
            action?.invoke()
        }
        Column(
            Modifier.weight(1f).fillMaxWidth()
                .then(if (scroll) Modifier.cuedVerticalScroll() else Modifier)
                .padding(horizontal = 24.dp),
            content = content,
        )
    }
}

/** The one list card used for deadlines everywhere: shape-coded state, title, when, and how tight it is. */
@Composable
fun ObligationRow(v: ObligationView, fmt: Fmt, today: java.time.LocalDate, zone: java.time.ZoneId, now: Instant, onClick: () -> Unit, modifier: Modifier = Modifier, shared: Boolean = false) {
    val c = SibTheme.colors
    val o = v.o
    val fog = o.fog
    val due = o.due
    val dueText = when {
        fog != null -> "Somewhere ${fmt.range(fog.earliest, fog.latest)}"
        due != null -> {
            val dateOnly = due.precision == com.hsib.sib.core.model.Precision.DATE_ONLY
            fmt.due(if (dateOnly) due.local else due.instant(zone).atZone(zone).toLocalDateTime(), dateOnly)
        }
        else -> "No date"
    }
    val relative = v.dueInstant?.let { fmt.daysFrom(today, it.atZone(zone).toLocalDate()) }
    val description = "${o.title}. ${v.urgency.label}. $dueText. ${pressureWords(v.pressure)}."
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised).bounceClick(onClick = onClick).padding(18.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.Top,
    ) {
        UrgencyGlyph(v.urgency, Modifier.padding(top = 2.dp), size = 26.dp, progress = o.effort?.let { if (it.expectedMin > 0) o.progressMin.toFloat() / it.expectedMin else 0f } ?: 0f)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(o.title, style = SibType.title, color = if (v.urgency == UrgencyState.DONE) c.inkSoft else c.ink)
            Spacer(Modifier.height(2.dp))
            Text(listOfNotNull(relative, dueText).joinToString("  ·  "), style = SibType.small, color = c.inkSoft)
            if (v.tentative) { Spacer(Modifier.height(8.dp)); Chip("Date not confirmed", Tone.WARN) }
            if (shared) { Spacer(Modifier.height(8.dp)); Chip("Shared") }
            if (v.urgency.rank in 1..6 && o.effort != null && v.pressure > 0) {
                Spacer(Modifier.height(12.dp))
                PressureBar(v.pressure, v.urgency)
            }
        }
    }
}

val ScreenPadding = PaddingValues(horizontal = 20.dp)
