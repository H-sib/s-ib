package com.hsib.sib.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hsib.sib.Ready
import com.hsib.sib.core.motivation.Motivation
import com.hsib.sib.domain.AppState
import com.hsib.sib.platform.PhoneCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Encouraging lines: one switch for all of it, one for each place they appear, and one for each kind of deadline. */
@Composable
fun MotivationSettings(st: AppState, ready: Ready, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val s = st.settings
    var sent by remember { mutableStateOf(false) }
    fun change(f: (com.hsib.sib.data.AppSettings) -> com.hsib.sib.data.AppSettings) { scope.launch { ready.settings.update(f) } }
    Page("Motivation", onBack) {
        Explain("A short, light line to help you get going, written for the kind of deadline you have. You choose where it shows up and for which kinds. Everything here can be turned off.")
        ToggleRow("Encouraging lines", "The master switch. Off hides every line, everywhere.", s.motivationOn, { v -> change { it.copy(motivationOn = v) } })
        if (s.motivationOn) {
            Group("Where") {
                ToggleRow("On a deadline's page", "A line under the date.", s.motivationShow, { v -> change { it.copy(motivationShow = v) } })
                GroupDivider()
                ToggleRow("One quiet notification a day", "Arrives by surprise in the afternoon, makes no sound and disappears by evening. Never shown on the lock screen.", s.motivationNotify, { v ->
                    change { it.copy(motivationNotify = v) }
                })
            }
            Group("For which deadlines") {
                Motivation.Group.values().forEachIndexed { i, g ->
                    if (i > 0) GroupDivider()
                    ToggleRow(g.label, null, g.name !in s.motivationOff, { on -> change { it.copy(motivationOff = if (on) it.motivationOff - g.name else it.motivationOff + g.name) } })
                }
            }
            SibButton(if (sent) "Sent. Look in the notification bar" else "Show me one now", { scope.launch { ready.reminders.fireMotivation(force = true); sent = true } }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** Reads the next two months from the calendar apps on the phone, only after permission, and only into Review. */
@Composable
fun CalendarSettings(st: AppState, ready: Ready, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(PhoneCalendar.allowed(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    Page("Calendar", onBack) {
        Explain("People write plans in Samsung Calendar and Google Calendar. With your permission, H-sib Reminder can read the next ${PhoneCalendar.DAYS} days of entries from the calendars on this phone and offer them in Review. Nothing is added until you confirm it, nothing is written to your calendar, and nothing leaves your phone.")
        Group("Access") {
            ExpandItem("Allowed", if (allowed) "Yes. You can take it back any time in Android settings, Apps, H-sib Reminder, Permissions." else "Not yet.")
        }
        if (!allowed) {
            SibButton("Allow calendar access", { ask.launch(android.Manifest.permission.READ_CALENDAR) }, Modifier.fillMaxWidth())
        } else {
            SibButton("Read my calendar now", {
                scope.launch {
                    val items = withContext(Dispatchers.IO) { PhoneCalendar.read(context) }
                    val result = ready.importer.fromCalendarItems(items)
                    message = if (result.saved.isEmpty()) "Nothing new was found." else "${result.saved.size} suggestions are waiting in Review."
                }
            }, Modifier.fillMaxWidth())
        }
        message?.let { Text(it, style = SibType.body, color = SibTheme.colors.ink) }
        Spacer(Modifier.height(32.dp))
    }
}
