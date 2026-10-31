package com.hsib.sib.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hsib.sib.data.HomeStyle
import com.hsib.sib.domain.AppState
import com.hsib.sib.domain.Fmt
import com.hsib.sib.domain.ObligationView
import com.hsib.sib.social.SocialState
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * The top of Today in one of four styles the person picks in Settings. FOCUS is the original: one big card and the rest listed
 * under it. The nearest deadline is always the first one shown in every style.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DeadlineDeck(
    style: HomeStyle,
    items: List<ObligationView>,
    next: ObligationView?,
    st: AppState,
    fmt: Fmt,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    step: NextStep?,
    onLog: (String, Int) -> Unit,
    social: SocialState?,
) {
    val c = SibTheme.colors
    // The nearest first, then the rest in the same order the app already uses.
    val ordered = remember(items, next) { if (next == null) items else listOf(next) + items.filter { it !== next } }
    fun shared(v: ObligationView?) = v != null && social?.roomFor(v.o.id) != null
    if (ordered.size <= 1 || style == HomeStyle.FOCUS) {
        NextCard(next, st, fmt, onOpen, onAdd, step, onLog, shared = shared(next))
        return
    }
    when (style) {
        HomeStyle.SWIPE -> {
            val pager = rememberPagerState { ordered.size }
            HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 16.dp) { i ->
                val v = ordered[i]
                NextCard(v, st, fmt, onOpen, onAdd, if (i == 0) step else null, onLog, shared = shared(v))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                for (i in ordered.indices.take(12)) {
                    Box(Modifier.padding(horizontal = 3.dp).size(if (i == pager.currentPage) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(if (i == pager.currentPage) c.violet else c.lifted))
                }
            }
            Text("Swipe for the next deadline", style = SibType.small, color = c.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        HomeStyle.STACK -> {
            var top by rememberSaveable { mutableIntStateOf(0) }
            val index = top.coerceIn(0, ordered.lastIndex)
            val behind = (1..3).mapNotNull { k -> ordered.getOrNull((index + k) % ordered.size)?.takeIf { ordered.size > k } }
            Box(
                Modifier.fillMaxWidth().padding(top = 36.dp).pointerInput(ordered.size) {
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = { if (abs(drag) > 80f) top = if (drag < 0) (index + 1) % ordered.size else (index - 1 + ordered.size) % ordered.size },
                    ) { _, d -> drag += d }
                },
            ) {
                behind.reversed().forEachIndexed { r, v ->
                    val k = behind.size - r
                    Box(
                        Modifier.matchParentSizeCompat().offset(y = (-14 * k).dp)
                            .graphicsLayer { scaleX = 1f - 0.05f * k; scaleY = 1f; alpha = 1f - 0.28f * k }
                            .clip(RoundedCornerShape(36.dp)).background(c.raised),
                        contentAlignment = Alignment.TopCenter,
                    ) { Text(v.o.title, style = SibType.small, color = c.inkSoft, maxLines = 1, modifier = Modifier.padding(top = 4.dp)) }
                }
                NextCard(ordered[index], st, fmt, onOpen, onAdd, if (index == 0) step else null, onLog, shared = shared(ordered[index]))
            }
            Text("Swipe sideways to bring the next one forward", style = SibType.small, color = c.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        }
        else -> Collage(ordered, st, fmt, onOpen)
    }
}

/** A box that takes the size of its parent. Written out so the stack can place faded cards behind the front one. */
private fun Modifier.matchParentSizeCompat(): Modifier = this.then(Modifier.fillMaxWidth().heightIn(min = 120.dp))

/** Every deadline as a tile: the nearest big across the top, the rest two to a row, tinted by how soon each is. */
@Composable
private fun Collage(items: List<ObligationView>, st: AppState, fmt: Fmt, onOpen: (String) -> Unit) {
    val c = SibTheme.colors
    @Composable
    fun Tile(v: ObligationView, big: Boolean, modifier: Modifier) {
        val day = v.dueInstant?.atZone(st.zone)?.toLocalDate()
        val days = day?.let { ChronoUnit.DAYS.between(st.today, it) }
        val tone = stateColor(v.urgency)
        Column(
            modifier.heightIn(min = if (big) 150.dp else 132.dp).clip(RoundedCornerShape(28.dp)).background(tone.copy(alpha = 0.16f))
                .bounceClick { onOpen(v.o.id) }.padding(18.dp)
                .semantics(mergeDescendants = true) { contentDescription = v.o.title + (if (days != null) ", " + dayWord(days) else "") },
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(v.o.title, style = if (big) SibType.headline else SibType.bodyStrong, color = c.ink)
            Column {
                Text(days?.let { if (it < 0) "${-it}" else "$it" } ?: "-", style = SibType.display, color = tone)
                Text(days?.let { dayWord(it) } ?: "No date", style = SibType.small, color = c.inkSoft)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile(items.first(), true, Modifier.fillMaxWidth())
        items.drop(1).chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(pair[0], false, Modifier.weight(1f))
                if (pair.size > 1) Tile(pair[1], false, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun dayWord(days: Long): String = when {
    days < 0 -> if (days == -1L) "day past due" else "days past due"
    days == 0L -> "due today"
    days == 1L -> "day left"
    else -> "days left"
}
