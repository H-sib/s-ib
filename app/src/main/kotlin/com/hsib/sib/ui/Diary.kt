package com.hsib.sib.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hsib.sib.Ready
import com.hsib.sib.core.medical.DiaryBucket
import com.hsib.sib.core.medical.DiaryEntry
import com.hsib.sib.core.medical.DiarySpan
import com.hsib.sib.core.medical.DiaryStats
import com.hsib.sib.data.ClassNote
import com.hsib.sib.domain.AppState
import com.hsib.sib.domain.Fmt
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

private enum class DiaryTab { WRITE, CHARTS, TABLES }

/**
 * The class diary: write each class (day, kind, subject, topic, teacher), see it as bars by day, week, month or year, and read
 * it as tables of teachers and topics. Everything stays on the phone and is part of the backup.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassDiaryScreen(st: AppState, ready: Ready, onBack: () -> Unit) {
    val c = SibTheme.colors
    val fmt = rememberFmt()
    val notes by ready.store.classNotes.collectAsState(initial = emptyList())
    val entries = remember(notes) { notes.map { DiaryEntry(it.id, it.day, it.subject, it.topic, it.faculty, it.kind) } }
    var tab by remember { mutableStateOf(DiaryTab.WRITE) }
    Page("Class diary", onBack) {
        Choice(listOf(DiaryTab.WRITE to "Write", DiaryTab.CHARTS to "Charts", DiaryTab.TABLES to "Tables"), tab, { tab = it })
        Spacer(Modifier.height(16.dp))
        when (tab) {
            DiaryTab.WRITE -> DiaryWrite(st, ready, notes, entries, fmt)
            DiaryTab.CHARTS -> DiaryCharts(entries, st.today, fmt)
            DiaryTab.TABLES -> DiaryTables(entries)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiaryWrite(st: AppState, ready: Ready, notes: List<ClassNote>, entries: List<DiaryEntry>, fmt: Fmt) {
    val c = SibTheme.colors
    val scope = rememberCoroutineScope()
    var editId by remember { mutableStateOf<String?>(null) }
    var day by remember { mutableStateOf(st.today) }
    var pickDay by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf("Lecture") }
    var subject by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var faculty by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var shown by remember { mutableIntStateOf(40) }
    var deleting by remember { mutableStateOf<ClassNote?>(null) }
    val courseSubjects = st.settings.course.phases.flatMap { it.subjects }
    val subjects = remember(entries, courseSubjects) { (courseSubjects + DiaryStats.subjects(entries)).distinctBy(DiaryStats::norm) }
    val teachers = remember(entries) { DiaryStats.facultyNames(entries).take(8) }
    val topics = remember(entries, subject) { if (subject.isBlank()) emptyList() else DiaryStats.topicNames(entries, subject).take(6) }

    Explain("Write down each class: the subject, the topic and who taught it. Tap a chip to reuse a name so the same teacher or topic is always spelled the same way. Nothing leaves your phone.")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (editId != null) Text("Editing a saved class", style = SibType.bodyStrong, color = c.violet)
        SibButton(fmt.dayLong(day), { pickDay = true }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Lecture", "Tutorial", "Ward").forEach { k -> SelectChip(k, kind == k, { kind = k }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            subjects.forEach { s -> SelectChip(s, DiaryStats.norm(subject) == DiaryStats.norm(s), { subject = s }) }
        }
        Field("Subject", subject, { subject = it.take(60) })
        if (topics.isNotEmpty()) {
            Text("Topics you wrote before", style = SibType.small, color = c.inkSoft)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                topics.forEach { t -> SelectChip(t, DiaryStats.norm(topic) == DiaryStats.norm(t), { topic = t }) }
            }
        }
        Field("Topic", topic, { topic = it.take(160) })
        if (teachers.isNotEmpty()) {
            Text("Teachers you wrote before", style = SibType.small, color = c.inkSoft)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                teachers.forEach { t -> SelectChip(t, DiaryStats.norm(faculty) == DiaryStats.norm(t), { faculty = t }) }
            }
        }
        Field("Faculty", faculty, { faculty = it.take(80) })
        SibButton(if (editId != null) "Save changes" else "Add to the diary", {
            val id = editId ?: UUID.randomUUID().toString()
            scope.launch {
                ready.store.addClassNote(ClassNote(id, day, subject.trim(), topic.trim(), faculty.trim(), kind))
                // The day, kind, subject and teacher stay for the next class of the same lesson; only the topic is cleared.
                topic = ""; editId = null; saved = true
            }
        }, Modifier.fillMaxWidth(), enabled = subject.isNotBlank() && topic.isNotBlank())
        if (editId != null) SibButton("Cancel", { editId = null; topic = ""; saved = false }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
        if (saved) Text("Saved. " + notes.size + " classes in the diary.", style = SibType.small, color = c.verdigris)
    }
    Spacer(Modifier.height(16.dp))
    if (notes.isEmpty()) Text("Nothing written yet.", style = SibType.body, color = c.inkSoft)
    val recent = notes.sortedWith(compareByDescending<ClassNote> { it.day }.thenBy { it.subject }).take(shown)
    recent.groupBy { it.day }.forEach { (d, list) ->
        SectionHeader(fmt.dayLong(d), count = list.size)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { n ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.raised).padding(16.dp)) {
                    Text(n.subject + " · " + n.kind, style = SibType.bodyStrong, color = c.ink)
                    Text(n.topic, style = SibType.body, color = c.ink)
                    if (n.faculty.isNotBlank()) Text(n.faculty, style = SibType.small, color = c.inkSoft)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SibButton("Edit", { editId = n.id; day = n.day; kind = n.kind; subject = n.subject; topic = n.topic; faculty = n.faculty; saved = false }, kind = ButtonKind.TEXT, compact = true)
                        SibButton("Delete", { deleting = n }, kind = ButtonKind.TEXT, compact = true)
                    }
                }
            }
        }
    }
    if (notes.size > shown) {
        Spacer(Modifier.height(12.dp))
        SibButton("Show more", { shown += 40 }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
    }
    deleting?.let { n ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this class?") },
            text = { Text(n.subject + ", " + n.topic + ". This cannot be undone.") },
            confirmButton = { SibButton("Delete", { scope.launch { ready.store.removeClassNote(n.id) }; deleting = null }, kind = ButtonKind.DANGER, compact = true) },
            dismissButton = { SibButton("Keep", { deleting = null }, kind = ButtonKind.QUIET, compact = true) },
        )
    }
    if (pickDay) SibDatePicker(day, { day = it; pickDay = false }, { pickDay = false })
}

private fun label(b: DiaryBucket, fmt: Fmt): String = when (b.span) {
    DiarySpan.DAY -> fmt.weekday(b.start).take(3) + "\n" + b.start.dayOfMonth
    DiarySpan.WEEK -> fmt.dayNoWeek(b.start)
    DiarySpan.MONTH -> fmt.monthShort(b.start) + "\n" + b.start.year
    DiarySpan.YEAR -> b.start.year.toString()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiaryCharts(entries: List<DiaryEntry>, today: LocalDate, fmt: Fmt) {
    val c = SibTheme.colors
    var span by remember { mutableStateOf(DiarySpan.WEEK) }
    if (entries.isEmpty()) { Text("Write a few classes first. The charts fill in by themselves.", style = SibType.body, color = c.inkSoft); return }
    val buckets = remember(entries, span, today) { DiaryStats.buckets(entries, span, today) }
    Choice(listOf(DiarySpan.DAY to "Day", DiarySpan.WEEK to "Week", DiarySpan.MONTH to "Month", DiarySpan.YEAR to "Year"), span, { span = it })
    Spacer(Modifier.height(14.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Classes", entries.size.toString())
        StatTile("Days written", DiaryStats.daysWritten(entries).toString())
        StatTile("Days in a row", DiaryStats.streakEndingAt(entries, today).toString())
    }
    Spacer(Modifier.height(16.dp))
    val lectureColor = c.violet
    val tutorialColor = c.ochre
    val wardColor = c.verdigris
    val peak = (buckets.maxOfOrNull { it.total } ?: 0).coerceAtLeast(1)
    val spanText = when (span) { DiarySpan.DAY -> "last 14 days"; DiarySpan.WEEK -> "last 12 weeks"; DiarySpan.MONTH -> "last 12 months"; DiarySpan.YEAR -> "each year" }
    Text("Classes, $spanText", style = SibType.bodyStrong, color = c.ink)
    Spacer(Modifier.height(8.dp))
    val barW = 44.dp
    val chartH = 160.dp
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised).padding(14.dp)
            .semantics { contentDescription = "Chart of classes, $spanText. Total ${buckets.sumOf { it.total }}. The Tables page has the exact numbers." },
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState(Int.MAX_VALUE)), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            buckets.forEach { b ->
                Column(Modifier.width(barW), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (b.total > 0) b.total.toString() else "", style = SibType.label, color = c.ink)
                    Canvas(Modifier.width(barW).height(chartH)) {
                        val unit = size.height / peak
                        var y = size.height
                        listOf(b.lectures to lectureColor, b.tutorials to tutorialColor, b.wards to wardColor).forEach { (n, col) ->
                            if (n > 0) { val h = n * unit; y -= h; drawRoundRect(col, Offset(size.width * 0.18f, y), Size(size.width * 0.64f, h), CornerRadius(6f, 6f)) }
                        }
                        if (b.total == 0) drawRoundRect(c.ink.copy(alpha = 0.15f), Offset(size.width * 0.18f, size.height - 4f), Size(size.width * 0.64f, 4f), CornerRadius(2f, 2f))
                    }
                    FitText(label(b, fmt), SibType.small.copy(fontSize = 11.sp), c.inkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Swipe sideways to see earlier ones.", style = SibType.small, color = c.inkSoft)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Legend("Lectures", lectureColor)
            Legend("Tutorials", tutorialColor)
            Legend("Ward or block", wardColor)
        }
    }
}

@Composable
private fun Legend(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = SibType.small, color = SibTheme.colors.ink)
    }
}

@Composable
private fun StatTile(label: String, value: String) {
    val c = SibTheme.colors
    Column(Modifier.clip(RoundedCornerShape(20.dp)).background(c.raised).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(value, style = SibType.title, color = c.violet)
        Text(label, style = SibType.small, color = c.inkSoft)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiaryTables(entries: List<DiaryEntry>) {
    val c = SibTheme.colors
    if (entries.isEmpty()) { Text("Write a few classes first. The tables fill in by themselves.", style = SibType.body, color = c.inkSoft); return }
    var subject by remember { mutableStateOf<String?>(null) }
    var topicsShown by remember { mutableIntStateOf(30) }
    val subjects = remember(entries) { DiaryStats.subjects(entries) }
    val pool = remember(entries, subject) { if (subject == null) entries else entries.filter { DiaryStats.norm(it.subject) == DiaryStats.norm(subject!!) } }
    val faculties = remember(pool) { DiaryStats.faculties(pool) }
    val topics = remember(pool) { DiaryStats.topics(pool) }
    val roomy = LocalDensity.current.fontScale <= 1.15f

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectChip("All subjects", subject == null, { subject = null; topicsShown = 30 })
        subjects.forEach { s -> SelectChip(s, subject != null && DiaryStats.norm(subject!!) == DiaryStats.norm(s), { subject = s; topicsShown = 30 }) }
    }
    Spacer(Modifier.height(16.dp))
    SectionHeader("Teachers", count = faculties.size)
    val none = DiaryStats.withoutFaculty(pool)
    if (faculties.isEmpty()) Text("No teacher written yet.", style = SibType.body, color = c.inkSoft)
    else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.raised).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (roomy) Row { Text("Teacher", style = SibType.label, color = c.inkSoft, modifier = Modifier.weight(1f)); Text("Classes", style = SibType.label, color = c.inkSoft, modifier = Modifier.width(64.dp)); Text("Topics", style = SibType.label, color = c.inkSoft, modifier = Modifier.width(56.dp)) }
        faculties.forEach { f ->
            if (roomy) Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) { Text(f.faculty, style = SibType.bodyStrong, color = c.ink); Text(f.subjects.joinToString(", "), style = SibType.small, color = c.inkSoft) }
                Text(f.classes.toString(), style = SibType.bodyStrong, color = c.violet, modifier = Modifier.width(64.dp))
                Text(f.topics.toString(), style = SibType.bodyStrong, color = c.violet, modifier = Modifier.width(56.dp))
            } else Column {
                Text(f.faculty, style = SibType.bodyStrong, color = c.ink)
                Text(f.classes.toString() + " classes, " + f.topics + " topics", style = SibType.body, color = c.violet)
                Text(f.subjects.joinToString(", "), style = SibType.small, color = c.inkSoft)
            }
        }
    }
    if (none > 0) { Spacer(Modifier.height(6.dp)); Text("$none classes have no teacher written, so they are not in this table.", style = SibType.small, color = c.inkSoft) }
    Spacer(Modifier.height(18.dp))
    SectionHeader("Topics", count = topics.size)
    Text("Same teacher is the teacher who took that topic most often. Other teachers are the rest.", style = SibType.small, color = c.inkSoft)
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        topics.take(topicsShown).forEach { t ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.raised).padding(14.dp)) {
                Text(t.topic, style = SibType.bodyStrong, color = c.ink)
                Text(t.subject, style = SibType.small, color = c.inkSoft)
                Spacer(Modifier.height(4.dp))
                Text(t.classes.toString() + (if (t.classes == 1) " class" else " classes") + ": " + t.sameFaculty + " from the same teacher, " + t.otherFaculty + " from others", style = SibType.body, color = c.ink)
                if (t.faculties.isNotEmpty()) Text(t.faculties.joinToString(", ") { it.first + " " + it.second }, style = SibType.small, color = c.inkSoft)
            }
        }
    }
    if (topics.size > topicsShown) { Spacer(Modifier.height(12.dp)); SibButton("Show more", { topicsShown += 30 }, Modifier.fillMaxWidth(), ButtonKind.QUIET) }
}
