package com.hsib.sib.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hsib.sib.Ready
import com.hsib.sib.core.medical.Medical
import com.hsib.sib.core.medical.RoutineLimits
import com.hsib.sib.data.RoutineSlot
import com.hsib.sib.domain.AppState
import com.hsib.sib.domain.Fmt
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

/** The weekdays a routine can cover, starting from Saturday. Which of them are used is the student's choice; Saturday to Thursday by default. */
val ROUTINE_ORDER = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

private val SUBJECT_COLORS = listOf(
    Color(0xFF0E9F8E), Color(0xFF3B82F6), Color(0xFFE11D48), Color(0xFFD97706), Color(0xFF7C3AED), Color(0xFF16A34A), Color(0xFFDB2777), Color(0xFF0891B2),
)

/** The same subject always gets the same colour, so a timetable reads at a glance. */
fun subjectColor(subject: String): Color = SUBJECT_COLORS[(subject.trim().lowercase().hashCode() and 0x7fffffff) % SUBJECT_COLORS.size]

private fun minutesToTime(m: Int): LocalTime = LocalTime.ofSecondOfDay(m.coerceIn(0, 1439) * 60L)

private fun RoutineSlot.label(): String = when (kind) { "Ward" -> if (shift.isNotBlank()) "Ward or block, $shift" else "Ward or block"; else -> kind }

private fun limitFor(kind: String): Int = when (kind) { "Lecture" -> RoutineLimits.LECTURES; "Tutorial" -> RoutineLimits.TUTORIALS; else -> RoutineLimits.WARDS }

/** One class as a coloured card: a bar in the subject's colour, what it is, where, and when. */
@Composable
fun ClassCard(s: RoutineSlot, fmt: Fmt, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = SibTheme.colors
    val tone = subjectColor(s.subject)
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(20.dp)).background(tone.copy(alpha = 0.14f))
            .then(if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier),
    ) {
        Box(Modifier.width(8.dp).fillMaxHeight().background(tone))
        Column(Modifier.padding(14.dp)) {
            Text(s.subject.ifBlank { "No subject" } + "  ·  " + s.label(), style = SibType.bodyStrong, color = c.ink)
            Text(fmt.time(minutesToTime(s.startMin)) + " to " + fmt.time(minutesToTime(s.endMin)) + if (s.room.isNotBlank()) "  ·  " + s.room else "", style = SibType.small, color = c.inkSoft)
        }
    }
}

/** The weekly class routine. The student sets how many lectures, tutorials and ward classes each day has, and the time of each. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassRoutineScreen(st: AppState, ready: Ready, onBack: () -> Unit) {
    val c = SibTheme.colors
    val fmt = rememberFmt()
    val scope = rememberCoroutineScope()
    val all by ready.store.routine.collectAsState(initial = emptyList())
    val days = ROUTINE_ORDER.filter { it.value in st.settings.routineDays }.ifEmpty { ROUTINE_ORDER.take(6) }
    var dayIndex by rememberSaveable { mutableStateOf(days.indexOf(st.today.dayOfWeek).coerceAtLeast(0)) }
    var table by rememberSaveable { mutableStateOf(true) }
    var editing by remember { mutableStateOf<RoutineSlot?>(null) }
    val day = days[dayIndex.coerceIn(0, days.lastIndex)]
    val today = all.filter { it.day == day.value }.sortedBy { it.startMin }
    val subjects = remember { Medical.profs.flatMap { it.subjects }.distinct() }

    fun change(f: (com.hsib.sib.data.AppSettings) -> com.hsib.sib.data.AppSettings) { scope.launch { ready.settings.update(f) } }
    fun newSlot(d: DayOfWeek, kind: String): RoutineSlot {
        val mine = all.filter { it.day == d.value }
        val start = (mine.maxOfOrNull { it.endMin } ?: 8 * 60).coerceAtMost(22 * 60)
        return RoutineSlot("", d.value, kind, if (kind == "Ward") "Morning" else "", "", "", start, (start + 60).coerceAtMost(1439))
    }

    Page("Class routine", onBack) {
        Explain("Your week as a table. Choose which days it covers and how many lectures, tutorials and ward or block classes a day has. Tap a cell to set the subject, the classroom and the time. The colours follow the subject.")
        InfoTitle("Days and numbers", "Each phase has its own timetable, so you decide the numbers. Lectures can be 0 to 8 a day, tutorials 0 to 2, and ward or block classes 0 to 2 (morning or evening). Turn Friday on or any day off.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ROUTINE_ORDER.forEach { d ->
                SelectChip(d.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, d.value in st.settings.routineDays, {
                    change { cur -> val next = if (d.value in cur.routineDays) cur.routineDays - d.value else cur.routineDays + d.value; cur.copy(routineDays = next.ifEmpty { cur.routineDays }) }
                })
            }
        }
        Spacer(Modifier.height(10.dp))
        CountStepper("Lectures a day", st.settings.routineLectures, 0, RoutineLimits.LECTURES) { v -> change { it.copy(routineLectures = v) } }
        CountStepper("Tutorials a day", st.settings.routineTutorials, 0, RoutineLimits.TUTORIALS) { v -> change { it.copy(routineTutorials = v) } }
        CountStepper("Ward or block classes a day", st.settings.routineWards, 0, RoutineLimits.WARDS) { v -> change { it.copy(routineWards = v) } }
        Spacer(Modifier.height(10.dp))
        Choice(listOf(true to "Week table", false to "One day"), table, { table = it })
        Spacer(Modifier.height(14.dp))
        if (table) {
            RoutineTable(days, all, st.settings.routineLectures, st.settings.routineTutorials, st.settings.routineWards, fmt) { editing = it }
            Text("Tap a cell. L is a lecture, T a tutorial and W a ward or block class.", style = SibType.small, color = c.inkSoft)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                days.forEachIndexed { i, d -> SelectChip(d.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, d == day, { dayIndex = i }) }
            }
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                today.forEach { s -> ClassCard(s, fmt) { editing = s } }
                if (today.isEmpty()) Text("Nothing on this day yet.", style = SibType.body, color = c.inkSoft)
            }
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (kind in listOf("Lecture", "Tutorial", "Ward")) {
                    val n = today.count { it.kind == kind }
                    val max = limitFor(kind)
                    SibButton("Add " + (if (kind == "Ward") "ward or block" else kind.lowercase()) + " ($n of $max)", { editing = newSlot(day, kind) }, kind = ButtonKind.QUIET, enabled = n < max)
                }
            }
        }
        if (today.isNotEmpty() && !table) {
            Spacer(Modifier.height(18.dp))
            Text("Copy this day to", style = SibType.bodyStrong, color = c.ink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                days.filter { it != day }.forEach { d ->
                    SelectChip(d.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, false, { scope.launch { ready.store.replaceRoutineDay(d.value, today) } })
                }
            }
            Text("This replaces what is on that day.", style = SibType.small, color = c.inkSoft)
        }
        Spacer(Modifier.height(32.dp))
    }

    editing?.let { s ->
        var subject by remember(s.id, s.kind) { mutableStateOf(s.subject) }
        var room by remember(s.id, s.kind) { mutableStateOf(s.room) }
        var shift by remember(s.id, s.kind) { mutableStateOf(s.shift) }
        var start by remember(s.id, s.kind) { mutableStateOf(minutesToTime(s.startMin)) }
        var end by remember(s.id, s.kind) { mutableStateOf(minutesToTime(s.endMin)) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = c.raised,
            title = { Text((if (s.id.isEmpty()) "New " else "") + s.label().lowercase(), style = SibType.title, color = c.ink) },
            text = {
                Column(Modifier.cuedVerticalScroll(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Subject", style = SibType.bodyStrong, color = c.ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        subjects.forEach { sub -> SelectChip(sub, subject == sub, { subject = sub }) }
                    }
                    Field("Or type a subject", subject, { subject = it.take(60) })
                    if (s.kind == "Lecture") Field("Classroom", room, { room = it.take(60) }, placeholder = "Room 2")
                    if (s.kind == "Ward") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Morning", "Evening").forEach { sh -> SelectChip(sh, shift == sh, { shift = sh }) } }
                    }
                    Text("Starts", style = SibType.bodyStrong, color = c.ink)
                    TimePick(start, fmt) { start = it }
                    Text("Ends", style = SibType.bodyStrong, color = c.ink)
                    TimePick(end, fmt) { end = it }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton({
                    val id = s.id.ifEmpty { UUID.randomUUID().toString() }
                    val a = start.hour * 60 + start.minute
                    val b = (end.hour * 60 + end.minute).let { if (it <= a) (a + 30).coerceAtMost(1439) else it }
                    scope.launch { ready.store.saveRoutineSlot(RoutineSlot(id, s.day, s.kind, shift, subject.trim(), room.trim(), a, b)) }
                    editing = null
                }) { Text("Save", style = SibType.bodyStrong, color = c.ink) }
            },
            dismissButton = {
                Row {
                    if (s.id.isNotEmpty()) androidx.compose.material3.TextButton({ scope.launch { ready.store.removeRoutineSlot(s.id) }; editing = null }) { Text("Delete", style = SibType.body, color = c.vermilion) }
                    androidx.compose.material3.TextButton({ editing = null }) { Text("Cancel", style = SibType.body, color = c.inkSoft) }
                }
            },
        )
    }
}

/** Today's classes on the Today screen, only for medical students who have set a routine. */
@Composable
fun TodayClasses(slots: List<RoutineSlot>, today: java.time.LocalDate, fmt: Fmt) {
    val c = SibTheme.colors
    val mine = slots.filter { it.day == today.dayOfWeek.value }.sortedBy { it.startMin }
    if (mine.isEmpty()) return
    SectionHeader("Today's classes", count = mine.size)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { mine.forEach { ClassCard(it, fmt) } }
}

@Composable
private fun CountStepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val c = SibTheme.colors
    val stacked = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.15f
    val controls: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SibButton("-", { onChange((value - 1).coerceAtLeast(min)) }, kind = ButtonKind.QUIET, compact = true, enabled = value > min)
            Text("$value", style = SibType.title, color = c.ink, modifier = Modifier.width(28.dp))
            SibButton("+", { onChange((value + 1).coerceAtMost(max)) }, kind = ButtonKind.QUIET, compact = true, enabled = value < max)
        }
    }
    if (stacked) Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(label, style = SibType.body, color = c.ink); controls() }
    else Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, style = SibType.body, color = c.ink, modifier = Modifier.weight(1f)); controls() }
}

/** The week as a table: a row for each day, a column for each lecture, tutorial and ward or block class. */
@Composable
private fun RoutineTable(days: List<DayOfWeek>, all: List<RoutineSlot>, lectures: Int, tutorials: Int, wards: Int, fmt: Fmt, onCell: (RoutineSlot) -> Unit) {
    val c = SibTheme.colors
    fun of(d: DayOfWeek, kind: String) = all.filter { it.day == d.value && it.kind == kind }.sortedBy { it.startMin }
    // A column is shown for every class the student set, and for every class already saved even if the number was lowered later.
    val nL = maxOf(lectures, days.maxOf { of(it, "Lecture").size })
    val nT = maxOf(tutorials, days.maxOf { of(it, "Tutorial").size })
    val nW = maxOf(wards, days.maxOf { of(it, "Ward").size })
    val columns = List(nL) { "Lecture" to it } + List(nW) { "Ward" to it } + List(nT) { "Tutorial" to it }
    val cellW = 112.dp
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(Modifier.width(48.dp))
                columns.forEach { (kind, i) ->
                    val tag = when (kind) { "Lecture" -> "L"; "Ward" -> "W"; else -> "T" } + (i + 1)
                    Text(tag, style = SibType.label, color = c.violet, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.width(cellW).clip(RoundedCornerShape(12.dp)).background(c.violet.copy(alpha = 0.12f)).padding(vertical = 8.dp))
                }
            }
            days.forEach { d ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(d.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, style = SibType.label, color = if (d == DayOfWeek.FRIDAY) c.vermilion else c.violet,
                        modifier = Modifier.width(48.dp).padding(top = 14.dp))
                    columns.forEach { (kind, i) ->
                        val slot = of(d, kind).getOrNull(i)
                        val tone = slot?.let { subjectColor(it.subject) }
                        Column(
                            Modifier.width(cellW).heightIn(min = 72.dp).clip(RoundedCornerShape(16.dp))
                                .background(tone?.copy(alpha = 0.20f) ?: c.lifted)
                                .bounceClick {
                                    onCell(slot ?: RoutineSlot("", d.value, kind, if (kind == "Ward") "Morning" else "", "", "",
                                        (of(d, kind).lastOrNull()?.endMin ?: all.filter { it.day == d.value }.maxOfOrNull { it.endMin } ?: 8 * 60).coerceAtMost(22 * 60),
                                        ((of(d, kind).lastOrNull()?.endMin ?: all.filter { it.day == d.value }.maxOfOrNull { it.endMin } ?: 8 * 60) + 60).coerceAtMost(1439)))
                                }.padding(10.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (slot == null) {
                                Text("+", style = SibType.title, color = c.inkSoft)
                            } else {
                                Text(slot.subject.ifBlank { "No subject" }, style = SibType.bodyStrong.copy(fontSize = 13.sp), color = c.ink)
                                if (slot.room.isNotBlank()) Text(slot.room, style = SibType.small.copy(fontSize = 12.sp), color = c.inkSoft)
                                Text(fmt.time(minutesToTime(slot.startMin)), style = SibType.small.copy(fontSize = 12.sp), color = c.inkSoft)
                            }
                        }
                    }
                }
            }
        }
    }
}
