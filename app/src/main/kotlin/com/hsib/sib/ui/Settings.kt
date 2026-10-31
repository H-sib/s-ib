package com.hsib.sib.ui

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.hsib.sib.BuildConfig
import com.hsib.sib.Ready
import com.hsib.sib.core.backup.RecoveryKey
import com.hsib.sib.core.backup.Secret
import com.hsib.sib.core.time.DateOrder
import com.hsib.sib.core.urgency.UrgencyState
import com.hsib.sib.data.AppSettings
import com.hsib.sib.data.DataCounts
import com.hsib.sib.data.LockScreenDetail
import com.hsib.sib.data.ThemeMode
import com.hsib.sib.domain.AppState
import com.hsib.sib.social.SocialState
import com.hsib.sib.platform.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle

// ---- small building blocks ---------------------------------------------------------------------

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, hint: String? = null) {
    val c = SibTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = SibType.body, color = c.ink)
            if (hint != null) Text(hint, style = SibType.small, color = c.inkSoft)
        }
        SibButton("−", onMinus, kind = ButtonKind.QUIET, modifier = Modifier.width(52.dp))
        Text(value, style = SibType.bodyStrong, color = c.ink, modifier = Modifier.width(78.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        SibButton("+", onPlus, kind = ButtonKind.QUIET, modifier = Modifier.width(52.dp))
    }
}

@Composable
fun Explain(text: String) = More(text)

private fun fmtMin(m: Int) = if (m < 60) "$m min" else if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60} min"

private fun LocalTime.hhmm() = "%02d:%02d".format(hour, minute)

// ---- root ---------------------------------------------------------------------------------------

@Composable
fun SettingsHome(st: AppState, social: SocialState? = null, onNav: (String) -> Unit) {
    val c = SibTheme.colors
    Column(Modifier.fillMaxWidth().cuedVerticalScroll().padding(horizontal = 24.dp)) {
        Text("Settings", style = SibType.display, color = c.ink, modifier = Modifier.padding(top = 20.dp, bottom = 20.dp))
        Group(null, Modifier.padding(top = 0.dp)) {
            NavItem("Guide", "Everything the app can do, in a few lines", { onNav("settings/guide") })
        }
        if (social != null) {
            Group("Account") {
                NavItem("Friends", social.user?.let { "Signed in as ${it.displayName}" } ?: "Share a deadline and chat about it", { onNav("friends") })
            }
        }
        Group("Daily use") {
            NavItem("Reminders", "Your time each day, quiet hours, how many reminders", { onNav("settings/reminders") })
            GroupDivider()
            NavItem("Chats and notifications", if (st.settings.notificationReaderApps.isEmpty()) "Find deadlines in your chats" else "Reading ${st.settings.notificationReaderApps.size} apps", { onNav("settings/reader") })
            GroupDivider()
            NavItem("Notification dashboard", null, { onNav("settings/notifications") }, trailing = if (st.settings.todayNotification) "On" else "Off")
            GroupDivider()
            NavItem("Close ones", null, { onNav("settings/close") }, trailing = if (st.settings.closeOnesEnabled) "On" else "Hidden")
            GroupDivider()
            NavItem("Medical student", "Exam types, subjects and a class diary", { onNav("settings/study") }, trailing = if (st.settings.medicalMode) "On" else "Off")
            GroupDivider()
            NavItem("Appearance", "Theme, motion, the opening", { onNav("settings/appearance") })
        }
        Group("Data and safety") {
            NavItem("Backup and restore", null, { onNav("settings/backup") }, trailing = if (st.settings.backupFolder != null) "On" else "Not set up")
            GroupDivider()
            NavItem("Security", null, { onNav("settings/security") }, trailing = if (st.settings.appLock) "App lock on" else "App lock off")
            GroupDivider()
            NavItem("Privacy and data", "What is stored and which permissions it has", { onNav("settings/privacy") })
            GroupDivider()
            NavItem("Sources", "What it can read, and what it keeps", { onNav("settings/sources") })
            GroupDivider()
            NavItem("Terms and security", "What you agreed to, and how data is protected", { onNav("settings/terms") })
        }
        Group("Help") {
            NavItem("Reminder health check", "Make sure reminders can reach you", { onNav("settings/health") })
            GroupDivider()
            NavItem("Send feedback", "Tell the maker what to improve", { onNav("settings/feedback") })
            GroupDivider()
            NavItem("About", null, { onNav("settings/about") }, trailing = BuildConfig.VERSION_NAME)
        }
        Spacer(Modifier.height(32.dp))
    }
}

// ---- reminders ----------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemindersSettings(st: AppState, ready: Ready, onBack: () -> Unit) {
    val s = st.settings
    val scope = rememberCoroutineScope()
    fun update(f: (AppSettings) -> AppSettings) { scope.launch { ready.settings.update(f) } }
    val locale = LocalConfiguration.current.locales[0]
    Page("Reminders", onBack) {
        SectionHeader("Your time")
        Explain("How much time you can usually give to your deadlines. H-sib Reminder spreads the work inside this, and tells you when it does not fit.")
        Stepper("On a normal day", fmtMin(s.weekdayMinutes), { update { it.copy(weekdayMinutes = (s.weekdayMinutes - 15).coerceAtLeast(0)) } }, { update { it.copy(weekdayMinutes = (s.weekdayMinutes + 15).coerceAtMost(960)) } })
        Stepper("On a rest day", fmtMin(s.weekendMinutes), { update { it.copy(weekendMinutes = (s.weekendMinutes - 15).coerceAtLeast(0)) } }, { update { it.copy(weekendMinutes = (s.weekendMinutes + 15).coerceAtMost(960)) } })
        Text("Rest days", style = SibType.bodyStrong, color = SibTheme.colors.ink, modifier = Modifier.padding(top = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            for (d in DayOfWeek.values()) {
                SelectChip(d.getDisplayName(TextStyle.SHORT, locale), d in s.weekend, { update { cur -> cur.copy(weekend = if (d in cur.weekend) cur.weekend - d else cur.weekend + d) } })
            }
        }
        Explain("Weekends differ around the world, so you choose which days count as rest days.")
        Text("Week starts on", style = SibType.bodyStrong, color = SibTheme.colors.ink, modifier = Modifier.padding(top = 6.dp))
        Choice(listOf(DayOfWeek.MONDAY to "Monday", DayOfWeek.SATURDAY to "Saturday", DayOfWeek.SUNDAY to "Sunday"), s.weekStart, { update { cur -> cur.copy(weekStart = it) } })
        Text("A date like 03/04 means", style = SibType.bodyStrong, color = SibTheme.colors.ink, modifier = Modifier.padding(top = 14.dp))
        Choice(listOf(DateOrder.AUTO to "Phone default", DateOrder.DMY to "Day first", DateOrder.MDY to "Month first"), s.dateOrder, { update { cur -> cur.copy(dateOrder = it) } })
        Explain("Used when a shared notice is ambiguous. H-sib Reminder always shows you the other reading too.")

        SectionHeader("How often it speaks")
        Stepper("Reminders per day", s.dailyBudget.toString(), { update { it.copy(dailyBudget = (s.dailyBudget - 1).coerceAtLeast(0)) } }, { update { it.copy(dailyBudget = (s.dailyBudget + 1).coerceAtMost(10)) } },
            hint = "Things due that day are never counted. The rest wait in the daily summary.")
        Stepper("Last reminder before a timed deadline", fmtMin(s.finalLeadMinutes), { update { it.copy(finalLeadMinutes = (s.finalLeadMinutes - 30).coerceAtLeast(30)) } }, { update { it.copy(finalLeadMinutes = (s.finalLeadMinutes + 30).coerceAtMost(1440)) } })
        ToggleRow("Check in on things past due", "One calm reminder the day after.", s.remindOverdue, { update { cur -> cur.copy(remindOverdue = it) } })
        ToggleRow("Daily summary", "One quiet notification with the day at a glance.", s.digestEnabled, { update { cur -> cur.copy(digestEnabled = it) } })
        if (s.digestEnabled) {
            ToggleRow("Learn when I open the app", "Sends the summary at the hour you usually look. Learned on this phone only.", s.learnDigestTime, { update { cur -> cur.copy(learnDigestTime = it) } })
            if (!s.learnDigestTime) Stepper("Summary time", "%02d:00".format(s.digestHour), { update { it.copy(digestHour = (s.digestHour + 23) % 24) } }, { update { it.copy(digestHour = (s.digestHour + 1) % 24) } })
        }

        SectionHeader("Quiet hours")
        Explain("Nothing makes a sound between these times. A reminder that would land inside is moved to the minute quiet hours end, or to the minute they begin if the deadline is too close.")
        Stepper("Starts", s.quietStart.hhmm(), { update { it.copy(quietStart = s.quietStart.minusMinutes(30)) } }, { update { it.copy(quietStart = s.quietStart.plusMinutes(30)) } })
        Stepper("Ends", s.quietEnd.hhmm(), { update { it.copy(quietEnd = s.quietEnd.minusMinutes(30)) } }, { update { it.copy(quietEnd = s.quietEnd.plusMinutes(30)) } })
        Stepper("Morning reminders at", s.morning.hhmm(), { update { it.copy(morning = s.morning.minusMinutes(30)) } }, { update { it.copy(morning = s.morning.plusMinutes(30)) } })
        Spacer(Modifier.height(24.dp))
        Explain("Reminders use the phone's alarm system and may arrive a few minutes late when battery saving is on. The health check shows how to make them more dependable.")
        Spacer(Modifier.height(24.dp))
    }
}

// ---- appearance ----------------------------------------------------------------------------------

@Composable
fun AppearanceSettings(st: AppState, ready: Ready, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val systemDark = isSystemInDarkTheme()
    val current = st.settings.theme
    val shown = if (current == ThemeMode.SYSTEM) (if (systemDark) ThemeMode.DARK else ThemeMode.LIGHT) else current
    fun choose(mode: ThemeMode) { scope.launch { ready.settings.update { it.copy(theme = mode) } } }
    Page("Appearance", onBack) {
        SectionHeader("Theme")
        ToggleRow(
            "Follow my phone", "Light by day, Twilight by night", current == ThemeMode.SYSTEM,
            { on -> choose(if (on) ThemeMode.SYSTEM else shown) },
        )
        Gap(12.dp)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(listOf(ThemeMode.LIGHT, ThemeMode.DARK), listOf(ThemeMode.BLACK, ThemeMode.NEON)).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { mode -> ThemeSwatch(mode, mode == shown, { choose(mode) }, Modifier.weight(1f)) }
                }
            }
        }
        SectionHeader("Today style")
        Choice(com.hsib.sib.data.HomeStyle.values().map { it to it.label }, st.settings.homeStyle, { v -> scope.launch { ready.settings.update { it.copy(homeStyle = v) } } })
        Text(st.settings.homeStyle.hint, style = SibType.small, color = SibTheme.colors.inkSoft)
        SectionHeader("Screen privacy")
        ToggleRow("Block screenshots", "Also hides the app in the recent apps list. Off by default.", st.settings.hideInRecents, { v -> scope.launch { ready.settings.update { it.copy(hideInRecents = v) } } })
        SectionHeader("Motion")
        ToggleRow("Reduce motion", "Skips the filling rings and other animations. H-sib Reminder also follows your phone's Remove animations setting.", st.settings.reduceMotion, { v -> scope.launch { ready.settings.update { it.copy(reduceMotion = v) } } })
        SectionHeader("Opening")
        Choice(com.hsib.sib.data.IntroSpeed.values().map { it to it.label }, st.settings.introSpeed, { v -> scope.launch { ready.settings.update { it.copy(introSpeed = v) } } })
        Gap(40.dp)
    }
}

/** A small picture of a theme: its base, a hero card with a graded ring, and the signature pill. */
@Composable
private fun ThemeSwatch(mode: ThemeMode, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ui = SibTheme.colors
    val p = paletteFor(mode)
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier.clip(shape).background(ui.raised).then(if (selected) Modifier.border(2.dp, ui.violet, shape) else Modifier)
            .bounceClick(role = Role.RadioButton, onClick = onClick).padding(10.dp)
            .semantics { contentDescription = mode.label + (if (selected) ", selected" else "") },
    ) {
        Canvas(Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(18.dp))) {
            drawRect(p.paper)
            val pad = 8.dp.toPx()
            val card = Size(size.width - pad * 2, 58.dp.toPx())
            drawRoundRect(p.raised, Offset(pad, pad), card, CornerRadius(14.dp.toPx()))
            val ringCenter = Offset(pad + 30.dp.toPx(), pad + card.height / 2)
            drawCircle(p.lifted, 16.dp.toPx(), ringCenter, style = Stroke(6.dp.toPx()))
            drawArc(
                Brush.sweepGradient(listOf(p.glowA, p.glowB, p.glowA), ringCenter), -90f, 250f, false,
                Offset(ringCenter.x - 16.dp.toPx(), ringCenter.y - 16.dp.toPx()), Size(32.dp.toPx(), 32.dp.toPx()), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
            )
            drawRoundRect(p.ink, Offset(pad + 64.dp.toPx(), pad + 16.dp.toPx()), Size(54.dp.toPx(), 6.dp.toPx()), CornerRadius(3.dp.toPx()))
            drawRoundRect(p.inkSoft, Offset(pad + 64.dp.toPx(), pad + 30.dp.toPx()), Size(34.dp.toPx(), 5.dp.toPx()), CornerRadius(3.dp.toPx()))
            drawRoundRect(p.action, Offset(size.width - pad - 56.dp.toPx(), size.height - pad - 22.dp.toPx()), Size(56.dp.toPx(), 22.dp.toPx()), CornerRadius(11.dp.toPx()))
            drawRoundRect(p.lifted, Offset(pad, size.height - pad - 22.dp.toPx()), Size(42.dp.toPx(), 22.dp.toPx()), CornerRadius(8.dp.toPx()))
        }
        Spacer(Modifier.height(10.dp))
        Text(mode.label, style = SibType.title, color = ui.ink, modifier = Modifier.padding(horizontal = 6.dp))
    }
}

// ---- close ones -------------------------------------------------------------------------------------

@Composable
fun CloseOnesSettings(st: AppState, ready: Ready, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    Page("Close ones", onBack) {
        Explain("A private space for the dates that matter to the people closest to you: anniversaries, birthdays, date nights and gifts. It has its own colours and its own gentle reminders.")
        ToggleRow("Show Close ones on Today", "Turn this off to hide the entry. Your dates stay saved and their reminders keep working.", st.settings.closeOnesEnabled, { v -> scope.launch { ready.settings.update { it.copy(closeOnesEnabled = v) } } })
        Group("How it is kept private") {
            ExpandItem("Lock screen", "Reminders from Close ones never show a title or a name on the lock screen, whatever you chose for deadlines. They read \"A reminder\" until you unlock.")
            GroupDivider()
            ExpandItem("Everywhere else", "Close ones are left out of the widget, the Quick Settings tile, the daily summary, the calendar export and sharing.")
            GroupDivider()
            ExpandItem("Backups", "They are included in your encrypted backup, so a new phone gets them back.")
        }
        Spacer(Modifier.height(32.dp))
    }
}

// ---- notifications --------------------------------------------------------------------------------

@Composable
fun NotificationSettings(st: AppState, ready: Ready, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Page("Notifications and privacy", onBack) {
        SectionHeader("Lock screen")
        Choice(listOf(LockScreenDetail.HIDDEN to "Hide details", LockScreenDetail.TITLES to "Show titles"), st.settings.lockScreenDetail, { v -> scope.launch { ready.settings.update { it.copy(lockScreenDetail = v) } } })
        Explain("With details hidden, a locked phone only shows that a reminder exists. The same choice applies to the home-screen widget and the Quick Settings tile.")
        SectionHeader("Notification bar card")
        ToggleRow("Show my next deadline", "A card in the notification bar whenever something needs you. Swipe it away whenever you like; it comes back tomorrow, or sooner if the date gets closer.", st.settings.todayNotification, { v -> scope.launch { ready.settings.update { it.copy(todayNotification = v) } } })
        Gap(8.dp)
        DashboardPreview(3, "Signals lab report", "Fri, Oct 16  ·  in 3 days", expanded = true)
        Explain("The colour warms from violet to amber to coral as the date gets close, and one step faster for a critical deadline. Pull the card down to see the next deadlines, Open and Done.")
        Gap(8.dp)
        var confirmKeep by remember { mutableStateOf(false) }
        ToggleRow("Keep it after Clear all", "Stays even when you clear all notifications, so your next deadline is always one pull away. It is your choice, and you can turn it off here. Newer versions of Android may still let you swipe it away.", st.settings.todayPinned, { v -> if (v) confirmKeep = true else scope.launch { ready.settings.update { it.copy(todayPinned = false) } } })
        if (confirmKeep) {
            val c = SibTheme.colors
            AlertDialog(
                onDismissRequest = { confirmKeep = false }, containerColor = c.raised,
                title = { Text("Keep the card after Clear all?", style = SibType.title, color = c.ink) },
                text = { Text("Clear all will no longer remove it. You can turn this off here at any time, and the card still disappears by itself when nothing needs you.", style = SibType.body, color = c.inkSoft) },
                confirmButton = { TextButton({ confirmKeep = false; scope.launch { ready.settings.update { it.copy(todayPinned = true) } } }) { Text("Keep it", style = SibType.bodyStrong, color = c.violet) } },
                dismissButton = { TextButton({ confirmKeep = false }) { Text("Not now", style = SibType.body, color = c.ink) } },
            )
        }
        SectionHeader("Quick Settings tile and widget")
        Explain("Pull the shade down twice, tap the pencil, and drag the H-sib Reminder tile into your panel. Long-press your home screen, choose Widgets, and add Next up.")
        Spacer(Modifier.height(8.dp))
        SibButton("Open system notification settings", {
            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
    }
}

// ---- sources ---------------------------------------------------------------------------------------

@Composable
fun SourcesSettings(st: AppState, ready: Ready, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val dismissed by produceState(0, st.pending.size) { value = withContext(Dispatchers.IO) { ready.store.counts().dismissed } }
    Page("Sources", onBack) {
        Text("It only reads what you hand it, on this phone. Nothing here needs the internet.", style = SibType.body, color = SibTheme.colors.inkSoft)
        Group("What you can hand it") {
            ExpandItem("Share from any app", "Text, screenshots, photos and PDFs. Choose H-sib Reminder in the share menu.")
            GroupDivider()
            ExpandItem("Copied messages", "Tap Paste when adding. H-sib Reminder reads the clipboard only when you tap it.")
            GroupDivider()
            ExpandItem("Photos and scans", "Pick a picture, or scan a notice. Text is read on the phone and the picture is not kept.")
            GroupDivider()
            ExpandItem("Documents", "PDF, Word and Excel files, read with strict size limits.")
            GroupDivider()
            ExpandItem("Calendar files", ".ics files give exact dates and times.")
            GroupDivider()
            ExpandItem("Chat exports", "Export one chat from a messaging app and open it in H-sib Reminder. Relative dates like tomorrow are read against when each message was sent, and sender names are not kept.")
        }
        Group("What it will not do") {
            TextItem("It does not read your messages, email or notifications in the background unless you switch that on, and it has no accessibility service. Those routes need powers that are not worth giving to a reminder app.")
        }
        Group("What it keeps") {
            GroupPad { Stepper("Forget old suggestions after", "${st.settings.candidateTtlDays} days", { scope.launch { ready.settings.update { it.copy(candidateTtlDays = (st.settings.candidateTtlDays - 7).coerceAtLeast(7)) } } }, { scope.launch { ready.settings.update { it.copy(candidateTtlDays = (st.settings.candidateTtlDays + 7).coerceAtMost(90)) } } }) }
            GroupDivider()
            GroupPad { ToggleRow("Keep the sentence as evidence by default", "Off by default. You can choose per deadline too.", st.settings.keepExcerptsByDefault, { v -> scope.launch { ready.settings.update { it.copy(keepExcerptsByDefault = v) } } }) }
            GroupDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Dismissed suggestions remembered: $dismissed", style = SibType.body, color = SibTheme.colors.ink, modifier = Modifier.weight(1f))
                if (dismissed > 0) SibButton("Forget", { scope.launch(Dispatchers.IO) { ready.db.dismissed().clear() } }, kind = ButtonKind.QUIET, compact = true)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Only an unreadable fingerprint is kept for each dismissed suggestion, so it is not shown twice. Never the text.", style = SibType.small, color = SibTheme.colors.inkSoft)
        Spacer(Modifier.height(32.dp))
    }
}

// ---- security ---------------------------------------------------------------------------------------

@Composable
fun SecuritySettings(st: AppState, ready: Ready, onBack: () -> Unit, onEnableLock: (Boolean) -> Unit) {
    val c = SibTheme.colors
    Page("Security", onBack) {
        Group("App lock") {
            GroupPad { ToggleRow("Ask for fingerprint, face or screen lock", "Shown when you return to the app after a short break.", st.settings.appLock, onEnableLock) }
        }
        Spacer(Modifier.height(8.dp))
        Text("The lock keeps people who pick up your phone out of the app. It is a screen in front of the app, not the encryption. Your data is encrypted whether the lock is on or off.", style = SibType.small, color = c.inkSoft)
        Group("What protects your data") {
            ExpandItem("On this phone", "The database is encrypted with AES-256. Its key is wrapped by a key that never leaves the phone's secure hardware where the phone has it.")
            GroupDivider()
            ExpandItem("In backups", "Each backup is encrypted on this phone before it is saved anywhere. The storage app only receives scrambled data.")
            GroupDivider()
            ExpandItem("Over the network", if (BuildConfig.FIREBASE_READY) "The internet is used only if you sign in to share a deadline. What you share is sealed on your phone first, so the server cannot read it." else "This version asks for no internet permission, so it cannot send anything by itself.")
            GroupDivider()
            ExpandItem("History", "Every change to a deadline is chained to the one before it, so changes that were not recorded can be detected.")
            GroupDivider()
            ExpandItem(
                "The honest limits",
                "No app can protect you from a phone that is already compromised, from someone who knows your screen lock and your recovery key, or from malware that reads the screen while the app is open. " +
                    "If you lose both your phone and your recovery key, your backup cannot be opened by anyone, including the developer. That is by design.",
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---- privacy and data -------------------------------------------------------------------------------

private fun friendlyPermission(p: String): String? = when (p) {
    "android.permission.POST_NOTIFICATIONS" -> "Show reminders"
    "android.permission.RECEIVE_BOOT_COMPLETED" -> "Restore reminders after a restart"
    "android.permission.USE_BIOMETRIC", "android.permission.USE_FINGERPRINT" -> "Fingerprint or face for the optional app lock"
    "android.permission.WAKE_LOCK" -> "Finish a task such as a backup"
    "android.permission.FOREGROUND_SERVICE" -> null
    else -> null
}

@Composable
fun PrivacyScreen(st: AppState, ready: Ready, onBack: () -> Unit, onWiped: () -> Unit) {
    val c = SibTheme.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val counts by produceState<DataCounts?>(null, st.items.size, st.pending.size) { value = withContext(Dispatchers.IO) { ready.store.counts() } }
    val stats by produceState(0L to 0L) {
        value = withContext(Dispatchers.IO) { (ready.settings.raw("stat.imports")?.toLongOrNull() ?: 0L) to (ready.settings.raw("stat.messages")?.toLongOrNull() ?: 0L) }
    }
    val requested = remember {
        val pm = ctx.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        else @Suppress("DEPRECATION") pm.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
        info.requestedPermissions?.toList().orEmpty()
    }
    val hasInternet = requested.any { it == "android.permission.INTERNET" }
    var confirmText by remember { mutableStateOf("") }
    var wiping by remember { mutableStateOf(false) }

    Page("Privacy and data", onBack) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(if (hasInternet) c.ochreWash else c.verdigrisWash).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            UrgencyGlyph(if (hasInternet) UrgencyState.START_SOON else UrgencyState.DONE, size = 28.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(if (hasInternet) "This build can use the internet" else "Internet permission: not requested", style = SibType.bodyStrong, color = c.ink)
                Text("Checked live from the installed app, not from a promise.", style = SibType.small, color = c.inkSoft)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Data leaves this phone only through two doors you open yourself: the backup folder you choose, and files you export or share.", style = SibType.body, color = c.inkSoft)

        Group("What it holds right now") {
            val k = counts
            if (k == null) TextItem("Counting") else {
                ValueItem("Deadlines", k.obligations.toString()); GroupDivider()
                ValueItem("Suggestions waiting", k.candidates.toString()); GroupDivider()
                ValueItem("Steps", k.steps.toString()); GroupDivider()
                ValueItem("Receipts kept", k.receipts.toString()); GroupDivider()
                ValueItem("Sentences kept as evidence", k.excerpts.toString()); GroupDivider()
                ValueItem("History entries", k.historyEntries.toString())
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Since you installed it, it has read ${stats.first} shared items and ${stats.second} chat messages, and kept none of the original text or images.", style = SibType.small, color = c.inkSoft)

        Group("Permissions") {
            val listed = requested.mapNotNull(::friendlyPermission)
            if (listed.isEmpty()) TextItem("None beyond the basics.")
            listed.forEachIndexed { i, p -> if (i > 0) GroupDivider(); TextItem(p, strong = true) }
        }
        Spacer(Modifier.height(8.dp))
        Text("It has no permission for contacts, messages, location, the calendar, the microphone or storage. Files are opened through the system file picker, which only shares the file you pick.", style = SibType.small, color = c.inkSoft)

        Group("Where your data can go") {
            ExpandItem("Your backup folder", "Only if you turn on backup. Encrypted before it leaves the app. Whatever app provides that folder cannot read it.")
            GroupDivider()
            ExpandItem("Files you export", "A calendar file is plain text: anyone who gets it can read it. An encrypted export needs your passphrase.")
            GroupDivider()
            ExpandItem("Android's own backup", "Switched off. The app's keys cannot be restored onto another phone, so its backup is its own.")
        }

        Group("Delete everything") { TextItem("Removes all deadlines, history, receipts and settings from this phone and turns off backup. Backups you already made stay in your folder until you delete them there.") }
        Spacer(Modifier.height(12.dp))
        if (!wiping) SibButton("Delete everything", { wiping = true }, Modifier.fillMaxWidth(), ButtonKind.DANGER) else {
            Field("Type DELETE to confirm", confirmText, { confirmText = it }, keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done))
            Spacer(Modifier.height(10.dp))
            SibButton("Delete everything now", {
                scope.launch(Dispatchers.IO) {
                    ready.store.wipeAll()
                    ready.backup.forgetKey()
                    ctx.getSystemService(NotificationManager::class.java).cancelAll()
                    withContext(Dispatchers.Main) { onWiped() }
                }
            }, Modifier.fillMaxWidth(), ButtonKind.DANGER, enabled = confirmText.trim() == "DELETE")
        }
        Spacer(Modifier.height(32.dp))
    }
}

// ---- reminder health check ------------------------------------------------------------------------

@Composable
fun HealthScreen(st: AppState, ready: Ready, onBack: () -> Unit) {
    val c = SibTheme.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    val nm = ctx.getSystemService(NotificationManager::class.java)
    val enabled = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    val blocked = listOf(Notifier.CH_REMINDERS, Notifier.CH_URGENT).any { nm.getNotificationChannel(it)?.importance == NotificationManager.IMPORTANCE_NONE }
    val dnd = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL && nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    val pm = ctx.getSystemService(PowerManager::class.java)
    val unrestricted = pm.isIgnoringBatteryOptimizations(ctx.packageName)
    val upcoming by produceState(0, tick, st.items.size) { value = withContext(Dispatchers.IO) { ready.db.reminders().scheduled().count { !it.digestOnly } } }
    val missed by produceState<List<Pair<String, Long>>>(emptyList(), tick) {
        value = withContext(Dispatchers.IO) { ready.reminders.missed().map { r -> (st.items.firstOrNull { it.o.id == r.obligationId }?.o?.title ?: "A deadline") to r.fireAt } }
    }
    var testSent by remember { mutableStateOf(false) }

    Page("Reminder health check", onBack) {
        Text("A reminder only helps if it can reach you. These checks look at your phone's settings, which can silently stop apps from notifying you.", style = SibType.body, color = c.inkSoft)
        Group("Checks") {
            HealthRow("Notifications are allowed", enabled, if (enabled) "H-sib Reminder can show reminders." else "Android is blocking all notifications from H-sib Reminder."); GroupDivider()
            HealthRow("Reminder channels are on", !blocked, if (!blocked) "Both reminder channels can notify." else "A reminder channel is switched off in system settings."); GroupDivider()
            HealthRow("Do Not Disturb is off", !dnd, if (!dnd) "Nothing is silencing notifications right now." else "Do Not Disturb is on, so reminders may be silent until it ends.", warnOnly = true); GroupDivider()
            HealthRow("Battery saving will not hold back reminders", unrestricted, if (unrestricted) "The app is exempt from battery limits." else "Battery saving can delay reminders by several minutes. Some phone makers also close apps that are not in use; if reminders stop, look for an Autostart or Protected apps setting.", warnOnly = true); GroupDivider()
            HealthRow("Reminders are queued", upcoming > 0 || st.active.isEmpty(), if (upcoming > 0) "$upcoming upcoming reminders are scheduled." else if (st.active.isEmpty()) "Nothing is waiting, because there are no active deadlines." else "None are queued yet. Reopen the app and try again.")
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SibButton("Notification settings", { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)) }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
            SibButton("Battery settings", { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
        }
        SectionHeader("Try it")
        Explain("Send yourself a test reminder in 15 seconds. Lock the phone and wait. If it does not arrive, one of the settings above is the reason.")
        SibButton(if (testSent) "Sent. Lock your phone and wait" else "Send a test reminder", { scope.launch(Dispatchers.IO) { ready.reminders.scheduleTest(15); testSent = true } }, Modifier.fillMaxWidth())
        SectionHeader("You may have missed")
        if (missed.isEmpty()) Text("Nothing missed.", style = SibType.body, color = c.inkSoft) else {
            missed.forEach { (t, at) -> Text("• $t, planned for ${rememberFmt().dayShort(java.time.Instant.ofEpochMilli(at).atZone(st.zone).toLocalDate())}", style = SibType.body, color = c.ink) }
            Spacer(Modifier.height(8.dp))
            SibButton("Clear this list", { scope.launch(Dispatchers.IO) { ready.reminders.clearMissed(); tick++ } }, kind = ButtonKind.QUIET)
        }
        Spacer(Modifier.height(32.dp))
    }
    LaunchedEffect(Unit) { tick++ }
}

@Composable
private fun HealthRow(title: String, ok: Boolean, detail: String, warnOnly: Boolean = false) {
    val c = SibTheme.colors
    val state = if (ok) UrgencyState.DONE else if (warnOnly) UrgencyState.START_SOON else UrgencyState.OVERDUE
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.Top) {
        UrgencyGlyph(state, size = 24.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = SibType.bodyStrong, color = c.ink)
            Text(detail, style = SibType.small, color = c.inkSoft)
        }
    }
}

// ---- about ---------------------------------------------------------------------------------------------

@Composable
fun AboutScreen(onBack: () -> Unit, onTerms: () -> Unit = {}) {
    val c = SibTheme.colors
    val ctx = LocalContext.current
    var showNotices by remember { mutableStateOf(false) }
    var showLicense by remember { mutableStateOf(false) }
    Page("About", onBack) {
        Gap(8.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            HsibMark(72.dp)
            Spacer(Modifier.width(18.dp))
            Column {
                AppName(size = 26.sp)
                Text("Version ${BuildConfig.VERSION_NAME}", style = SibType.body, color = c.inkSoft)
                Text("Made by H-sib", style = SibType.small, color = c.inkSoft)
            }
        }
        Group("How it works") {
            ExpandItem("Finding dates", "It reads the text you hand it with rules and a very small model that runs on your phone. It tells you why it picked each date, and it ignores publication dates, history and sale offers.")
            GroupDivider()
            ExpandItem("Planning", "It works out the latest day you can start, spreads the work over the days you have, and says exactly how short you are when it does not fit.")
            GroupDivider()
            ExpandItem("Reminding", "Few reminders, placed with care, never inside quiet hours. A confirmed deadline never changes by itself.")
            GroupDivider()
            ExpandItem("The name", "In H-sib, the letters s, i and b stand for Start It Before. They are also the closing sound of the maker's name, Hasib, which can mean one who counts. A sib is a brother or sister, the spirit of Close ones.")
        }
        Group("Credits") {
            ExpandItem("Dr. Muser", "Closed tester, and the voice behind the medical student version. She explained how an MBBS course works: the profs, the cards, items and terms, the practicals and the class diary. Her feedback also shaped version 1.0.1 and 1.1.0: the month view, clearer wording, parts with a battery, more deadlines on Today, your own plan and Friend in Close ones. Thank you.")
        }
        Group("Legal") {
            NavItem("Privacy policy", "Opens in your browser", { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://h-sib.github.io/s-ib/privacy.html"))) } })
            GroupDivider()
            NavItem("Terms of use", "Opens in your browser", { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://h-sib.github.io/s-ib/terms.html"))) } })
            GroupDivider()
            NavItem("Terms and security", null, onTerms)
            GroupDivider()
            NavItem("Open source notices", null, { showNotices = true })
        }
        Spacer(Modifier.height(32.dp))
    }
    if (showNotices) {
        AlertDialog(
            onDismissRequest = { showNotices = false }, containerColor = c.raised,
            title = { Text("Open source notices", style = SibType.title, color = c.ink) },
            text = {
                Text(
                    "Built on AndroidX and Jetpack Compose, Room, WorkManager and Glance (Apache License 2.0), Google Tink (Apache License 2.0), SQLCipher (BSD-style licence), Google ML Kit text recognition and document scanning (Google terms), Firebase and Google Play services (Google terms), and kotlinx libraries (Apache License 2.0).\n\nThe Sora typeface is by The Sora Project Authors under the SIL Open Font License 1.1.",
                    style = SibType.body, color = c.inkSoft, modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton({ showNotices = false }) { Text("Close", style = SibType.bodyStrong, color = c.ink) } },
            dismissButton = { TextButton({ showNotices = false; showLicense = true }) { Text("Font licence", style = SibType.body, color = c.ink) } },
        )
    }
    if (showLicense) {
        val text = remember { ctx.assets.open("licenses/Sora-OFL.txt").bufferedReader().use { it.readText() } }
        AlertDialog(
            onDismissRequest = { showLicense = false }, containerColor = c.raised,
            title = { Text("SIL Open Font License", style = SibType.title, color = c.ink) },
            text = { Text(text, style = SibType.small, color = c.inkSoft, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton({ showLicense = false }) { Text("Close", style = SibType.bodyStrong, color = c.ink) } },
        )
    }
}

// ---- backup and restore ----------------------------------------------------------------------------

@Composable
fun BackupScreen(st: AppState, ready: Ready, onBack: () -> Unit, onRestore: () -> Unit) {
    val c = SibTheme.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val fmt = rememberFmt()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var settingUp by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf<String?>(null) }
    var passphraseFor by remember { mutableStateOf<String?>(null) }
    val s = st.settings
    val configured = s.backupFolder != null && ready.backup.hasKey()

    val exportIcs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        if (uri != null) scope.launch { message = ready.backup.exportIcs(uri).message }
    }
    var passphrase by remember { mutableStateOf("") }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch { message = ready.backup.exportFile(uri, Secret.Passphrase(passphrase.toCharArray())).message; passphrase = "" }
    }

    if (settingUp) { BackupSetup(ready, { settingUp = false }, { settingUp = false; message = it }); return }

    Page("Backup and restore", onBack) {
        Explain("Backups are encrypted on this phone before they are saved. You choose where they go with the system file picker, so any storage app, including Google Drive, can hold them without being able to read them.")
        if (!configured) {
            SectionHeader("Not set up")
            Explain("Without a backup, losing this phone means losing your deadlines. Setting it up takes about a minute and gives you a recovery key to keep somewhere safe.")
            SibButton("Set up encrypted backup", { settingUp = true }, Modifier.fillMaxWidth())
        } else {
            SectionHeader("Status")
            Text(if (s.backupLastAt > 0) "Last backup: ${fmt.dayShort(java.time.Instant.ofEpochMilli(s.backupLastAt).atZone(st.zone).toLocalDate())} (number ${s.backupSeq})" else "No backup made yet", style = SibType.body, color = c.ink)
            Text(runCatching { Uri.parse(s.backupFolder).lastPathSegment }.getOrNull() ?: "", style = SibType.small, color = c.inkSoft)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SibButton(if (busy) "Working" else "Back up now", { busy = true; scope.launch { message = ready.backup.backupNow().message; busy = false } }, Modifier.weight(1f), enabled = !busy)
                SibButton("Verify", { busy = true; scope.launch { val r = ready.backup.verify(); message = r.headline + "\n" + r.details.joinToString("\n"); busy = false } }, Modifier.weight(1f), ButtonKind.QUIET, enabled = !busy)
            }
            ToggleRow("Back up automatically", "About once a day, when something has changed.", s.backupAuto, { v -> scope.launch { ready.settings.update { it.copy(backupAuto = v) } } })
            Explain("Verify decrypts your newest backup completely without changing anything, checks the chain of snapshots, and warns if newer backups seem to have been removed.")
            SectionHeader("Recovery key")
            Explain("The only way to open a backup on a new phone. Nobody, including the developer, can recover it for you.")
            SibButton("Show my recovery key", { showKey = ready.backup.recoveryKeyText() }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
        }
        message?.let {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(c.raised).padding(14.dp)) { Text(it, style = SibType.body, color = c.ink) }
        }
        SectionHeader("Moving to a new phone")
        Explain("Install H-sib Reminder, choose Restore, pick the same folder and type your recovery key.")
        SibButton("Restore from a backup", onRestore, Modifier.fillMaxWidth(), ButtonKind.QUIET)
        SectionHeader("Export")
        Explain("A calendar file is not encrypted. An encrypted file needs a passphrase that only you know. Choose a long one.")
        SibButton("Export a calendar file", { exportIcs.launch("sib-deadlines.ics") }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
        Spacer(Modifier.height(8.dp))
        SibButton("Export an encrypted file", { passphraseFor = "export" }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
        if (configured) {
            SectionHeader("Turn off")
            Explain("Stops new backups and forgets the recovery key on this phone. Files already in the folder stay there.")
            SibButton("Turn off backup", { ready.backup.forgetKey(); scope.launch { ready.settings.update { it.copy(backupFolder = null, backupAuto = false, backupSeq = -1, backupLineage = null, backupHead = null) } } }, Modifier.fillMaxWidth(), ButtonKind.DANGER)
        }
        Spacer(Modifier.height(32.dp))
    }

    showKey?.let { key ->
        AlertDialog(
            onDismissRequest = { showKey = null }, containerColor = c.raised,
            title = { Text("Your recovery key", style = SibType.title, color = c.ink) },
            text = {
                Column {
                    Text(key, style = SibType.body.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = c.ink)
                    Spacer(Modifier.height(8.dp))
                    Text("Write it down or keep it in a password manager. Do not photograph it.", style = SibType.small, color = c.inkSoft)
                }
            },
            confirmButton = { TextButton({ showKey = null }) { Text("Done", style = SibType.bodyStrong, color = c.ink) } },
            dismissButton = { TextButton({ ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, key), "Save to a password manager")) }) { Text("Send to a password manager", style = SibType.body, color = c.ink) } },
        )
    }
    if (passphraseFor != null) {
        AlertDialog(
            onDismissRequest = { passphraseFor = null; passphrase = "" }, containerColor = c.raised,
            title = { Text("Choose a passphrase", style = SibType.title, color = c.ink) },
            text = {
                Column {
                    Text("Anyone with this file and a guess of the passphrase can try to open it, so make it long: four unrelated words or more. If you forget it, the file cannot be opened.", style = SibType.small, color = c.inkSoft)
                    Spacer(Modifier.height(10.dp))
                    Field("Passphrase", passphrase, { passphrase = it })
                    Text(strengthWords(passphrase), style = SibType.small, color = c.inkSoft)
                }
            },
            confirmButton = { TextButton({ passphraseFor = null; exportFile.launch("sib-backup.sibbak") }, enabled = passphrase.length >= 12) { Text("Choose where to save", style = SibType.bodyStrong, color = c.ink) } },
            dismissButton = { TextButton({ passphraseFor = null; passphrase = "" }) { Text("Cancel", style = SibType.body, color = c.inkSoft) } },
        )
    }
}

fun strengthWords(p: String): String = when {
    p.length < 12 -> "At least 12 characters."
    p.length < 16 -> "Acceptable. Longer is stronger."
    p.split(" ", "-").size >= 4 || p.length >= 20 -> "Strong."
    else -> "Good."
}

@Composable
private fun BackupSetup(ready: Ready, onClose: () -> Unit, onDone: (String) -> Unit) {
    val c = SibTheme.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val key = remember { RecoveryKey.generate() }
    val keyText = remember { RecoveryKey.encode(key) }
    var folder by remember { mutableStateOf<Uri?>(null) }
    var typed by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            folder = uri
        }
    }
    val groups = keyText.split("-")
    val confirmed = typed.replace(" ", "").uppercase() == groups.first() + groups.last()

    Page("Set up backup", onClose) {
        SectionHeader("1. Where to keep it")
        Explain("Choose a folder in a storage app you trust, for example Google Drive, or a folder on your phone that you sync elsewhere. H-sib Reminder only ever writes scrambled files there.")
        SibButton(if (folder == null) "Choose a folder" else "Folder chosen. Change it", { picker.launch(null) }, Modifier.fillMaxWidth(), if (folder == null) ButtonKind.PRIMARY else ButtonKind.QUIET)
        folder?.let { Text(it.lastPathSegment.orEmpty(), style = SibType.small, color = c.inkSoft) }

        SectionHeader("2. Your recovery key")
        Explain("This key unlocks your backups on a new phone. Write it down on paper or keep it in a password manager. If you lose it and your phone, your backups cannot be opened by anyone. There is no reset.")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(c.raised).padding(16.dp)) {
            groups.chunked(4).forEach { row ->
                Text(row.joinToString("   "), style = SibType.headline.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = c.ink)
            }
        }
        Spacer(Modifier.height(8.dp))
        SibButton("Send to a password manager", { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, keyText), "Save to a password manager")) }, Modifier.fillMaxWidth(), ButtonKind.QUIET)

        SectionHeader("3. Prove you have it")
        Explain("Type the first and last groups of the key, joined together. That shows you looked at it properly.")
        Field("First and last group", typed, { typed = it }, mono = true, keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done))
        Spacer(Modifier.height(20.dp))
        SibButton(if (busy) "Setting up" else "Turn on backup", {
            busy = true
            scope.launch {
                ready.backup.saveKey(key)
                ready.settings.update { it.copy(backupFolder = folder.toString(), backupAuto = true, backupSeq = -1, backupLineage = null, backupHead = null) }
                val r = ready.backup.backupNow()
                busy = false
                onDone(if (r.ok) "Backup is on. ${r.message}" else "Backup is set up but the first copy failed: ${r.message}")
            }
        }, Modifier.fillMaxWidth(), enabled = folder != null && confirmed && !busy)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun RestoreScreen(st: AppState, ready: Ready, onBack: () -> Unit, onRestored: () -> Unit) {
    val c = SibTheme.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var folder by remember { mutableStateOf<Uri?>(null) }
    var key by remember { mutableStateOf("") }
    var merge by remember { mutableStateOf(st.items.isNotEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<com.hsib.sib.platform.RestoreOutcome?>(null) }
    var pass by remember { mutableStateOf("") }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            folder = uri
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> fileUri = uri }

    Page("Restore", onBack) {
        Explain("Use the same folder you backed up to, and the recovery key you wrote down.")
        SectionHeader("From your backup folder")
        SibButton(if (folder == null) "Choose the backup folder" else "Folder chosen. Change it", { picker.launch(null) }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
        folder?.let { Text(it.lastPathSegment.orEmpty(), style = SibType.small, color = c.inkSoft) }
        Spacer(Modifier.height(14.dp))
        Field("Recovery key", key, { key = it.uppercase() }, mono = true, minLines = 2, singleLine = false, keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Default))
        if (st.items.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("This phone already has deadlines", style = SibType.label, color = c.inkSoft)
            Choice(listOf(true to "Merge, newest edit wins", false to "Replace everything"), merge, { merge = it })
        }
        Spacer(Modifier.height(16.dp))
        SibButton(if (busy) "Restoring" else "Restore", {
            busy = true
            scope.launch { result = ready.backup.restoreFromFolder(folder.toString(), key, merge && st.items.isNotEmpty()); busy = false }
        }, Modifier.fillMaxWidth(), enabled = folder != null && key.isNotBlank() && !busy)

        SectionHeader("From an exported file")
        SibButton(if (fileUri == null) "Choose the file" else "File chosen. Change it", { filePicker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
        Spacer(Modifier.height(10.dp))
        Field("Passphrase", pass, { pass = it })
        Spacer(Modifier.height(10.dp))
        SibButton("Restore from file", {
            busy = true
            scope.launch { result = ready.backup.restoreFromFile(fileUri!!, Secret.Passphrase(pass.toCharArray()), merge && st.items.isNotEmpty()); busy = false }
        }, Modifier.fillMaxWidth(), enabled = fileUri != null && pass.isNotBlank() && !busy)

        result?.let { r ->
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (r.ok) c.verdigrisWash else c.vermilionWash).padding(14.dp)) {
                Text(r.message, style = SibType.bodyStrong, color = c.ink)
                r.notes.forEach { Text(it, style = SibType.small, color = c.ink) }
                if (r.ok) { Spacer(Modifier.height(8.dp)); SibButton("Open H-sib Reminder", onRestored) }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

// ---- feedback ----------------------------------------------------------------------------------------

private fun deviceLine() = "H-sib Reminder ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER} ${Build.MODEL}"

/**
 * Feedback in any language. It is handed to whichever app the person picks (a chat, mail, anything), with the app version and
 * the phone model added so the maker can reproduce a problem. Nothing is sent by the app itself.
 */
@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val c = SibTheme.colors
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    Page("Send feedback", onBack) {
        Text("Write in English, Bangla, Hindi or any language you like.", style = SibType.body, color = c.inkSoft)
        Gap(20.dp)
        Field("Your feedback", text, { text = it.take(4000) }, singleLine = false, minLines = 6, keyboard = KeyboardOptions(imeAction = ImeAction.Default))
        Gap(12.dp)
        Text("The app you choose sends it, with this line added: " + deviceLine(), style = SibType.small, color = c.inkSoft)
        Gap(20.dp)
        ActionButton(
            "Send",
            {
                val body = text.trim() + "\n" + "\n" + deviceLine()
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "H-sib Reminder feedback").putExtra(Intent.EXTRA_TEXT, body), "Send feedback"))
            },
            Modifier.fillMaxWidth(), enabled = text.isNotBlank(),
        )
        Gap(40.dp)
    }
}
