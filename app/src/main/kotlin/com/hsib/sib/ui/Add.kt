package com.hsib.sib.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hsib.sib.Ready
import com.hsib.sib.core.extract.EffortParser
import com.hsib.sib.core.extract.Extraction
import com.hsib.sib.core.extract.VoiceHints
import com.hsib.sib.core.model.Candidate
import com.hsib.sib.core.model.Category
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Effort
import com.hsib.sib.core.model.Importance
import com.hsib.sib.core.model.Kind
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Part
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.model.Recurrence
import com.hsib.sib.core.model.Repeat
import com.hsib.sib.core.plan.Defaults
import com.hsib.sib.domain.AppState
import com.hsib.sib.platform.Sharing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/** The ways to bring something in. The launchers live with the activity; the screens only call these. */
class ImportActions(
    val paste: () -> Unit,
    val photo: () -> Unit,
    val scan: () -> Unit,
    val file: () -> Unit,
)

@Composable
fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = SibTheme.colors
    val shape = RoundedCornerShape(50)
    Text(
        text, style = SibType.bodyStrong.copy(fontSize = 14.sp), color = if (selected) c.onAction else c.ink,
        modifier = modifier.heightIn(min = 48.dp).clip(shape)
            .then(if (selected) Modifier.background(c.action) else Modifier.background(c.lifted))
            .bounceClick(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddScreen(st: AppState, ready: Ready, importActions: ImportActions, onBack: () -> Unit, onJoin: (() -> Unit)? = null, onSaved: (String) -> Unit) {
    val c = SibTheme.colors
    val fmt = rememberFmt()
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var time by remember { mutableStateOf<LocalTime?>(null) }
    var category by remember { mutableStateOf(Category.ASSIGNMENT) }
    var importance by remember { mutableStateOf(Importance.NORMAL) }
    var effortMin by remember { mutableStateOf(Defaults.effort(Category.ASSIGNMENT).likelyMin) }
    var touchedTitle by remember { mutableStateOf(false) }
    var touchedDate by remember { mutableStateOf(false) }
    var touchedEffort by remember { mutableStateOf(false) }
    var touchedCategory by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Extraction?>(null) }
    var reading by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var series by remember { mutableStateOf(Repeat.NONE) }
    var moreOptions by remember { mutableStateOf(false) }
    var seriesCount by remember { mutableStateOf(4) }
    var shareAsk by remember { mutableStateOf<List<Obligation>?>(null) }
    var medParts by remember { mutableStateOf<List<String>>(emptyList()) }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == android.app.Activity.RESULT_OK && !said.isNullOrBlank()) text = (if (text.isBlank()) "" else text.trim() + ". ") + said
    }
    val hints = remember(text) { VoiceHints.parse(text) }
    val context = LocalContext.current

    LaunchedEffect(text) {
        if (text.isBlank()) { preview = null; return@LaunchedEffect }
        delay(250)
        preview = ready.importer.preview(text)
        reading = 0
    }
    val cand: Candidate? = preview?.candidates?.firstOrNull()
    val parsedEffort = remember(text) { EffortParser.parse(text) }
    val readings = listOfNotNull(cand?.due) + (cand?.alternatives ?: emptyList())

    // Fill the form from what was understood, but never overwrite something the user typed or picked.
    LaunchedEffect(cand?.id, reading, parsedEffort, hints) {
        if (cand != null) {
            if (!touchedTitle) title = cand.title
            if (!touchedCategory) { category = cand.category; importance = Defaults.importance(cand.category) }
            val d = readings.getOrNull(reading) ?: cand.due
            if (!touchedDate && d != null) {
                date = d.local.toLocalDate()
                time = if (d.precision == Precision.DATE_TIME) d.local.toLocalTime() else null
            }
        }
        if (!touchedEffort) effortMin = hints.effortMin ?: parsedEffort ?: Defaults.effort(category).likelyMin
    }

    Page("Add", onBack) {
        Spacer(Modifier.height(4.dp))
        if (st.settings.medicalMode) {
            MedicalPicker(st.today) { kind, medTitle, parts, day ->
                category = kind; touchedCategory = true; importance = Defaults.importance(kind)
                if (!touchedEffort) effortMin = Defaults.effort(kind).likelyMin
                if (!touchedTitle) title = medTitle
                medParts = parts
                if (!touchedDate && day != null) date = day
            }
            Spacer(Modifier.height(12.dp))
        }
        Field(
            "Describe it", text, { text = it }, placeholder = "Report due 18 Nov, 11:59 PM, about 6 hours",
            singleLine = false, minLines = 2, keyboard = KeyboardOptions(imeAction = ImeAction.Default),
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconAction(SibIcon.PASTE, "Paste", Modifier.weight(1f), onClick = importActions.paste)
            IconAction(SibIcon.PHOTO, "Photo", Modifier.weight(1f), onClick = importActions.photo)
            IconAction(SibIcon.SCAN, "Scan", Modifier.weight(1f), onClick = importActions.scan)
            IconAction(SibIcon.FILE, "File", Modifier.weight(1f), onClick = importActions.file)
            IconAction(SibIcon.CHAT, "Speak", Modifier.weight(1f), description = "Say it out loud. The phone's own speech window opens.") {
                runCatching {
                    voice.launch(
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the deadline, the date and the hours"),
                    )
                }
            }
        }
        if (onJoin != null) SibButton("Join a shared deadline with a code", onJoin, kind = ButtonKind.TEXT)

        // What was understood, in plain words, so a wrong reading is caught before it is saved.
        if (text.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised).padding(20.dp)) {
                if (cand != null) {
                    Text("Understood as", style = SibType.bodyStrong, color = c.ink)
                    Text(cand.title, style = SibType.title, color = c.ink)
                    val d = readings.getOrNull(reading) ?: cand.due
                    d?.let { Text(fmt.due(it.local, it.precision == Precision.DATE_ONLY), style = SibType.bodyStrong, color = c.ink) }
                    cand.fog?.let { Text("Somewhere ${fmt.range(it.earliest, it.latest)}", style = SibType.bodyStrong, color = c.ink) }
                    val warnings = cand.flags.mapNotNull { flagText(it, cand, fmt, st.today) }
                    if (warnings.isNotEmpty()) More(warnings.joinToString("\n\n"), label = "Check this")
                    if (readings.size > 1) {
                        Spacer(Modifier.height(6.dp))
                        Text("Other readings", style = SibType.bodyStrong, color = c.ink)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            readings.forEachIndexed { i, r -> SelectChip(fmt.due(r.local, r.precision == Precision.DATE_ONLY), reading == i, { reading = i; touchedDate = false }) }
                        }
                    }
                } else {
                    val why = preview?.outcomes?.firstOrNull()?.explanation
                    Text(why ?: "No date found yet", style = SibType.body, color = c.ink)
                }
            }
        }

        Gap(24.dp)
        Field("Title", title, { title = it; touchedTitle = true })
        Spacer(Modifier.height(14.dp))
        Text("Date", style = SibType.bodyStrong, color = c.ink)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
            SibButton(date?.let { fmt.dayShort(it) } ?: "Choose a date", { pickDate = true }, kind = ButtonKind.QUIET)
            if (date != null) {
                Spacer(Modifier.width(8.dp))
                SibButton("No date", { date = null; time = null; touchedDate = true }, kind = ButtonKind.TEXT)
            }
        }
        if (date != null) {
            ToggleRow("Include a time", "Turn on if it is due at a certain hour, such as 10:00. Off means it is due any time that day.", time != null, { time = if (it) (time ?: LocalTime.of(23, 59)) else null; touchedDate = true })
            time?.let { t -> TimePick(t, fmt) { time = it; touchedDate = true } }
        }

        Gap(12.dp)
        SibButton(if (moreOptions) "Fewer options" else "More options", { moreOptions = !moreOptions }, kind = ButtonKind.TEXT)
        if (moreOptions) {
        Spacer(Modifier.height(16.dp))
        Text("Kind", style = SibType.bodyStrong, color = c.ink)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (Category.work + if (st.settings.medicalMode) Category.medical else emptyList()).forEach { cat ->
                SelectChip(cat.label, category == cat, { category = cat; touchedCategory = true; importance = Defaults.importance(cat); if (!touchedEffort) effortMin = Defaults.effort(cat).likelyMin })
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Importance", style = SibType.bodyStrong, color = c.ink)
        Choice(listOf(Importance.LOW to "Low", Importance.NORMAL to "Normal", Importance.HIGH to "High", Importance.CRITICAL to "Critical"), importance, { importance = it })
        Spacer(Modifier.height(16.dp))
        Text("Effort", style = SibType.bodyStrong, color = c.ink)
        if (parsedEffort != null && !touchedEffort) Text("Read from your text: about ${fmt.minutes(parsedEffort)}.", style = SibType.small, color = c.inkSoft)
        Choice(
            EffortChoices,
            listOf(30, 120, 240, 480, 960).minBy { kotlin.math.abs(it - effortMin) },
            { effortMin = it; touchedEffort = true },
        )
        if (parsedEffort != null && listOf(30, 120, 240, 480, 960).none { it == parsedEffort } && !touchedEffort) {
            Text("Planning will use exactly ${fmt.minutes(parsedEffort)}.", style = SibType.small, color = c.inkSoft, modifier = Modifier.padding(top = 4.dp))
        }

        val firstDay = date
        if (firstDay != null) {
            Spacer(Modifier.height(16.dp))
            Text("Repeat", style = SibType.bodyStrong, color = c.ink)
            Choice(listOf(Repeat.NONE to "Once", Repeat.WEEKLY to "Weekly", Repeat.FORTNIGHTLY to "2 weeks", Repeat.MONTHLY to "Monthly"), series, { series = it })
            if (series != Repeat.NONE) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    SibButton("−", { seriesCount = (seriesCount - 1).coerceAtLeast(2) }, kind = ButtonKind.QUIET)
                    Text("$seriesCount deadlines", style = SibType.headline, color = c.ink, modifier = Modifier.padding(horizontal = 16.dp))
                    SibButton("+", { seriesCount = (seriesCount + 1).coerceAtMost(52) }, kind = ButtonKind.QUIET)
                }
                val last = Recurrence.occurrence(firstDay, series, seriesCount - 1L)
                Text("${series.label}, until ${fmt.dayShort(last)}", style = SibType.small, color = c.inkSoft, modifier = Modifier.padding(top = 4.dp))
            }
        }
        }

        Spacer(Modifier.height(24.dp))
        SibButton(
            if (saving) "Saving" else "Save", {
                saving = true
                val d = date
                val zone = (readings.getOrNull(reading) ?: cand?.due)?.zone ?: st.zone
                val effort = Effort.fromLikely(effortMin)
                val count = if (series == Repeat.NONE || d == null) 1 else seriesCount
                val made = (0 until count).map { i ->
                    val day = d?.let { Recurrence.occurrence(it, series, i.toLong()) }
                    val due = day?.let { DueSpec(LocalDateTime.of(it, time ?: LocalTime.MIDNIGHT), zone, if (time != null) Precision.DATE_TIME else Precision.DATE_ONLY) }
                    Obligation(
                        id = UUID.randomUUID().toString(), title = if (count > 1) "${title.trim()} (${i + 1} of $count)" else title.trim(), category = category,
                        kind = if (due == null) Kind.OPEN_ENDED else Kind.DEADLINE, due = due, importance = importance, effort = effort,
                        parts = medParts.map { Part(it) }, leadDays = hints.leadDays,
                    )
                }
                scope.launch {
                    val saved = made.map { ready.store.create(it, "USER", if (count > 1) "created as part of a series" else "created by typing") }
                    if (saved.size > 1) shareAsk = saved else onSaved(saved.first().id)
                }
            },
            Modifier.fillMaxWidth(), enabled = title.isNotBlank() && !saving,
        )
        Spacer(Modifier.height(40.dp))
    }

    shareAsk?.let { list ->
        AlertDialog(
            onDismissRequest = { shareAsk = null; onSaved(list.first().id) },
            containerColor = c.raised,
            title = { Text("Share this series?", style = SibType.title, color = c.ink) },
            text = {
                Text(
                    "Friends with the same deadlines can add all ${list.size} to their calendar in one tap. Only titles and dates are sent, and nobody needs an account.",
                    style = SibType.body, color = c.inkSoft,
                )
            },
            confirmButton = {
                TextButton({ Sharing.share(context, list, st.zone, "A series of deadlines:"); shareAsk = null; onSaved(list.first().id) }) {
                    Text("Share", style = SibType.bodyStrong, color = c.ink)
                }
            },
            dismissButton = { TextButton({ shareAsk = null; onSaved(list.first().id) }) { Text("Not now", style = SibType.body, color = c.inkSoft) } },
        )
    }

    if (pickDate) SibDatePicker(date ?: st.today, { date = it; touchedDate = true; pickDate = false }, { pickDate = false })
}
