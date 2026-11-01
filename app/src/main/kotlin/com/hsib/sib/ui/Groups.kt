package com.hsib.sib.ui

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * How settings and information pages are laid out: a small title, then one rounded card holding a list of rows with thin
 * dividers between them. No loose boxes, no stacks of separate cards, so a page reads as a few clear groups.
 */
@Composable
fun Group(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = SibTheme.colors
    Column(modifier.fillMaxWidth().padding(top = 28.dp)) {
        if (title != null) Text(tr(title), style = SibType.label.copy(letterSpacing = 0.8.sp), color = c.inkSoft, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised), content = content)
    }
}

/** A group whose title is a tappable header. Only the title shows until it is opened, so long pages stay short. */
@Composable
fun CollapsibleGroup(title: String, icon: SibIcon? = iconFor(title), content: @Composable ColumnScope.() -> Unit) {
    val c = SibTheme.colors
    var open by rememberSaveable(title, "group") { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 20.dp).clip(RoundedCornerShape(24.dp)).background(c.raised).animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClickLabel = if (open) "Close" else "Open") { open = !open }.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) { IconBadge(icon, size = 40.dp); Spacer(Modifier.width(14.dp)) }
            Text(tr(title), style = SibType.title, color = c.ink, modifier = Modifier.weight(1f))
            Canvas(Modifier.size(14.dp, 10.dp)) {
                val p = Path().apply {
                    if (open) { moveTo(1f, size.height - 1f); lineTo(size.width / 2, 1f); lineTo(size.width - 1f, size.height - 1f) }
                    else { moveTo(1f, 1f); lineTo(size.width / 2, size.height - 1f); lineTo(size.width - 1f, 1f) }
                }
                drawPath(p, c.violet, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        if (open) { GroupDivider(); Column(Modifier.fillMaxWidth(), content = content) }
    }
}

/** The story of the name as three big letters and two short lines, not a paragraph. */
@Composable
fun NameStory() {
    val c = SibTheme.colors
    var open by rememberSaveable("nameStory") { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize().clickable(role = Role.Button) { open = !open }.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(SibIcon.SPARK, size = 36.dp)
            Spacer(Modifier.width(14.dp))
            Text("The name", style = SibType.bodyStrong.copy(fontSize = 16.sp), color = c.ink, modifier = Modifier.weight(1f))
            Canvas(Modifier.size(14.dp, 10.dp)) {
                val p = Path().apply {
                    if (open) { moveTo(1f, size.height - 1f); lineTo(size.width / 2, 1f); lineTo(size.width - 1f, size.height - 1f) }
                    else { moveTo(1f, 1f); lineTo(size.width / 2, size.height - 1f); lineTo(size.width - 1f, 1f) }
                }
                drawPath(p, c.violet, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        if (open) {
            Spacer(Modifier.height(14.dp))
            listOf(Triple("S", "tart", c.violet), Triple("I", "t", c.verdigris), Triple("B", "efore", c.vermilion)).forEach { (letter, rest, tone) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(18.dp)).background(tone.copy(alpha = 0.12f)).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(letter, style = SibType.display, color = tone)
                    Text(rest, style = SibType.headline, color = c.ink)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                SibIconView(SibIcon.USER, size = 22.dp, tint = c.violet)
                Spacer(Modifier.width(12.dp))
                Text("Hasib, the maker's name, ends in the same sound. It can mean one who counts.", style = SibType.body, color = c.inkSoft)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                SibIconView(SibIcon.HEART, size = 22.dp, tint = c.vermilion)
                Spacer(Modifier.width(12.dp))
                Text("A sib is a brother or sister: the spirit of Close ones.", style = SibType.body, color = c.inkSoft)
            }
        }
    }
}

@Composable
fun GroupDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(SibTheme.colors.rule))
}

/** Space around a control (a switch, a stepper) so it lines up with the text of the other rows in its group. */
@Composable
fun GroupPad(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) { content() }
}

/** A row that opens another page. The current value, if any, sits on the right. */
@Composable
fun NavItem(title: String, subtitle: String? = null, onClick: () -> Unit, trailing: String? = null, icon: SibIcon? = iconFor(title)) {
    val c = SibTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { IconBadge(icon); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            FitText(title, SibType.bodyStrong.copy(fontSize = 16.sp), c.ink)
            if (subtitle != null) Text(subtitle, style = SibType.small, color = c.inkSoft)
            if (trailing != null && LocalDensity.current.fontScale > 1.15f) Text(trailing, style = SibType.small, color = c.violet)
        }
        if (trailing != null && LocalDensity.current.fontScale <= 1.15f) { Text(trailing, style = SibType.bodyStrong, color = c.violet); Spacer(Modifier.width(10.dp)) }
        Canvas(Modifier.size(10.dp, 14.dp)) {
            val p = Path().apply { moveTo(1f, 1f); lineTo(size.width - 1f, size.height / 2); lineTo(1f, size.height - 1f) }
            drawPath(p, c.violet, style = Stroke(2.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A titled fact whose detail opens in place, so a long list of facts stays a short page. */
@Composable
fun ExpandItem(title: String, body: String, icon: SibIcon? = iconFor(title)) {
    val c = SibTheme.colors
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize().clickable(role = Role.Button) { open = !open }.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { IconBadge(icon, size = 36.dp); Spacer(Modifier.width(14.dp)) }
            FitText(tr(title), SibType.bodyStrong.copy(fontSize = 16.sp), c.ink, modifier = Modifier.weight(1f))
            Canvas(Modifier.size(14.dp, 10.dp)) {
                val p = Path().apply {
                    if (open) { moveTo(1f, size.height - 1f); lineTo(size.width / 2, 1f); lineTo(size.width - 1f, size.height - 1f) }
                    else { moveTo(1f, 1f); lineTo(size.width / 2, size.height - 1f); lineTo(size.width - 1f, 1f) }
                }
                drawPath(p, c.violet, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = SibType.body, color = c.inkSoft, modifier = Modifier.padding(start = if (icon != null) 50.dp else 0.dp))
        }
    }
}

/** A label on the left and a figure or word on the right. */
@Composable
fun ValueItem(label: String, value: String) {
    val c = SibTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = SibType.body, color = c.ink, modifier = Modifier.weight(1f))
        Text(value, style = SibType.bodyStrong, color = c.ink)
    }
}

/** A plain paragraph inside a group. */
@Composable
fun TextItem(text: String, strong: Boolean = false) {
    val c = SibTheme.colors
    Text(text, style = if (strong) SibType.bodyStrong else SibType.body, color = if (strong) c.ink else c.inkSoft, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp))
}

/** The H-sib mark: two bars, the dots between them are the time you have, and the marker above is the date. */
@Composable
fun HsibMark(size: Dp = 72.dp, modifier: Modifier = Modifier) {
    val c = SibTheme.colors
    Canvas(modifier.size(size)) {
        val u = this.size.width / 108f
        drawRoundRect(c.ink, Offset(34f * u, 36f * u), Size(8f * u, 40f * u), CornerRadius(4f * u))
        drawRoundRect(Brush.verticalGradient(listOf(c.glowB, c.glowA), startY = 36f * u, endY = 76f * u), Offset(66f * u, 36f * u), Size(8f * u, 40f * u), CornerRadius(4f * u))
        val dots = Brush.linearGradient(listOf(c.glowA, c.glowB), start = Offset(47f * u, 57f * u), end = Offset(64f * u, 48f * u))
        drawCircle(dots, 2.2f * u, Offset(49.2f * u, 57f * u))
        drawCircle(dots, 2.8f * u, Offset(54.8f * u, 52.5f * u))
        drawCircle(dots, 3.4f * u, Offset(60.9f * u, 48.8f * u))
        drawCircle(c.ink, 5f * u, Offset(70f * u, 23.5f * u))
    }
}

/**
 * The name at one size: "sib" (Start It Before) is the idea, so it is heavy and in the signature colours; "H-" (the maker) and
 * "Reminder" (what it is) are the same size but faded, so they sit behind it without shrinking.
 */
@Composable
fun AppName(size: TextUnit = 34.sp, modifier: Modifier = Modifier, reminder: Boolean = true) {
    val c = SibTheme.colors
    val faded = c.ink.copy(alpha = 0.5f)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = faded, fontWeight = FontWeight.Medium)) { append("H-") }
            withStyle(SpanStyle(brush = c.glow, fontWeight = FontWeight.ExtraBold)) { append("sib") }
            if (reminder) withStyle(SpanStyle(color = faded, fontWeight = FontWeight.Medium)) { append(" Reminder") }
        },
        style = SibType.display.copy(fontSize = size, lineHeight = size * 1.15f, letterSpacing = (-0.5).sp),
        modifier = modifier.semantics { contentDescription = if (reminder) "H-sib Reminder" else "H-sib" },
    )
}
