package com.hsib.sib.ui

import androidx.compose.foundation.background
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

/** The days of a medical week: Saturday to Thursday, Friday off. */
val ROUTINE_DAYS = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)

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
    var dayIndex by rememberSaveable { mutableStateOf(ROUTINE_DAYS.indexOf(st.today.dayOfWeek).coerceAtLeast(0)) }
    var editing by remember { mutableStateOf<RoutineSlot?>(null) }
    val day = ROUTINE_DAYS[dayIndex]
    val today = all.filter { it.day == day.value }.sortedBy { it.startMin }
    val subjects = remember { Medical.profs.flatMap { it.subjects }.distinct() }

    Page("Class routine", onBack) {
        Explain("Set up your week once, Saturday to Thursday. You choose how many lectures, tutorials and ward or block classes each day has, which subject each one is, and its time. The colours follow the subject.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ROUTINE_DAYS.forEachIndexed { i, d -> SelectChip(d.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, i == dayIndex, { dayIndex = i }) }
        }
        Spacer(Modifier.height(14.dp))
        Text("${today.count { it.kind == "Lecture" }} lectures  ·  ${today.count { it.kind == "Tutorial" }} tutorials  ·  ${today.count { it.kind == "Ward" }} ward or block", style = SibType.small, color = c.inkSoft)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            today.forEach { s -> ClassCard(s, fmt) { editing = s } }
            if (today.isEmpty()) Text("Nothing on this day yet.", style = SibType.body, color = c.inkSoft)
        }
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (kind in listOf("Lecture", "Tutorial", "Ward")) {
                val n = today.count { it.kind == kind }
                val max = limitFor(kind)
                val start = (today.maxOfOrNull { it.endMin } ?: 8 * 60).coerceAtMost(22 * 60)
                SibButton(
                    "Add " + (if (kind == "Ward") "ward or block" else kind.lowercase()) + " ($n of $max)",
                    { editing = RoutineSlot("", day.value, kind, if (kind == "Ward") "Morning" else "", "", "", start, (start + 60).coerceAtMost(1439)) },
                    kind = ButtonKind.QUIET, enabled = n < max,
                )
            }
        }
        if (today.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text("Copy this day to", style = SibType.bodyStrong, color = c.ink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ROUTINE_DAYS.filter { it != day }.forEach { d ->
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
                    if (s.kind == "Lecture") Field("Class or gallery", room, { room = it.take(60) }, placeholder = "Gallery 2")
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
