package com.hsib.sib.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hsib.sib.Ready
import com.hsib.sib.core.urgency.UrgencyState
import com.hsib.sib.domain.AppState
import com.hsib.sib.platform.KnownApps
import com.hsib.sib.platform.NotificationReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Switching on notification reading, in two steps: allow H-sib Reminder in Android's notification access list, then tick the apps.
 * The plain-language disclosure comes first, before Android's own screen, because this is access people should choose
 * knowingly.
 */
@Composable
fun ReaderScreen(st: AppState, ready: Ready, onBack: () -> Unit) {
    val c = SibTheme.colors
    val ctx = LocalContext.current
    val fmt = rememberFmt()
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(NotificationReader.isEnabled(ctx)) }
    var disclosure by remember { mutableStateOf(false) }

    // Coming back from Android's settings screen: look again.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) allowed = NotificationReader.isEnabled(ctx) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val installed = remember { KnownApps.all.filter { (pkg, _) -> runCatching { ctx.packageManager.getApplicationInfo(pkg, 0) }.isSuccess } }
    val chosen = st.settings.notificationReaderApps
    val stats by produceState(Pair(0L, 0L), chosen, allowed) {
        value = withContext(Dispatchers.IO) { (ready.settings.raw("stat.notif.found")?.toLongOrNull() ?: 0L) to (ready.settings.raw("stat.notif.last")?.toLongOrNull() ?: 0L) }
    }
    val active = allowed && chosen.isNotEmpty()

    Page("Chats and notifications", onBack) {
        Gap(8.dp)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(if (active) c.verdigrisWash else c.raised).padding(22.dp)) {
            Text(if (active) "On" else "Off", style = SibType.display, color = if (active) c.verdigris else c.ink)
            Text(
                when {
                    active -> "Reading ${chosen.size} ${if (chosen.size == 1) "app" else "apps"}"
                    allowed -> "Allowed, but no app is chosen yet"
                    else -> "H-sib Reminder is not reading any notifications"
                },
                style = SibType.body, color = c.ink,
            )
            if (active && stats.first > 0) {
                val day = Instant.ofEpochMilli(stats.second).atZone(st.zone).toLocalDate()
                Text("${stats.first} possible deadlines found, last on ${fmt.dayShort(day)}", style = SibType.small, color = c.inkSoft)
            }
        }

        SectionHeader("Access")
        if (allowed) {
            Chip("Allowed in Android", Tone.GOOD)
            Gap(8.dp)
            SibButton("Manage in Android", { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, kind = ButtonKind.TEXT)
        } else {
            ActionButton("Allow access", { disclosure = true })
            More("Android keeps this off until you switch H-sib Reminder on in its notification access list. H-sib Reminder then reads only the apps you tick below.", label = "Why is this needed?")
        }

        SectionHeader("Apps")
        if (installed.isEmpty()) {
            Text("None of the supported chat apps are installed.", style = SibType.body, color = c.inkSoft)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                installed.forEach { (pkg, label) ->
                    val on = pkg in chosen
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.raised).padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToggleRow(
                            label, null, on,
                            { scope.launch { ready.settings.update { s -> s.copy(notificationReaderApps = if (on) s.notificationReaderApps - pkg else s.notificationReaderApps + pkg) } } },
                            Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        SectionHeader("Your privacy")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Only the apps you tick", "Read on this phone, never sent", "No notification text is kept", "Nothing is added without your yes").forEach { line ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.raised).padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    UrgencyGlyph(UrgencyState.DONE, size = 22.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(line, style = SibType.bodyStrong, color = c.ink)
                }
            }
        }
        More(
            "H-sib Reminder looks at the title and text of a notification from a ticked app, finds dates in it, and files a suggestion in Review. The notification text itself is not saved. A suggestion keeps only the sentence it came from, until you accept it or it expires. Turn this off here or in Android's notification access list at any time.",
            label = "The full detail",
        )
        Gap(40.dp)
    }

    if (disclosure) {
        AlertDialog(
            onDismissRequest = { disclosure = false },
            containerColor = c.raised,
            title = { Text("Read notifications?", style = SibType.title, color = c.ink) },
            text = {
                Text(
                    "H-sib Reminder will read the text of notifications from the apps you tick, on this phone only, to find deadlines. It ignores every other app, keeps no notification text and sends nothing anywhere. Android will now ask you to switch H-sib Reminder on in a list. You can turn it off at any time.",
                    style = SibType.body, color = c.inkSoft,
                )
            },
            confirmButton = {
                TextButton({ disclosure = false; ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Continue", style = SibType.bodyStrong, color = c.violet) }
            },
            dismissButton = { TextButton({ disclosure = false }) { Text("Not now", style = SibType.body, color = c.ink) } },
        )
    }
}
