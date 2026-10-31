package com.hsib.sib.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hsib.sib.Ready
import com.hsib.sib.core.medical.Medical
import com.hsib.sib.core.medical.Prof
import com.hsib.sib.core.model.Category
import com.hsib.sib.data.ClassNote
import com.hsib.sib.domain.AppState
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/** What a medical student picks first: the prof, then the kind of exam, then the subject. The title and the plan follow from it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicalPicker(today: LocalDate, onPick: (kind: Category, title: String, parts: List<String>, suggestedDay: LocalDate?) -> Unit) {
    val c = SibTheme.colors
    var profNo by remember { mutableStateOf(0) }
    var kind by remember { mutableStateOf<Category?>(null) }
    var subject by remember { mutableStateOf<String?>(null) }
    val prof: Prof? = if (profNo == 0) null else Medical.prof(profNo)

    fun push() {
        val k = kind ?: return
        val whole = k == Category.PROF || k == Category.SUPPLEMENTARY
        val day = if (prof != null && (whole || k == Category.TERM)) Medical.nextExamMonthStart(prof, today, k == Category.SUPPLEMENTARY) else null
        onPick(k, Medical.title(k, subject, prof), if (prof != null) Medical.partsFor(k, prof) else emptyList(), day)
    }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised).padding(18.dp)) {
        InfoTitle("Medical quick start", "Pick your prof, the kind of exam and the subject. The title, the time needed and the study steps are filled in for you, and you can still change every one of them.")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Medical.profs.forEach { p -> SelectChip(p.name, profNo == p.number, { profNo = p.number; subject = null; if (kind !in Medical.kinds(p)) kind = null; push() }) }
        }
        if (prof != null) {
            Spacer(Modifier.height(12.dp))
            Text("Kind", style = SibType.bodyStrong, color = c.ink)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Medical.kinds(prof).forEach { k -> SelectChip(k.label, kind == k, { kind = k; push() }) }
            }
            if (kind != null && kind != Category.PROF && kind != Category.SUPPLEMENTARY) {
                Spacer(Modifier.height(12.dp))
                Text(if (kind == Category.REPORT || kind == Category.PRACTICAL) "Subject or report" else "Subject", style = SibType.bodyStrong, color = c.ink)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val choices = if (kind == Category.REPORT || kind == Category.PRACTICAL) prof.practicals else prof.subjects
                    choices.forEach { s -> SelectChip(s, subject == s, { subject = s; push() }) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("A normal week: ${prof.week}.", style = SibType.small, color = c.inkSoft)
        }
    }
}

/** Settings page for medical student mode. */
@Composable
fun StudySettings(st: AppState, ready: Ready, onBack: () -> Unit, onDiary: () -> Unit) {
    val scope = rememberCoroutineScope()
    Page("Medical student", onBack) {
        Explain("Made for MBBS students. It adds your exam types (item, card, term, prof, practical, ward final, supplementary), your subjects by prof, study steps, and a class diary. Nothing leaves your phone.")
        ToggleRow("Medical student mode", "Turn this on if you study medicine. You can turn it off any time. Your deadlines stay.", st.settings.medicalMode, { v -> scope.launch { ready.settings.update { it.copy(medicalMode = v, medicalAsked = true) } } })
        if (st.settings.medicalMode) {
            Group("Your course") {
                NavItem("Class diary", "Which lecture, which subject, which topic, who taught it", onDiary)
                GroupDivider()
                ExpandItem("How the course is laid out", Medical.STRUCTURE)
                for (p in Medical.profs) {
                    GroupDivider()
                    ExpandItem(p.name, p.subjects.joinToString(", ") + ". Week: " + p.week + ". Practical work: " + p.practicals.joinToString(", ") + ".")
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** A calendar of classes kept by the student: the day, the subject, the topic and the faculty. Stays on the phone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassDiaryScreen(st: AppState, ready: Ready, onBack: () -> Unit) {
    val c = SibTheme.colors
    val fmt = rememberFmt()
    val scope = rememberCoroutineScope()
    val notes by ready.store.classNotes.collectAsState(initial = emptyList())
    var day by remember { mutableStateOf(st.today) }
    var pickDay by remember { mutableStateOf(false) }
    var subject by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var faculty by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("Lecture") }
    Page("Class diary", onBack) {
        Explain("Write down each class: the subject, the topic and who taught it. It helps you see what was covered before a card, term or prof exam.")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SibButton(fmt.dayLong(day), { pickDay = true }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Lecture", "Tutorial", "Ward").forEach { k -> SelectChip(k, kind == k, { kind = k }) }
            }
            val subjects = Medical.profs.flatMap { it.subjects }.distinct()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                subjects.forEach { s -> SelectChip(s, subject == s, { subject = s }) }
            }
            Field("Subject", subject, { subject = it.take(60) })
            Field("Topic", topic, { topic = it.take(160) })
            Field("Faculty", faculty, { faculty = it.take(80) })
            SibButton("Add to the diary", {
                scope.launch { ready.store.addClassNote(ClassNote(UUID.randomUUID().toString(), day, subject.trim(), topic.trim(), faculty.trim(), kind)); topic = "" }
            }, Modifier.fillMaxWidth(), enabled = subject.isNotBlank() && topic.isNotBlank())
        }
        Spacer(Modifier.height(16.dp))
        if (notes.isEmpty()) Text("Nothing written yet.", style = SibType.body, color = c.inkSoft)
        notes.groupBy { it.day }.forEach { (d, list) ->
            SectionHeader(fmt.dayLong(d), count = list.size)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { n ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.raised).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(n.subject + " · " + n.kind, style = SibType.bodyStrong, color = c.ink)
                            Text(n.topic, style = SibType.body, color = c.ink)
                            if (n.faculty.isNotBlank()) Text(n.faculty, style = SibType.small, color = c.inkSoft)
                        }
                        SibButton("Delete", { scope.launch { ready.store.removeClassNote(n.id) } }, kind = ButtonKind.TEXT, compact = true)
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
    if (pickDay) SibDatePicker(day, { day = it; pickDay = false }, { pickDay = false })
}
