package com.hsib.sib.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Building blocks for pages that show a little and keep the rest one tap away: a round icon button with a one word label, and a
 * row with an icon and a title that opens in place. Nobody has to read what they did not ask for.
 */

/** A round outlined icon button with a short label under it. [description] is what a screen reader says. */
@Composable
fun IconAction(icon: SibIcon, label: String, modifier: Modifier = Modifier, description: String = label, badge: Int? = null, onClick: () -> Unit) {
    val c = SibTheme.colors
    Column(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(20.dp)).bounceClick(onClick = onClick).padding(vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(c.ink.copy(alpha = 0.05f)).border(1.dp, c.inkSoft.copy(alpha = 0.35f), CircleShape), contentAlignment = Alignment.Center) {
            SibIconView(icon, size = 24.dp)
            if (badge != null) Box(Modifier.align(Alignment.TopEnd).padding(9.dp).size(10.dp).clip(CircleShape).background(c.vermilion))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = SibType.label.copy(fontWeight = FontWeight.Normal), color = c.inkSoft, textAlign = TextAlign.Center)
    }
}

/**
 * A row with an icon and a one word title that shows a short [hint] while closed and opens in place to show [content]. Rows are
 * meant to sit inside a [Group], with a [GroupDivider] between them.
 */
@Composable
fun FoldItem(icon: SibIcon, title: String, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = SibTheme.colors
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClickLabel = if (open) "Hide" else "Show") { open = !open }
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon)
            Spacer(Modifier.width(14.dp))
            Text(title, style = SibType.bodyStrong.copy(fontSize = 16.sp), color = c.ink, modifier = Modifier.weight(1f))
            if (hint != null && !open) {
                Text(hint, style = SibType.small, color = c.inkSoft, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 150.dp))
                Spacer(Modifier.width(10.dp))
            }
            Canvas(Modifier.size(14.dp, 10.dp)) {
                val p = Path().apply {
                    if (open) { moveTo(1f, size.height - 1f); lineTo(size.width / 2, 1f); lineTo(size.width - 1f, size.height - 1f) }
                    else { moveTo(1f, 1f); lineTo(size.width / 2, size.height - 1f); lineTo(size.width - 1f, 1f) }
                }
                drawPath(p, c.violet, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        if (open) Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

/**
 * A one line pill that adds time: a green plus, the amount, and the unit in small type. Tinted like the progress bar above it, so it
 * reads as something to press and not as part of the card. All three share one size, so no label ever breaks onto a second line.
 */
@Composable
fun QuickAdd(amount: String, unit: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = SibTheme.colors
    val shape = RoundedCornerShape(50)
    Row(
        modifier.heightIn(min = 48.dp).clip(shape).background(c.verdigris.copy(alpha = 0.16f)).border(1.dp, c.verdigris.copy(alpha = 0.55f), shape)
            .bounceClick(onClick = onClick).padding(horizontal = 10.dp)
            .clearAndSetSemantics { contentDescription = "Add $amount " + (if (unit == "hr") "hour" else "minutes") },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("+", style = SibType.bodyStrong.copy(fontSize = 18.sp), color = c.verdigris)
        Spacer(Modifier.width(2.dp))
        Text(amount, style = SibType.bodyStrong.copy(fontSize = 17.sp), color = c.ink, maxLines = 1, softWrap = false)
        Spacer(Modifier.width(3.dp))
        Text(unit, style = SibType.label.copy(fontWeight = FontWeight.Normal), color = c.inkSoft, maxLines = 1, softWrap = false)
    }
}
