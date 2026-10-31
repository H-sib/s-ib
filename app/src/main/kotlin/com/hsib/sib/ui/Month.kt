package com.hsib.sib.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SibButton("Back", { shown = month.minusMonths(1).toString() }, kind = ButtonKind.TEXT, compact = true)
        Text(
            month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year, style = SibType.title, color = c.ink,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
        )
        SibButton("Next", { shown = month.plusMonths(1).toString() }, kind = ButtonKind.TEXT, compact = true)
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth()) {
        for (i in 0 until 7) {
            val d: DayOfWeek = first.plus(i.toLong())
            Text(d.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = SibType.small, color = c.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 7) {
                    val day = gridStart.plusDays((w * 7 + i).toLong())
                    val inMonth = YearMonth.from(day) == month
                    val due = byDay[day].orEmpty()
                    val on = day == selected
                    val hot = due.any { it.urgency == UrgencyState.OVERDUE || it.urgency == UrgencyState.DUE_TODAY || it.urgency == UrgencyState.DUE_SOON }
                    Column(
                        Modifier.weight(1f).aspectRatio(0.85f).clip(RoundedCornerShape(14.dp))
                            .background(if (on) c.violetWash else Color.Transparent)
                            .bounceClick { onSelect(day) }
                            .semantics(mergeDescendants = true) {
                                contentDescription = fmt.dayShort(day) + if (due.isEmpty()) "" else ", ${due.size} due"
                            },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            day.dayOfMonth.toString(), style = SibType.bodyStrong,
                            color = when { day == st.today -> c.vermilion; inMonth -> c.ink; else -> c.inkSoft.copy(alpha = 0.45f) },
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (due.isEmpty()) Color.Transparent else if (hot) c.vermilion else c.violet))
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    DayDetails(st, fmt, selected, onOpen)
}
