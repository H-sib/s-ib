package com.hsib.sib.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hsib.sib.core.extract.Extraction
import com.hsib.sib.core.model.Importance
import com.hsib.sib.core.plan.Capacity
import com.hsib.sib.core.plan.PlanTask
import com.hsib.sib.core.plan.Planner
import com.hsib.sib.core.urgency.UrgencyState
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The sample notice is built from today's date so it stays believable in any year. It is clearly labelled as made up. */
fun demoNotice(today: LocalDate): String {
    val f = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)
    return "Notice No. 87 of the Science Department\n" +
        "Posted on ${today.minusDays(12).format(f)}.\n" +
        "The annual science fair will take place on ${today.plusDays(24).format(f)}.\n" +
        "All project reports must be submitted before ${today.plusDays(16).format(f)}, 5 PM.\n" +
        "Registration for next term opens on ${today.plusDays(40).format(f)}.\n" +
        "Maybe we can discuss the results around ${today.plusDays(30).format(f)}?"
}

@Composable
fun Onboarding(
    today: LocalDate,
    demo: suspend (String) -> Extraction?,
    onRestore: () -> Unit,
    onFinish: () -> Unit,
) {
    val c = SibTheme.colors
    var page by rememberSaveable { mutableIntStateOf(0) }
    val last = 3
    val ms = if (rememberMotionEnabled()) 280 else 0
    Column(Modifier.fillMaxSize().background(c.paper).windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                page,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(ms)) + slideInHorizontally(tween(ms)) { dir * it / 8 }) togetherWith fadeOut(tween(ms / 2))
                },
                label = "onboarding",
            ) { p ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp)) {
                    Gap(32.dp)
                    when (p) {
                        0 -> PageOne()
                        1 -> PageTry(today, demo)
                        2 -> PagePlan(today)
                        else -> PagePrivacy()
                    }
                    Gap(24.dp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0..last) Box(Modifier.size(width = if (i == page) 26.dp else 8.dp, height = 8.dp).clip(RoundedCornerShape(4.dp)).background(if (i == page) c.violet else c.lifted))
            }
            if (page == 0) SibButton("I have a backup", onRestore, kind = ButtonKind.TEXT) else SibButton("Back", { page-- }, kind = ButtonKind.TEXT)
            Spacer(Modifier.size(6.dp))
            ActionButton(if (page == last) "Start" else "Next", { if (page == last) onFinish() else page++ })
        }
    }
}

@Composable
private fun PageOne() {
    val c = SibTheme.colors
    Gap(24.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HeroRing(12, null, size = 190.dp) }
    Gap(36.dp)
    Text("Start before it is late.", style = SibType.display, color = c.ink)
    Gap(12.dp)
    Text("S-IB finds your deadlines and tells you when to begin.", style = SibType.body.copy(fontSize = 17.sp, lineHeight = 26.sp), color = c.inkSoft)
}

@Composable
private fun PageTry(today: LocalDate, demo: suspend (String) -> Extraction?) {
    val c = SibTheme.colors
    val scope = rememberCoroutineScope()
    val text = remember(today) { demoNotice(today) }
    var result by remember { mutableStateOf<Extraction?>(null) }
    var busy by remember { mutableStateOf(false) }
    Text("Try it.", style = SibType.display, color = c.ink)
    Gap(10.dp)
    Text("A made-up notice. Not every date is a deadline.", style = SibType.body, color = c.inkSoft)
    Gap(20.dp)
    val r = result
    if (r == null) {
        Text(text, style = SibType.small, color = c.ink, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised).padding(20.dp))
        Gap(20.dp)
        ActionButton(if (busy) "Reading" else "Read it", { busy = true; scope.launch { result = demo(text); busy = false } }, Modifier.fillMaxWidth(), enabled = !busy)
    } else {
        ExplainedText(r)
        Gap(12.dp)
        Text("Read on this phone. No internet needed.", style = SibType.small, color = c.inkSoft)
    }
}

@Composable
private fun PagePlan(today: LocalDate) {
    val c = SibTheme.colors
    val fmt = rememberFmt()
    var hours by remember { mutableFloatStateOf(5f) }
    val cap = remember { Capacity.uniform(120) }
    val tasks = listOf(
        PlanTask("quiz", "Quiz revision", 6 * 60, 8 * 60, today.plusDays(9), today, Importance.NORMAL),
        PlanTask("report", "Science fair report", (hours * 60).toInt(), (hours * 78).toInt(), today.plusDays(16), today, Importance.HIGH),
    )
    val plan = remember(hours) { Planner.plan(tasks, cap, today) }
    Text("It tells the truth.", style = SibType.display, color = c.ink)
    Gap(10.dp)
    Text("Drag how long the report takes.", style = SibType.body, color = c.inkSoft)
    Gap(20.dp)
    Text("${hours.toInt()} hours", style = SibType.number, color = c.violet)
    Slider(
        value = hours, onValueChange = { hours = it }, valueRange = 2f..40f,
        colors = SliderDefaults.colors(thumbColor = c.violet, activeTrackColor = c.violet, inactiveTrackColor = c.lifted),
    )
    Gap(12.dp)
    LoadHorizon(plan, today, fmt, days = 28)
    Gap(12.dp)
    LoadLegend()
    Gap(20.dp)
    val s = plan.shortfall
    if (s == null) {
        Text("It fits. Start by ${plan.latestStart["report"]?.let { fmt.dayShort(it) } ?: "today"}.", style = SibType.title, color = c.verdigris)
    } else {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.vermilionWash).padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Short by ${fmt.minutes(s.missingMin)}", style = SibType.title, color = c.vermilion)
        }
    }
}

@Composable
private fun PagePrivacy() {
    val c = SibTheme.colors
    Text("Yours alone.", style = SibType.display, color = c.ink)
    Gap(28.dp)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("Stays on your phone", "No internet permission", "Backups are encrypted").forEach {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.raised).padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrgencyGlyph(UrgencyState.DONE, size = 26.dp)
                Spacer(Modifier.size(16.dp))
                Text(it, style = SibType.title, color = c.ink)
            }
        }
    }
}
