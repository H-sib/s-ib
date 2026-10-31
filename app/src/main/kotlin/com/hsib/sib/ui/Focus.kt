package com.hsib.sib.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hsib.sib.Ready
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A focus session for one deadline. Start it, work, and stop it: the real time you spent is added to the deadline's progress.
 * The start time is kept on the phone, so closing the app does not lose the session.
 */
@Composable
fun FocusTile(deadlineId: String, ready: Ready, name: String?) {
    val c = SibTheme.colors
    val scope = rememberCoroutineScope()
    val key = "focus.$deadlineId"
    var startedAt by remember(deadlineId) { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(deadlineId) { startedAt = ready.settings.raw(key)?.toLongOrNull() ?: 0L }
    LaunchedEffect(startedAt) { while (startedAt > 0L) { now = System.currentTimeMillis(); delay(15_000) } }
    val minutes = if (startedAt > 0L) ((now - startedAt) / 60_000L).toInt().coerceAtLeast(0) else 0
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoTitle("Focus session", "Start it when you sit down to work, and stop it when you finish. The real time you spent is added to this deadline, so the progress bar is honest. You can close the app in between.", style = SibType.bodyStrong)
        if (startedAt == 0L) {
            Text(if (name != null) "$name, ready when you are." else "Ready when you are.", style = SibType.small, color = c.inkSoft)
            SibButton("Start a focus session", { scope.launch { val t = System.currentTimeMillis(); ready.settings.putRaw(key, t.toString()); startedAt = t; now = t } }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
        } else {
            Text("Working for $minutes min", style = SibType.title, color = c.verdigris)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SibButton(if (minutes >= 1) "Stop and add $minutes min" else "Stop", {
                    scope.launch { if (minutes >= 1) ready.store.addProgress(deadlineId, minutes); ready.settings.removeRaw(key); startedAt = 0L }
                }, kind = ButtonKind.PRIMARY, compact = true)
                SibButton("Cancel", { scope.launch { ready.settings.removeRaw(key); startedAt = 0L } }, kind = ButtonKind.TEXT, compact = true)
            }
        }
    }
}
