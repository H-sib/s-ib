package com.hsib.sib.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.time.LocalTime

/** A time in one tap steps: an hour up or down, then the minutes in quarters. Fast enough for a class timetable. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimePick(time: LocalTime, fmt: com.hsib.sib.domain.Fmt, onChange: (LocalTime) -> Unit) {
    val c = SibTheme.colors
    val big = LocalDensity.current.fontScale > 1.15f
    // In a narrow place such as a dialog the three parts would not fit on one line, so the buttons share a row under the time.
    BoxWithConstraints {
        val narrow = big || maxWidth < 380.dp
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (narrow) {
                Text(fmt.time(time), style = SibType.headline, color = c.ink)
                if (big) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SibButton("- 1 hour", { onChange(time.minusHours(1)) }, kind = ButtonKind.QUIET)
                    SibButton("+ 1 hour", { onChange(time.plusHours(1)) }, kind = ButtonKind.QUIET)
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SibButton("- 1 hour", { onChange(time.minusHours(1)) }, Modifier.weight(1f), ButtonKind.QUIET)
                    SibButton("+ 1 hour", { onChange(time.plusHours(1)) }, Modifier.weight(1f), ButtonKind.QUIET)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SibButton("- 1 hour", { onChange(time.minusHours(1)) }, kind = ButtonKind.QUIET)
                    Text(fmt.time(time), style = SibType.headline, color = c.ink, modifier = Modifier.padding(horizontal = 12.dp))
                    SibButton("+ 1 hour", { onChange(time.plusHours(1)) }, kind = ButtonKind.QUIET)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in listOf(0, 15, 30, 45)) SelectChip(":%02d".format(m), time.minute == m, { onChange(time.withMinute(m)) })
            }
        }
    }
}
