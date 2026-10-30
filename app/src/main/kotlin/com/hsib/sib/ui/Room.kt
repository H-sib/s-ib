package com.hsib.sib.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hsib.sib.Ready
import com.hsib.sib.core.social.ChatMessage
import com.hsib.sib.core.social.MessageKind
import com.hsib.sib.core.social.ReportReason
import com.hsib.sib.core.social.SocialLimits
import com.hsib.sib.domain.AppState
import com.hsib.sib.social.MemberView
import com.hsib.sib.social.SocialResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

// ---- the clipboard, kept here so no other screen needs to know -------------------------------------

/** Copies text. An invite code is marked sensitive so Android hides it from the clipboard preview. */
internal fun copyText(context: Context, label: String, text: String, sensitive: Boolean = false) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    if (sensitive) clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    manager.setPrimaryClip(clip)
}

internal fun pastedText(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}

// ---- the people on one shared deadline ----------------------------------------------------------------

@Composable
fun RoomScreen(obligationId: String, st: AppState, ready: Ready, onBack: () -> Unit, onChat: () -> Unit) {
    val social = ready.social
    val s = rememberSocial(ready)
    val c = SibTheme.colors
    val v = st.items.firstOrNull { it.o.id == obligationId }
    if (social == null || v == null) {
        Page("People", onBack) { Text("This deadline is not available for sharing.", style = SibType.body, color = c.inkSoft) }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ask = rememberRulesGate(social, s.termsAccepted)
    val room = s.roomFor(obligationId)
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<MemberView?>(null) }
    var confirmRemove by remember { mutableStateOf<MemberView?>(null) }
    var reporting by remember { mutableStateOf<MemberView?>(null) }
    var reportError by remember { mutableStateOf<String?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }

    Page(if (room == null || room.removed) "Share" else "People", onBack) {
        if (s.practice) PracticeBanner()
        Text(v.o.title, style = SibType.display, color = c.ink)
        Gap(20.dp)
        when {
            s.user == null -> SignInCard(social, headline = "Sign in to share")

            room == null -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(c.raised).padding(22.dp)) {
                Text("Add people", style = SibType.headline, color = c.ink)
                Gap(6.dp)
                Text("Everyone you add gets this deadline and its reminders, and a chat to sort it out.", style = SibType.body, color = c.inkSoft)
                Gap(20.dp)
                ActionButton(
                    if (busy) "Setting up" else "Share this deadline",
                    {
                        ask {
                            busy = true
                            error = null
                            scope.launch {
                                val r = social.share(obligationId)
                                if (r is SocialResult.Err) error = r.error.text
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                )
                error?.let { Gap(10.dp); Text(it, style = SibType.small, color = c.vermilion) }
                Gap(6.dp)
                More(
                    "Only the title, date, notes and chat are shared, and they are sealed on your phone, so only you and the people you invite can read them. Effort, importance and reminders stay your own.",
                    label = "What is shared?",
                )
            }

            room.removed -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(c.vermilionWash).padding(22.dp)) {
                Text("You are no longer in this", style = SibType.title, color = c.vermilion)
                Gap(6.dp)
                Text("It was deleted, or you were removed. The deadline stays on your list as your own.", style = SibType.body, color = c.ink)
                Gap(16.dp)
                SibButton("Keep it as mine", { scope.launch { social.dismissRemoved(room.roomId) } }, kind = ButtonKind.QUIET)
            }

            else -> {
                room.doneLine?.let { Chip(it, Tone.GOOD); Gap(14.dp) }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (m in room.members) MemberRow(m, onClick = if (m.isMe) null else ({ menu = m }))
                }
                if (!room.canRead) {
                    Gap(16.dp)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.ochreWash).padding(20.dp)) {
                        Text("This phone cannot read this yet", style = SibType.title, color = c.ochre)
                        Gap(4.dp)
                        Text("Open the app again in a moment. If it keeps happening, leave and ask for a new code.", style = SibType.body, color = c.ink)
                        Gap(12.dp)
                        SibButton("Try again", { scope.launch { social.syncOne(room.roomId) } }, kind = ButtonKind.QUIET)
                    }
                }
                Gap(20.dp)
                ActionButton(if (room.unread > 0) "Chat  ·  ${room.unread} new" else "Chat", onChat, Modifier.fillMaxWidth(), enabled = room.canRead)
                if (room.isOwner) {
                    Gap(12.dp)
                    SibButton(
                        if (code == null) "Invite someone" else "Make a new code",
                        {
                            ask {
                                busy = true
                                scope.launch {
                                    when (val r = social.invite(room.roomId)) {
                                        is SocialResult.Ok -> { code = r.value; error = null }
                                        is SocialResult.Err -> error = r.error.text
                                    }
                                    busy = false
                                }
                            }
                        },
                        Modifier.fillMaxWidth(), ButtonKind.QUIET, enabled = !busy && room.canRead,
                    )
                    code?.let { shown ->
                        Gap(14.dp)
                        InviteCard(shown, v.o.title, onCancel = { scope.launch { social.cancelInvites(room.roomId); code = null } })
                    }
                }
                error?.let { Gap(10.dp); Text(it, style = SibType.small, color = c.vermilion) }
                Gap(28.dp)
                SibButton(if (room.isOwner) "Delete for everyone" else "Leave", { confirmLeave = true }, Modifier.fillMaxWidth(), ButtonKind.DANGER)
            }
        }
        Gap(40.dp)
    }

    val roomNow = room
    menu?.let { m ->
        AlertDialog(
            onDismissRequest = { menu = null },
            containerColor = c.raised,
            title = { Row(verticalAlignment = Alignment.CenterVertically) { Avatar(m.name, m.uid, size = 40.dp); Spacer(Modifier.width(12.dp)); Text(m.name, style = SibType.title, color = c.ink) } },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SibButton("Report", { reporting = m; reportError = null; menu = null }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
                    SibButton(if (m.blocked) "Unblock" else "Block", { scope.launch { if (m.blocked) social.unblock(m.uid) else social.block(m.uid, m.name) }; menu = null }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
                    if (roomNow?.isOwner == true) SibButton("Remove from this deadline", { confirmRemove = m; menu = null }, Modifier.fillMaxWidth(), ButtonKind.DANGER)
                }
            },
            confirmButton = { TextButton({ menu = null }) { Text("Close", style = SibType.bodyStrong, color = c.ink) } },
        )
    }
    confirmRemove?.let { m ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            containerColor = c.raised,
            title = { Text("Remove ${m.name}?", style = SibType.title, color = c.ink) },
            text = { Text("They stop getting updates and can no longer read the chat. They keep what they already saw.", style = SibType.body, color = c.inkSoft) },
            confirmButton = {
                TextButton({
                    confirmRemove = null
                    scope.launch { roomNow?.let { r -> val res = social.removeMember(r.roomId, m.uid); if (res is SocialResult.Err) error = res.error.text } }
                }) { Text("Remove", style = SibType.bodyStrong, color = c.vermilion) }
            },
            dismissButton = { TextButton({ confirmRemove = null }) { Text("Keep", style = SibType.body, color = c.ink) } },
        )
    }
    reporting?.let { m ->
        ReportDialog(
            who = m.name, hasMessage = false, error = reportError,
            onSend = { reason, detail, _, alsoBlock ->
                scope.launch {
                    val r = roomNow?.let { social.report(it.roomId, m.uid, null, reason, detail, false) }
                    if (r is SocialResult.Err) {
                        reportError = r.error.text
                    } else {
                        if (alsoBlock) social.block(m.uid, m.name)
                        Toast.makeText(context, "Report sent. Thank you.", Toast.LENGTH_LONG).show()
                        reporting = null
                    }
                }
            },
            onClose = { reporting = null },
        )
    }
    if (confirmLeave && roomNow != null) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = c.raised,
            title = { Text(if (roomNow.isOwner) "Delete for everyone?" else "Leave this deadline?", style = SibType.title, color = c.ink) },
            text = {
                Text(
                    if (roomNow.isOwner) "The shared deadline and its chat are deleted for everyone. The deadline stays on your list as your own."
                    else "You stop getting updates and the chat. The deadline stays on your list as your own.",
                    style = SibType.body, color = c.inkSoft,
                )
            },
            confirmButton = {
                TextButton({
                    confirmLeave = false
                    scope.launch { val r = social.leaveOrDelete(roomNow.roomId); if (r is SocialResult.Err) error = r.error.text }
                }) { Text(if (roomNow.isOwner) "Delete" else "Leave", style = SibType.bodyStrong, color = c.vermilion) }
            },
            dismissButton = { TextButton({ confirmLeave = false }) { Text("Stay", style = SibType.body, color = c.ink) } },
        )
    }
}

@Composable
private fun MemberRow(m: MemberView, onClick: (() -> Unit)?) {
    val c = SibTheme.colors
    val words = buildString {
        append(m.name)
        if (m.isMe) append(", you")
        if (m.isOwner) append(", owner")
        if (m.done) append(", finished")
        if (m.blocked) append(", blocked")
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised)
            .then(if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp).semantics(mergeDescendants = true) { contentDescription = words },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(m.name, m.uid)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (m.isMe) "${m.name} (you)" else m.name, style = SibType.title, color = c.ink)
            if (m.blocked) Text("Blocked", style = SibType.small, color = c.vermilion)
        }
        if (m.isOwner) { Chip("Owner"); Spacer(Modifier.width(8.dp)) }
        if (m.done) Chip("Done", Tone.GOOD)
    }
}

/** The code, large enough to read out, with the two things people do with it. */
@Composable
private fun InviteCard(code: String, title: String, onCancel: () -> Unit) {
    val c = SibTheme.colors
    val context = LocalContext.current
    val groups = code.removePrefix("SIB-").split("-")
    val lines = groups.chunked(4).mapIndexed { i, g -> (if (i == 0) "SIB-" else "") + g.joinToString("-") }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(c.raised).padding(22.dp)) {
        Text("Invite code", style = SibType.bodyStrong, color = c.ink)
        Gap(12.dp)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.lifted).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (line in lines) Text(line, style = SibType.title.copy(fontFamily = FontFamily.Monospace), color = c.ink)
        }
        Gap(16.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SibButton(
                "Send",
                {
                    val message = "Join my deadline \"$title\" in H-sib Reminder.\n\nIn H-sib Reminder open Friends, then Join with a code, and paste this:\n$code"
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message), "Send the code"))
                },
                Modifier.weight(1f),
            )
            SibButton("Copy", { copyText(context, "H-sib Reminder invite", code, sensitive = true) }, Modifier.weight(1f), ButtonKind.QUIET)
        }
        Gap(4.dp)
        More("The code works for about a week and only for people who have it. If you sent it to the wrong person, cancel it and make a new one.", label = "Good to know")
        // The text button carries its own side padding, so it is pulled back to line up with the text above it.
        SibButton("Cancel invites", onCancel, Modifier.offset(x = (-8).dp), ButtonKind.TEXT)
    }
}

// ---- reporting -----------------------------------------------------------------------------------------

/** Asks why, lets the person add a word, and (for a message) whether to include its text. Sends to the developer. */
@Composable
fun ReportDialog(
    who: String,
    hasMessage: Boolean,
    error: String?,
    onSend: (reason: ReportReason, detail: String, includeText: Boolean, alsoBlock: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val c = SibTheme.colors
    var reason by remember { mutableStateOf(ReportReason.HARASSMENT) }
    var detail by remember { mutableStateOf("") }
    var includeText by remember { mutableStateOf(true) }
    var alsoBlock by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = c.raised,
        title = { Text("Report $who", style = SibType.title, color = c.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ReportFields(who, hasMessage, error, reason, { reason = it }, detail, { detail = it }, includeText, { includeText = it }, alsoBlock, { alsoBlock = it })
            }
        },
        confirmButton = { TextButton({ onSend(reason, detail, includeText && hasMessage, alsoBlock) }) { Text("Send report", style = SibType.bodyStrong, color = c.vermilion) } },
        dismissButton = { TextButton(onClose) { Text("Cancel", style = SibType.body, color = c.ink) } },
    )
}

/** The questions of a report, apart from the dialog around them, so the form can be shown and tested on its own. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReportFields(
    who: String,
    hasMessage: Boolean,
    error: String?,
    reason: ReportReason,
    onReason: (ReportReason) -> Unit,
    detail: String,
    onDetail: (String) -> Unit,
    includeText: Boolean,
    onIncludeText: (Boolean) -> Unit,
    alsoBlock: Boolean,
    onAlsoBlock: (Boolean) -> Unit,
) {
    val c = SibTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in ReportReason.values()) SelectChip(r.label, r == reason, { onReason(r) })
        }
        Field(
            "Anything to add? (optional)", detail, { onDetail(it.take(SocialLimits.REPORT_DETAIL_CHARS)) },
            singleLine = false, minLines = 2, keyboard = KeyboardOptions(imeAction = ImeAction.Default),
        )
        if (hasMessage) ToggleRow("Include the message", "Chat is sealed, so the developer can only read it if you send it.", includeText, onIncludeText)
        ToggleRow("Also block $who", null, alsoBlock, onAlsoBlock)
        error?.let { Text(it, style = SibType.small, color = c.vermilion) }
    }
}

// ---- the conversation ------------------------------------------------------------------------------------

@Composable
fun ChatScreen(obligationId: String, st: AppState, ready: Ready, onBack: () -> Unit) {
    val social = ready.social
    val s = rememberSocial(ready)
    val c = SibTheme.colors
    val fmt = rememberFmt()
    val room = s.roomFor(obligationId)
    val v = st.items.firstOrNull { it.o.id == obligationId }
    if (social == null || room == null || v == null) {
        Page("Chat", onBack) { Text("This conversation is not available.", style = SibType.body, color = c.inkSoft) }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val roomId = room.roomId
    val ask = rememberRulesGate(social, s.termsAccepted)
    val messages by remember(roomId) { social.chat(roomId) }.collectAsState(emptyList())
    var text by remember(roomId) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var acting by remember { mutableStateOf<ChatMessage?>(null) }
    var reporting by remember { mutableStateOf<ChatMessage?>(null) }
    var reportError by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // No instant delivery yet: while the chat is open it asks for new messages every few seconds, and for people and
    // changes about once a minute.
    LaunchedEffect(roomId) {
        social.syncOne(roomId)
        var round = 0
        while (true) {
            delay(6_000)
            round++
            if (round % 10 == 0) social.syncOne(roomId) else social.refreshChat(roomId)
        }
    }
    LaunchedEffect(messages.size) {
        social.markRead(roomId)
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    val send = {
        val draft = text
        ask {
            scope.launch {
                when (val r = social.send(roomId, draft)) {
                    is SocialResult.Ok -> { if (text == draft) text = ""; error = null }
                    is SocialResult.Err -> error = r.error.text
                }
            }
        }
    }

    ChatFrame(v.o.title, if (room.members.size == 1) "1 person" else "${room.members.size} people", room.members.map { it.name }, onBack) {
        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No messages yet." + "\n" + "Say hello.", style = SibType.small, color = c.inkSoft, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                itemsIndexed(messages, key = { _, m -> m.id }) { i, m ->
                    val day = Instant.ofEpochMilli(m.atMs).atZone(st.zone).toLocalDate()
                    val previous = messages.getOrNull(i - 1)
                    val next = messages.getOrNull(i + 1)
                    val newDay = previous == null || Instant.ofEpochMilli(previous.atMs).atZone(st.zone).toLocalDate() != day
                    val endsDay = next == null || Instant.ofEpochMilli(next.atMs).atZone(st.zone).toLocalDate() != day
                    if (newDay) {
                        Text(fmt.dayShort(day), style = SibType.label, color = c.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp))
                    }
                    val joinsPrevious = !newDay && previous != null && previous.kind == MessageKind.TEXT && m.kind == MessageKind.TEXT && previous.senderUid == m.senderUid
                    val joinsNext = !endsDay && next != null && next.kind == MessageKind.TEXT && m.kind == MessageKind.TEXT && next.senderUid == m.senderUid
                    ChatBubble(
                        m, first = !joinsPrevious, last = !joinsNext, showName = !m.mine && m.kind == MessageKind.TEXT && !joinsPrevious,
                        time = fmt.time(Instant.ofEpochMilli(m.atMs).atZone(st.zone).toLocalTime()),
                        onClick = { if (m.kind == MessageKind.TEXT) acting = m },
                    )
                }
            }
        }
        if (room.removed) {
            Text("You are no longer in this chat.", style = SibType.body, color = c.vermilion, modifier = Modifier.padding(vertical = 16.dp))
        } else {
            error?.let { Text(it, style = SibType.small, color = c.vermilion, modifier = Modifier.padding(bottom = 4.dp)) }
            ChatComposer(text, { text = it.take(SocialLimits.MESSAGE_CHARS) }, { send() })
        }
    }

    acting?.let { m ->
        AlertDialog(
            onDismissRequest = { acting = null },
            containerColor = c.raised,
            title = { Text(if (m.mine) "Your message" else m.senderName, style = SibType.title, color = c.ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (m.mine) {
                        SibButton("Delete for everyone", { scope.launch { social.deleteMessage(roomId, m.id) }; acting = null }, Modifier.fillMaxWidth(), ButtonKind.DANGER)
                    } else {
                        SibButton("Report", { reporting = m; reportError = null; acting = null }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
                        SibButton("Block ${m.senderName}", { scope.launch { social.block(m.senderUid, m.senderName) }; acting = null }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
                    }
                }
            },
            confirmButton = { TextButton({ acting = null }) { Text("Close", style = SibType.bodyStrong, color = c.ink) } },
        )
    }
    reporting?.let { m ->
        ReportDialog(
            who = m.senderName, hasMessage = true, error = reportError,
            onSend = { reason, detail, includeText, alsoBlock ->
                scope.launch {
                    val r = social.report(roomId, m.senderUid, m.id, reason, detail, includeText)
                    if (r is SocialResult.Err) {
                        reportError = r.error.text
                    } else {
                        if (alsoBlock) social.block(m.senderUid, m.senderName)
                        Toast.makeText(context, "Report sent. Thank you.", Toast.LENGTH_LONG).show()
                        reporting = null
                    }
                }
            },
            onClose = { reporting = null },
        )
    }
}

// ---- joining with a code ----------------------------------------------------------------------------------

@Composable
fun JoinScreen(ready: Ready, onBack: () -> Unit, onJoined: (String) -> Unit) {
    val social = ready.social
    val s = rememberSocial(ready)
    val c = SibTheme.colors
    if (social == null) {
        Page("Join", onBack) { Text("Sharing is not available in this version.", style = SibType.body, color = c.inkSoft) }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ask = rememberRulesGate(social, s.termsAccepted)
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Page("Join", onBack) {
        if (s.practice) PracticeBanner()
        Text("Got a code?", style = SibType.display, color = c.ink)
        Gap(6.dp)
        Text("Paste the code a friend sent you. It starts with SIB.", style = SibType.body, color = c.inkSoft)
        Gap(24.dp)
        if (s.user == null) {
            SignInCard(social, headline = "Sign in first")
            return@Page
        }
        Field(
            "Code", code, { code = it.take(120); error = null }, singleLine = false, minLines = 2, mono = true,
            placeholder = "SIB-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX", keyboard = KeyboardOptions(imeAction = ImeAction.Default),
        )
        Spacer(Modifier.size(10.dp))
        SibButton("Paste", { pastedText(context)?.let { code = it.take(120); error = null } }, kind = ButtonKind.QUIET)
        error?.let { Gap(12.dp); Text(it, style = SibType.small, color = c.vermilion) }
        Gap(24.dp)
        ActionButton(
            if (busy) "Joining" else "Join",
            {
                ask {
                    busy = true
                    error = null
                    scope.launch {
                        when (val r = social.join(code)) {
                            is SocialResult.Ok -> onJoined(r.value)
                            is SocialResult.Err -> error = r.error.text
                        }
                        busy = false
                    }
                }
            },
            Modifier.fillMaxWidth(), enabled = code.isNotBlank() && !busy,
        )
        Gap(40.dp)
    }
}
