package com.hsib.sib.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hsib.sib.core.social.ChatMessage
import com.hsib.sib.core.social.MessageKind

/*
 * The chat has a look of its own, on purpose quieter than the rest of the app: one tone for the page and the bubbles, and thin
 * outlines, not fills, to tell them apart. No headline, no filled buttons. Messages sit close together in groups.
 */

/** The page colour of the chat: the app's paper with a breath of violet. Bubbles use this same tone. */
@Composable
internal fun chatSurface(): Color {
    val c = SibTheme.colors
    return lerp(c.paper, c.violet, 0.06f)
}

@Composable
private fun chatLine(): Color = SibTheme.colors.inkSoft.copy(alpha = 0.32f)

/** The whole chat screen: a slim header with a thin back arrow, the title, who is in it, and the content below. */
@Composable
internal fun ChatFrame(title: String, subtitle: String, people: List<String>, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = SibTheme.colors
    Column(Modifier.fillMaxSize().background(chatSurface()).windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 4.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(10.dp, 18.dp)) {
                    val p = Path().apply { moveTo(size.width - 1f, 1f); lineTo(1f, size.height / 2); lineTo(size.width - 1f, size.height - 1f) }
                    drawPath(p, c.ink, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            Column(Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
                Text(title, style = SibType.bodyStrong.copy(fontSize = 16.sp), color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = SibType.label, color = c.inkSoft)
            }
            Row(Modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                people.take(3).forEach { ChatAvatar(it) }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.inkSoft.copy(alpha = 0.14f)))
        Column(Modifier.weight(1f).fillMaxWidth().imePadding().padding(horizontal = 14.dp), content = content)
    }
}

/** An outlined circle with an initial. Same tone as the page. */
@Composable
internal fun ChatAvatar(name: String, size: Dp = 28.dp) {
    val c = SibTheme.colors
    Box(Modifier.size(size).clip(CircleShape).background(chatSurface()).border(1.dp, chatLine(), CircleShape), contentAlignment = Alignment.Center) {
        Text(name.trim().firstOrNull()?.uppercase() ?: "", style = SibType.label.copy(fontSize = 11.sp), color = c.inkSoft)
    }
}

/**
 * One message. [first] and [last] say where it sits in a run from the same person: the corners that touch their neighbour are
 * tightened, the name shows on the first, and the avatar and the time on the last. Your messages are outlined in violet, the
 * others in a neutral line; neither is filled in more than a faint wash.
 */
@Composable
internal fun ChatBubble(m: ChatMessage, first: Boolean, last: Boolean, showName: Boolean, time: String, onClick: () -> Unit) {
    val c = SibTheme.colors
    if (m.kind == MessageKind.SYSTEM) {
        Text(
            m.senderName + " " + m.text, style = SibType.label, color = c.inkSoft, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        )
        return
    }
    val mine = m.mine
    val big = 20.dp
    val small = 5.dp
    val shape = if (mine) RoundedCornerShape(big, if (first) big else small, if (last) big else small, big)
    else RoundedCornerShape(if (first) big else small, big, big, if (last) big else small)
    val fill = if (mine) c.violet.copy(alpha = 0.10f) else c.ink.copy(alpha = 0.04f)
    val edge = if (mine) c.violet.copy(alpha = 0.55f) else chatLine()
    val widest = LocalConfiguration.current.screenWidthDp.dp * 0.76f
    Row(
        Modifier.fillMaxWidth().padding(top = if (first) 12.dp else 0.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) {
            if (last) ChatAvatar(m.senderName) else Spacer(Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (showName) Text(m.senderName, style = SibType.label, color = c.inkSoft, modifier = Modifier.padding(start = 4.dp, bottom = 3.dp))
            Text(
                m.text, style = SibType.body.copy(fontSize = 15.sp, lineHeight = 21.sp), color = c.ink,
                modifier = Modifier.widthIn(max = widest).clip(shape).background(fill).border(1.dp, edge, shape)
                    .bounceClick(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 9.dp)
                    .semantics { contentDescription = (if (mine) "You" else m.senderName) + " said " + m.text + " at " + time + ". Tap for options." },
            )
            if (last) Text(time, style = SibType.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal), color = c.inkSoft.copy(alpha = 0.8f), modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp))
        }
    }
}

/** An outlined pill, nothing filled. The send arrow fades in only when there is something to send. */
@Composable
internal fun ChatComposer(text: String, onText: (String) -> Unit, onSend: () -> Unit) {
    val c = SibTheme.colors
    val canSend = text.isNotBlank()
    val visible = animateFloatAsState(if (canSend) 1f else 0f, tween(180), label = "send").value
    BasicTextField(
        value = text, onValueChange = onText,
        textStyle = SibType.body.copy(fontSize = 15.sp, color = c.ink), cursorBrush = SolidColor(c.violet), maxLines = 5,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics { contentDescription = "Message" },
        decorationBox = { inner ->
            val shape = RoundedCornerShape(24.dp)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape).border(1.dp, c.inkSoft.copy(alpha = 0.45f), shape).padding(start = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    if (text.isEmpty()) Text("Message", style = SibType.body.copy(fontSize = 15.sp), color = c.inkSoft)
                    inner()
                }
                Box(
                    Modifier.size(48.dp).alpha(visible).bounceClick(enabled = canSend, onClick = onSend).semantics { contentDescription = "Send" },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(20.dp)) {
                        val w = size.width
                        drawLine(c.violet, Offset(w / 2, w * 0.82f), Offset(w / 2, w * 0.2f), 3f, StrokeCap.Round)
                        val head = Path().apply { moveTo(w * 0.24f, w * 0.44f); lineTo(w / 2, w * 0.18f); lineTo(w * 0.76f, w * 0.44f) }
                        drawPath(head, c.violet, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        },
    )
}
