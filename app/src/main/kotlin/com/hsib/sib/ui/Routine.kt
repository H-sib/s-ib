package com.hsib.sib.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hsib.sib.Ready
import com.hsib.sib.core.medical.Courses
import com.hsib.sib.core.medical.RoutineLimits
import com.hsib.sib.core.medical.RoutineTimeline
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

private fun kindIcon(kind: String): SibIcon = when (kind) { "Tutorial" -> SibIcon.PEOPLE; "Ward" -> SibIcon.PULSE; else -> SibIcon.BOOK }

private fun shortDay(d: DayOfWeek): String = d.name.take(3).lowercase().replaceFirstChar { it.uppercase() }

/** One class as a card: a soft colour wash from the subject's colour, a small icon for the kind, what it is, where, and when. */
@Composable
fun ClassCard(s: RoutineSlot, fmt: Fmt, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = SibTheme.colors
    val tone = subjectColor(s.subject)
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(tone.copy(alpha = 0.28f), tone.copy(alpha = 0.08f))))
            .then(if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(tone.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) { SibIconView(kindIcon(s.kind), size = 22.dp, tint = c.ink, accent = tone) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(s.subject.ifBlank { "No subject" }, style = SibType.bodyStrong, color = c.ink)
            Text(s.label() + if (s.room.isNotBlank()) "  ·  " + s.room else "", style = SibType.small, color = c.inkSoft)
            Text(fmt.time(minutesToTime(s.startMin)) + " to " + fmt.time(minutesToTime(s.endMin)), style = SibType.small, color = c.inkSoft)
        }
    }
}

/** The weekly class routine: a day strip, the day as a timeline or the whole week as a map, and one tap to add a class. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassRoutineScreen(st: AppState, ready: Ready, onBack: () -> Unit) {
    val c = SibTheme.colors
    val fmt = rememberFmt()
    val scope = rememberCoroutineScope()
    val saved by ready.store.routine.collectAsState(initial = null as List<RoutineSlot>?)
    val all = saved.orEmpty()
    val days = ROUTINE_ORDER.filter { it.value in st.settings.routineDays }.ifEmpty { ROUTINE_ORDER.take(6) }
    var dayIndex by rememberSaveable { mutableStateOf(days.indexOf(st.today.dayOfWeek).coerceAtLeast(0)) }
    var weekMap by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RoutineSlot?>(null) }
    var copyTo by remember { mutableStateOf<DayOfWeek?>(null) }
    val day = days[dayIndex.coerceIn(0, days.lastIndex)]
    val ofDay = all.filter { it.day == day.value }.sortedBy { it.startMin }
    // The built in medical course is only a starting point for people who chose a setup; a plain app suggests no subjects.
    val subjects = remember(st.settings.course, st.settings.medicalMode) { if (st.settings.medicalMode) st.settings.course.phases.flatMap { it.subjects }.distinct() else emptyList() }
    // Ward or block classes belong to medical and dental courses only. They stay visible for anyone who already saved some.
    val wardOk = (st.settings.medicalMode && Courses.isMedical(st.settings.course)) || all.any { it.kind == "Ward" }
    val kinds = if (wardOk) listOf("Lecture", "Tutorial", "Ward") else listOf("Lecture", "Tutorial")

    fun change(f: (com.hsib.sib.data.AppSettings) -> com.hsib.sib.data.AppSettings) { scope.launch { ready.settings.update(f) } }
    // A first visit outside medicine starts with lectures and tutorials only.
    LaunchedEffect(saved != null, wardOk) {
        if (saved != null && !wardOk && saved.orEmpty().isEmpty() && st.settings.routineTutorials == 1 && st.settings.routineWards == 1) change { it.copy(routineTutorials = 0, routineWards = 0) }
    }
    fun newSlot(d: DayOfWeek, kind: String): RoutineSlot {
        val mine = all.filter { it.day == d.value }
        val start = (mine.maxOfOrNull { it.endMin } ?: 8 * 60).coerceAtMost(22 * 60)
        return RoutineSlot("", d.value, kind, if (kind == "Ward") "Morning" else "", "", "", start, (start + 60).coerceAtMost(1439))
    }

    Page("Class routine", onBack) {
        Gap(8.dp)
        if (!weekMap) {
            RoutineHero(day, ofDay, st, fmt)
            Gap(16.dp)
            DayStrip(days, day, all, st.today.dayOfWeek) { dayIndex = it }
            Gap(14.dp)
        }
        Choice(listOf(false to "Day", true to "Week"), weekMap, { weekMap = it })
        val hint = if (weekMap) "Tap a day to open it." else if (ofDay.isNotEmpty()) "Tap a class to change it." else null
        if (hint != null) Text(hint, style = SibType.small, color = c.inkSoft, modifier = Modifier.padding(top = 8.dp, start = 8.dp))
        Gap(14.dp)
        if (weekMap) {
            WeekMap(days, all, st.today.dayOfWeek, fmt) { i -> dayIndex = i; weekMap = false }
        } else if (ofDay.isEmpty()) {
            EmptyState(SibIcon.CALENDAR, "Nothing on " + day.name.lowercase().replaceFirstChar { it.uppercase() } + " yet.", button = "Add a lecture", onButton = { editing = newSlot(day, "Lecture") })
        } else {
            Timeline(ofDay, st, day, fmt) { editing = it }
        }
        Gap(18.dp)
        if (!weekMap) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (kind in kinds) {
                    val n = ofDay.count { it.kind == kind }
                    AddKindPill(kindIcon(kind), "Add " + (if (kind == "Ward") "ward" else kind.lowercase()), n < limitFor(kind)) { editing = newSlot(day, kind) }
                }
            }
            if (ofDay.isNotEmpty() && days.size > 1) {
                Gap(22.dp)
                Text("Copy " + day.name.lowercase().replaceFirstChar { it.uppercase() } + " to", style = SibType.bodyStrong, color = c.ink, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    days.filter { it != day }.forEach { d ->
                        SelectChip(shortDay(d), false, {
                            if (all.any { it.day == d.value }) copyTo = d else scope.launch { ready.store.replaceRoutineDay(d.value, ofDay) }
                        })
                    }
                }
                Text("Copying replaces what is already on that day.", style = SibType.small, color = c.inkSoft, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            }
        }

        Group(null) {
            FoldItem(SibIcon.CALENDAR, "Days", hint = "${days.size} days", info = "Choose which days your week covers. Turn Friday on, or any day off. Classes already saved on a day you turn off are kept.") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ROUTINE_ORDER.forEach { d ->
                        SelectChip(shortDay(d), d.value in st.settings.routineDays, {
                            change { cur -> val next = if (d.value in cur.routineDays) cur.routineDays - d.value else cur.routineDays + d.value; cur.copy(routineDays = next.ifEmpty { cur.routineDays }) }
                        })
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    copyTo?.let { target ->
        val n = all.count { it.day == target.value }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { copyTo = null }, containerColor = c.raised,
            title = { Text("Replace " + target.name.lowercase().replaceFirstChar { it.uppercase() } + "?", style = SibType.title, color = c.ink) },
            text = { Text((if (n == 1) "Its 1 class" else "Its $n classes") + " will be replaced by the classes of " + day.name.lowercase().replaceFirstChar { it.uppercase() } + ".", style = SibType.body, color = c.inkSoft) },
            confirmButton = { androidx.compose.material3.TextButton({ scope.launch { ready.store.replaceRoutineDay(target.value, ofDay) }; copyTo = null }) { Text("Replace", style = SibType.bodyStrong, color = c.vermilion) } },
            dismissButton = { androidx.compose.material3.TextButton({ copyTo = null }) { Text("Cancel", style = SibType.body, color = c.inkSoft) } },
        )
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
                    if (subjects.isNotEmpty()) {
                        Text("Subject", style = SibType.bodyStrong, color = c.ink)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            subjects.forEach { sub -> SelectChip(sub, subject == sub, { subject = sub }) }
                        }
                    }
                    Field(if (subjects.isNotEmpty()) "Or type a subject" else "Subject", subject, { subject = it.take(60) })
                    if (s.kind == "Lecture") Field("Classroom", room, { room = it.take(60) }, placeholder = "Room 2")
                    if (s.kind == "Ward") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Morning", "Evening").forEach { sh -> SelectChip(sh, shift == sh, { shift = sh }) } }
                    }
                    Text("Starts", style = SibType.bodyStrong, color = c.ink)
                    TimePick(start, fmt) { start = it }
                    Text("Ends", style = SibType.bodyStrong, color = c.ink)
                    TimePick(end, fmt) { end = it }
                    // Copy this class, or every class of its kind on this day, to the days you pick. Nothing is copied twice.
                    if (s.id.isNotEmpty() && days.size > 1) {
                        var copyDays by remember(s.id) { mutableStateOf(setOf<Int>()) }
                        var copyKind by remember(s.id) { mutableStateOf(false) }
                        val others = days.filter { it.value != s.day }
                        val kindWord = if (s.kind == "Ward") "ward" else s.kind.lowercase()
                        Text("Copy to other days", style = SibType.bodyStrong, color = c.ink)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectChip("All days", copyDays.size == others.size, { copyDays = if (copyDays.size == others.size) emptySet() else others.map { it.value }.toSet() })
                            others.forEach { d -> SelectChip(shortDay(d), d.value in copyDays, { copyDays = if (d.value in copyDays) copyDays - d.value else copyDays + d.value }) }
                        }
                        if (copyDays.isNotEmpty()) {
                            Choice(listOf(false to "This class", true to "All ${kindWord}s of this day"), copyKind, { copyKind = it })
                            SibButton("Copy to " + copyDays.size + (if (copyDays.size == 1) " day" else " days"), {
                                val a = start.hour * 60 + start.minute
                                val z = (end.hour * 60 + end.minute).let { if (it <= a) (a + 30).coerceAtMost(1439) else it }
                                val here = RoutineSlot(s.id, s.day, s.kind, shift, subject.trim(), room.trim(), a, z)
                                val source = if (copyKind) all.filter { it.day == s.day && it.kind == s.kind } else listOf(here)
                                scope.launch {
                                    for (dv in copyDays) for (x in source) {
                                        val twin = all.any { it.day == dv && it.kind == x.kind && it.startMin == x.startMin && it.endMin == x.endMin && it.subject == x.subject }
                                        if (!twin) ready.store.saveRoutineSlot(x.copy(id = UUID.randomUUID().toString(), day = dv))
                                    }
                                }
                                editing = null
                            }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
                        }
                    }
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

/** The day at the top: its name large on a soft glow, how many classes and between which hours, and what comes next today. */
@Composable
private fun RoutineHero(day: DayOfWeek, ofDay: List<RoutineSlot>, st: AppState, fmt: Fmt) {
    val c = SibTheme.colors
    val times = ofDay.map { it.startMin to it.endMin }
    val span = RoutineTimeline.span(times)
    val nowMin = st.now.atZone(st.zone).let { it.hour * 60 + it.minute }
    val next = if (day == st.today.dayOfWeek) RoutineTimeline.nextIndex(times, nowMin)?.let { ofDay[it] } else null
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).glowBackdrop(c.glowA, c.glowB, 0.35f * c.glowStrength).padding(horizontal = 22.dp, vertical = 22.dp)) {
        Text(day.name.lowercase().replaceFirstChar { it.uppercase() }, style = SibType.display, color = c.ink)
        Spacer(Modifier.height(4.dp))
        Text(
            if (span == null) "A free day" else (if (ofDay.size == 1) "1 class" else "${ofDay.size} classes") + ", " + fmt.time(minutesToTime(span.first)) + " to " + fmt.time(minutesToTime(span.second)),
            style = SibType.body, color = c.ink,
        )
        if (next != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(c.ink.copy(alpha = 0.10f)).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SibIconView(kindIcon(next.kind), size = 18.dp, tint = c.ink, accent = subjectColor(next.subject))
                Text("Next: " + next.subject.ifBlank { next.kind } + " at " + fmt.time(minutesToTime(next.startMin)), style = SibType.small, color = c.ink)
            }
        }
    }
}

/** Seven small tiles, one for each day. The chosen day glows, today has a ring, and dots show how busy a day is. */
@Composable
private fun DayStrip(days: List<DayOfWeek>, selected: DayOfWeek, all: List<RoutineSlot>, today: DayOfWeek, onPick: (Int) -> Unit) {
    val c = SibTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEachIndexed { i, d ->
            val n = all.count { it.day == d.value }
            val on = d == selected
            Column(
                Modifier.weight(1f).heightIn(min = 72.dp).clip(RoundedCornerShape(20.dp))
                    .then(if (on) Modifier.background(c.glow) else Modifier.background(c.lifted))
                    .then(if (d == today && !on) Modifier.border(1.5.dp, c.violet, RoundedCornerShape(20.dp)) else Modifier)
                    .bounceClick { onPick(i) }.padding(vertical = 10.dp, horizontal = 2.dp)
                    .semantics { contentDescription = d.name.lowercase().replaceFirstChar { it.uppercase() } + (if (n == 1) ", 1 class" else ", $n classes") + (if (on) ", chosen" else "") },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                FitText(shortDay(d), SibType.label, if (on) c.onGlow else c.ink)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(8.dp)) {
                    repeat(minOf(n, 3)) { Box(Modifier.size(6.dp).clip(CircleShape).background(if (on) c.onGlow else c.violet)) }
                }
            }
        }
    }
}

/** The day top to bottom: when each class starts, a coloured rail, the class, and a quiet label for the breaks between them. */
@Composable
private fun Timeline(slots: List<RoutineSlot>, st: AppState, day: DayOfWeek, fmt: Fmt, onTap: (RoutineSlot) -> Unit) {
    val c = SibTheme.colors
    val times = slots.map { it.startMin to it.endMin }
    val breaks = RoutineTimeline.breaks(times)
    val nowMin = st.now.atZone(st.zone).let { it.hour * 60 + it.minute }
    val next = if (day == st.today.dayOfWeek) RoutineTimeline.nextIndex(times, nowMin) else null
    Column {
        slots.forEachIndexed { i, s ->
            val tone = subjectColor(s.subject)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Column(Modifier.width(66.dp).padding(top = 14.dp)) {
                    Text(fmt.time(minutesToTime(s.startMin)), style = SibType.small, color = if (i == next) c.ink else c.inkSoft)
                    Text(fmt.time(minutesToTime(s.endMin)), style = SibType.small.copy(fontSize = SibType.small.fontSize * 0.9f), color = c.inkSoft.copy(alpha = 0.7f))
                }
                Column(Modifier.width(22.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(18.dp))
                    Box(Modifier.size(if (i == next) 14.dp else 10.dp).clip(CircleShape).background(tone))
                    if (i < slots.lastIndex) Box(Modifier.width(2.dp).weight(1f).background(tone.copy(alpha = 0.35f)))
                }
                Box(Modifier.weight(1f).padding(bottom = if (i < slots.lastIndex) 4.dp else 0.dp)) { ClassCard(s, fmt) { onTap(s) } }
            }
            val gap = breaks.getOrNull(i)?.let { RoutineTimeline.breakLabel(it) }
            if (gap != null) {
                Row(Modifier.fillMaxWidth().padding(start = 66.dp, top = 2.dp, bottom = 6.dp)) {
                    Spacer(Modifier.width(22.dp))
                    Text(gap, style = SibType.small, color = c.inkSoft, modifier = Modifier.padding(start = 10.dp).clip(RoundedCornerShape(50)).background(c.lifted).padding(horizontal = 12.dp, vertical = 4.dp))
                }
            }
        }
    }
}

/** The whole week at once: hour labels down the side, a column for each day, and a block for every class with a short name, placed by its time. Tap a day to open it. */
@Composable
private fun WeekMap(days: List<DayOfWeek>, all: List<RoutineSlot>, today: DayOfWeek, fmt: Fmt, onDay: (Int) -> Unit) {
    val c = SibTheme.colors
    val hours = RoutineTimeline.mapHours(all.filter { r -> days.any { it.value == r.day } }.map { it.startMin to it.endMin })
    val span = (hours.last - hours.first).toFloat()
    val mapHeight = 340.dp
    val headerHeight = 30.dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.width(40.dp)) {
            Spacer(Modifier.height(headerHeight))
            Box(Modifier.fillMaxWidth().height(mapHeight)) {
                for (hr in hours.first..hours.last step (if (span > 8f) 2 else 1)) {
                    Box(Modifier.offset(y = (mapHeight * ((hr - hours.first) / span) - 8.dp).coerceAtLeast(0.dp))) {
                        FitText(fmt.time(LocalTime.of(hr.coerceAtMost(23), 0)).replace(":00", ""), SibType.small, c.inkSoft)
                    }
                }
            }
        }
        days.forEachIndexed { i, d ->
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(headerHeight), contentAlignment = Alignment.Center) { FitText(shortDay(d), SibType.label, if (d == today) c.violet else c.inkSoft) }
                Box(
                    Modifier.fillMaxWidth().height(mapHeight).clip(RoundedCornerShape(16.dp)).background(c.lifted)
                        .drawBehind {
                            for (k in 1 until span.toInt()) drawLine(c.ink.copy(alpha = 0.07f), Offset(0f, size.height * k / span), Offset(size.width, size.height * k / span), 1.dp.toPx())
                        }
                        .bounceClick { onDay(i) }
                        .semantics { contentDescription = d.name.lowercase().replaceFirstChar { it.uppercase() } + ", open the day" },
                ) {
                    all.filter { it.day == d.value }.forEach { s ->
                        val top = ((s.startMin - hours.first * 60) / (span * 60f)).coerceIn(0f, 1f)
                        val bottom = ((s.endMin - hours.first * 60) / (span * 60f)).coerceIn(0f, 1f)
                        Box(
                            Modifier.offset(y = mapHeight * top).fillMaxWidth().height((mapHeight * (bottom - top)).coerceAtLeast(16.dp)).padding(horizontal = 3.dp, vertical = 1.dp)
                                .clip(RoundedCornerShape(8.dp)).background(subjectColor(s.subject).copy(alpha = 0.92f)),
                            contentAlignment = Alignment.TopCenter,
                        ) { FitText(RoutineTimeline.tag(s.subject, s.kind), SibType.label, Color.White, modifier = Modifier.padding(top = 3.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddKindPill(icon: SibIcon, text: String, enabled: Boolean, onClick: () -> Unit) {
    val c = SibTheme.colors
    Row(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).background(c.violet.copy(alpha = if (enabled) 0.14f else 0.05f))
            .then(if (enabled) Modifier.bounceClick(onClick = onClick) else Modifier).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SibIconView(icon, size = 20.dp, tint = if (enabled) c.ink else c.inkSoft)
        FitText(text, SibType.bodyStrong, if (enabled) c.ink else c.inkSoft)
    }
}

/** A small button for Today that opens the class routine. It shows only once a class is saved. */
@Composable
fun RoutineChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = SibTheme.colors
    Row(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).background(c.violet.copy(alpha = 0.12f)).bounceClick(onClick = onClick)
            .padding(horizontal = 16.dp).semantics { contentDescription = "Open your class routine" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SibIconView(SibIcon.BOOK, size = 20.dp)
        FitText("Classes", SibType.bodyStrong, c.ink)
    }
}

/** Today's classes on the Today screen, for anyone who has set a routine. */
@Composable
fun TodayClasses(slots: List<RoutineSlot>, today: java.time.LocalDate, fmt: Fmt) {
    val mine = slots.filter { it.day == today.dayOfWeek.value }.sortedBy { it.startMin }
    if (mine.isEmpty()) return
    SectionHeader("Today's classes", count = mine.size)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { mine.forEach { ClassCard(it, fmt) } }
}
