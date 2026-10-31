package com.hsib.sib.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hsib.sib.core.model.Candidate
import com.hsib.sib.core.model.Effort
import com.hsib.sib.core.model.Flag
import com.hsib.sib.core.model.Importance
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.model.Relation
import com.hsib.sib.core.model.Space
import com.hsib.sib.core.model.Tier
import com.hsib.sib.core.plan.Defaults
import com.hsib.sib.core.plan.Impact
import com.hsib.sib.core.plan.ImpactPreview
import com.hsib.sib.core.plan.PlanTask
import com.hsib.sib.core.plan.Remedy
import com.hsib.sib.core.urgency.UrgencyState
import com.hsib.sib.data.CandidateEdit
import com.hsib.sib.data.HomeStyle
import com.hsib.sib.domain.AppState
import com.hsib.sib.domain.Fmt
import com.hsib.sib.domain.ObligationView
import com.hsib.sib.social.SocialState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun rememberFmt(): Fmt {
    val ctx = LocalContext.current
    return remember(ctx) { Fmt(ctx) }
}

// ---- bottom bar ------------------------------------------------------------------------------

enum class Tab(val label: String) { TODAY("Today"), PLAN("Plan"), REVIEW("Review"), MESSAGES("Messages"), SETTINGS("More") }

/** A floating pill: the chosen tab opens up to show its name, the others stay as icons. */
@Composable
fun SibBottomBar(selected: Tab, onSelect: (Tab) -> Unit, reviewCount: Int, unreadMessages: Int = 0, tabs: List<Tab> = Tab.values().toList()) {
    val c = SibTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).clip(RoundedCornerShape(34.dp)).background(c.raised).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (tab in tabs) {
            val on = tab == selected
            val weight by animateFloatAsState(if (on) 1.7f else 1f, tween(260), label = "tabWeight")
            val desc = tab.label + (if (tab == Tab.REVIEW && reviewCount > 0) ", $reviewCount waiting" else "") + (if (tab == Tab.MESSAGES && unreadMessages > 0) ", $unreadMessages new" else "")
            Row(
                Modifier.weight(weight).height(54.dp).clip(RoundedCornerShape(28.dp))
                    .then(if (on) Modifier.background(c.violetWash) else Modifier)
                    .bounceClick(role = Role.Tab) { onSelect(tab) }.semantics { contentDescription = desc },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    TabGlyph(tab, if (on) c.violet else c.inkSoft)
                    val badge = if (tab == Tab.REVIEW) reviewCount else if (tab == Tab.MESSAGES) unreadMessages else 0
                    if (badge > 0) {
                        Text(
                            badge.coerceAtMost(99).toString(), style = SibType.label.copy(fontSize = 10.sp), color = c.paper,
                            modifier = Modifier.align(Alignment.TopEnd).padding(start = 18.dp).clip(RoundedCornerShape(8.dp)).background(c.vermilion).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                if (on) {
                    Spacer(Modifier.width(8.dp))
                    Text(tab.label, style = SibType.bodyStrong, color = c.ink, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TabGlyph(tab: Tab, color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.size(26.dp)) {
        val u = size.width / 24f
        val sw = 2.4f * u
        val cx = size.width / 2
        val cy = size.height / 2
        when (tab) {
            Tab.TODAY -> {
                drawCircle(color, size.minDimension / 2 - 3f * u, style = Stroke(sw))
                drawLine(color, Offset(cx, cy), Offset(cx, cy - 6.5f * u), sw, StrokeCap.Round)
                drawLine(color, Offset(cx, cy), Offset(cx + 5f * u, cy + 3f * u), sw, StrokeCap.Round)
            }
            Tab.PLAN -> {
                drawLine(color, Offset(3f * u, 7f * u), Offset(size.width - 3f * u, 7f * u), sw, StrokeCap.Round)
                drawLine(color, Offset(3f * u, 12f * u), Offset(size.width * 0.65f, 12f * u), sw, StrokeCap.Round)
                drawLine(color, Offset(3f * u, 17f * u), Offset(size.width * 0.4f, 17f * u), sw, StrokeCap.Round)
                drawLine(color, Offset(size.width - 3f * u, 3f * u), Offset(size.width - 3f * u, 21f * u), sw, StrokeCap.Round)
            }
            Tab.MESSAGES -> {
                drawRoundRect(color, Offset(3f * u, 4f * u), androidx.compose.ui.geometry.Size(18f * u, 12f * u), androidx.compose.ui.geometry.CornerRadius(4f * u), style = Stroke(sw))
                drawLine(color, Offset(8f * u, 16f * u), Offset(7f * u, 21f * u), sw, StrokeCap.Round)
                drawLine(color, Offset(7f * u, 21f * u), Offset(12f * u, 16f * u), sw, StrokeCap.Round)
            }
            Tab.REVIEW -> drawCircle(color, size.minDimension / 2 - 3f * u, style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * u, 4f * u))))
            Tab.SETTINGS -> {
                for (i in 0..2) {
                    val y = (5f + i * 7f) * u
                    drawLine(color, Offset(3f * u, y), Offset(size.width - 3f * u, y), sw, StrokeCap.Round)
                    drawCircle(color, 2.8f * u, Offset((6f + ((i * 7) % 12)) * u, y))
                }
            }
        }
    }
}

@Composable
fun PlusButton(description: String = "Add a deadline", onClick: () -> Unit) {
    val c = SibTheme.colors
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(c.action).bounceClick(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { TileGlyph(TileIcon.ADD, c.onAction, size = 24.dp) }
}

// ---- today -----------------------------------------------------------------------------------

private fun weekPhrase(st: AppState, fmt: Fmt): String {
    st.plan.shortfall?.let { return "Needs ${fmt.minutes(it.missingMin)} more time before ${fmt.dayNoWeek(it.toDay)}" }
    val days = (0 until 7).map { st.today.plusDays(it.toLong()) }
    val cap = days.sumOf { st.plan.capacityByDay[it] ?: 0 }
    val load = days.sumOf { st.plan.loadByDay[it] ?: 0 }
    if (st.active.isEmpty()) return "All clear"
    if (cap == 0) return "No time set aside this week"
    val r = load.toDouble() / cap
    return when {
        load == 0 -> "Nothing planned this week"
        r < 0.4 -> "A light week"
        r < 0.75 -> "A steady week"
        else -> "A heavy week"
    }
}

@Composable
fun TodayScreen(
    st: AppState,
    importActions: ImportActions,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onReview: () -> Unit,
    onPlan: () -> Unit,
    onAll: () -> Unit,
    onLog: (String, Int) -> Unit,
    onCloseOnes: () -> Unit,
    onPrompt: (Prompt) -> Unit,
    social: SocialState? = null,
    onFriends: () -> Unit = {},
    whatsNew: List<WhatsNew.Entry> = emptyList(),
    onWhatsNewSeen: () -> Unit = {},
    classes: List<com.hsib.sib.data.RoutineSlot> = emptyList(),
) {
    val c = SibTheme.colors
    val fmt = rememberFmt()
    val items = st.items.filter { it.urgency != UrgencyState.DONE && it.o.space == Space.WORK }
    val next = items.firstOrNull { it.dueInstant != null && it.urgency != UrgencyState.MOVED }
    val overdue = items.count { it.urgency == UrgencyState.OVERDUE }
    val dueToday = items.count { it.urgency == UrgencyState.DUE_TODAY }
    val thisWeek = items.count { it.urgency == UrgencyState.DUE_SOON || it.urgency == UrgencyState.APPROACHING }

    Column(Modifier.fillMaxSize().cuedVerticalScroll().padding(horizontal = 24.dp)) {
        Gap(20.dp)
        Text(fmt.dayShort(st.today), style = SibType.display, color = c.ink)
        Text(weekPhrase(st, fmt), style = SibType.body, color = if (st.plan.shortfall != null) c.vermilion else c.inkSoft)

        Gap(24.dp)
        DeadlineDeck(st.settings.homeStyle, items, next, st, fmt, onOpen, onAdd, nextStep(st), onLog, social)

        // The hero shows the next one; the others wait right below it so nothing is hidden behind "All deadlines".
        val others = items.filter { it !== next && it.urgency != UrgencyState.MOVED }.take(3)
        if (others.isNotEmpty() && st.settings.homeStyle == HomeStyle.FOCUS) {
            Gap(20.dp)
            SectionHeader("Also coming up", count = others.size)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                others.forEach { v -> ObligationRow(v, fmt, st.today, st.zone, st.now, { onOpen(v.o.id) }, shared = social?.roomFor(v.o.id) != null) }
            }
        }

        if (overdue + dueToday + thisWeek > 0) {
            Gap(16.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (overdue > 0) StatPill(overdue, "past due", Tone.ALERT, onAll)
                if (dueToday > 0) StatPill(dueToday, "today", Tone.ALERT, onAll)
                if (thisWeek > 0) StatPill(thisWeek, "this week", Tone.WARN, onAll)
            }
        }

        if (st.settings.medicalMode && classes.any { it.day == st.today.dayOfWeek.value }) {
            Gap(16.dp)
            TodayClasses(classes, st.today, fmt)
        }

        if (whatsNew.isNotEmpty()) {
            Gap(16.dp)
            WhatsNewCard(whatsNew, onWhatsNewSeen)
        }

        // One gentle recommendation at a time: the notification dashboard first, then reading chats.
        val askDashboard = next != null && !st.settings.todayNotification && !st.settings.dashboardPromptDismissed
        val askReader = !askDashboard && st.settings.notificationReaderApps.isEmpty() && !st.settings.readerPromptDismissed
        if (askDashboard || askReader) {
            Gap(16.dp)
            PromptCard(askDashboard, onPrompt)
        }

        if (st.pending.isNotEmpty()) {
            Gap(16.dp)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.violetWash).bounceClick(onClick = onReview).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrgencyGlyph(UrgencyState.REVIEW, size = 24.dp)
                Spacer(Modifier.width(14.dp))
                Text("${st.pending.size} to review", style = SibType.title, color = c.ink, modifier = Modifier.weight(1f))
                Text("Open", style = SibType.bodyStrong, color = c.violet)
            }
        }

        st.plan.shortfall?.let { s ->
            Gap(12.dp)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.vermilionWash).bounceClick(onClick = onPlan).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Needs ${fmt.minutes(s.missingMin)} more time", style = SibType.title, color = c.vermilion, modifier = Modifier.weight(1f))
                Text("Options", style = SibType.bodyStrong, color = c.vermilion)
            }
        }

        Gap(32.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile("Add", TileIcon.ADD, onAdd, Modifier.weight(1f), accent = true)
            ActionTile("Paste", TileIcon.PASTE, importActions.paste, Modifier.weight(1f))
        }
        Gap(12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile("Photo", TileIcon.PHOTO, importActions.photo, Modifier.weight(1f))
            ActionTile("Scan", TileIcon.SCAN, importActions.scan, Modifier.weight(1f))
        }

        Gap(28.dp)
        NavRow("All deadlines", if (items.isEmpty()) "Nothing yet" else "${items.size} open", onAll)
        if (social != null) {
            Gap(12.dp)
            val unread = social.rooms.values.sumOf { it.unread }
            NavRow(
                "Friends",
                when {
                    social.user == null -> "Share a deadline and chat about it"
                    social.rooms.isEmpty() -> "Signed in"
                    unread > 0 -> "${social.rooms.size} shared  ·  $unread new"
                    else -> "${social.rooms.size} shared"
                },
                onFriends,
            )
        }
        if (st.settings.closeOnesEnabled) {
            Gap(12.dp)
            CloseOnesEntry(st, onCloseOnes)
        }
        Gap(40.dp)
    }
}

/** The hero: the next deadline as a graded ring with the days left in large figures. */
@Composable
internal fun NextCard(
    v: ObligationView?,
    st: AppState,
    fmt: Fmt,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    step: NextStep?,
    onLog: (String, Int) -> Unit,
    shared: Boolean = false,
) {
    val c = SibTheme.colors
    val day = v?.dueInstant?.atZone(st.zone)?.toLocalDate()
    val days = day?.let { ChronoUnit.DAYS.between(st.today, it) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(c.raised).glowBackdrop(c.glowA, c.glowB, c.glowStrength).padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeroRing(days, v?.urgency)
        Gap(22.dp)
        if (v == null) {
            Text("All clear", style = SibType.headline, color = c.ink)
            Gap(20.dp)
            ActionButton("Add a deadline", onAdd)
        } else {
            Text(v.o.title, style = SibType.headline, color = c.ink, textAlign = TextAlign.Center)
            Gap(6.dp)
            Text(day?.let { fmt.dayLong(it) } ?: "", style = SibType.body, color = c.inkSoft)
            Gap(10.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                UrgencyGlyph(v.urgency, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(v.urgency.label, style = SibType.bodyStrong, color = stateColor(v.urgency))
                if (shared) { Spacer(Modifier.width(10.dp)); Chip("Shared") }
            }
            Gap(22.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionButton("Open", { onOpen(v.o.id) })
                if (step != null && step.id == v.o.id && step.day == st.today) {
                    val minutes = minOf(step.minutes, 30)
                    SibButton("Log $minutes min", { onLog(step.id, minutes) }, kind = ButtonKind.QUIET)
                }
            }
        }
    }
}

/** The second layer: every open deadline, grouped by how soon it needs you. */
@Composable
fun AllScreen(st: AppState, onOpen: (String) -> Unit, onBack: () -> Unit, sharedIds: Set<String> = emptySet()) {
    val fmt = rememberFmt()
    val items = st.items.filter { it.urgency != UrgencyState.DONE && it.o.space == Space.WORK }
    val groups = listOf(
        "Past due" to items.filter { it.urgency == UrgencyState.OVERDUE },
        "Today" to items.filter { it.urgency == UrgencyState.DUE_TODAY },
        "This week" to items.filter { it.urgency == UrgencyState.DUE_SOON || it.urgency == UrgencyState.APPROACHING },
        "Start soon" to items.filter { it.urgency == UrgencyState.START_SOON || it.urgency == UrgencyState.IN_PROGRESS },
        "Later" to items.filter { it.urgency == UrgencyState.PLANNED || it.urgency == UrgencyState.MOVED },
    )
    Page("All deadlines", onBack) {
        for ((title, list) in groups) {
            if (list.isEmpty()) continue
            SectionHeader(title, count = list.size)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                list.forEach { v -> ObligationRow(v, fmt, st.today, st.zone, st.now, { onOpen(v.o.id) }, shared = v.o.id in sharedIds) }
            }
        }
        if (items.isEmpty()) {
            Gap(48.dp)
            Text("Nothing open.", style = SibType.headline, color = SibTheme.colors.ink)
        }
        Gap(40.dp)
    }
}

internal data class NextStep(val id: String, val title: String, val day: LocalDate, val minutes: Int)

internal fun nextStep(st: AppState): NextStep? {
    val b = st.plan.blocks.filter { !it.day.isBefore(st.today) }.minWithOrNull(compareBy({ it.day }, { -it.minutes })) ?: return null
    val v = st.items.firstOrNull { it.o.id == b.taskId } ?: return null
    val total = st.plan.blocks.filter { it.taskId == b.taskId && it.day == b.day }.sumOf { it.minutes }
    return NextStep(b.taskId, v.o.title, b.day, total)
}

// ---- plan ------------------------------------------------------------------------------------

@Composable
fun PlanScreen(st: AppState, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val c = SibTheme.colors
    val fmt = rememberFmt()
    var view by rememberSaveable { mutableStateOf(PlanView.WEEK) }
    var selected by remember { mutableStateOf(st.today) }
    Column(Modifier.fillMaxSize().cuedVerticalScroll().padding(horizontal = 24.dp)) {
        Gap(20.dp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Plan", style = SibType.display, color = c.ink, modifier = Modifier.weight(1f))
            PlusButton(onClick = onAdd)
        }
        st.plan.shortfall?.let { s ->
            Gap(20.dp)
            ShortfallCard(s, st, fmt)
        }
        Gap(24.dp)
        InfoTitle("Views", "Week shows the next seven days. Month is a calendar with a dot on every day something is due. Runway shows how much room each deadline has. Load shows how busy each week is. They show the same plan in four ways.")
        Gap(8.dp)
        Choice(PlanView.values().map { it to it.label }, view, { view = it })
        Gap(24.dp)
        when (view) {
            PlanView.WEEK -> WeekView(st, fmt, selected, { selected = it }, onOpen)
            PlanView.MONTH -> MonthView(st, fmt, selected, { selected = it }, onOpen)
            PlanView.RUNWAY -> Runway(remember(st.items, st.pending, st.today) { runwayItems(st) }, st.today, fmt, onOpen)
            PlanView.LOAD -> {
                LoadHorizon(st.plan, st.today, fmt, weekStart = st.settings.weekStart)
                Gap(12.dp)
                LoadLegend()
            }
        }
        Gap(40.dp)
    }
}

private enum class PlanView(val label: String) { WEEK("Week"), MONTH("Month"), RUNWAY("Runway"), LOAD("Load") }

@Composable
private fun WeekView(st: AppState, fmt: Fmt, selected: LocalDate, onSelect: (LocalDate) -> Unit, onOpen: (String) -> Unit) {
    val c = SibTheme.colors
    val cells = (0 until 7).map { i ->
        val d = st.today.plusDays(i.toLong())
        DayCell(d, st.plan.loadByDay[d] ?: 0, st.plan.capacityByDay[d] ?: 0, st.active.count { v -> v.dueInstant?.atZone(st.zone)?.toLocalDate() == d })
    }
    WeekStrip(cells, selected, st.today, fmt, onSelect)
    Gap(20.dp)
    DayDetails(st, fmt, selected, onOpen)
}

/** What is due and planned on one day. Shared by the week and month views. */
@Composable
fun DayDetails(st: AppState, fmt: Fmt, selected: LocalDate, onOpen: (String) -> Unit) {
    val c = SibTheme.colors
    val dayBlocks = st.plan.blocks.filter { it.day == selected }
    val dueThatDay = st.active.filter { it.dueInstant?.atZone(st.zone)?.toLocalDate() == selected }
    if (dayBlocks.isEmpty() && dueThatDay.isEmpty()) {
        Text("Nothing on ${fmt.dayShort(selected)}", style = SibType.title, color = c.ink)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        dueThatDay.forEach { v ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.vermilionWash).bounceClick { onOpen(v.o.id) }.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrgencyGlyph(v.urgency, size = 22.dp)
                Spacer(Modifier.width(14.dp))
                Text(v.o.title, style = SibType.title, color = c.ink, modifier = Modifier.weight(1f))
                Text("Due", style = SibType.bodyStrong, color = c.vermilion)
            }
        }
        dayBlocks.forEach { b ->
            val title = st.items.firstOrNull { it.o.id == b.taskId }?.o?.title ?: ""
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.raised).bounceClick { onOpen(b.taskId) }.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(fmt.minutes(b.minutes), style = SibType.number.copy(fontSize = 17.sp, lineHeight = 22.sp), color = c.violet, modifier = Modifier.widthIn(min = 104.dp))
                Text(title, style = SibType.title, color = c.ink, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** When the work does not fit: the size of the gap up front, the ways out one tap deeper. */
@Composable
private fun ShortfallCard(s: com.hsib.sib.core.plan.Shortfall, st: AppState, fmt: Fmt) {
    val c = SibTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize().clip(RoundedCornerShape(30.dp)).background(c.vermilionWash).padding(22.dp)) {
        Text("Needs ${fmt.minutes(s.missingMin)} more time", style = SibType.display, color = c.vermilion)
        Text("Before ${fmt.dayNoWeek(s.toDay)}", style = SibType.body, color = c.ink)
        Gap(14.dp)
        SibButton(if (open) "Hide options" else "See options", { open = !open }, kind = ButtonKind.QUIET)
        if (open) {
            Gap(14.dp)
            s.remedies.forEach { r ->
                val text = when (r) {
                    is Remedy.MoreTimePerDay -> "Find ${fmt.minutes(r.extraMinPerDay)} more a day, ${fmt.dayNoWeek(r.fromDay)} to ${fmt.dayNoWeek(r.toDay)}"
                    is Remedy.ExtendDeadline -> "Ask for ${r.byDays} more ${if (r.byDays == 1) "day" else "days"} on ${st.items.firstOrNull { it.o.id == r.taskId }?.o?.title ?: ""}"
                    is Remedy.ReduceScope -> "Cut ${fmt.minutes(r.minutes)} from ${st.items.firstOrNull { it.o.id == r.taskId }?.o?.title ?: ""}"
                }
                Text(text, style = SibType.body, color = c.ink, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }
        }
    }
}

fun runwayItems(st: AppState): List<RunwayItem> {
    val real = st.items.filter { it.o.lifecycle == com.hsib.sib.core.model.Lifecycle.ACTIVE && it.o.space == Space.WORK && it.dueInstant != null }.map { v ->
        val fog = v.o.fog
        val due = if (fog != null) fog.earliest else v.dueInstant!!.atZone(st.zone).toLocalDate()
        RunwayItem(v.o.id, v.o.title, due, fog?.latest, v.blocks.map { it.day }.distinct(), v.latestStart, v.urgency, false)
    }.sortedBy { it.due }.take(14)
    val previews = st.pending.take(4).mapNotNull { cand ->
        val day = cand.fog?.earliest ?: cand.due?.dayIn(st.zone) ?: return@mapNotNull null
        RunwayItem(cand.id, cand.title, day, cand.fog?.latest, emptyList(), null, UrgencyState.REVIEW, true)
    }
    return real + previews
}

// ---- review ----------------------------------------------------------------------------------

@Composable
fun ReviewScreen(
    st: AppState,
    onConfirm: (Candidate, CandidateEdit) -> Unit,
    onDismiss: (String) -> Unit,
    onSnooze: (String) -> Unit,
    onAdd: () -> Unit,
    onRoutineSeparate: (List<Candidate>) -> Unit = {},
    onRoutineOne: (List<Candidate>, String) -> Unit = { _, _ -> },
) {
    val c = SibTheme.colors
    Column(Modifier.fillMaxSize().cuedVerticalScroll().padding(horizontal = 24.dp)) {
        Gap(20.dp)
        Text("Review", style = SibType.display, color = c.ink)
        if (st.pending.isEmpty()) {
            Gap(64.dp)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                HeroRing(null, null, size = 150.dp)
                Gap(20.dp)
                Text("All caught up", style = SibType.headline, color = c.ink)
                if (st.snoozedCount > 0) Text("${st.snoozedCount} for later", style = SibType.body, color = c.inkSoft)
                Gap(24.dp)
                SibButton("Add or import", onAdd, kind = ButtonKind.QUIET)
            }
        } else {
            Text("Confirm what is right.", style = SibType.body, color = c.inkSoft)
            Gap(20.dp)
            val routine = remember(st.pending) { com.hsib.sib.core.extract.ExamRoutine.find(st.pending) }
            if (routine.isNotEmpty()) {
                RoutineCard(routine, { onRoutineSeparate(routine) }, { onRoutineOne(routine, "Exam routine") })
                Gap(20.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                (st.pending.filter { it.tier == Tier.SUGGEST } + st.pending.filter { it.tier == Tier.MAYBE }).forEach {
                    CandidateCard(it, st, onConfirm, onDismiss, onSnooze)
                }
            }
        }
        Gap(40.dp)
    }
}

/** Offered when a timetable was read: one deadline for each exam, or a single deadline with a part for each. */
@Composable
private fun RoutineCard(routine: List<Candidate>, onSeparate: () -> Unit, onOne: () -> Unit) {
    val c = SibTheme.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.violetWash).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoTitle("This looks like an exam routine", "You can keep every exam as its own deadline, each with its own reminders, or keep them as one deadline on the last exam date with a checklist item for each exam, and tick them off as you go.", style = SibType.title)
        Text("${routine.size} exams were found. How do you want to keep them?", style = SibType.body, color = c.ink)
        SibButton("${routine.size} separate deadlines", onSeparate, Modifier.fillMaxWidth())
        SibButton("One deadline, ${routine.size} checklist items", onOne, Modifier.fillMaxWidth(), ButtonKind.QUIET)
    }
}

fun flagText(f: Flag, c: Candidate, fmt: Fmt, today: LocalDate): String? = when (f) {
    Flag.AMBIGUOUS_ORDER -> "Day and month order is ambiguous. Check which reading is right."
    Flag.MIDNIGHT -> "Midnight belongs to two days. Check which one is meant."
    Flag.YEAR_INFERRED -> "No year was written, so ${c.due?.local?.year ?: today.year} was assumed."
    Flag.AMPM_UNCERTAIN -> "AM or PM was not written. Check the time."
    Flag.ZONE_ASSUMED -> if (c.due?.precision == Precision.DATE_TIME) "No time zone was written, so yours is used." else null
    Flag.WEEKDAY_MISMATCH -> "The weekday written does not match that date."
    Flag.TENTATIVE_WORDING -> "The wording is tentative, so this may not be settled."
    Flag.VAGUE -> "This is a stretch of days, not a single day. Planning uses the earliest day."
    else -> null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CandidateCard(
    cand: Candidate,
    st: AppState,
    onConfirm: (Candidate, CandidateEdit) -> Unit,
    onDismiss: (String) -> Unit,
    onSnooze: (String) -> Unit,
) {
    val c = SibTheme.colors
    val fmt = rememberFmt()
    var title by remember(cand.id) { mutableStateOf(cand.title) }
    var chosen by remember(cand.id) { mutableStateOf(0) }
    var importance by remember(cand.id) { mutableStateOf(Defaults.importance(cand.category)) }
    var effort by remember(cand.id) { mutableStateOf(Defaults.effort(cand.category)) }
    var keep by remember(cand.id) { mutableStateOf(st.settings.keepExcerptsByDefault) }
    var details by remember(cand.id) { mutableStateOf(false) }
    val readings = listOfNotNull(cand.due) + cand.alternatives
    val due = readings.getOrNull(chosen) ?: cand.due
    val related = cand.relatedId?.let { id -> st.items.firstOrNull { it.o.id == id }?.o }
    val warnings = cand.flags.mapNotNull { flagText(it, cand, fmt, st.today) }

    fun dueLabel(d: com.hsib.sib.core.model.DueSpec) = fmt.due(d.local, d.precision == Precision.DATE_ONLY)

    Column(Modifier.fillMaxWidth().animateContentSize().clip(RoundedCornerShape(30.dp)).background(c.raised).padding(22.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip(if (cand.tier == Tier.SUGGEST) "Likely" else "Maybe", if (cand.tier == Tier.SUGGEST) Tone.GOOD else Tone.WARN)
            when (cand.relation) {
                Relation.UPDATE -> Chip("Change", Tone.WARN)
                Relation.CANCEL -> Chip("Cancellation", Tone.ALERT)
                else -> {}
            }
        }
        Gap(14.dp)
        when (cand.relation) {
            Relation.CANCEL -> Text(related?.title ?: cand.title, style = SibType.headline, color = c.ink)
            Relation.UPDATE -> {
                Text(related?.title ?: cand.title, style = SibType.headline, color = c.ink)
                Gap(4.dp)
                Text("was " + (related?.due?.let { dueLabel(it) } ?: "no date"), style = SibType.body, color = c.inkSoft)
                Text("now " + (due?.let { dueLabel(it) } ?: "no date"), style = SibType.title, color = c.violet)
            }
            else -> {
                Text(title, style = SibType.headline, color = c.ink)
                Gap(4.dp)
                val candFog = cand.fog
                when {
                    candFog != null -> Text("Somewhere ${fmt.range(candFog.earliest, candFog.latest)}", style = SibType.title, color = c.violet)
                    due != null -> Text(dueLabel(due), style = SibType.title, color = c.violet)
                }
            }
        }
        if (warnings.isNotEmpty()) {
            Gap(8.dp)
            More(warnings.joinToString("\n\n"), label = "Check this")
        }
        Gap(18.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(
                when (cand.relation) { Relation.CANCEL -> "Cancel it"; Relation.UPDATE -> "Apply"; else -> "Confirm" },
                { onConfirm(cand, CandidateEdit(title = title.trim().ifBlank { cand.title }, due = due, importance = importance, effort = effort, keepExcerpt = keep)) },
                Modifier.weight(1f),
            )
            SibButton("Skip", { onDismiss(cand.id) }, kind = ButtonKind.QUIET)
        }
        Gap(6.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SibButton("Later", { onSnooze(cand.id) }, kind = ButtonKind.TEXT)
            SibButton(if (details) "Fewer details" else "Details", { details = !details }, kind = ButtonKind.TEXT)
        }
        if (details) {
            if (cand.relation == Relation.NEW) {
                Gap(8.dp)
                Field("Title", title, { title = it }, singleLine = true)
                if (readings.size > 1) {
                    Gap(16.dp)
                    Text("Which date?", style = SibType.bodyStrong, color = c.ink)
                    Gap(8.dp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        readings.forEachIndexed { i, r -> SelectChip(dueLabel(r), chosen == i, { chosen = i }) }
                    }
                }
                Gap(16.dp)
                Text("Importance", style = SibType.bodyStrong, color = c.ink)
                Gap(8.dp)
                Choice(
                    listOf(Importance.LOW to "Low", Importance.NORMAL to "Normal", Importance.HIGH to "High", Importance.CRITICAL to "Critical"),
                    importance, { importance = it },
                )
                Gap(16.dp)
                Text("Effort", style = SibType.bodyStrong, color = c.ink)
                Gap(8.dp)
                Choice(
                    EffortChoices.take(4), effort.likelyMin.let { l -> listOf(30, 120, 240, 480).minByOrNull { kotlin.math.abs(it - l) } ?: 120 },
                    { effort = Effort.fromLikely(it) },
                )
                if (due != null || cand.fog != null) {
                    Gap(16.dp)
                    ImpactPanel(cand, due, effort, importance, st, fmt)
                }
            }
            if (cand.reasons.isNotEmpty() || cand.excerpt.isNotBlank()) {
                Gap(8.dp)
                More(
                    cand.reasons.joinToString("\n") { it.text } + (if (cand.excerpt.isNotBlank()) "\n\nFrom: \"${cand.excerpt}\"" else ""),
                    label = "Why this?",
                )
            }
            if (cand.excerpt.isNotBlank() && cand.relation != Relation.CANCEL) {
                ToggleRow("Keep the sentence", null, keep, { keep = it })
            }
        }
    }
}

@Composable
private fun ImpactPanel(cand: Candidate, due: com.hsib.sib.core.model.DueSpec?, effort: Effort, importance: Importance, st: AppState, fmt: Fmt) {
    val c = SibTheme.colors
    val impact by produceState<Impact?>(null, cand.id, effort, importance, st.planTasks, st.settings) {
        val day = cand.fog?.earliest ?: due?.dayIn(st.zone) ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val task = PlanTask(
                "candidate", cand.title, (effort.expectedMin * st.calibration).toInt(), (effort.safeMin * st.calibration).toInt(),
                day, st.today, importance,
            )
            ImpactPreview.compute(st.planTasks, st.capacity, st.today, task)
        }
    }
    val i = impact
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (i?.collides == true) c.vermilionWash else c.verdigrisWash).padding(16.dp)) {
        if (i == null) {
            Text("Working it out", style = SibType.small, color = c.ink)
        } else {
            val first = i.firstDay
            val last = i.lastDay
            val where = if (first != null && last != null) " between ${fmt.dayNoWeek(first)} and ${fmt.dayNoWeek(last)}" else ""
            Text(
                "Adds about ${fmt.minutes(i.addedMin)} of work$where.",
                style = SibType.bodyStrong, color = c.ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    i.collides && !i.feasibleBefore -> "You were already short, and this makes it ${fmt.minutes(i.extraMissingMin)} worse."
                    i.collides -> "That stretch is already full. Accepting it leaves you ${fmt.minutes(i.missingAfterMin)} short."
                    i.peakAfterMin > i.peakBeforeMin -> "It fits. Your busiest day grows from ${fmt.minutes(i.peakBeforeMin)} to ${fmt.minutes(i.peakAfterMin)}."
                    else -> "It fits without making any day busier."
                },
                style = SibType.body, color = c.ink,
            )
            if (i.newlyAtRisk.isNotEmpty()) {
                Text(
                    "It would put at risk: " + i.newlyAtRisk.mapNotNull { id -> st.items.firstOrNull { it.o.id == id }?.o?.title }.joinToString(", ") + ".",
                    style = SibType.small, color = c.vermilion,
                )
            }
        }
    }
}
