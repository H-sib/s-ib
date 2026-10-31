package com.hsib.sib.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hsib.sib.domain.Fmt
import java.time.LocalDate

/** Any number of hours, not only the presets: 48 hours for a big project is allowed. */
@Composable
fun HoursField(currentMin: Int, onSet: (Int) -> Unit) {
    val c = SibTheme.colors
    var text by remember(currentMin) { mutableStateOf(trimHours(currentMin / 60.0)) }
    Field(
        "Hours you will give in total", text, { text = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6) },
        keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        onDone = { text.toDoubleOrNull()?.let { h -> onSet((h * 60).toInt().coerceIn(15, 999 * 60)) } },
    )
    Text("You decide. Type any number, then press done on the keyboard.", style = SibType.small, color = c.inkSoft)
}

private fun trimHours(h: Double): String = if (h == h.toLong().toDouble()) h.toLong().toString() else "%.1f".format(h)

/** Pick the minutes for each day yourself. Days you leave at zero get no work from this deadline. */
@Composable
fun ManualPlanEditor(plan: Map<LocalDate, Int>, today: LocalDate, lastDay: LocalDate?, fmt: Fmt, onChange: (Map<LocalDate, Int>) -> Unit) {
    val c = SibTheme.colors
    val stacked = LocalDensity.current.fontScale > 1.15f
    val end = lastDay?.takeIf { !it.isBefore(today) }?.let { minOf(it, today.plusDays(20)) } ?: today.plusDays(13)
    val days = generateSequence(today) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (d in days) {
            val m = plan[d] ?: 0
            fun set(v: Int) = onChange(if (v <= 0) plan - d else plan + (d to v))
            val controls: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SibButton("-", { set(m - 30) }, kind = ButtonKind.QUIET, compact = true, enabled = m > 0)
                    Text(if (m == 0) "none" else fmt.minutes(m), style = SibType.bodyStrong, color = if (m == 0) c.inkSoft else c.violet, modifier = Modifier.width(72.dp))
                    SibButton("+", { set(m + 30) }, kind = ButtonKind.QUIET, compact = true)
                }
            }
            if (stacked) {
                Column(Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(fmt.dayShort(d), style = SibType.body, color = c.ink); controls() }
            } else {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(fmt.dayShort(d), style = SibType.body, color = c.ink, modifier = Modifier.weight(1f))
                    controls()
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        val total = plan.filter { !it.key.isBefore(today) }.values.sum()
        if (plan.isNotEmpty()) {
            Text("Your plan: ${fmt.minutes(total)} in total.", style = SibType.small, color = c.inkSoft)
            SibButton("Go back to the automatic plan", { onChange(emptyMap()) }, kind = ButtonKind.TEXT)
        }
    }
}
