package com.hsib.sib.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hsib.sib.Ready
import com.hsib.sib.core.focus.FocusRules
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A focus session for one deadline. Start it, work, and stop it: the real time you spent is added to the deadline's progress.
 * The start time is kept on the phone, so closing the app does not lose the session. While it runs, a quiet notification shows a live
 * timer. A session that has run for hours was probably forgotten, so it is never added as it stands: the person says what they really worked,
 * or restarts the clock, or throws the session away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusTile(deadlineId: String, ready: Ready, name: String?, title: String = "", notify: Boolean = true) {
    val c = SibTheme.colors
    val scope = rememberCoroutineScope()
    val key = "focus.$deadlineId"
    var startedAt by remember(deadlineId) { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var asking by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    LaunchedEffect(deadlineId) { startedAt = ready.settings.raw(key)?.toLongOrNull() ?: 0L }
    LaunchedEffect(startedAt, notify) {
        // Keep the notification in step: it comes back after a restart of the phone and goes when the switch is turned off.
        if (startedAt > 0L && notify) ready.notifier.showFocus(deadlineId, title, startedAt) else if (startedAt > 0L) ready.notifier.hideFocus(deadlineId)
    }
    LaunchedEffect(startedAt) { while (startedAt > 0L) { now = System.currentTimeMillis(); delay(15_000) } }
    val minutes = if (startedAt > 0L) FocusRules.elapsedMin(startedAt, now) else 0
    val long = FocusRules.isLong(minutes)

    fun begin(at: Long) = scope.launch {
        ready.settings.putRaw(key, at.toString()); startedAt = at; now = at
        if (notify) ready.notifier.showFocus(deadlineId, title, at)
    }
    fun end() = scope.launch { ready.settings.removeRaw(key); startedAt = 0L; ready.notifier.hideFocus(deadlineId) }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoTitle("Focus session", "Start it when you sit down to work, and stop it when you finish. The real time you spent is added to this deadline, so the progress bar is honest. You can close the app in between. If you forget it, Restart sets the clock back to zero and Discard throws the session away.", style = SibType.bodyStrong)
        if (startedAt == 0L) {
            Text(if (name != null) "$name, ready when you are." else "Ready when you are.", style = SibType.small, color = c.inkSoft)
            SibButton("Start a focus session", { begin(System.currentTimeMillis()) }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
        } else {
            Text("Working for ${FocusRules.describe(minutes)}", style = SibType.title, color = if (long) c.vermilion else c.verdigris)
            if (long) Text("Still working? This has run for ${FocusRules.describe(minutes)}. Say how long you really worked, or start the clock again.", style = SibType.small, color = c.inkSoft)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SibButton(
                    if (long) "Stop and set minutes" else if (minutes >= 1) "Stop and add $minutes min" else "Stop",
                    {
                        if (long) { typed = FocusRules.suggestedMin(minutes).toString(); asking = true }
                        else scope.launch { if (minutes >= 1) ready.store.addProgress(deadlineId, minutes); end() }
                    },
                    kind = ButtonKind.PRIMARY, compact = true,
                )
                SibButton("Restart", { begin(System.currentTimeMillis()) }, kind = ButtonKind.QUIET, compact = true)
                SibButton("Discard", { end() }, kind = ButtonKind.TEXT, compact = true)
            }
        }
    }

    if (asking) {
        val worked = FocusRules.parseMinutes(typed)
        AlertDialog(
            onDismissRequest = { asking = false }, containerColor = c.raised,
            title = { Text("How long did you really work?", style = SibType.title, color = c.ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The clock ran for ${FocusRules.describe(minutes)}. Type the minutes you worked and that is what gets added.", style = SibType.small, color = c.inkSoft)
                    Field("Minutes", typed, { typed = it.filter { ch -> ch.isDigit() }.take(5) }, keyboard = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done))
                }
            },
            confirmButton = {
                TextButton({
                    asking = false
                    scope.launch { if (worked >= 1) ready.store.addProgress(deadlineId, worked); end() }
                }) { Text(if (worked >= 1) "Add ${FocusRules.describe(worked)}" else "Add nothing", style = SibType.bodyStrong, color = c.ink) }
            },
            dismissButton = { TextButton({ asking = false }) { Text("Back", style = SibType.body, color = c.inkSoft) } },
        )
    }
}
