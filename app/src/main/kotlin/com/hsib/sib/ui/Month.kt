package com.hsib.sib.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hsib.sib.core.urgency.UrgencyState
import com.hsib.sib.domain.AppState
import com.hsib.sib.domain.Fmt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** The days of one month as a grid. A small dot marks a deadline: red when it is close or late, violet otherwise. */
@Composable
fun MonthView(st: AppState, fmt: Fmt, selected: LocalDate, onSelect: (LocalDate) -> Unit, onOpen: (String) -> Unit) {
    val c = SibTheme.colors
    var shown by rememberSaveable { mutableStateOf(YearMonth.from(selected).toString()) }
    val month = YearMonth.parse(shown)
    val first = st.settings.weekStart
    val byDay = st.active.groupBy { it.dueInstant?.atZone(st.zone)?.toLocalDate() }
    val gridStart = month.atDay(1).with(TemporalAdjusters.previousOrSame(first))
    val weeks = ((month.atEndOfMonth().toEpochDay() - gridStart.toEpochDay()) / 7 + 1).toInt()

    // The month bar: a tinted band with round arrows and the month in large type.
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.violet.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonthArrow(true, "Previous month") { shown = month.minusMonths(1).toString() }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()), style = SibType.headline, color = c.ink, textAlign = TextAlign.Center)
            Text(month.year.toString(), style = SibType.small, color = c.inkSoft)
        }
        MonthArrow(false, "Next month") { shown = month.plusMonths(1).toString() }
    }
    Spacer(Modifier.height(14.dp))
    // Weekday names: bold, in their own tinted pills, and the weekend in the alert colour so the week has a shape.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until 7) {
            val d: DayOfWeek = first.plus(i.toLong())
            val weekend = d in st.settings.weekend
            Text(
                d.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3), style = SibType.label, color = if (weekend) c.vermilion else c.violet, textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (weekend) c.vermilion.copy(alpha = 0.12f) else c.violet.copy(alpha = 0.12f)).padding(vertical = 8.dp),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 7) {
                    val day = gridStart.plusDays((w * 7 + i).toLong())
                    if (YearMonth.from(day) != month) { Spacer(Modifier.weight(1f).aspectRatio(0.85f)); continue }
                    val due = byDay[day].orEmpty()
                    val on = day == selected
                    val isToday = day == st.today
                    val weekend = day.dayOfWeek in st.settings.weekend
                    val hot = due.any { it.urgency == UrgencyState.OVERDUE || it.urgency == UrgencyState.DUE_TODAY || it.urgency == UrgencyState.DUE_SOON }
                    val fill = when {
                        on -> c.action
                        due.isNotEmpty() -> (if (hot) c.vermilion else c.violet).copy(alpha = 0.16f)
                        weekend -> c.vermilion.copy(alpha = 0.06f)
                        else -> c.lifted
                    }
                    Column(
                        Modifier.weight(1f).aspectRatio(0.85f).clip(RoundedCornerShape(16.dp)).background(fill)
                            .then(if (isToday && !on) Modifier.border(2.dp, c.vermilion, RoundedCornerShape(16.dp)) else Modifier)
                            .bounceClick { onSelect(day) }
                            .semantics(mergeDescendants = true) {
                                contentDescription = fmt.dayShort(day) + (if (isToday) ", today" else "") + if (due.isEmpty()) "" else ", ${due.size} due"
                            },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text(day.dayOfMonth.toString(), style = SibType.bodyStrong, color = when { on -> c.onAction; isToday -> c.vermilion; else -> c.ink })
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (due.isEmpty()) Color.Transparent else if (on) c.onAction else if (hot) c.vermilion else c.violet))
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    DayDetails(st, fmt, selected, onOpen)
}

@Composable
private fun MonthArrow(left: Boolean, description: String, onClick: () -> Unit) {
    val c = SibTheme.colors
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(c.raised).bounceClick(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp, 16.dp)) {
            val p = Path().apply {
                if (left) { moveTo(size.width - 1f, 1f); lineTo(1f, size.height / 2); lineTo(size.width - 1f, size.height - 1f) }
                else { moveTo(1f, 1f); lineTo(size.width - 1f, size.height / 2); lineTo(1f, size.height - 1f) }
            }
            drawPath(p, c.violet, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
