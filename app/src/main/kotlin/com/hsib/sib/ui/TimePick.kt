package com.hsib.sib.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
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
    val hourButtons: @Composable () -> Unit = {
        SibButton("- 1 hour", { onChange(time.minusHours(1)) }, kind = ButtonKind.QUIET)
        Text(fmt.time(time), style = SibType.headline, color = c.ink, modifier = Modifier.padding(horizontal = 12.dp))
        SibButton("+ 1 hour", { onChange(time.plusHours(1)) }, kind = ButtonKind.QUIET)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (big) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { hourButtons() } else Row(verticalAlignment = Alignment.CenterVertically) { hourButtons() }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in listOf(0, 15, 30, 45)) SelectChip(":%02d".format(m), time.minute == m, { onChange(time.withMinute(m)) })
        }
    }
}
