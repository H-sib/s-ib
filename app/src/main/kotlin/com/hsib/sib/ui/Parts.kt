package com.hsib.sib.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hsib.sib.core.model.Part
import com.hsib.sib.core.model.Priority
import kotlin.math.roundToInt

private const val MAX_PARTS = 50

/** Bright colours for the dot and the cell in the bar: red, yellow and blue. Green is kept for done. */
fun priorityDot(p: Priority): Color = when (p) {
    Priority.HIGH -> Color(0xFFE5484D)
    Priority.MEDIUM -> Color(0xFFF5B800)
    Priority.LOW -> Color(0xFF3B82F6)
}

/** The same three colours as writing: darker on a light page and lighter on a dark one, so the words stay easy to read. */
@Composable
fun priorityText(p: Priority): Color {
    val c = SibTheme.colors
    return when (p) {
        Priority.HIGH -> c.vermilion
        Priority.MEDIUM -> c.ochre
        Priority.LOW -> if (c.dark) Color(0xFF8DB8FF) else Color(0xFF1F5FBF)
    }
}

/**
 * A progress bar in the shape of a battery. Each checklist item is one cell, so seven items show seven cells.
 * With [tint] on and at least one priority set, every cell is its own topic: done ones are green and open ones take the colour of
 * their priority. Otherwise the bar fills from the left as topics are ticked, as before.
 */
@Composable
fun PartsBattery(parts: List<Part>, tint: Boolean, modifier: Modifier = Modifier) {
    val c = SibTheme.colors
    val done = parts.count { it.done }
    val total = parts.size
    val colored = tint && parts.any { it.priority != null }
    val target = if (total == 0) 0f else done.toFloat() / total
    val fill by animateFloatAsState(target, tween(600), label = "battery")
    val open = parts.count { !it.done && it.priority == Priority.HIGH }
    Canvas(modifier.fillMaxWidth().height(40.dp).semantics { contentDescription = "$done of $total done" + if (colored && open > 0) ", $open high priority still open" else "" }) {
        val nub = 6.dp.toPx()
        val stroke = 2.dp.toPx()
        val bodyW = size.width - nub
        val r = CornerRadius(10.dp.toPx())
        drawRoundRect(c.ink.copy(alpha = 0.55f), Offset(stroke / 2, stroke / 2), Size(bodyW - stroke, size.height - stroke), r, style = Stroke(stroke))
        drawRoundRect(c.ink.copy(alpha = 0.55f), Offset(bodyW, size.height * 0.3f), Size(nub, size.height * 0.4f), CornerRadius(2.dp.toPx()))
        val pad = 5.dp.toPx()
        val innerW = bodyW - 2 * pad
        if (colored) {
            val cellW = innerW / total
            val gap = if (total > 30) 1.dp.toPx() else 2.dp.toPx()
            parts.forEachIndexed { i, p ->
                val color = if (p.done) c.verdigris else p.priority?.let { priorityDot(it).copy(alpha = 0.85f) }
                if (color != null) {
                    drawRoundRect(color, Offset(pad + cellW * i + gap / 2, pad), Size((cellW - gap).coerceAtLeast(1f), size.height - 2 * pad), CornerRadius(4.dp.toPx()))
                }
            }
        } else {
            drawRoundRect(c.verdigris, Offset(pad, pad), Size(innerW * fill, size.height - 2 * pad), CornerRadius(6.dp.toPx()))
            if (total in 2..MAX_PARTS) {
                for (i in 1 until total) {
                    val x = pad + innerW * i / total
                    drawLine(c.raised, Offset(x, pad), Offset(x, size.height - pad), 2.dp.toPx())
                }
            }
        }
    }
}

/**
 * Split one deadline into named parts, tick them off, rename them, set how much each matters, move them up and down and remove one
 * without touching the rest. Tap a topic's name to change it. Nothing here needs the clock or the network.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PartsTile(parts: List<Part>, priorityColors: Boolean = true, onChange: (List<Part>) -> Unit) {
    val c = SibTheme.colors
    var count by rememberSaveable { mutableIntStateOf(3) }
    var editing by remember { mutableIntStateOf(-1) }
    var draft by remember { mutableStateOf("") }
    var draftPriority by remember { mutableStateOf<Priority?>(null) }
    var confirmRemoveAll by remember { mutableStateOf(false) }

    fun open(i: Int) { editing = i; draft = parts[i].name; draftPriority = parts[i].priority }
    fun save(i: Int) {
        onChange(parts.mapIndexed { j, q -> if (j == i) q.copy(name = draft.trim().ifEmpty { q.name }.take(60), priority = draftPriority) else q })
        editing = -1
    }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised).padding(18.dp)) {
        if (parts.isEmpty()) {
            InfoTitle("Checklist", "Use a checklist when one deadline holds several pieces, such as 7 assignments or 5 topics. Give each one your own name, tick it when done, and the progress bar, drawn in the shape of a battery, shows how far you are. Tap a topic to move it, set how much it matters or remove it.")
            Text("Several pieces due on the same day, such as 7 assignments or the topics of an exam? Make a checklist and tick each one off.", style = SibType.small, color = c.inkSoft)
            Spacer(Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SibButton("-", { count = (count - 1).coerceAtLeast(2) }, kind = ButtonKind.QUIET, compact = true)
                Text("$count", style = SibType.title, color = c.ink)
                SibButton("+", { count = (count + 1).coerceAtMost(MAX_PARTS) }, kind = ButtonKind.QUIET, compact = true)
            }
            Spacer(Modifier.size(12.dp))
            SibButton("Make a checklist of $count", { onChange(List(count) { Part("Topic ${it + 1}") }) }, kind = ButtonKind.QUIET)
        } else {
            val done = parts.count { it.done }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$done of ${parts.size} done", style = SibType.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
                Text("${(done * 100f / parts.size).roundToInt()}%", style = SibType.bodyStrong, color = c.verdigris)
            }
            Spacer(Modifier.size(12.dp))
            PartsBattery(parts, priorityColors)
            Spacer(Modifier.size(12.dp))
            parts.forEachIndexed { i, p ->
                if (editing == i) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Name (up to 60 letters)", draft, { draft = it.take(60) }, onDone = { save(i) })
                        Text("How much it matters", style = SibType.bodyStrong, color = c.ink)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectChip("None", draftPriority == null, { draftPriority = null })
                            Priority.values().forEach { pr -> SelectChip(pr.label, draftPriority == pr, { draftPriority = pr }) }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SibButton("Move up", { onChange(Part.move(parts, i, i - 1)); editing = i - 1 }, kind = ButtonKind.QUIET, compact = true, enabled = i > 0)
                            SibButton("Move down", { onChange(Part.move(parts, i, i + 1)); editing = i + 1 }, kind = ButtonKind.QUIET, compact = true, enabled = i < parts.lastIndex)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SibButton("Save", { save(i) }, compact = true)
                            SibButton("Cancel", { editing = -1 }, kind = ButtonKind.TEXT, compact = true)
                            SibButton("Remove this topic", { onChange(Part.remove(parts, i)); editing = -1 }, kind = ButtonKind.DANGER, compact = true)
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).border(2.dp, if (p.done) c.verdigris else c.rule, CircleShape)
                                .background(if (p.done) c.verdigris else c.raised)
                                .clickable(role = Role.Checkbox) { onChange(parts.mapIndexed { j, q -> if (j == i) q.copy(done = !q.done) else q }) }
                                .semantics { contentDescription = (if (p.done) "Done: " else "Not done: ") + p.name },
                        )
                        Spacer(Modifier.width(12.dp))
                        val shown = if (priorityColors) p.priority else null
                        if (shown != null) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(priorityDot(shown)))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            p.name, style = SibType.body, color = if (p.done) c.inkSoft else shown?.let { priorityText(it) } ?: c.ink,
                            modifier = Modifier.weight(1f).clickable(role = Role.Button) { open(i) }.padding(vertical = 10.dp)
                                .semantics { contentDescription = p.name + (shown?.let { ", ${it.label.lowercase()} priority" } ?: "") + ". Tap to change." },
                        )
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (parts.size < MAX_PARTS) SibButton("Add one", { onChange(parts + Part("Topic ${parts.size + 1}")) }, kind = ButtonKind.QUIET, compact = true)
                SibButton("Remove the checklist", { confirmRemoveAll = true }, kind = ButtonKind.TEXT, compact = true)
            }
        }
    }

    if (confirmRemoveAll) {
        AlertDialog(
            onDismissRequest = { confirmRemoveAll = false }, containerColor = c.raised,
            title = { Text("Remove the whole checklist?", style = SibType.title, color = c.ink) },
            text = { Text("All ${parts.size} topics go. To take out a single topic, tap its name and choose Remove this topic.", style = SibType.body, color = c.inkSoft) },
            confirmButton = { TextButton({ confirmRemoveAll = false; editing = -1; onChange(emptyList()) }) { Text("Remove all", style = SibType.bodyStrong, color = c.vermilion) } },
            dismissButton = { TextButton({ confirmRemoveAll = false }) { Text("Keep it", style = SibType.body, color = c.inkSoft) } },
        )
    }
}
