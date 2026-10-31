package com.hsib.sib.ui

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
import com.hsib.sib.core.medical.Course
import com.hsib.sib.core.medical.CoursePhase
import com.hsib.sib.core.medical.Courses
import com.hsib.sib.core.model.Category
import com.hsib.sib.data.ClassNote
import com.hsib.sib.domain.AppState
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/** What a student or worker picks first: the phase or area, then the kind, then the subject or item. The title and the plan follow from it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicalPicker(course: Course, today: LocalDate, onPick: (kind: Category, title: String, parts: List<String>, suggestedDay: LocalDate?, hours: Int?) -> Unit) {
    val c = SibTheme.colors
    val work = Courses.isWork(course)
    var phaseIdx by remember(course.name) { mutableStateOf(-1) }
    var kind by remember { mutableStateOf<Category?>(null) }
    var type by remember { mutableStateOf<com.hsib.sib.core.medical.WorkType?>(null) }
    var subject by remember { mutableStateOf<String?>(null) }
    val prof: CoursePhase? = course.phases.getOrNull(phaseIdx)

    fun push() {
        val p = prof
        val tp = type
        if (work && tp != null) {
            val cat = Category.values().firstOrNull { it.name == tp.category && !it.close } ?: Category.OTHER
            onPick(cat, listOfNotNull(subject, tp.name).joinToString(" "), emptyList(), null, tp.hours)
            return
        }
        val k = kind ?: return
        val whole = k == Category.PROF || k == Category.SUPPLEMENTARY
        val day = if (p != null && (whole || k == Category.TERM)) Medical.nextExamMonthStart(p, today, k == Category.SUPPLEMENTARY) else null
        onPick(k, Medical.title(k, subject, p), if (p != null) Medical.partsFor(k, p) else emptyList(), day, null)
    }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised).padding(18.dp)) {
        InfoTitle(course.name + " quick start", if (work) "Pick the area of your work, the kind of deadline and the item. The title and the hours are filled in for you, and you can still change every one of them." else "Pick your phase, the kind of exam and the subject. The title, the time needed and the study steps are filled in for you, and you can still change every one of them.")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            course.phases.forEachIndexed { i, p -> SelectChip(p.name, phaseIdx == i, { phaseIdx = i; subject = null; kind = null; type = null }) }
        }
        if (prof != null) {
            Spacer(Modifier.height(12.dp))
            Text(if (work) "Kind of deadline" else "Kind", style = SibType.bodyStrong, color = c.ink)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (work) prof.types.forEach { tp -> SelectChip(tp.name, type == tp, { type = tp; push() }) }
                else Medical.kinds(prof).forEach { k -> SelectChip(k.label, kind == k, { kind = k; push() }) }
            }
            val showSubjects = if (work) type != null else kind != null && kind != Category.PROF && kind != Category.SUPPLEMENTARY
            if (showSubjects) {
                Spacer(Modifier.height(12.dp))
                Text(if (work) "Item" else if (kind == Category.PRACTICAL) "Subject or report" else "Subject", style = SibType.bodyStrong, color = c.ink)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val choices = if (!work && kind == Category.PRACTICAL) prof.practicals else prof.subjects
                    choices.forEach { s -> SelectChip(s, subject == s, { subject = s; push() }) }
                }
            }
            if (prof.week.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text("${prof.week.replaceFirstChar { it.uppercase() }}. Your own numbers go in Class routine.", style = SibType.small, color = c.inkSoft)
            }
        }
    }
}

/** Settings page for medical student mode. */
@Composable
fun StudySettings(st: AppState, ready: Ready, onBack: () -> Unit, onDiary: () -> Unit, onRoutine: () -> Unit, onCourse: () -> Unit) {
    val scope = rememberCoroutineScope()
    Page("Study tools", onBack) {
        Explain("Your class routine and class diary, and the switch for your setup. Pick or change the setup itself under More, Make it yours. Nothing leaves your phone.")
        ToggleRow("Use my setup", "Turn this off to go back to the plain app. Your deadlines stay.", st.settings.medicalMode, { v -> scope.launch { ready.settings.update { it.copy(medicalMode = v, medicalAsked = true) } } })
        if (st.settings.medicalMode) {
            Group("Your course") {
                NavItem("My setup", st.settings.course.name + ": choose, edit or share", onCourse)
                GroupDivider()
                NavItem("Class routine", "Your week, Saturday to Thursday: lectures, tutorials, ward classes", onRoutine)
                GroupDivider()
                NavItem("Class diary", "Which lecture, which subject, which topic, who taught it", onDiary)
                GroupDivider()
                if (st.settings.course.name == "MBBS") ExpandItem("How the course is laid out", Medical.STRUCTURE)
                for (p in st.settings.course.phases) {
                    GroupDivider()
                    ExpandItem(p.name + (if (p.duration.isNotBlank()) ", " + p.duration else ""), p.subjects.joinToString(", ") + ". Week: " + p.week + ". Practical work: " + p.practicals.joinToString(", ") + ".")
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
    val course = st.settings.course
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
            val subjects = course.phases.flatMap { it.subjects }.distinct()
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
