package com.hsib.sib.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hsib.sib.Ready
import com.hsib.sib.core.model.Category
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Kind
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.model.Recurrence
import com.hsib.sib.core.model.Repeat
import com.hsib.sib.core.model.Space
import com.hsib.sib.core.plan.Defaults
import com.hsib.sib.domain.AppState
import com.hsib.sib.domain.Fmt
import com.hsib.sib.domain.ObligationView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID

// ---- templates -------------------------------------------------------------------------------

/** What an occasion starts from. Picking one fills in sensible repeats, lead times and a few ideas. */
class CloseTemplate(
    val key: String,
    val label: String,
    val blurb: String,
    val category: Category,
    val repeat: Repeat,
    val lead: List<Int>,
    val countYears: Boolean,
    val timed: Boolean,
    val dateLabel: String,
    val ideas: List<String>,
)

object CloseTemplates {
    val all = listOf(
        CloseTemplate(
            "anniversary", "Anniversary", "Once a year, with time to plan", Category.ANNIVERSARY, Repeat.YEARLY, listOf(30, 7, 1), true, false, "The day it began",
            listOf("Go back to where it started", "Write a short letter by hand", "Book the place a few weeks ahead", "Make something together instead of buying something"),
        ),
        CloseTemplate(
            "birthday", "Birthday", "Once a year", Category.BIRTHDAY, Repeat.YEARLY, listOf(21, 7, 1), false, false, "Their birthday",
            listOf("Ask a friend what they have been wanting", "Plan the day around their favourite thing", "Gather a few people for a short surprise", "Write down three things you value about them"),
        ),
        CloseTemplate(
            "monthly", "Monthly anniversary", "The same day every month", Category.ANNIVERSARY, Repeat.MONTHLY, listOf(1), true, false, "The day it began",
            listOf("Keep it small and consistent", "Take one photo on the same spot each month"),
        ),
        CloseTemplate(
            "date", "Date night", "A plan with a time", Category.DATE_NIGHT, Repeat.NONE, listOf(2), false, true, "The day",
            listOf("Pick a place neither of you has been", "Book ahead, then keep the phones away", "Choose something to do, not only somewhere to eat"),
        ),
        CloseTemplate(
            "checkin", "Check in", "A regular moment for each other", Category.CHECK_IN, Repeat.WEEKLY, emptyList(), false, true, "The first one",
            listOf("Ask one question you have not asked before", "Say one thing that went well this week", "Plan the next small thing together"),
        ),
        CloseTemplate(
            "gift", "Gift", "Something to find before the day", Category.GIFT, Repeat.NONE, listOf(14, 3), false, false, "Give it on",
            listOf("Note what they mentioned this month", "Set a budget first", "Order a week early", "Add a handwritten line"),
        ),
        CloseTemplate(
            "trip", "Trip", "Time away together", Category.TRIP, Repeat.NONE, listOf(30, 7, 1), false, false, "Leaving on",
            listOf("Agree the budget and the dates first", "List one thing each person wants to do", "Leave one day empty on purpose"),
        ),
        CloseTemplate(
            "custom", "Something else", "Any date that matters", Category.SPECIAL, Repeat.NONE, listOf(7, 1), false, false, "The day",
            listOf("Decide what would make it feel marked", "Tell one person who can help"),
        ),
    )

    fun byKey(key: String?): CloseTemplate = all.firstOrNull { it.key == key } ?: all.last()

    fun of(o: Obligation): CloseTemplate =
        all.firstOrNull { it.category == o.category && it.repeat == o.repeat } ?: all.firstOrNull { it.category == o.category } ?: all.last()
}

private val RELATIONS = listOf("Partner", "Girlfriend", "Boyfriend", "Wife", "Husband", "Someone special")
private val LEAD_CHOICES = listOf(60, 30, 21, 14, 7, 3, 2, 1)

/** "4 years together", "Turns 28": derived from the original date, never stored. Null when there is nothing to count. */
fun milestone(o: Obligation, day: LocalDate): String? {
    val since = o.since ?: return null
    if (!day.isAfter(since)) return null
    return when (o.repeat) {
        Repeat.YEARLY -> {
            val years = day.year - since.year
            if (years <= 0) null else if (o.category == Category.BIRTHDAY) "Turns $years" else "$years ${if (years == 1) "year" else "years"} together"
        }
        Repeat.MONTHLY -> {
            val months = (day.year - since.year) * 12 + (day.monthValue - since.monthValue)
            if (months <= 0) null else "$months ${if (months == 1) "month" else "months"} together"
        }
        else -> null
    }
}

/** An occasion with the calendar day it falls on in the phone's zone. */
private class Occasion(val v: ObligationView, val day: LocalDate) {
    val o: Obligation get() = v.o
    fun daysAway(today: LocalDate): Long = ChronoUnit.DAYS.between(today, day)
}

private fun occasions(st: AppState): List<Occasion> =
    st.close.mapNotNull { v -> v.dueInstant?.let { Occasion(v, it.atZone(st.zone).toLocalDate()) } }.sortedBy { it.day }

// ---- small pieces ----------------------------------------------------------------------------

/** Two rings that overlap: the mark of the Close ones space. */
@Composable
fun CloseGlyph(modifier: Modifier = Modifier, size: Dp = 22.dp, color: Color = SibTheme.colors.vermilion) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val sw = w * 0.09f
        val r = w * 0.25f
        val dx = w * 0.19f
        drawCircle(color, r, Offset(w / 2 - dx, this.size.height / 2), style = Stroke(sw))
        drawCircle(color, r, Offset(w / 2 + dx, this.size.height / 2), style = Stroke(sw))
    }
}

/** Twelve months, a dot for each date that matters. Habits such as weekly check ins are left out so it stays readable. */
@Composable
private fun YearRibbon(items: List<Occasion>, today: LocalDate, fmt: Fmt) {
    val c = SibTheme.colors
    val first = YearMonth.from(today)
    val months = (0 until 12).map { first.plusMonths(it.toLong()) }
    val limit = today.plusMonths(12)
    val counts = months.associateWith { 0 }.toMutableMap()
    for (occ in items) {
        val dates = when (occ.o.repeat) {
            Repeat.NONE -> listOf(occ.day)
            Repeat.YEARLY, Repeat.MONTHLY -> {
                val anchor = occ.o.repeatAnchor ?: occ.day
                generateSequence(occ.day) { d -> Recurrence.nextOnOrAfter(anchor, occ.o.repeat, d.plusDays(1)) }.takeWhile { d -> d.isBefore(limit) }.take(13).toList()
            }
            Repeat.WEEKLY, Repeat.FORTNIGHTLY -> emptyList()
        }
        for (d in dates) YearMonth.from(d).let { m -> if (m in counts) counts[m] = counts.getValue(m) + 1 }
    }
    val summary = months.filter { counts.getValue(it) > 0 }.joinToString(", ") { fmt.monthYear(it.atDay(1)) + " " + counts.getValue(it) }
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = "The year ahead. " + summary.ifEmpty { "No dates yet." } },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        months.forEachIndexed { i, m ->
            val n = counts.getValue(m)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.height(44.dp), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom), horizontalAlignment = Alignment.CenterHorizontally) {
                    repeat(minOf(n, 3)) { Box(Modifier.size(9.dp).clip(CircleShape).background(c.vermilion)) }
                    if (n > 3) Text("+${n - 3}", style = SibType.label.copy(fontSize = 10.sp), color = c.inkSoft)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.width(14.dp).height(if (i == 0) 2.dp else 1.dp).background(if (i == 0) c.vermilion else c.rule))
                Spacer(Modifier.height(3.dp))
                Text(fmt.monthInitial(m.atDay(1)), style = SibType.label.copy(fontSize = 11.sp), color = if (i == 0) c.vermilion else c.inkSoft)
            }
        }
    }
}

@Composable
private fun OccasionRow(occ: Occasion, fmt: Fmt, today: LocalDate, onClick: () -> Unit) {
    val c = SibTheme.colors
    val o = occ.o
    val away = occ.daysAway(today)
    val whenText = fmt.daysFrom(today, occ.day)
    val sub = listOfNotNull(o.person.takeIf { p -> p.isNotBlank() }, milestone(o, occ.day), if (o.repeat != Repeat.NONE) o.repeat.label else null).joinToString("  ·  ")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised).bounceClick(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${o.title}. ${fmt.dayLong(occ.day)}. $whenText. $sub" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(occ.day.dayOfMonth.toString(), style = SibType.number, color = if (away <= 7) c.vermilion else c.ink)
            Text(fmt.weekday(occ.day), style = SibType.label.copy(fontSize = 11.sp), color = c.inkSoft)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(o.title, style = SibType.title, color = c.ink)
            if (sub.isNotEmpty()) Text(sub, style = SibType.small, color = c.inkSoft)
        }
        Spacer(Modifier.width(8.dp))
        Text(whenText, style = SibType.label, color = if (away <= 7) c.vermilion else c.inkSoft)
    }
}

/**
 * The way into the space, shown on Today. It carries a count and nothing else, so a glance over the shoulder
 * never reveals what the dates are.
 */
@Composable
fun CloseOnesEntry(st: AppState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val soon = st.close.count { v -> v.dueInstant?.let { ChronoUnit.DAYS.between(st.today, it.atZone(st.zone).toLocalDate()) in 0..30 } == true }
    SibTheme(LocalThemeMode.current, Space.CLOSE) {
        val c = SibTheme.colors
        val sub = when {
            st.close.isEmpty() -> "Anniversaries and birthdays"
            soon == 0 -> "Nothing soon"
            soon == 1 -> "1 coming up"
            else -> "$soon coming up"
        }
        Row(
            modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.action).bounceClick(onClick = onClick).padding(horizontal = 20.dp, vertical = 18.dp)
                .semantics(mergeDescendants = true) { contentDescription = "Close ones. $sub" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(c.onAction.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                CloseGlyph(size = 26.dp, color = c.onAction)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Close ones", style = SibType.title, color = c.onAction)
                Text(sub, style = SibType.small, color = c.onAction)
            }
            Canvas(Modifier.size(10.dp, 14.dp)) {
                val p = Path().apply { moveTo(1f, 1f); lineTo(size.width - 1f, size.height / 2); lineTo(1f, size.height - 1f) }
                drawPath(p, c.onAction, style = Stroke(2.8f, cap = StrokeCap.Round))
            }
        }
    }
}

// ---- home ------------------------------------------------------------------------------------

@Composable
fun CloseHomeScreen(st: AppState, onBack: () -> Unit, onOpen: (String) -> Unit, onAdd: (String?) -> Unit) {
    val c = SibTheme.colors
    val fmt = rememberFmt()
    val all = occasions(st)
    val upcoming = all.filter { !it.day.isBefore(st.today) }
    val passed = all.filter { it.day.isBefore(st.today) }
    val hero = upcoming.firstOrNull()
    val soon = upcoming.count { it.daysAway(st.today) <= 30 }

    Page("Close ones", onBack, action = { PlusButton("Add a date") { onAdd(null) } }) {
        Text(
            when {
                all.isEmpty() -> "Dates that matter"
                soon == 0 -> "Nothing soon"
                soon == 1 -> "1 in the next 30 days"
                else -> "$soon in the next 30 days"
            },
            style = SibType.body, color = c.inkSoft,
        )

        if (hero != null) {
            Spacer(Modifier.height(16.dp))
            val o = hero.o
            val away = hero.daysAway(st.today)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.raised).clickable(role = Role.Button) { onOpen(o.id) }.padding(18.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "Next: ${o.title}, ${fmt.daysFrom(st.today, hero.day)}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroRing(away, null, size = 150.dp, brushOverride = Brush.sweepGradient(listOf(SibTheme.colors.glowA, SibTheme.colors.glowB, SibTheme.colors.glowA)))
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text("Next", style = SibType.bodyStrong, color = c.vermilion)
                    Text(o.title, style = SibType.headline, color = c.ink)
                    o.person.takeIf { it.isNotBlank() }?.let { Text(it, style = SibType.body, color = c.inkSoft) }
                    Text(fmt.dayLong(hero.day), style = SibType.small, color = c.inkSoft)
                    milestone(o, hero.day)?.let { Spacer(Modifier.height(6.dp)); Chip(it, Tone.ALERT) }
                }
            }
        }

        if (upcoming.isNotEmpty()) {
            SectionHeader("The year ahead")
            YearRibbon(all, st.today, fmt)
        }

        var lastMonth: YearMonth? = null
        upcoming.forEach { occ ->
            val m = YearMonth.from(occ.day)
            if (m != lastMonth) { SectionHeader(fmt.monthYear(occ.day)); lastMonth = m } else Spacer(Modifier.height(12.dp))
            OccasionRow(occ, fmt, st.today) { onOpen(occ.o.id) }
        }

        if (passed.isNotEmpty()) {
            SectionHeader("Passed", count = passed.size)
            passed.forEachIndexed { i, occ ->
                if (i > 0) Hairline()
                OccasionRow(occ, fmt, st.today) { onOpen(occ.o.id) }
            }
        }

        if (all.isEmpty()) {
            Spacer(Modifier.height(28.dp))
            CloseGlyph(size = 48.dp)
            Spacer(Modifier.height(12.dp))
            Text("Add a date you never want to miss.", style = SibType.headline, color = c.ink)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SibButton("Anniversary", { onAdd("anniversary") }, Modifier.weight(1f))
                SibButton("Birthday", { onAdd("birthday") }, Modifier.weight(1f), ButtonKind.QUIET)
            }
        }

        Spacer(Modifier.height(20.dp))
        More("Reminders here never show a title or a name on the lock screen. This space is left out of the widget, the summary and sharing, so a surprise stays a surprise.", label = "How is this kept private?")
        Spacer(Modifier.height(40.dp))
    }
}

// ---- detail ----------------------------------------------------------------------------------

private enum class CloseTab { IDEAS, NOTES, REMINDERS }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloseDetailScreen(id: String, st: AppState, ready: Ready, onBack: () -> Unit, onEdit: (String) -> Unit, onDeleted: () -> Unit) {
    val c = SibTheme.colors
    val fmt = rememberFmt()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val v = st.items.firstOrNull { it.o.id == id && it.o.space == Space.CLOSE }
    if (v == null) {
        Page("Close ones", onBack) { Text("This date is no longer on your list.", style = SibType.body, color = c.inkSoft) }
        return
    }
    val o = v.o
    val template = CloseTemplates.of(o)
    val day = v.dueInstant?.atZone(st.zone)?.toLocalDate() ?: st.today
    val away = ChronoUnit.DAYS.between(st.today, day)
    var notes by remember(id, o.notes) { mutableStateOf(o.notes) }
    var confirmDelete by remember { mutableStateOf(false) }
    var doneMessage by remember { mutableStateOf<String?>(null) }
    var tab by rememberSaveable(id) { mutableStateOf(CloseTab.IDEAS) }

    LaunchedEffect(doneMessage) { if (doneMessage != null) { delay(5000); doneMessage = null } }

    Page(o.category.label, onBack, action = {
        Text(
            "Done", style = SibType.bodyStrong, color = c.paper,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(c.verdigris)
                .bounceClick {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val repeating = o.repeat != Repeat.NONE
                    scope.launch {
                        ready.store.complete(id, null, null)
                        if (repeating) doneMessage = "Done. The next one is already waiting." else onBack()
                    }
                }.padding(horizontal = 22.dp, vertical = 11.dp),
        )
    }) {
        doneMessage?.let { Chip(it, Tone.GOOD); Spacer(Modifier.height(8.dp)) }
        Text(o.title, style = SibType.display, color = c.ink)
        val who = listOfNotNull(o.person.takeIf { it.isNotBlank() }, o.relation.takeIf { it.isNotBlank() }).joinToString("  ·  ")
        if (who.isNotEmpty()) Text(who, style = SibType.body, color = c.inkSoft)

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.raised).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            HeroRing(away, null, size = 110.dp, brushOverride = Brush.sweepGradient(listOf(SibTheme.colors.glowA, SibTheme.colors.glowB, SibTheme.colors.glowA)))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(fmt.dayLong(day), style = SibType.title, color = c.ink)
                o.due?.takeIf { it.precision == Precision.DATE_TIME }?.let { Text("At " + fmt.time(it.local.toLocalTime()), style = SibType.body, color = c.inkSoft) }
                Text(fmt.daysFrom(st.today, day), style = SibType.body, color = c.inkSoft)
                if (o.repeat != Repeat.NONE) Text(o.repeat.label, style = SibType.small, color = c.inkSoft)
                milestone(o, day)?.let { Spacer(Modifier.height(6.dp)); Chip(it, Tone.ALERT) }
            }
        }

        Gap(24.dp)
        Choice(listOf(CloseTab.IDEAS to "Ideas", CloseTab.NOTES to "Notes", CloseTab.REMINDERS to "Reminders"), tab, { tab = it })
        Gap(16.dp)
        if (tab == CloseTab.REMINDERS) {
        val morning = fmt.time(st.settings.morning)
        val leads = o.leadDays.filter { it > 0 }.distinct().sortedDescending()
        leads.forEach { d -> Text("$d ${if (d == 1) "day" else "days"} before, at $morning", style = SibType.body, color = c.ink, modifier = Modifier.padding(vertical = 2.dp)) }
        Text(
            if (o.due?.precision == Precision.DATE_TIME) "On the day, in the morning and shortly before" else "On the day, at $morning",
            style = SibType.body, color = c.ink, modifier = Modifier.padding(vertical = 2.dp),
        )

        }
        if (tab == CloseTab.IDEAS) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            template.ideas.forEach { idea ->
                SelectChip(idea, notes.contains(idea), {
                    if (!notes.contains(idea)) {
                        notes = (if (notes.isBlank()) "" else notes.trimEnd() + "\n") + idea
                        scope.launch { ready.store.save(o.copy(notes = notes), "USER", "notes changed") }
                    }
                })
            }
        }

        }
        if (tab == CloseTab.NOTES) {
        Field("Notes", notes, { notes = it }, singleLine = false, minLines = 3, keyboard = KeyboardOptions(imeAction = ImeAction.Default))
        if (notes != o.notes) {
            Spacer(Modifier.height(8.dp))
            SibButton("Save notes", { scope.launch { ready.store.save(o.copy(notes = notes.trim()), "USER", "notes changed") } }, kind = ButtonKind.QUIET)
        }
        }

        Spacer(Modifier.height(24.dp))
        SibButton("Edit", { onEdit(id) }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
        Spacer(Modifier.height(8.dp))
        SibButton("Delete this date", { confirmDelete = true }, Modifier.fillMaxWidth(), ButtonKind.DANGER)
        Spacer(Modifier.height(40.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.raised,
            title = { Text("Delete this date?", style = SibType.title, color = c.ink) },
            text = { Text("It, its notes and its reminders are removed from this phone. Backups you already made keep their own copy.", style = SibType.body, color = c.inkSoft) },
            confirmButton = { TextButton({ confirmDelete = false; scope.launch { ready.store.delete(id); onDeleted() } }) { Text("Delete", style = SibType.bodyStrong, color = c.vermilion) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Keep it", style = SibType.body, color = c.ink) } },
        )
    }
}

// ---- editor ----------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloseEditorScreen(existingId: String?, templateKey: String?, st: AppState, ready: Ready, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val c = SibTheme.colors
    val fmt = rememberFmt()
    val scope = rememberCoroutineScope()
    val existing = existingId?.let { id -> st.items.firstOrNull { it.o.id == id && it.o.space == Space.CLOSE }?.o }
    var template by remember { mutableStateOf(existing?.let { CloseTemplates.of(it) } ?: templateKey?.let { CloseTemplates.byKey(it) }) }
    val t = template

    if (t == null) {
        Page("Add a date", onBack) {
            Text("What are you planning for?", style = SibType.body, color = c.inkSoft)
            Spacer(Modifier.height(12.dp))
            CloseTemplates.all.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { tpl ->
                        Column(
                            Modifier.weight(1f).heightIn(min = 92.dp).clip(RoundedCornerShape(8.dp)).background(c.raised)
                                .clickable(role = Role.Button) { template = tpl }.padding(14.dp),
                        ) {
                            Text(tpl.label, style = SibType.title, color = c.ink)
                            Spacer(Modifier.height(2.dp))
                            Text(tpl.blurb, style = SibType.small, color = c.inkSoft)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        return
    }

    val startDate = existing?.repeatAnchor ?: existing?.due?.local?.toLocalDate()
    var title by remember(t.key) { mutableStateOf(existing?.title ?: t.label) }
    var person by remember(t.key) { mutableStateOf(existing?.person.orEmpty()) }
    var relation by remember(t.key) { mutableStateOf(existing?.relation.orEmpty()) }
    var date by remember(t.key) { mutableStateOf(startDate) }
    var time by remember(t.key) { mutableStateOf<LocalTime?>(existing?.due?.takeIf { it.precision == Precision.DATE_TIME }?.local?.toLocalTime() ?: if (t.timed) LocalTime.of(19, 0) else null) }
    var repeat by remember(t.key) { mutableStateOf(existing?.repeat ?: t.repeat) }
    var lead by remember(t.key) { mutableStateOf((existing?.leadDays ?: t.lead).toSet()) }
    var countYears by remember(t.key) { mutableStateOf(existing?.since != null || (existing == null && t.countYears)) }
    var notes by remember(t.key) { mutableStateOf(existing?.notes.orEmpty()) }
    var pickDate by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val picked = date
    val pastOneOff = picked != null && repeat == Repeat.NONE && picked.isBefore(st.today)

    // Back goes to the template list when the template was chosen on this screen, otherwise leaves.
    val back: () -> Unit = if (existing == null && templateKey == null) ({ template = null }) else onBack

    Page(if (existing != null) "Edit" else t.label, back) {
        Spacer(Modifier.height(4.dp))
        Field("Title", title, { title = it })
        Spacer(Modifier.height(14.dp))
        Field("Who is it for", person, { person = it }, placeholder = "Their name")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RELATIONS.forEach { r -> SelectChip(r, relation == r, { relation = if (relation == r) "" else r }) }
        }

        Spacer(Modifier.height(18.dp))
        Text(t.dateLabel, style = SibType.bodyStrong, color = c.ink)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
            SibButton(picked?.let { fmt.dayLong(it) } ?: "Choose a date", { pickDate = true }, kind = ButtonKind.QUIET)
        }
        if (pastOneOff) Text("That day has already passed.", style = SibType.small, color = c.vermilion)
        if (picked != null && repeat != Repeat.NONE && picked.isBefore(st.today)) {
            val next = Recurrence.nextOnOrAfter(picked, repeat, st.today)
            Text("The next one is ${fmt.dayLong(next)}.", style = SibType.small, color = c.inkSoft)
        }
        ToggleRow("Add a time", null, time != null, { time = if (it) (time ?: LocalTime.of(19, 0)) else null })
        time?.let { tm ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SibButton("− 15 min", { time = tm.minusMinutes(15) }, kind = ButtonKind.QUIET)
                Text(fmt.time(tm), style = SibType.headline, color = c.ink, modifier = Modifier.padding(horizontal = 16.dp))
                SibButton("+ 15 min", { time = tm.plusMinutes(15) }, kind = ButtonKind.QUIET)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Repeats", style = SibType.bodyStrong, color = c.ink)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Repeat.values().forEach { r -> SelectChip(r.label, repeat == r, { repeat = r }) }
        }

        if (t.countYears && repeat != Repeat.NONE) {
            ToggleRow(
                if (t.category == Category.BIRTHDAY) "Show their age" else "Count the time together",
                "Uses the date above as the start, for example \"4 years together\".", countYears, { countYears = it },
            )
        } else if (t.category == Category.BIRTHDAY && repeat == Repeat.YEARLY) {
            ToggleRow("Show their age", "Pick the real birth date above so the age is right.", countYears, { countYears = it })
        }

        Spacer(Modifier.height(16.dp))
        Text("Remind me", style = SibType.bodyStrong, color = c.ink)
        Text("Plus a reminder on the day itself.", style = SibType.small, color = c.inkSoft)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LEAD_CHOICES.forEach { d ->
                SelectChip("$d ${if (d == 1) "day" else "days"} before", d in lead, { lead = if (d in lead) lead - d else lead + d })
            }
        }

        Spacer(Modifier.height(16.dp))
        Field("Notes only you can see", notes, { notes = it }, placeholder = "Plans, gift ideas", singleLine = false, minLines = 2, keyboard = KeyboardOptions(imeAction = ImeAction.Default))

        Spacer(Modifier.height(24.dp))
        SibButton(
            if (saving) "Saving" else "Save", {
                val d = date ?: return@SibButton
                saving = true
                val tm = time
                // The chosen day is the anchor; for something that repeats, the date shown is the next one on or after today.
                val shown = if (repeat == Repeat.NONE) d else Recurrence.nextOnOrAfter(d, repeat, st.today)
                val due = DueSpec(
                    LocalDateTime.of(shown, tm ?: LocalTime.MIDNIGHT), st.zone,
                    if (tm != null) Precision.DATE_TIME else Precision.DATE_ONLY, floating = true,
                )
                val base = existing ?: Obligation(id = UUID.randomUUID().toString(), title = "")
                val o = base.copy(
                    title = title.trim().ifEmpty { t.label }, notes = notes.trim(), category = t.category, kind = Kind.EVENT, due = due, fog = null,
                    importance = Defaults.importance(t.category), effort = null, space = Space.CLOSE,
                    repeat = repeat, repeatAnchor = if (repeat == Repeat.NONE) null else d,
                    leadDays = lead.sortedDescending(), person = person.trim(), relation = relation,
                    since = if (countYears && repeat != Repeat.NONE) d else null,
                )
                scope.launch { val saved = ready.store.save(o, "USER", if (existing == null) "created" else "edited"); onSaved(saved.id) }
            },
            Modifier.fillMaxWidth(), enabled = date != null && !pastOneOff && !saving,
        )
        Spacer(Modifier.height(40.dp))
    }

    if (pickDate) SibDatePicker(date ?: st.today, { date = it; pickDate = false }, { pickDate = false })
}
