package com.hsib.sib.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hsib.sib.Ready
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Effort
import com.hsib.sib.core.model.Importance
import com.hsib.sib.core.model.Lifecycle
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.model.Space
import com.hsib.sib.core.plan.Step
import com.hsib.sib.core.seal.SealCheck
import com.hsib.sib.data.UserStep
import com.hsib.sib.domain.AppState
import com.hsib.sib.domain.Fmt
import com.hsib.sib.platform.Sharing
import com.hsib.sib.platform.readScaledJpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SibDatePicker(initial: LocalDate, onPick: (LocalDate) -> Unit, onCancel: () -> Unit) {
    val c = SibTheme.colors
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onCancel() }) {
                Text("Use this date", style = SibType.bodyStrong, color = c.ink)
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel", style = SibType.body, color = c.inkSoft) } },
        colors = DatePickerDefaults.colors(containerColor = c.raised),
    ) {
        DatePicker(
            state,
            colors = DatePickerDefaults.colors(
                containerColor = c.raised, selectedDayContainerColor = c.ink, selectedDayContentColor = c.paper,
                todayContentColor = c.vermilion, todayDateBorderColor = c.vermilion,
            ),
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(id: String, st: AppState, ready: Ready, onBack: () -> Unit, onDeleted: () -> Unit, onPeople: (String) -> Unit = {}) {
    val c = SibTheme.colors
    val fmt = rememberFmt()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val social = rememberSocial(ready)
    val shared = social.roomFor(id)
    // In a shared deadline only the owner changes the title and date; everyone else asks in the chat.
    val locked = shared != null && !shared.isOwner && !shared.removed
    val v = st.items.firstOrNull { it.o.id == id }
    if (v == null) {
        Page("Deadline", onBack) { Text("This deadline is no longer on your list.", style = SibType.body, color = c.inkSoft) }
        return
    }
    val o = v.o
    val steps = st.steps[id].orEmpty()
    val suggested = st.suggestedSteps[id].orEmpty()
    val history by ready.store.history(id).collectAsState(emptyList())
    val receipts by ready.store.receipts(id).collectAsState(emptyList())
    val excerpts by ready.store.excerpts(id).collectAsState(emptyList())
    val check by produceState<SealCheck?>(null, history) { value = withContext(Dispatchers.IO) { ready.store.verifyHistory(id) } }
    var editingTitle by remember(id) { mutableStateOf(false) }
    var title by remember(id, o.title) { mutableStateOf(o.title) }
    var pickDate by remember { mutableStateOf<String?>(null) }
    var completing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var tab by rememberSaveable(id) { mutableStateOf(DetailTab.OVERVIEW) }

    suspend fun save(new: com.hsib.sib.core.model.Obligation, reason: String) {
        ready.store.save(new, "USER", reason)
        // If this is the owner's shared deadline, everyone gets the new title or date. Personal changes send nothing.
        val note = when (reason) {
            "date changed", "date confirmed" -> new.due?.let { "moved the date to " + fmt.dayShort(it.dayIn(st.zone)) }
            "renamed" -> "renamed this to \"${new.title}\""
            else -> null
        }
        ready.social?.pushIfShared(id, note)
    }

    Page(o.category.label, onBack, action = {
        if (o.lifecycle == Lifecycle.ACTIVE) {
            Text(
                "Done", style = SibType.bodyStrong, color = c.paper,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(c.verdigris)
                    .bounceClick { completing = true }.padding(horizontal = 22.dp, vertical = 11.dp),
            )
        }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UrgencyGlyph(v.urgency, size = 30.dp, progress = o.effort?.let { if (it.expectedMin > 0) o.progressMin.toFloat() / it.expectedMin else 0f } ?: 0f)
            Spacer(Modifier.width(10.dp))
            Text(v.urgency.label, style = SibType.bodyStrong, color = stateColor(v.urgency))
            if (v.tentative) { Spacer(Modifier.width(10.dp)); Chip("Date not confirmed", Tone.WARN) }
        }
        Spacer(Modifier.height(10.dp))
        if (editingTitle) {
            Field("Title", title, { title = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                SibButton("Save", { scope.launch { save(o.copy(title = title.trim().ifBlank { o.title }), "renamed"); editingTitle = false } })
                SibButton("Cancel", { title = o.title; editingTitle = false }, kind = ButtonKind.QUIET)
            }
        } else {
            Text(o.title, style = SibType.display, color = c.ink, modifier = Modifier.clickable(enabled = !locked, role = Role.Button) { editingTitle = true }.semantics { contentDescription = if (locked) o.title else "${o.title}. Tap to rename." })
        }

        Gap(24.dp)
        Choice(listOf(DetailTab.OVERVIEW to "Overview", DetailTab.STEPS to "Steps", DetailTab.HISTORY to "History"), tab, { tab = it })

        if (tab == DetailTab.OVERVIEW) {
            val fog = o.fog
            val due = o.due
            val effort = o.effort
            val active = o.lifecycle == Lifecycle.ACTIVE
            val tone = stateColor(v.urgency)
            val dateOnly = due?.precision == Precision.DATE_ONLY
            Gap(20.dp)
            // ---- the date, large, tinted by how close it is ----
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(tone.copy(alpha = 0.10f))
                    .border(1.dp, tone.copy(alpha = 0.30f), RoundedCornerShape(28.dp)).padding(22.dp),
            ) {
                if (fog != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SibIconView(SibIcon.CALENDAR, size = 20.dp, tint = tone, accent = tone)
                        Spacer(Modifier.width(8.dp))
                        Text("Date to be announced", style = SibType.bodyStrong, color = c.ink)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Somewhere ${fmt.range(fog.earliest, fog.latest)}", style = SibType.headline, color = c.ink)
                    More("The exact day is not announced. H-sib Reminder plans for the earliest day, ${fmt.dayShort(fog.earliest)}, so you are never caught short.", label = "How is it planned?")
                } else if (due != null) {
                    val mine = if (dateOnly) due.local else due.instant(st.zone).atZone(st.zone).toLocalDateTime()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SibIconView(SibIcon.CALENDAR, size = 20.dp, tint = tone, accent = tone)
                        Spacer(Modifier.width(8.dp))
                        Text(fmt.due(mine, dateOnly), style = SibType.bodyStrong, color = c.ink)
                    }
                    v.dueInstant?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(fmt.countdown(st.now, it, st.zone), style = SibType.headline, color = c.ink)
                    }
                    if (!dateOnly && !due.floating && due.zone != st.zone) {
                        Spacer(Modifier.height(4.dp))
                        Text("Written as ${fmt.due(due.local, false)} in ${due.zone.id}. Shown above in your time.", style = SibType.small, color = c.inkSoft)
                    }
                } else {
                    Text("No date. This stays on your list until you finish it.", style = SibType.body, color = c.inkSoft)
                }
                if (active && effort != null) {
                    Spacer(Modifier.height(16.dp))
                    PressureBar(v.pressure, v.urgency)
                    Spacer(Modifier.height(6.dp))
                    Text(pressureWords(v.pressure), style = SibType.small, color = c.inkSoft)
                }
            }
            if (locked) {
                Spacer(Modifier.height(8.dp))
                Text("Only ${shared.ownerName} can change the title and date. Ask in the chat.", style = SibType.small, color = c.inkSoft)
            }

            // ---- what you can do with it: icons, one word each ----
            if (active) {
                Gap(16.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (fog != null) {
                        if (!locked) IconAction(SibIcon.CALENDAR, "Set date", Modifier.weight(1f), description = "The date is now known. Set it.") { pickDate = "set" }
                    } else if (due != null) {
                        if (!locked) IconAction(SibIcon.MOVE, "Move", Modifier.weight(1f), description = "Change the date") { pickDate = "move" }
                        IconAction(SibIcon.CALENDAR_PLUS, "Calendar", Modifier.weight(1f), description = "Add to calendar") {
                            val start = due.instant(st.zone).toEpochMilli()
                            context.startActivity(
                                Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                                    .putExtra(CalendarContract.Events.TITLE, o.title)
                                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 30 * 60_000L)
                                    .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, dateOnly),
                            )
                        }
                        IconAction(SibIcon.SEND, "Send", Modifier.weight(1f), description = "Send as a calendar file. Notes and progress stay private.") { Sharing.share(context, listOf(o), st.zone) }
                    }
                    if (ready.social != null && o.space == Space.WORK) {
                        IconAction(
                            SibIcon.CHAT, if (shared == null) "Share" else "Chat", Modifier.weight(1f),
                            description = if (shared == null) "Share with people" else "People and chat", badge = shared?.unread?.takeIf { it > 0 },
                        ) { onPeople(id) }
                    }
                }
            }

            // ---- parts: several pieces due together ----
            if (active && o.space == Space.WORK) {
                Gap(12.dp)
                PartsTile(o.parts) { scope.launch { save(o.copy(parts = it), "parts changed") } }
            }

            // ---- progress ----
            if (active && effort != null) {
                Gap(12.dp)
                val total = (effort.expectedMin * st.calibration).toInt().coerceAtLeast(1)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised).padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(SibIcon.CLOCK, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        InfoTitle("${fmt.minutes(o.progressMin)} done, about ${fmt.minutes(total)} needed", "Add the time you really worked with the buttons below. The bar shows how much of your estimate is done. Change the estimate under Hours and importance.", style = SibType.bodyStrong)
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(c.ink.copy(alpha = 0.08f))) {
                        Box(Modifier.fillMaxWidth((o.progressMin.toFloat() / total).coerceIn(0.02f, 1f)).height(8.dp).clip(RoundedCornerShape(50)).background(c.verdigris))
                    }
                    Spacer(Modifier.height(14.dp))
                    val quick = listOf(Triple("15", "min", 15), Triple("30", "min", 30), Triple("1", "hr", 60))
                    if (LocalDensity.current.fontScale > 1.15f) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((a, u, m) in quick) QuickAdd(a, u, Modifier.fillMaxWidth()) { scope.launch { ready.store.addProgress(id, m) } }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((a, u, m) in quick) QuickAdd(a, u, Modifier.weight(1f)) { scope.launch { ready.store.addProgress(id, m) } }
                        }
                    }
                }
            }

            // ---- files: only remembered, never copied ----
            if (active && o.space == Space.WORK) {
                Gap(12.dp)
                FilesTile(o.links) { scope.launch { save(o.copy(links = it), "files changed") } }
            }

            // ---- everything else stays folded until asked for ----
            Group(null) {
                if (active && effort != null) {
                    FoldItem(SibIcon.GAUGE, "Plan", hint = if (o.manualPlan.isNotEmpty()) "Your own" else v.latestStart?.let { "Start by " + fmt.dayShort(it) }, info = "The plan says which days to work on this and for how long. Normally the app spreads the hours over your free days, sharing them fairly with your other deadlines. You can set your own hours below, and then the app follows you.") {
                        v.latestStart?.let {
                            Row {
                                Text("Latest safe start", style = SibType.small, color = c.inkSoft, modifier = Modifier.width(130.dp))
                                Text(fmt.dayShort(it) + "  ·  " + fmt.daysFrom(st.today, it), style = SibType.bodyStrong, color = c.ink)
                            }
                            More("If you begin by this day, even the slow case still finishes in time.", label = "What is this?")
                        } ?: Text(
                            if (o.manualPlan.isNotEmpty()) "You are using your own plan, so no start day is worked out." else if (v.atRiskMin > 0) "Not enough days are left to finish this at an easy pace. About ${fmt.minutes(v.atRiskMin)} of the work will not fit." else "No safe start can be worked out yet.",
                            style = SibType.small, color = c.vermilion,
                        )
                        if (v.blocks.isNotEmpty()) {
                            Text("Planned: ${v.blocks.size} ${if (v.blocks.size == 1) "session" else "sessions"}, ${fmt.minutes(v.blocks.sumOf { it.minutes })} in total.", style = SibType.small, color = c.inkSoft)
                        }
                        if (st.calibration > 1.05) More("Your past jobs ran longer than planned, so estimates are raised by ${((st.calibration - 1) * 100).toInt()}%.", label = "Estimates raised ${((st.calibration - 1) * 100).toInt()}%")
                    }
                    GroupDivider()
                    FoldItem(SibIcon.CALENDAR, "My own plan", hint = if (o.manualPlan.isNotEmpty()) "On" else "Automatic", info = "Choose how many minutes to give on each day yourself, for example one whole day for this deadline and another day for a different one. Your hours are taken off those days for your other deadlines. Turn it off to let the app plan again.") {
                        ManualPlanEditor(o.manualPlan, st.today, v.planDay, fmt) { scope.launch { save(o.copy(manualPlan = it), "own plan changed") } }
                    }
                    GroupDivider()
                    FoldItem(SibIcon.SLIDERS, "Hours and importance", info = "Hours is how long you think the whole job will take. The app uses it to work out when to start. Importance decides which deadline gets time first when days are short. Both are your call and you can change them any time.") {
                        HoursField(effort.likelyMin) { m -> scope.launch { save(o.copy(effort = Effort.fromLikely(m)), "estimate changed") } }
                        Spacer(Modifier.height(6.dp))
                        Text("Quick picks", style = SibType.bodyStrong, color = c.ink)
                        Choice(EffortChoices, listOf(30, 120, 240, 480, 960).minBy { kotlin.math.abs(it - effort.likelyMin) },
                            { scope.launch { save(o.copy(effort = Effort.fromLikely(it)), "estimate changed") } })
                        Spacer(Modifier.height(6.dp))
                        Text("Importance", style = SibType.bodyStrong, color = c.ink)
                        Choice(listOf(Importance.LOW to "Low", Importance.NORMAL to "Normal", Importance.HIGH to "High", Importance.CRITICAL to "Critical"), o.importance,
                            { scope.launch { save(o.copy(importance = it), "importance changed") } })
                    }
                    GroupDivider()
                }
                if (active && due != null) {
                    FoldItem(SibIcon.BELL, "Reminder days", hint = if (o.leadDays.isEmpty()) "7, 6, 5" else o.leadDays.sortedDescending().joinToString(", "), info = "These are the early reminders, counted in days before the date. Until you choose, you get a calm one 7, 6 and 5 days ahead. Pick your own days if you want fewer or different ones. Reminders on the last days stay on.") {
                        val picked = o.leadDays.ifEmpty { com.hsib.sib.core.urgency.ReminderPlanner.DEFAULT_LEADS }.toSet()
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(14, 10, 7, 6, 5, 4, 3, 2, 1).forEach { d ->
                                SelectChip("$d", d in picked, { val next = if (d in picked) picked - d else picked + d; scope.launch { save(o.copy(leadDays = next.sortedDescending()), "reminder days changed") } })
                            }
                        }
                        Text("Days before the date.", style = SibType.small, color = c.inkSoft)
                    }
                    GroupDivider()
                }
                if (active) {
                    FoldItem(SibIcon.PAUSE, "Pause", hint = o.postponedUntil?.let { "Until " + fmt.dayShort(it) }) {
                        val pausedUntil = o.postponedUntil
                        if (pausedUntil != null) {
                            Text("Paused until ${fmt.dayShort(pausedUntil)}. No work is planned before then.", style = SibType.body, color = c.ink)
                            SibButton("Resume now", { scope.launch { ready.store.postpone(id, null) } }, kind = ButtonKind.QUIET)
                        } else {
                            Text("Pause planning without moving the date.", style = SibType.body, color = c.inkSoft)
                            SibButton("Pause until a date", { pickDate = "pause" }, kind = ButtonKind.QUIET)
                        }
                    }
                    GroupDivider()
                }
                FoldItem(SibIcon.MORE, "More") {
                    if (!active) SibButton("Reopen", { scope.launch { ready.store.reopen(id); ready.social?.markDone(id, false) } }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
                    SibButton("Delete this deadline", { confirmDelete = true }, Modifier.fillMaxWidth(), ButtonKind.DANGER)
                }
            }
        }
        if (tab == DetailTab.STEPS) {
        // ---- steps ----
        if (o.lifecycle == Lifecycle.ACTIVE && (steps.isNotEmpty() || suggested.isNotEmpty())) {
            SectionHeader("Steps")
            if (steps.isEmpty()) {
                Text("Suggested steps", style = SibType.body, color = c.inkSoft)
                suggested.forEach { SuggestedStepRow(it, fmt) }
                Spacer(Modifier.height(8.dp))
                SibButton("Use this plan", {
                    scope.launch {
                        ready.store.saveSteps(id, suggested.mapIndexed { i, s ->
                            UserStep(UUID.randomUUID().toString(), id, s.title, s.minutes, s.targetDay, false, true, i, Instant.now())
                        })
                    }
                }, kind = ButtonKind.QUIET)
            } else {
                steps.forEach { s ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox) { scope.launch { ready.store.toggleStep(s) } }, verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(22.dp)) {
                            val dash = if (s.suggested) PathEffect.dashPathEffect(floatArrayOf(5f, 4f)) else null
                            drawRect(c.ink, topLeft = Offset(2f, 2f), size = Size(size.width - 4f, size.height - 4f), style = Stroke(2f, pathEffect = dash))
                            if (s.done) { drawLine(c.verdigris, Offset(6f, 12f), Offset(10f, 17f), 3f); drawLine(c.verdigris, Offset(10f, 17f), Offset(18f, 6f), 3f) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = SibType.body, color = if (s.done) c.inkSoft else c.ink)
                            Text(listOfNotNull(s.targetDay?.let { fmt.dayShort(it) }, fmt.minutes(s.minutes)).joinToString("  ·  "), style = SibType.small, color = c.inkSoft)
                        }
                        Box(Modifier.size(44.dp).clickable(role = Role.Button) { scope.launch { ready.store.removeStep(s.id) } }.semantics { contentDescription = "Remove step" }, contentAlignment = Alignment.Center) { SibIconView(SibIcon.CLOSE, size = 18.dp, tint = c.inkSoft) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                SibButton("Go back to suggested steps", { scope.launch { ready.store.saveSteps(id, emptyList()) } }, kind = ButtonKind.TEXT)
            }
        }

        }
        if (tab == DetailTab.HISTORY) {
        // ---- receipts and evidence ----
        if (receipts.isNotEmpty()) {
            SectionHeader("Proof of finishing")
            receipts.forEach { r ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(fmt.dayShort(r.at.atZone(st.zone).toLocalDate()), style = SibType.label, color = c.inkSoft)
                    if (r.note.isNotBlank()) Text(r.note, style = SibType.body, color = c.ink)
                    val bytes = remember(r.id) { ready.store.receiptImage(r) }
                    val bmp = remember(bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
                    if (bmp != null) Image(bmp.asImageBitmap(), "Photo kept as proof", Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Fit)
                }
            }
        }
        if (excerpts.isNotEmpty()) {
            SectionHeader("Evidence you kept")
            excerpts.forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Text("“${e.excerpt}”", style = SibType.small, color = c.ink, modifier = Modifier.weight(1f))
                    Box(Modifier.size(44.dp).clickable(role = Role.Button) { scope.launch { ready.store.deleteExcerpt(e.id) } }.semantics { contentDescription = "Delete this excerpt" }, contentAlignment = Alignment.Center) { SibIconView(SibIcon.TRASH, size = 18.dp, tint = c.vermilion) }
                }
            }
        }

        // ---- history ----
        SectionHeader("History")
        val ck = check
        when (ck) {
            is SealCheck.Ok -> Chip("Sealed and verified, ${ck.entries} entries", Tone.GOOD)
            is SealCheck.Broken -> Chip("History does not verify at entry ${ck.atSeq + 1}: ${ck.why}", Tone.ALERT)
            null -> Chip("Checking", Tone.NEUTRAL)
        }
        Spacer(Modifier.height(4.dp))
        More("Every change to this deadline is chained to the one before it, so an edit that was not recorded can be detected.", label = "How does this work?")
        Gap(12.dp)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            history.reversed().forEach { e -> HistoryEntryCard(e, fmt, st.zone) }
        }

        }
        Spacer(Modifier.height(40.dp))
    }

    pickDate?.let { mode ->
        val initial = when (mode) {
            "pause" -> st.today.plusDays(7)
            "set" -> o.fog?.earliest ?: st.today
            else -> o.due?.dayIn(st.zone) ?: st.today
        }
        SibDatePicker(initial, { picked ->
            pickDate = null
            scope.launch {
                when (mode) {
                    "pause" -> ready.store.postpone(id, picked)
                    "set" -> save(o.copy(fog = null, due = DueSpec(picked.atTime(LocalTime.MIDNIGHT), st.zone, Precision.DATE_ONLY)), "date confirmed")
                    else -> {
                        val old = o.due
                        val time = if (old != null && old.precision == Precision.DATE_TIME) old.local.toLocalTime() else LocalTime.MIDNIGHT
                        save(o.copy(due = DueSpec(LocalDateTime.of(picked, time), old?.zone ?: st.zone, old?.precision ?: Precision.DATE_ONLY, old?.floating ?: false)), "date changed")
                    }
                }
            }
        }, { pickDate = null })
    }

    if (completing) CompleteDialog(o.title, onCancel = { completing = false }) { note, image ->
        completing = false
        scope.launch(Dispatchers.IO) {
            val bytes = image?.let { readScaledJpeg(context, it) }
            ready.store.complete(id, note, bytes)
            ready.engine.refreshCalibration()
            ready.social?.markDone(id, true)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.raised,
            title = { Text("Delete this deadline?", style = SibType.title, color = c.ink) },
            text = {
                Text(
                    "Its history, steps and receipts are deleted from this phone. Backups you already made keep their own copy." +
                        when {
                            shared == null || shared.removed -> ""
                            shared.isOwner -> " It is also deleted for everyone you shared it with."
                            else -> " You also leave the shared deadline."
                        },
                    style = SibType.body, color = c.inkSoft,
                )
            },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    scope.launch { shared?.let { ready.social?.leaveOrDelete(it.roomId) }; ready.store.delete(id); onDeleted() }
                }) { Text("Delete", style = SibType.bodyStrong, color = c.vermilion) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Keep it", style = SibType.body, color = c.ink) } },
        )
    }
}

private enum class DetailTab { OVERVIEW, STEPS, HISTORY }

@Composable
private fun SuggestedStepRow(s: Step, fmt: Fmt) {
    val c = SibTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(22.dp)) { drawRect(c.inkSoft, topLeft = Offset(2f, 2f), size = Size(size.width - 4f, size.height - 4f), style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)))) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(s.title, style = SibType.body, color = c.ink)
            Text("by " + fmt.dayShort(s.targetDay) + "  ·  " + fmt.minutes(s.minutes), style = SibType.small, color = c.inkSoft)
        }
    }
}

@Composable
private fun CompleteDialog(title: String, onCancel: () -> Unit, onDone: (String?, Uri?) -> Unit) {
    val c = SibTheme.colors
    var note by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { photo = it }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = c.raised,
        title = { Text("Finished: $title", style = SibType.title, color = c.ink) },
        text = {
            Column {
                Text("Optionally keep proof, such as a confirmation number or a screenshot of the receipt. It stays encrypted on this phone.", style = SibType.small, color = c.inkSoft)
                Spacer(Modifier.height(10.dp))
                Field("Note (optional)", note, { note = it }, singleLine = false, minLines = 2)
                Spacer(Modifier.height(10.dp))
                SibButton(if (photo == null) "Add a photo" else "Photo added, change", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, kind = ButtonKind.QUIET, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton({ onDone(note.trim().ifEmpty { null }, photo) }) { Text("Mark done", style = SibType.bodyStrong, color = c.ink) } },
        dismissButton = { TextButton(onCancel) { Text("Cancel", style = SibType.body, color = c.inkSoft) } },
    )
}
