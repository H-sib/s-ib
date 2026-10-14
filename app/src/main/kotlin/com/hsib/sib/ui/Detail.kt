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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hsib.sib.Ready
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Effort
import com.hsib.sib.core.model.Importance
import com.hsib.sib.core.model.Lifecycle
import com.hsib.sib.core.model.Precision
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

@Composable
fun DetailScreen(id: String, st: AppState, ready: Ready, onBack: () -> Unit, onDeleted: () -> Unit) {
    val c = SibTheme.colors
    val fmt = rememberFmt()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    var adjust by rememberSaveable(id) { mutableStateOf(false) }

    suspend fun save(new: com.hsib.sib.core.model.Obligation, reason: String) { ready.store.save(new, "USER", reason) }

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
            Text(o.title, style = SibType.display, color = c.ink, modifier = Modifier.clickable(role = Role.Button) { editingTitle = true }.semantics { contentDescription = "${o.title}. Tap to rename." })
        }

        Gap(24.dp)
        Choice(listOf(DetailTab.OVERVIEW to "Overview", DetailTab.STEPS to "Steps", DetailTab.HISTORY to "History"), tab, { tab = it })

        if (tab == DetailTab.OVERVIEW) {
        // ---- when ----
        SectionHeader("When")
        val fog = o.fog
        val due = o.due
        if (fog != null) {
            Text("Somewhere ${fmt.range(fog.earliest, fog.latest)}", style = SibType.headline, color = c.ink)
            More("The exact day is not announced. S-IB plans for the earliest day, ${fmt.dayShort(fog.earliest)}, so you are never caught short.", label = "How is it planned?")
            Spacer(Modifier.height(8.dp))
            SibButton("The date is now known", { pickDate = "set" }, kind = ButtonKind.QUIET)
        } else if (due != null) {
            val dateOnly = due.precision == Precision.DATE_ONLY
            val mine = if (dateOnly) due.local else due.instant(st.zone).atZone(st.zone).toLocalDateTime()
            Text(fmt.due(mine, dateOnly), style = SibType.headline, color = c.ink)
            v.dueInstant?.let { Text(fmt.countdown(st.now, it, st.zone), style = SibType.body, color = c.inkSoft) }
            if (!dateOnly && !due.floating && due.zone != st.zone) {
                Spacer(Modifier.height(4.dp))
                Text("Written as ${fmt.due(due.local, false)} in ${due.zone.id}. Shown above in your time.", style = SibType.small, color = c.inkSoft)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (o.lifecycle == Lifecycle.ACTIVE) SibButton("Change the date", { pickDate = "move" }, kind = ButtonKind.QUIET)
                if (o.lifecycle == Lifecycle.ACTIVE) SibButton("Add to calendar", {
                    val start = due.instant(st.zone).toEpochMilli()
                    context.startActivity(
                        Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                            .putExtra(CalendarContract.Events.TITLE, o.title)
                            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 30 * 60_000L)
                            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, dateOnly),
                    )
                }, kind = ButtonKind.QUIET)
            }
            if (o.lifecycle == Lifecycle.ACTIVE) {
                Spacer(Modifier.height(8.dp))
                SibButton("Share with friends", { Sharing.share(context, listOf(o), st.zone) }, kind = ButtonKind.QUIET)
                More("Sends the title and date as a calendar file. No account needed, and your notes and progress stay private.", label = "What is shared?")
            }
        } else {
            Text("No date. This stays on your list until you finish it.", style = SibType.body, color = c.inkSoft)
        }

        // ---- how tight ----
        if (o.lifecycle == Lifecycle.ACTIVE && o.effort != null) {
            SectionHeader("How tight")
            PressureBar(v.pressure, v.urgency)
            Spacer(Modifier.height(4.dp))
            Text(pressureWords(v.pressure), style = SibType.body, color = c.ink)
            v.latestStart?.let {
                Spacer(Modifier.height(6.dp))
                Row {
                    Text("Latest safe start", style = SibType.small, color = c.inkSoft, modifier = Modifier.width(130.dp))
                    Text(fmt.dayShort(it) + "  ·  " + fmt.daysFrom(st.today, it), style = SibType.bodyStrong, color = c.ink)
                }
                More("If you begin by this day, even the slow case still finishes in time.", label = "What is this?")
            } ?: Text(
                if (v.atRiskMin > 0) "There is no longer a comfortable way to finish all of this. ${fmt.minutes(v.atRiskMin)} would not fit." else "No safe start can be worked out yet.",
                style = SibType.small, color = c.vermilion,
            )
            if (v.blocks.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Planned: ${v.blocks.size} ${if (v.blocks.size == 1) "session" else "sessions"}, ${fmt.minutes(v.blocks.sumOf { it.minutes })} in total.", style = SibType.small, color = c.inkSoft)
            }
        }

        // ---- progress ----
        val effort = o.effort
        if (o.lifecycle == Lifecycle.ACTIVE && effort != null) {
            SectionHeader("Progress")
            val total = (effort.expectedMin * st.calibration).toInt().coerceAtLeast(1)
            Text("${fmt.minutes(o.progressMin)} of about ${fmt.minutes(total)}", style = SibType.body, color = c.ink)
            if (st.calibration > 1.05) More("Your past jobs ran longer than planned, so estimates are raised by ${((st.calibration - 1) * 100).toInt()}%.", label = "Estimates raised ${((st.calibration - 1) * 100).toInt()}%")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in listOf(15, 30, 60)) SibButton("+${if (m == 60) "1 h" else "$m min"}", { scope.launch { ready.store.addProgress(id, m) } }, Modifier.weight(1f), ButtonKind.QUIET)
            }
            if (adjust) {
            Spacer(Modifier.height(14.dp))
            Text("Effort", style = SibType.bodyStrong, color = c.ink)
            Choice(listOf(30 to "30 min", 120 to "2 h", 240 to "4 h", 480 to "A day", 960 to "2 days"), listOf(30, 120, 240, 480, 960).minBy { kotlin.math.abs(it - effort.likelyMin) },
                { scope.launch { save(o.copy(effort = Effort.fromLikely(it)), "estimate changed") } })
            Spacer(Modifier.height(8.dp))
            Text("Importance", style = SibType.bodyStrong, color = c.ink)
            Choice(listOf(Importance.LOW to "Low", Importance.NORMAL to "Normal", Importance.HIGH to "High", Importance.CRITICAL to "Critical"), o.importance,
                { scope.launch { save(o.copy(importance = it), "importance changed") } })
            }
        }

        Gap(12.dp)
        SibButton(if (adjust) "Fewer options" else "Adjust", { adjust = !adjust }, kind = ButtonKind.TEXT)
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
                        Text("Remove", style = SibType.small, color = c.inkSoft, modifier = Modifier.clickable { scope.launch { ready.store.removeStep(s.id) } }.padding(8.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                SibButton("Go back to suggested steps", { scope.launch { ready.store.saveSteps(id, emptyList()) } }, kind = ButtonKind.TEXT)
            }
        }

        }
        if (tab == DetailTab.OVERVIEW && adjust) {
        // ---- pause ----
        if (o.lifecycle == Lifecycle.ACTIVE) {
            SectionHeader("Pause")
            val pausedUntil = o.postponedUntil
            if (pausedUntil != null) {
                Text("Paused until ${fmt.dayShort(pausedUntil)}. No work is planned before then.", style = SibType.body, color = c.ink)
                SibButton("Resume now", { scope.launch { ready.store.postpone(id, null) } }, kind = ButtonKind.QUIET)
            } else {
                Text("Pause planning without moving the date.", style = SibType.body, color = c.inkSoft)
                Spacer(Modifier.height(6.dp))
                SibButton("Pause until...", { pickDate = "pause" }, kind = ButtonKind.QUIET)
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
                    Text("Delete", style = SibType.small, color = c.vermilion, modifier = Modifier.clickable { scope.launch { ready.store.deleteExcerpt(e.id) } }.padding(8.dp))
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
        history.reversed().forEach { e ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(fmt.dayShort(Instant.ofEpochSecond(e.atEpochSec).atZone(st.zone).toLocalDate()), style = SibType.small, color = c.inkSoft, modifier = Modifier.width(96.dp))
                Column(Modifier.weight(1f)) {
                    if (e.field == "created") Text("Created: ${e.newValue}", style = SibType.small, color = c.ink)
                    else Text("${e.field.replaceFirstChar { it.uppercase() }}: ${e.oldValue}  →  ${e.newValue}", style = SibType.small, color = c.ink)
                    Text(
                        (if (e.actor == "USER") "by you" else "after you accepted a suggestion") + ", " + e.reason,
                        style = SibType.small.copy(fontSize = 12.sp), color = c.inkSoft,
                    )
                }
            }
        }

        }
        if (tab == DetailTab.OVERVIEW && adjust) {
        // ---- danger zone ----
        Spacer(Modifier.height(20.dp))
        if (o.lifecycle != Lifecycle.ACTIVE) {
            SibButton("Reopen", { scope.launch { ready.store.reopen(id) } }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
            Spacer(Modifier.height(8.dp))
        }
        SibButton("Delete this deadline", { confirmDelete = true }, Modifier.fillMaxWidth(), ButtonKind.DANGER)
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
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.raised,
            title = { Text("Delete this deadline?", style = SibType.title, color = c.ink) },
            text = { Text("Its history, steps and receipts are deleted from this phone. Backups you already made keep their own copy.", style = SibType.body, color = c.inkSoft) },
            confirmButton = { TextButton({ confirmDelete = false; scope.launch { ready.store.delete(id); onDeleted() } }) { Text("Delete", style = SibType.bodyStrong, color = c.vermilion) } },
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
