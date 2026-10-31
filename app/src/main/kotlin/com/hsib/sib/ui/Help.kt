package com.hsib.sib.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.hsib.sib.Ready
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which Guide topic explains which screen. A screen that is not listed never gets a hint. */
object HelpMap {
    fun topic(key: String): String? = when {
        key == "tab:PLAN" -> "The plan"
        key == "tab:REVIEW" -> "Review suggestions"
        key == "tab:MESSAGES" -> "Find a friend and chat"
        key == "add" -> "Add a deadline"
        key.startsWith("item/") -> "Steps you can edit"
        key == "settings/course" || key.startsWith("course/") -> "Make it yours"
        key == "study/routine" -> "Medical student mode"
        key == "settings/appearance" || key == "settings/motivation" -> "Hide what you do not need"
        else -> null
    }

    const val GAP_BETWEEN_HINTS_MS = 20 * 60_000L
    const val LONG_ABSENCE_MS = 14L * 24 * 60 * 60_000
    const val FIRST_VISIT_WAIT_MS = 12_000L
    const val RETURN_WAIT_MS = 8_000L
    const val IDLE_WAIT_MS = 40_000L
    const val SNOOZE_NO_THANKS_MS = 7L * 24 * 60 * 60_000
    const val SNOOZE_SHOWN_MS = 3L * 24 * 60 * 60_000

    /**
     * How long to watch before wondering whether someone is stuck: a screen seen for the first time, or after two weeks away,
     * is watched for a few seconds; any other visit needs a long quiet spell. Null when this screen was snoozed.
     */
    fun waitFor(visits: Int, lastVisitMs: Long?, snoozeUntilMs: Long, nowMs: Long): Long? = when {
        nowMs < snoozeUntilMs -> null
        visits == 0 -> FIRST_VISIT_WAIT_MS
        lastVisitMs != null && nowMs - lastVisitMs > LONG_ABSENCE_MS -> RETURN_WAIT_MS
        else -> IDLE_WAIT_MS
    }
}

/**
 * A small card that offers the Guide page for the screen someone is on, when they seem unsure: it is new to them, they have been
 * away a long time, or they have not touched anything for a while. It never blocks, goes away by itself, and each choice is
 * remembered on the phone only. Turn it off in Settings, Your name and hints.
 */
@Composable
fun HelpNudge(routeKey: String?, name: String?, ready: Ready, enabled: Boolean, lastTouchAt: Long, onOpenGuide: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = SibTheme.colors
    val scope = rememberCoroutineScope()
    val topic = routeKey?.let(HelpMap::topic)
    var visible by remember(routeKey) { mutableStateOf(false) }
    LaunchedEffect(routeKey, enabled) {
        visible = false
        if (!enabled || routeKey == null || topic == null) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val last = ready.settings.raw("help.last.$routeKey")?.toLongOrNull()
        val visits = ready.settings.raw("help.n.$routeKey")?.toIntOrNull() ?: 0
        val snooze = ready.settings.raw("help.snooze.$routeKey")?.toLongOrNull() ?: 0L
        ready.settings.putRaw("help.last.$routeKey", now.toString())
        ready.settings.putRaw("help.n.$routeKey", (visits + 1).toString())
        val wait = HelpMap.waitFor(visits, last, snooze, now) ?: return@LaunchedEffect
        delay(wait)
        val shownAt = ready.settings.raw("help.shown")?.toLongOrNull() ?: 0L
        val quiet = System.currentTimeMillis() - lastTouchAt >= wait - 500
        if (quiet && System.currentTimeMillis() - shownAt > HelpMap.GAP_BETWEEN_HINTS_MS) {
            ready.settings.putRaw("help.shown", System.currentTimeMillis().toString())
            ready.settings.putRaw("help.snooze.$routeKey", (System.currentTimeMillis() + HelpMap.SNOOZE_SHOWN_MS).toString())
            visible = true
            delay(20_000)
            visible = false
        }
    }
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible && topic != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(SibIcon.INFO, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text((name?.let { "$it, need" } ?: "Need") + " a hand with this page?", style = SibType.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SibButton("Show me", { visible = false; topic?.let(onOpenGuide) }, kind = ButtonKind.QUIET, compact = true)
                    SibButton("No thanks", {
                        visible = false
                        scope.launch { ready.settings.putRaw("help.snooze.$routeKey", (System.currentTimeMillis() + HelpMap.SNOOZE_NO_THANKS_MS).toString()) }
                    }, kind = ButtonKind.TEXT, compact = true)
                    SibButton("Stop these hints", { visible = false; scope.launch { ready.settings.update { it.copy(helpHints = false) } } }, kind = ButtonKind.TEXT, compact = true)
                }
            }
        }
    }
}

/** Notes every touch, without taking it from the screen underneath, so the hint knows when someone has been still. */
fun Modifier.notingTouches(onTouch: () -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            onTouch()
        }
    }
}
