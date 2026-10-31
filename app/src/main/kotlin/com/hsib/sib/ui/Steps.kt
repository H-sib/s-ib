package com.hsib.sib.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hsib.sib.data.UserStep
import com.hsib.sib.domain.Fmt
import java.time.LocalDate

/** Add or change one step: what it is, how long it takes, and on which day. Every part is optional except the words. */
@Composable
fun StepDialog(initial: UserStep?, today: LocalDate, fmt: Fmt, onSave: (title: String, minutes: Int, day: LocalDate?) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val c = SibTheme.colors
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var minutes by remember(initial?.id) { mutableStateOf(initial?.minutes ?: 60) }
    var day by remember(initial?.id) { mutableStateOf(initial?.targetDay) }
    var pick by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.raised,
        title = { Text(if (initial == null) "New step" else "Change this step", style = SibType.title, color = c.ink) },
        text = {
            Column(Modifier.cuedVerticalScroll(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("What to do", title, { title = it.take(120) })
                Text("How long", style = SibType.bodyStrong, color = c.ink)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SibButton("-", { minutes = (minutes - 15).coerceAtLeast(5) }, kind = ButtonKind.QUIET, compact = true)
                    Text(fmt.minutes(minutes), style = SibType.title, color = c.ink)
                    SibButton("+", { minutes = (minutes + 15).coerceAtMost(60 * 40) }, kind = ButtonKind.QUIET, compact = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SibButton("+ 1 hour", { minutes = (minutes + 60).coerceAtMost(60 * 40) }, kind = ButtonKind.QUIET, compact = true)
                    SibButton("- 1 hour", { minutes = (minutes - 60).coerceAtLeast(5) }, kind = ButtonKind.QUIET, compact = true)
                }
                Text("On which day", style = SibType.bodyStrong, color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SibButton(day?.let { fmt.dayShort(it) } ?: "Pick a day", { pick = true }, kind = ButtonKind.QUIET, compact = true)
                    if (day != null) SibButton("No day", { day = null }, kind = ButtonKind.TEXT, compact = true)
                }
                if (onDelete != null) {
                    Spacer(Modifier.height(4.dp))
                    SibButton("Remove this step", onDelete, Modifier.fillMaxWidth(), ButtonKind.DANGER)
                }
            }
        },
        confirmButton = { TextButton({ if (title.isNotBlank()) onSave(title.trim(), minutes, day) }) { Text("Save", style = SibType.bodyStrong, color = if (title.isNotBlank()) c.ink else c.inkSoft) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", style = SibType.body, color = c.inkSoft) } },
    )
    if (pick) SibDatePicker(day ?: today, { day = it; pick = false }, { pick = false })
}
