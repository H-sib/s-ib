package com.hsib.sib.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hsib.sib.core.plan.PlanResult
import com.hsib.sib.core.urgency.UrgencyState
import com.hsib.sib.domain.Fmt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

data class RunwayItem(
    val id: String,
    val title: String,
    val due: LocalDate,
    val windowEnd: LocalDate?,
    val workDays: List<LocalDate>,
    val latestStart: LocalDate?,
    val state: UrgencyState,
    val preview: Boolean,
)

private const val HORIZON_DAYS = 120
private const val TAU = 12.0
private const val LEFT_MARGIN = 0.07f

/** Time on the Runway is stretched near today and compressed far away, so next week is readable and next quarter still fits. */
fun runwayX(today: LocalDate, d: LocalDate): Float {
    val n = ChronoUnit.DAYS.between(today, d)
    return if (n < 0) LEFT_MARGIN * (1f - min(-n, 14L) / 14f)
    else LEFT_MARGIN + (1f - LEFT_MARGIN) * (ln(1.0 + min(n, HORIZON_DAYS.toLong()) / TAU) / ln(1.0 + HORIZON_DAYS / TAU)).toFloat()
}

private val axisDays = listOf(0 to "today", 7 to "1 wk", 14 to "2 wk", 30 to "1 mo", 60 to "2 mo", 90 to "3 mo")

@Composable
fun Runway(items: List<RunwayItem>, today: LocalDate, fmt: Fmt, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = SibTheme.colors
    val measurer = rememberTextMeasurer()
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(26.dp).semantics { contentDescription = "Time axis from today to three months ahead" }) {
            drawLine(c.rule, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1.5f)
            for ((d, label) in axisDays) {
                val x = runwayX(today, today.plusDays(d.toLong())) * size.width
                drawLine(c.inkSoft, Offset(x, size.height - 8f), Offset(x, size.height), 2f)
                drawLabel(measurer, label, x, 0f, c.inkSoft, first = d == 0, last = d == 90)
            }
        }
        if (items.isEmpty()) {
            Text("Nothing on the Runway yet.", style = SibType.small, color = c.inkSoft, modifier = Modifier.padding(vertical = 16.dp))
        }
        for (item in items) RunwayRow(item, today, fmt, onOpen)
    }
}

private fun DrawScope.drawLabel(measurer: TextMeasurer, text: String, x: Float, y: Float, color: Color, first: Boolean, last: Boolean) {
    val layout = measurer.measure(text, SibType.small.copy(fontSize = 11.sp, color = color))
    val w = layout.size.width.toFloat()
    val px = when { first -> x; last -> x - w; else -> x - w / 2f }.coerceIn(0f, size.width - w)
    drawText(layout, topLeft = Offset(px, y))
}

@Composable
private fun RunwayRow(item: RunwayItem, today: LocalDate, fmt: Fmt, onOpen: (String) -> Unit) {
    val c = SibTheme.colors
    val color = stateColor(item.state)
    val desc = (if (item.preview) "Suggestion: " else "") + "${item.title}, ${item.state.label}, due ${fmt.dayShort(item.due)}"
    Column(
        Modifier.fillMaxWidth().clickable(enabled = !item.preview, role = Role.Button) { onOpen(item.id) }
            .padding(top = 10.dp).semantics(mergeDescendants = true) { contentDescription = desc },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (item.preview) "? " else "") + item.title, style = SibType.bodyStrong.copy(fontSize = 14.sp), color = c.ink,
                maxLines = 1, modifier = Modifier.weight(1f),
            )
            Text(fmt.dayNoWeek(item.due), style = SibType.small, color = c.inkSoft)
        }
        Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            val h = size.height
            val mid = h / 2f
            fun x(d: LocalDate) = runwayX(today, d) * size.width
            for ((d, _) in axisDays) drawLine(c.rule.copy(alpha = 0.55f), Offset(x(today.plusDays(d.toLong())), 0f), Offset(x(today.plusDays(d.toLong())), h), 1f)
            drawLine(c.rule, Offset(0f, mid), Offset(size.width, mid), 1f)
            val dash = PathEffect.dashPathEffect(floatArrayOf(7f, 6f))
            val dueX = x(item.due)
            val first = item.workDays.minOrNull()
            if (first != null) {
                val left = x(first).coerceAtMost(dueX)
                val band = Size(max(dueX - left, 6f), 10.dp.toPx())
                val top = Offset(left, mid - band.height / 2f)
                val round = CornerRadius(band.height / 2f)
                if (item.preview) drawRoundRect(color, top, band, round, style = Stroke(1.5.dp.toPx(), pathEffect = dash))
                else drawRoundRect(color.copy(alpha = 0.85f), top, band, round)
                for (d in item.workDays) if (!item.preview) drawLine(color, Offset(x(d), mid - 3.dp.toPx()), Offset(x(d), mid + 3.dp.toPx()), 1.5f)
            }
            if (item.windowEnd != null) {
                val endX = x(item.windowEnd)
                drawRect(color.copy(alpha = 0.12f), Offset(dueX, mid - 7.dp.toPx()), Size(max(endX - dueX, 4f), 14.dp.toPx()))
                drawRect(color, Offset(dueX, mid - 7.dp.toPx()), Size(max(endX - dueX, 4f), 14.dp.toPx()), style = Stroke(1.2f, pathEffect = dash))
            }
            item.latestStart?.let { ls ->
                val lx = x(ls)
                val tri = Path().apply { moveTo(lx - 5.dp.toPx(), 0f); lineTo(lx + 5.dp.toPx(), 0f); lineTo(lx, 6.dp.toPx()); close() }
                drawPath(tri, c.ink)
            }
            // The deadline itself: a solid end cap, hollow and dashed when it is only a suggestion.
            val capTop = Offset(dueX, mid - 9.dp.toPx())
            if (item.preview) drawLine(color, capTop, Offset(dueX, mid + 9.dp.toPx()), 3f, pathEffect = dash)
            else drawLine(color, capTop, Offset(dueX, mid + 9.dp.toPx()), 4f)
            if (item.state == UrgencyState.OVERDUE) {
                val p = Path().apply { moveTo(dueX + 9.dp.toPx(), mid - 5.dp.toPx()); lineTo(dueX + 2.dp.toPx(), mid); lineTo(dueX + 9.dp.toPx(), mid + 5.dp.toPx()) }
                drawPath(p, color, style = Stroke(2f))
            }
        }
    }
}

// ---- load horizon -----------------------------------------------------------------------------

@Composable
fun LoadHorizon(
    plan: PlanResult,
    today: LocalDate,
    fmt: Fmt,
    modifier: Modifier = Modifier,
    previewMinutes: Map<LocalDate, Int> = emptyMap(),
    days: Int = 28,
    weekStart: DayOfWeek = DayOfWeek.MONDAY,
) {
    val c = SibTheme.colors
    val measurer = rememberTextMeasurer()
    val dates = remember(today, days) { (0 until days).map { today.plusDays(it.toLong()) } }
    val maxCap = (dates.maxOf { plan.capacityByDay[it] ?: 0 }).coerceAtLeast(60)
    val peakDay = dates.maxByOrNull { plan.loadByDay[it] ?: 0 }
    val peakMin = peakDay?.let { plan.loadByDay[it] } ?: 0
    val summary = buildString {
        append("Planned work for the next four weeks. ")
        if (peakMin > 0) append("Busiest day ${fmt.dayShort(peakDay!!)}, ${fmt.minutes(peakMin)}. ") else append("No work planned yet. ")
        plan.shortfall?.let { append("Shortfall: ${fmt.minutes(it.missingMin)} between ${fmt.dayNoWeek(it.fromDay)} and ${fmt.dayNoWeek(it.toDay)}.") }
    }
    Canvas(modifier.fillMaxWidth().height(192.dp).semantics { contentDescription = summary }) {
        val labelH = 22.dp.toPx()
        val topPad = 36.dp.toPx()
        val chartH = size.height - labelH - topPad
        val gap = 10.dp.toPx()
        // The days, grouped into weeks. Every week is its own outlined panel and the panels are kept apart by a clear gap,
        // so it is obvious where one week ends and the next begins.
        val weeks = ArrayList<IntRange>()
        var from = 0
        while (from < days) {
            val ws = dates[from].with(TemporalAdjusters.previousOrSame(weekStart))
            var to = from
            while (to + 1 < days && dates[to + 1].with(TemporalAdjusters.previousOrSame(weekStart)) == ws) to++
            weeks += from..to
            from = to + 1
        }
        val slot = (size.width - gap * (weeks.size - 1)) / days
        val barW = slot * 0.56f
        val weekOf = IntArray(days).also { arr -> weeks.forEachIndexed { w, r -> for (i in r) arr[i] = w } }
        fun leftOf(i: Int) = i * slot + weekOf[i] * gap
        val scale = chartH / (max(maxCap, previewMinutes.values.maxOrNull()?.let { m -> dates.maxOf { (plan.loadByDay[it] ?: 0) + (previewMinutes[it] ?: 0) } } ?: 0) * 1.08f)
        val thisWeek = today.with(TemporalAdjusters.previousOrSame(weekStart))
        val corner = CornerRadius(16.dp.toPx())
        for (r in weeks) {
            val ws = dates[r.first].with(TemporalAdjusters.previousOrSame(weekStart))
            val left = leftOf(r.first)
            val right = leftOf(r.last) + slot
            val current = ws == thisWeek
            drawRoundRect(if (current) c.violetWash else c.raised, Offset(left, 0f), Size(right - left, size.height), corner)
            drawRoundRect(if (current) c.violet.copy(alpha = 0.7f) else c.rule, Offset(left, 0f), Size(right - left, size.height), corner, style = Stroke(1.5.dp.toPx()))
            val style = SibType.label.copy(color = if (current) c.violet else c.ink)
            var name = measurer.measure(if (current) "This week" else fmt.dayNoWeek(ws), style)
            if (current && name.size.width >= right - left - 16.dp.toPx()) name = measurer.measure("Now", style)
            if (name.size.width < right - left - 16.dp.toPx()) drawText(name, topLeft = Offset(left + 12.dp.toPx(), 11.dp.toPx()))
            drawLine(c.rule, Offset(left + 8.dp.toPx(), topPad + chartH), Offset(right - 8.dp.toPx(), topPad + chartH), 1.5f)
        }
        translate(top = topPad) {
            dates.forEachIndexed { i, d ->
                val x0 = leftOf(i) + (slot - barW) / 2f
                val weekend = d.dayOfWeek.value >= 6
                val cap = plan.capacityByDay[d] ?: 0
                val load = plan.loadByDay[d] ?: 0
                if (load > 0) {
                    val over = cap in 1 until load
                    val barBrush = if (over) Brush.verticalGradient(listOf(c.vermilion, c.vermilion)) else Brush.verticalGradient(listOf(c.glowB, c.glowA), startY = chartH - load * scale, endY = chartH)
                    drawRoundRect(barBrush, Offset(x0, chartH - load * scale), Size(barW, load * scale), CornerRadius(barW / 3f))
                }
                val extra = previewMinutes[d] ?: 0
                if (extra > 0) {
                    val top = chartH - (load + extra) * scale
                    drawRect(c.vermilion, Offset(x0, top), Size(barW, extra * scale), style = Stroke(1.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))))
                }
                if (cap > 0) {
                    val startsWeek = i == 0 || d.dayOfWeek == weekStart
                    val endsWeek = i == days - 1 || d.plusDays(1).dayOfWeek == weekStart
                    val l = leftOf(i) + (if (startsWeek) 6.dp.toPx() else 1.dp.toPx())
                    val rr = leftOf(i) + slot - (if (endsWeek) 6.dp.toPx() else 1.dp.toPx())
                    drawLine(c.ochre, Offset(l, chartH - cap * scale), Offset(rr, chartH - cap * scale), 1.5.dp.toPx(), StrokeCap.Round)
                }
                val letter = fmt.weekday(d).take(1).uppercase()
                val lay = measurer.measure(letter, SibType.small.copy(fontSize = 10.sp, color = if (weekend) c.inkSoft else c.ink))
                drawText(lay, topLeft = Offset(leftOf(i) + (slot - lay.size.width) / 2f, chartH + 4.dp.toPx()))
            }
            // The shortfall is a stretch of days, shown as a hatched band under the axis and named in words below.
            plan.shortfall?.let { s ->
                val a = ChronoUnit.DAYS.between(today, s.fromDay).toInt().coerceIn(0, days - 1)
                val b = ChronoUnit.DAYS.between(today, s.toDay).toInt().coerceIn(0, days - 1)
                val left = leftOf(a)
                val right = leftOf(b) + slot
                val y0 = chartH - 5.dp.toPx()
                drawRect(c.vermilion.copy(alpha = 0.14f), Offset(left, 0f), Size(right - left, chartH))
                var hx = left
                while (hx < right) {
                    drawLine(c.vermilion.copy(alpha = 0.7f), Offset(hx, chartH), Offset(min(hx + 8.dp.toPx(), right), y0), 1.5f)
                    hx += 6.dp.toPx()
                }
                drawLine(c.vermilion, Offset(left, 0f), Offset(left, chartH), 1.5f)
                drawLine(c.vermilion, Offset(right, 0f), Offset(right, chartH), 1.5f)
            }
        }
    }
}

@Composable
fun LoadLegend(modifier: Modifier = Modifier) {
    val c = SibTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem("Planned work") { Box(Modifier.size(width = 14.dp, height = 10.dp).clip(RoundedCornerShape(3.dp)).background(c.glow)) }
        LegendItem("Your time") { Box(Modifier.height(3.dp).width(14.dp).clip(RoundedCornerShape(2.dp)).background(c.ochre)) }
    }
}

@Composable
private fun LegendItem(text: String, mark: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        mark()
        Text(text, style = SibType.small.copy(fontSize = 12.sp), color = SibTheme.colors.ink)
    }
}

// ---- week strip -------------------------------------------------------------------------------

data class DayCell(val date: LocalDate, val loadMin: Int, val capMin: Int, val dueCount: Int)

@Composable
fun WeekStrip(cells: List<DayCell>, selected: LocalDate, today: LocalDate, fmt: Fmt, onSelect: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val c = SibTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (cell in cells) {
            val on = cell.date == selected
            val fill = if (cell.capMin <= 0) 0f else (cell.loadMin.toFloat() / cell.capMin).coerceIn(0f, 1f)
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                    .background(if (on) c.violetWash else Color.Transparent)
                    .bounceClick { onSelect(cell.date) }
                    .padding(vertical = 12.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${fmt.dayShort(cell.date)}, ${if (cell.dueCount > 0) "${cell.dueCount} due, " else ""}${fmt.minutes(cell.loadMin)} planned"
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(fmt.weekday(cell.date).take(3), style = SibType.small.copy(fontSize = 12.sp), color = c.inkSoft)
                Spacer(Modifier.height(4.dp))
                Text(cell.date.dayOfMonth.toString(), style = SibType.title, color = if (cell.date == today) c.vermilion else c.ink)
                Spacer(Modifier.height(10.dp))
                Canvas(Modifier.size(width = 10.dp, height = 40.dp)) {
                    val r = CornerRadius(size.width / 2)
                    drawRoundRect(if (on) c.lifted else c.raised, Offset.Zero, size, r)
                    if (fill > 0f) drawRoundRect(if (fill >= 1f) c.vermilion else c.violet, Offset(0f, size.height * (1 - fill)), Size(size.width, size.height * fill), r)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(6.dp)) {
                    repeat(min(cell.dueCount, 3)) { Box(Modifier.size(6.dp).clip(CircleShape).background(c.vermilion)) }
                }
            }
        }
    }
}
