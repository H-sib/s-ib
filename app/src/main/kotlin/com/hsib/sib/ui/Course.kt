package com.hsib.sib.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hsib.sib.Ready
import com.hsib.sib.core.medical.Course
import com.hsib.sib.core.medical.CoursePhase
import com.hsib.sib.core.medical.CourseTemplate
import com.hsib.sib.core.medical.CourseTemplates
import com.hsib.sib.core.medical.Courses
import com.hsib.sib.core.model.Category
import com.hsib.sib.domain.AppState
import kotlinx.coroutines.launch
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

private fun monthName(m: Int) = Month.of(m.coerceIn(1, 12)).getDisplayName(TextStyle.FULL, Locale.getDefault())

/** A name that is not taken yet: "IUB", then "IUB (2)", and so on. */
private fun freeName(wanted: String, taken: Set<String>): String {
    if (wanted !in taken) return wanted
    var i = 2
    while ("$wanted ($i)" in taken) i++
    return "$wanted ($i)"
}

/** Saves a course and makes it the one in use. A saved course with the same name is replaced. The built in one is never overwritten. */
private suspend fun useCourse(ready: Ready, st: AppState, c: Course, replacing: String? = null) {
    val clean = Courses.sanitize(c) ?: return
    ready.settings.update { cur ->
        val others = cur.courses.filter { it.name != (replacing ?: clean.name) }
        val taken = others.map { it.name }.toSet() + Courses.mbbs.name
        val named = clean.copy(name = if (replacing == null || clean.name != replacing) freeName(clean.name, taken) else clean.name)
        cur.copy(courses = others + named, courseActive = named.name, medicalMode = true, medicalAsked = true)
    }
    // Only when the person switched the count on and is signed in. A failure changes nothing for them.
    if (st.settings.courseCount) runCatching { ready.social?.countCourse(ready.settings.current().course) }
}

/** A big tinted choice with an icon: where to go to set the app up for study, work or daily life. */
@Composable
private fun SetupTile(icon: SibIcon, title: String, sub: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val c = SibTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(tint.copy(alpha = 0.14f)).bounceClick(onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, size = 52.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = SibType.title, color = c.ink)
            Text(sub, style = SibType.small, color = c.inkSoft)
        }
    }
}

/** The place to set the app up for you: study, work or daily life, then edit, share, or take a setup from a friend. */
@Composable
fun CourseScreen(st: AppState, ready: Ready, onBack: () -> Unit, onEdit: (String) -> Unit, onWizard: () -> Unit, onImport: () -> Unit, onTemplates: (String) -> Unit) {
    val c = SibTheme.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val active = st.settings.course
    val on = st.settings.medicalMode
    Page("Make it yours", onBack) {
        Explain("Choose what fits your life and the app is set up for it: your deadline types, subjects or areas, usual hours and exam months. If something does not match, edit it. Everything can be saved and shared with friends or colleagues.")
        SetupTile(SibIcon.BOOK, CourseTemplates.STUDY, "Medical, dental, engineering, law, nursing, business and more", c.violet) { onTemplates(CourseTemplates.STUDY) }
        Spacer(Modifier.height(12.dp))
        SetupTile(SibIcon.GAUGE, CourseTemplates.WORK, "Office, teacher, developer, doctor, lawyer, banker and more", c.verdigris) { onTemplates(CourseTemplates.WORK) }
        Spacer(Modifier.height(12.dp))
        SetupTile(SibIcon.HEART, CourseTemplates.LIFE, "Home, parenting, health, travel, hobbies, job hunting and more", c.vermilion) { onTemplates(CourseTemplates.LIFE) }
        Spacer(Modifier.height(20.dp))
        Group(if (on) "In use: ${active.name}" else "Not set up yet") {
            if (on) {
                NavItem("Edit", "Change the areas, subjects, kinds of deadline, hours and months", { onEdit(active.name) })
                GroupDivider()
                NavItem("Share", "Send it to a friend as a short piece of text", {
                    val text = "My setup for H-sib Reminder: ${active.name}. In the app open More, Make it yours, Take a setup from a friend, and paste this.\n\n" + Courses.toCode(active)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share my setup"))
                })
                if (active.name != Courses.mbbs.name) {
                    GroupDivider()
                    NavItem("Delete from this phone", null, { scope.launch { ready.settings.update { cur -> cur.copy(courses = cur.courses.filter { it.name != active.name }, courseActive = Courses.mbbs.name) } } })
                }
                GroupDivider()
                ToggleRow("Count my setup anonymously", "Off by default. When on, and signed in for friends and chat, your setup is added once to a count of people who use the same one, so popular setups can be offered to everyone. The server keeps a number and the setup's name, nothing about you.", st.settings.courseCount, { v ->
                    scope.launch { ready.settings.update { it.copy(courseCount = v) }; if (v) runCatching { ready.social?.countCourse(active) } }
                })
                GroupDivider()
                NavItem("Switch it off", "Go back to the plain app. Your deadlines stay.", { scope.launch { ready.settings.update { it.copy(medicalMode = false) } } })
            } else {
                ExpandItem("How it works", "Pick one of the three choices above. The app then shows your own kinds of deadline in Add, with the usual hours filled in.")
            }
        }
        if (st.settings.courses.isNotEmpty()) {
            Group("Saved on this phone") {
                st.settings.courses.forEachIndexed { i, course ->
                    if (i > 0) GroupDivider()
                    NavItem(course.name, course.field, { scope.launch { ready.settings.update { it.copy(courseActive = course.name, medicalMode = true, medicalAsked = true) } } }, trailing = if (on && course.name == active.name) "In use" else "")
                }
            }
        }
        Group("More") {
            NavItem("Make one by answering questions", "For a college, a job or anything else", onWizard)
            GroupDivider()
            NavItem("Take a setup from a friend", "Paste the text they sent you", onImport)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun MonthStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    val c = SibTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = SibType.bodyStrong, color = c.ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SibButton("Back", { onChange(if (value <= 1) 12 else value - 1) }, kind = ButtonKind.QUIET, compact = true)
            Text(monthName(value), style = SibType.title, color = c.ink, modifier = Modifier.width(120.dp))
            SibButton("Next", { onChange(if (value >= 12) 1 else value + 1) }, kind = ButtonKind.QUIET, compact = true)
        }
    }
}

/** Edit a course. The built in one opens as a copy, because it is never overwritten. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CourseEditor(st: AppState, ready: Ready, name: String, onBack: () -> Unit) {
    val c = SibTheme.colors
    val scope = rememberCoroutineScope()
    val original = remember(name) { (st.settings.courses + Courses.mbbs).firstOrNull { it.name == name } ?: Courses.mbbs }
    val builtIn = original.name == Courses.mbbs.name && st.settings.courses.none { it.name == name }
    var draft by remember(name) { mutableStateOf(if (builtIn) original.copy(name = "My " + original.name) else original) }
    var pick by remember { mutableStateOf<Triple<Int, String, Boolean>?>(null) }
    var newSubject by remember { mutableStateOf(mapOf<Int, String>()) }
    var newPractical by remember { mutableStateOf(mapOf<Int, String>()) }
    var newType by remember { mutableStateOf(mapOf<Int, String>()) }
    var editType by remember { mutableStateOf<Pair<Int, com.hsib.sib.core.medical.WorkType>?>(null) }
    fun setPhase(i: Int, f: (CoursePhase) -> CoursePhase) { draft = draft.copy(phases = draft.phases.mapIndexed { j, p -> if (j == i) f(p) else p }) }

    Page("Edit course", onBack) {
        Explain("Change anything. Tap a subject or a practical to move or remove it. A subject that sits in the wrong phase can be moved with one tap.")
        Field("Name of the course", draft.name, { draft = draft.copy(name = it.take(60)) })
        Spacer(Modifier.height(8.dp))
        Text("Field", style = SibType.bodyStrong, color = c.ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Courses.FIELDS.forEach { f -> SelectChip(f, draft.field == f, { draft = draft.copy(field = f) }) }
        }
        draft.phases.forEachIndexed { i, p ->
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Phase ${i + 1} name", p.name, { v -> setPhase(i) { it.copy(name = v.take(40)) } })
                Field("Name of its main exam", p.examName, { v -> setPhase(i) { it.copy(examName = v.take(80)) } }, placeholder = "1st Professional Exam")
                Field("How long", p.duration, { v -> setPhase(i) { it.copy(duration = v.take(40)) } }, placeholder = "1 year")
                InfoTitle("Subjects", "These become the subject buttons in Add and in your class routine. A prof or term exam gets one checklist item for each. Tap a subject to move it to another phase or remove it.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    p.subjects.forEach { s -> SelectChip(s, false, { pick = Triple(i, s, true) }) }
                }
                Field("Add a subject", newSubject[i].orEmpty(), { v -> newSubject = newSubject + (i to v.take(80)) }, onDone = {
                    val s = newSubject[i].orEmpty().trim(); if (s.isNotEmpty()) setPhase(i) { it.copy(subjects = (it.subjects + s).distinct()) }; newSubject = newSubject + (i to "")
                })
                Text("Practical work and reports", style = SibType.bodyStrong, color = c.ink)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    p.practicals.forEach { s -> SelectChip(s, false, { pick = Triple(i, s, false) }) }
                }
                Field("Add one", newPractical[i].orEmpty(), { v -> newPractical = newPractical + (i to v.take(80)) }, onDone = {
                    val s = newPractical[i].orEmpty().trim(); if (s.isNotEmpty()) setPhase(i) { it.copy(practicals = (it.practicals + s).distinct()) }; newPractical = newPractical + (i to "")
                })
                InfoTitle("Kinds of deadline", "For a job: the kinds of deadline in this area, with the hours each usually takes. They become the buttons in Add. Tap one to change its hours or remove it.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    p.types.forEach { tp -> SelectChip(tp.name + ", " + tp.hours + " h", false, { editType = i to tp }) }
                }
                Field("Add a kind of deadline", newType[i].orEmpty(), { v -> newType = newType + (i to v.take(40)) }, onDone = {
                    val s = newType[i].orEmpty().trim(); if (s.isNotEmpty()) setPhase(i) { it.copy(types = (it.types + com.hsib.sib.core.medical.WorkType(s, "OTHER", 2)).distinctBy { t -> t.name }) }; newType = newType + (i to "")
                })
                Text("Exam kinds in this phase", style = SibType.bodyStrong, color = c.ink)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Courses.selectableKinds().forEach { k ->
                        SelectChip(k.label, k.name in p.kinds, { setPhase(i) { it.copy(kinds = if (k.name in it.kinds) it.kinds - k.name else it.kinds + k.name) } })
                    }
                }
                MonthStepper("Exam month", p.examMonth) { m -> setPhase(i) { it.copy(examMonth = m) } }
                MonthStepper("Supplementary month", p.supplementaryMonth) { m -> setPhase(i) { it.copy(supplementaryMonth = m) } }
                if (draft.phases.size > 1) SibButton("Remove this phase", { draft = draft.copy(phases = draft.phases.filterIndexed { j, _ -> j != i }) }, kind = ButtonKind.TEXT)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (draft.phases.size < 10) SibButton("Add a phase", { draft = draft.copy(phases = draft.phases + CoursePhase("Phase ${draft.phases.size + 1}", kinds = listOf("ITEM", "TERM", "PROF"))) }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
        Spacer(Modifier.height(12.dp))
        SibButton(if (builtIn) "Save as my own course" else "Save", { scope.launch { useCourse(ready, st, draft, replacing = if (builtIn) null else original.name); onBack() } }, Modifier.fillMaxWidth(), enabled = draft.name.isNotBlank() && draft.phases.isNotEmpty())
        if (!builtIn) SibButton("Save as a new copy", { scope.launch { useCourse(ready, st, draft, replacing = null); onBack() } }, Modifier.fillMaxWidth(), ButtonKind.QUIET, enabled = draft.name.isNotBlank())
        Spacer(Modifier.height(32.dp))
    }

    editType?.let { (i, tp) ->
        var hours by remember(tp.name) { mutableStateOf(tp.hours) }
        var cat by remember(tp.name) { mutableStateOf(tp.category) }
        AlertDialog(
            onDismissRequest = { editType = null }, containerColor = c.raised,
            title = { Text(tp.name, style = SibType.title, color = c.ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Usual hours", style = SibType.bodyStrong, color = c.ink)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SibButton("-", { hours = (hours - 1).coerceAtLeast(1) }, kind = ButtonKind.QUIET, compact = true)
                        Text("$hours h", style = SibType.title, color = c.ink)
                        SibButton("+", { hours = (hours + 1).coerceAtMost(200) }, kind = ButtonKind.QUIET, compact = true)
                    }
                    Text("It counts as", style = SibType.bodyStrong, color = c.ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Category.work.forEach { k -> SelectChip(k.label, cat == k.name, { cat = k.name }) }
                    }
                    SibButton("Remove", { setPhase(i) { it.copy(types = it.types.filter { t -> t.name != tp.name }) }; editType = null }, Modifier.fillMaxWidth(), ButtonKind.DANGER)
                }
            },
            confirmButton = { TextButton({ setPhase(i) { it.copy(types = it.types.map { t -> if (t.name == tp.name) t.copy(hours = hours, category = cat) else t }) }; editType = null }) { Text("Save", style = SibType.bodyStrong, color = c.ink) } },
            dismissButton = { TextButton({ editType = null }) { Text("Cancel", style = SibType.body, color = c.inkSoft) } },
        )
    }

    pick?.let { (i, item, isSubject) ->
        AlertDialog(
            onDismissRequest = { pick = null }, containerColor = c.raised,
            title = { Text(item, style = SibType.title, color = c.ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    draft.phases.forEachIndexed { j, ph ->
                        if (j != i) SibButton("Move to " + ph.name, {
                            draft = draft.copy(phases = draft.phases.mapIndexed { k, p ->
                                when (k) {
                                    i -> if (isSubject) p.copy(subjects = p.subjects - item) else p.copy(practicals = p.practicals - item)
                                    j -> if (isSubject) p.copy(subjects = (p.subjects + item).distinct()) else p.copy(practicals = (p.practicals + item).distinct())
                                    else -> p
                                }
                            }); pick = null
                        }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
                    }
                    SibButton("Remove", { setPhase(i) { if (isSubject) it.copy(subjects = it.subjects - item) else it.copy(practicals = it.practicals - item) }; pick = null }, Modifier.fillMaxWidth(), ButtonKind.DANGER)
                }
            },
            confirmButton = { TextButton({ pick = null }) { Text("Cancel", style = SibType.body, color = c.inkSoft) } },
        )
    }
}

/** Answer a few questions and a course is made for any college or university. Everything can be edited afterwards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CourseWizard(st: AppState, ready: Ready, onBack: () -> Unit, onDone: () -> Unit) {
    val c = SibTheme.colors
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var field by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("Year") }
    var unitOther by rememberSaveable { mutableStateOf("") }
    var count by rememberSaveable { mutableIntStateOf(4) }
    var kinds by remember { mutableStateOf(setOf(Category.ITEM, Category.TERM, Category.PROF)) }
    var subjects by remember { mutableStateOf(mapOf<Int, String>()) }
    var examMonth by rememberSaveable { mutableIntStateOf(12) }
    var suppMonth by rememberSaveable { mutableIntStateOf(6) }
    val last = 5
    val unitName = if (unit == "Other") unitOther.trim().ifEmpty { "Phase" } else unit
    val made = Courses.fromAnswers(
        name.ifBlank { "My course" }, field.ifBlank { "Other" }, unitName, count,
        Courses.selectableKinds().filter { it in kinds }, (1..count).map { i -> subjects[i].orEmpty().split(",", "\\n").map { it.trim() }.filter { it.isNotEmpty() } }, examMonth, suppMonth,
    )
    Page("Make one for me", { if (step > 0) step-- else onBack() }) {
        Text("Question ${step + 1} of ${last + 1}", style = SibType.small, color = c.inkSoft)
        Spacer(Modifier.height(8.dp))
        when (step) {
            0 -> {
                Text("What is it for?", style = SibType.headline, color = c.ink)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Courses.FIELDS.forEach { f -> SelectChip(f, field == f, { field = f }) }
                }
                if (field.startsWith("Medicine")) {
                    Spacer(Modifier.height(12.dp))
                    SibButton("Start from the MBBS course", { scope.launch { useCourse(ready, st, Courses.mbbs.copy(name = "My MBBS")); onDone() } }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
                    Text("It is already filled in. You can edit it afterwards.", style = SibType.small, color = c.inkSoft)
                }
            }
            1 -> { Text("What is your college or university called?", style = SibType.headline, color = c.ink); Spacer(Modifier.height(12.dp)); Field("Name", name, { name = it.take(60) }, placeholder = "International University of Bangladesh") }
            2 -> {
                Text("How is your course divided?", style = SibType.headline, color = c.ink)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Year", "Semester", "Term", "Phase", "Prof", "Other").forEach { u -> SelectChip(u, unit == u, { unit = u }) }
                }
                if (unit == "Other") Field("What do you call it?", unitOther, { unitOther = it.take(20) })
                Spacer(Modifier.height(12.dp))
                Text("How many $unitName" + "s does it have?", style = SibType.bodyStrong, color = c.ink)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SibButton("-", { count = (count - 1).coerceAtLeast(1) }, kind = ButtonKind.QUIET, compact = true)
                    Text("$count", style = SibType.headline, color = c.ink)
                    SibButton("+", { count = (count + 1).coerceAtMost(10) }, kind = ButtonKind.QUIET, compact = true)
                }
            }
            3 -> {
                Text("Which kinds of exam do you have?", style = SibType.headline, color = c.ink)
                Text("Tick all that apply. You can change this for each phase later.", style = SibType.small, color = c.inkSoft)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Courses.selectableKinds().forEach { k -> SelectChip(k.label, k in kinds, { kinds = if (k in kinds) kinds - k else kinds + k }) }
                }
            }
            4 -> {
                Text("What are the subjects?", style = SibType.headline, color = c.ink)
                Text("Separate them with commas. You can leave a phase empty and fill it in later.", style = SibType.small, color = c.inkSoft)
                (1..count).forEach { i -> Spacer(Modifier.height(10.dp)); Field("$unitName $i", subjects[i].orEmpty(), { v -> subjects = subjects + (i to v.take(400)) }, placeholder = "Maths, Physics, English") }
            }
            else -> {
                Text("When are the exams?", style = SibType.headline, color = c.ink)
                MonthStepper("Main exam month", examMonth) { examMonth = it }
                MonthStepper("Retake or supplementary month", suppMonth) { suppMonth = it }
                Spacer(Modifier.height(16.dp))
                made?.let { m ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.violetWash).padding(18.dp)) {
                        Text(m.name, style = SibType.title, color = c.ink)
                        Text(m.field + ", " + m.phases.size + " phases, " + m.phases.sumOf { it.subjects.size } + " subjects", style = SibType.body, color = c.inkSoft)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) SibButton("Back", { step-- }, kind = ButtonKind.QUIET)
            if (step < last) SibButton("Next", { step++ }, enabled = when (step) { 0 -> field.isNotEmpty(); 1 -> name.isNotBlank(); 3 -> kinds.isNotEmpty(); else -> true })
            else SibButton("Create and use it", { made?.let { m -> scope.launch { useCourse(ready, st, m); onDone() } } }, enabled = made != null)
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** Paste the text a friend sent. The course is shown first, and nothing is used until you say so. */
@Composable
fun CourseImport(st: AppState, ready: Ready, onBack: () -> Unit, onDone: () -> Unit) {
    val c = SibTheme.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }
    val found = remember(text) { Courses.fromCode(text.substring(text.indexOf(Courses.CODE_PREFIX).coerceAtLeast(0))) }
    Page("Take a setup", onBack) {
        Explain("Paste the text your friend sent. It only holds subjects or areas, kinds of deadline and months, nothing personal. You will see it before you use it.")
        Field("The text", text, { text = it.take(Courses.MAX_CODE_CHARS); tried = true }, singleLine = false, minLines = 3, mono = true)
        SibButton("Paste from the clipboard", {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            text = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty().take(Courses.MAX_CODE_CHARS); tried = true
        }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
        Spacer(Modifier.height(12.dp))
        if (tried && text.isNotBlank() && found == null) Text("That does not look like a setup. Paste the whole text, starting with ${Courses.CODE_PREFIX}", style = SibType.body, color = c.vermilion)
        found?.let { f ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.violetWash).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(f.name, style = SibType.title, color = c.ink)
                Text(f.field, style = SibType.small, color = c.inkSoft)
                f.phases.forEach { p -> Text(p.name + ": " + p.subjects.joinToString(", ").ifEmpty { "no subjects" }, style = SibType.body, color = c.ink) }
            }
            Spacer(Modifier.height(12.dp))
            SibButton("Use this course", { scope.launch { useCourse(ready, st, f); onDone() } }, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** Ready made courses and professions. Each one is only a starting point: it is copied, so editing it never changes the original. */
@Composable
fun TemplateGallery(st: AppState, ready: Ready, group: String, onBack: () -> Unit, onDone: () -> Unit) {
    val c = SibTheme.colors
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf<CourseTemplate?>(null) }
    Page(group, onBack) {
        Explain(when (group) { CourseTemplates.WORK -> "Pick the work you do and the app is set up for it: your areas, your usual kinds of deadline and how many hours each takes. Something does not match? Edit it, save it, and share it with colleagues."; CourseTemplates.LIFE -> "Pick what fits your days and the app is set up for it. Something does not match? Edit it and make it yours."; else -> "Pick your course and the app is set up for it. Colleges differ, so if something does not match, edit it, then share your version with friends." })
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CourseTemplates.group(group).forEach { t -> NavRow(t.course.name, t.blurb, { open = t }, icon = when (group) { CourseTemplates.WORK -> SibIcon.GAUGE; CourseTemplates.LIFE -> SibIcon.HEART; else -> SibIcon.BOOK }) }
        }
        Spacer(Modifier.height(32.dp))
    }
    open?.let { t ->
        AlertDialog(
            onDismissRequest = { open = null }, containerColor = c.raised,
            title = { Text(t.course.name, style = SibType.title, color = c.ink) },
            text = {
                Column(Modifier.cuedVerticalScroll(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(t.blurb, style = SibType.body, color = c.inkSoft)
                    t.course.phases.forEach { p ->
                        val line = if (p.types.isNotEmpty()) p.types.joinToString(", ") { it.name } else p.subjects.joinToString(", ")
                        Text(p.name + (if (line.isNotEmpty()) ": $line" else ""), style = SibType.small, color = c.ink)
                    }
                }
            },
            confirmButton = { TextButton({ scope.launch { useCourse(ready, st, t.course); open = null; onDone() } }) { Text("Set up for this", style = SibType.bodyStrong, color = c.ink) } },
            dismissButton = { TextButton({ open = null }) { Text("Cancel", style = SibType.body, color = c.inkSoft) } },
        )
    }
}
