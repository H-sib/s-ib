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
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hsib.sib.core.model.Part
import kotlin.math.roundToInt

private const val MAX_PARTS = 30

/** A battery that fills as parts are done. Each part is one cell, so seven parts show seven cells. */
@Composable
fun PartsBattery(done: Int, total: Int, modifier: Modifier = Modifier) {
    val c = SibTheme.colors
    val target = if (total == 0) 0f else done.toFloat() / total
    val fill by animateFloatAsState(target, tween(600), label = "battery")
    Canvas(modifier.fillMaxWidth().height(40.dp).semantics { contentDescription = "$done of $total done" }) {
        val nub = 6.dp.toPx()
        val stroke = 2.dp.toPx()
        val bodyW = size.width - nub
        val r = CornerRadius(10.dp.toPx())
        drawRoundRect(c.ink.copy(alpha = 0.55f), Offset(stroke / 2, stroke / 2), Size(bodyW - stroke, size.height - stroke), r, style = Stroke(stroke))
        drawRoundRect(c.ink.copy(alpha = 0.55f), Offset(bodyW, size.height * 0.3f), Size(nub, size.height * 0.4f), CornerRadius(2.dp.toPx()))
        val pad = 5.dp.toPx()
        val innerW = bodyW - 2 * pad
        drawRoundRect(c.verdigris, Offset(pad, pad), Size(innerW * fill, size.height - 2 * pad), CornerRadius(6.dp.toPx()))
        if (total in 2..MAX_PARTS) {
            for (i in 1 until total) {
                val x = pad + innerW * i / total
                drawLine(c.raised, Offset(x, pad), Offset(x, size.height - pad), 2.dp.toPx())
            }
        }
    }
}

/** Split one deadline into named parts, tick them off, rename them. Nothing here needs the clock or the network. */
@Composable
fun PartsTile(parts: List<Part>, onChange: (List<Part>) -> Unit) {
    val c = SibTheme.colors
    var count by rememberSaveable { mutableIntStateOf(3) }
    var editing by remember { mutableIntStateOf(-1) }
    var draft by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised).padding(18.dp)) {
        if (parts.isEmpty()) {
            Text("Parts", style = SibType.bodyStrong, color = c.ink)
            Text("Several pieces due on the same day, such as 7 assignments? Split them and tick each one off.", style = SibType.small, color = c.inkSoft)
            Spacer(Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SibButton("-", { count = (count - 1).coerceAtLeast(2) }, kind = ButtonKind.QUIET, compact = true)
                Text("$count", style = SibType.title, color = c.ink)
                SibButton("+", { count = (count + 1).coerceAtMost(MAX_PARTS) }, kind = ButtonKind.QUIET, compact = true)
            }
            Spacer(Modifier.size(12.dp))
            SibButton("Split into $count parts", { onChange(List(count) { Part("Part ${it + 1}") }) }, kind = ButtonKind.QUIET)
        } else {
            val done = parts.count { it.done }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$done of ${parts.size} done", style = SibType.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
                Text("${(done * 100f / parts.size).roundToInt()}%", style = SibType.bodyStrong, color = c.verdigris)
            }
            Spacer(Modifier.size(12.dp))
            PartsBattery(done, parts.size)
            Spacer(Modifier.size(12.dp))
            parts.forEachIndexed { i, p ->
                if (editing == i) {
                    Field("Name", draft, { draft = it.take(60) }, onDone = {
                        onChange(parts.mapIndexed { j, q -> if (j == i) q.copy(name = draft.trim().ifEmpty { q.name }) else q })
                        editing = -1
                    })
                } else {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).border(2.dp, if (p.done) c.verdigris else c.rule, CircleShape)
                                .background(if (p.done) c.verdigris else c.raised)
                                .clickable(role = Role.Checkbox) { onChange(parts.mapIndexed { j, q -> if (j == i) q.copy(done = !q.done) else q }) }
                                .semantics { contentDescription = (if (p.done) "Done: " else "Not done: ") + p.name },
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            p.name, style = SibType.body, color = if (p.done) c.inkSoft else c.ink,
                            modifier = Modifier.weight(1f).clickable(role = Role.Button) { draft = p.name; editing = i }.padding(vertical = 10.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (parts.size < MAX_PARTS) SibButton("Add a part", { onChange(parts + Part("Part ${parts.size + 1}")) }, kind = ButtonKind.QUIET, compact = true)
                SibButton("Remove parts", { onChange(emptyList()) }, kind = ButtonKind.TEXT, compact = true)
            }
        }
    }
}
