package com.hsib.sib.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hsib.sib.Ready
import com.hsib.sib.core.social.CommunityRules
import com.hsib.sib.core.social.SocialLimits
import com.hsib.sib.domain.AppState
import com.hsib.sib.social.SignInResult
import com.hsib.sib.social.SocialRepository
import com.hsib.sib.social.SocialResult
import com.hsib.sib.social.SocialState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** What the screens watch for sharing. When this build has no sharing it is the empty "off" state. */
@Composable
fun rememberSocial(ready: Ready): SocialState {
    val flow = remember(ready) { ready.social?.state ?: MutableStateFlow(SocialState.Off) }
    val state by flow.collectAsState()
    return state
}

/**
 * A round initial in one of four colours, chosen from the person's id so it stays the same everywhere. None of them is the
 * alert red, which is kept for things that need attention.
 */
@Composable
fun Avatar(name: String, key: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val c = SibTheme.colors
    val (bg, fg) = when (Math.floorMod(key.hashCode(), 4)) {
        0 -> c.violetWash to c.violet
        1 -> c.verdigrisWash to c.verdigris
        2 -> c.ochreWash to c.ochre
        else -> c.lifted to c.ink
    }
    val trimmed = name.trim()
    val initial = if (trimmed.isEmpty()) "?" else String(Character.toChars(trimmed.codePointAt(0))).uppercase()
    Box(modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(initial, style = SibType.title, color = fg)
    }
}

/** The short rules everyone agrees to before sharing or chatting. With [onAgree] null it is just a reminder. */
@Composable
fun RulesDialog(onAgree: (() -> Unit)?, onClose: () -> Unit) {
    val c = SibTheme.colors
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = c.raised,
        title = { Text("Community rules", style = SibType.title, color = c.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for (rule in CommunityRules.rules) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 9.dp).size(6.dp).clip(CircleShape).background(c.violet))
                        Spacer(Modifier.width(12.dp))
                        Text(rule, style = SibType.body, color = c.ink)
                    }
                }
            }
        },
        confirmButton = { TextButton(onAgree ?: onClose) { Text(if (onAgree != null) "I agree" else "Close", style = SibType.bodyStrong, color = c.ink) } },
        dismissButton = { if (onAgree != null) TextButton(onClose) { Text("Not now", style = SibType.body, color = c.inkSoft) } },
    )
}

/**
 * Wraps an action that needs the community rules to have been accepted. The first time it shows the rules; after the
 * person agrees, the action runs. Afterwards it simply runs.
 */
@Composable
fun rememberRulesGate(social: SocialRepository, accepted: Boolean): (() -> Unit) -> Unit {
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val scope = rememberCoroutineScope()
    pending?.let { action ->
        RulesDialog(onAgree = { pending = null; scope.launch { social.acceptTerms(); action() } }, onClose = { pending = null })
    }
    return { action -> if (accepted) { action() } else { pending = action } }
}

@Composable
fun PracticeBanner() {
    val c = SibTheme.colors
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(20.dp)).background(c.ochreWash).padding(16.dp)) {
        Text("Practice mode", style = SibType.bodyStrong, color = c.ochre)
        Text("Nothing leaves this phone and nothing is kept after you close the app. Real sharing is not connected yet.", style = SibType.small, color = c.ink)
    }
}

/** Asks the person to sign in with Google, with the reassurance that the rest of the app does not need it. */
@Composable
fun SignInCard(social: SocialRepository, modifier: Modifier = Modifier, headline: String = "Share a deadline") {
    val c = SibTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(c.raised).padding(22.dp)) {
        Text(headline, style = SibType.headline, color = c.ink)
        Gap(6.dp)
        Text("Add friends so everyone is reminded, and chat about it.", style = SibType.body, color = c.inkSoft)
        Gap(20.dp)
        ActionButton(
            if (busy) "Signing in" else "Continue with Google",
            {
                scope.launch {
                    busy = true
                    error = null
                    val result = social.signIn(context)
                    if (result is SignInResult.Failed) error = result.error.text
                    busy = false
                }
            },
            enabled = !busy,
        )
        error?.let { Gap(10.dp); Text(it, style = SibType.small, color = c.vermilion) }
        Gap(6.dp)
        More(
            "Signing in is optional. Without it everything else works exactly as before, offline. Titles, dates, notes and chat are sealed on your phone before they are sent, so the server cannot read them.",
            label = "What does signing in change?",
        )
    }
}

/** The second layer for friends: who you are, how to join, and everything you share. */
@Composable
fun FriendsScreen(st: AppState, ready: Ready, onBack: () -> Unit, onOpenRoom: (String) -> Unit, onJoin: () -> Unit, onAccount: () -> Unit) {
    val social = ready.social
    val s = rememberSocial(ready)
    val c = SibTheme.colors
    Page("Friends", onBack) {
        if (social == null) {
            Text("Sharing is not available in this version.", style = SibType.body, color = c.inkSoft)
            return@Page
        }
        if (s.practice) PracticeBanner()
        if (s.user == null) {
            SignInCard(social)
            return@Page
        }
        // Intrinsic height keeps both tiles the same size even when one label wraps onto two lines.
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile("Join with a code", TileIcon.ADD, onJoin, Modifier.weight(1f).fillMaxHeight(), accent = true)
            ActionTile("Account", TileIcon.PERSON, onAccount, Modifier.weight(1f).fillMaxHeight())
        }
        val rooms = s.rooms.values.sortedByDescending { it.unread }
        SectionHeader("Shared deadlines", count = rooms.size.takeIf { it > 0 })
        if (rooms.isEmpty()) {
            Text("Nothing shared yet. Open a deadline and tap People.", style = SibType.body, color = c.inkSoft)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (room in rooms) {
                    val title = st.items.firstOrNull { it.o.id == room.obligationId }?.o?.title ?: "Shared deadline"
                    val people = if (room.members.size == 1) "Only you" else "${room.members.size} people"
                    val sub = when {
                        room.removed -> "You are no longer in this"
                        room.unread > 0 -> "$people  ·  ${room.unread} new"
                        else -> people
                    }
                    NavRow(title, sub, { onOpenRoom(room.obligationId) })
                }
            }
        }
        Gap(40.dp)
    }
}

/** Name, blocked people, the rules, signing out and deleting the account. */
@Composable
fun AccountScreen(ready: Ready, onBack: () -> Unit, onSignedOut: () -> Unit) {
    val social = ready.social
    val s = rememberSocial(ready)
    val c = SibTheme.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val user = s.user
    var name by remember(user?.uid) { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(user?.uid) { if (social != null && user != null) name = social.displayName() }

    Page("Account", onBack) {
        if (social == null || user == null) {
            if (social != null) SignInCard(social)
            return@Page
        }
        if (s.practice) PracticeBanner()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name.ifBlank { user.displayName }, user.uid, size = 56.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(user.displayName, style = SibType.title, color = c.ink)
                user.email?.let { Text(it, style = SibType.small, color = c.inkSoft) }
            }
        }
        Gap(28.dp)
        Field("Name friends see", name, { name = it.take(SocialLimits.DISPLAY_NAME_CHARS); saved = false })
        Spacer(Modifier.size(10.dp))
        SibButton(
            if (saved) "Saved" else "Save name",
            {
                scope.launch {
                    when (val r = social.setDisplayName(name)) {
                        is SocialResult.Ok -> { saved = true; error = null }
                        is SocialResult.Err -> error = r.error.text
                    }
                }
            },
            kind = ButtonKind.QUIET, enabled = name.isNotBlank() && !saved,
        )
        error?.let { Spacer(Modifier.size(8.dp)); Text(it, style = SibType.small, color = c.vermilion) }

        Group("Blocked people") {
            if (s.blocked.isEmpty()) {
                TextItem("Nobody. You can block someone from a chat or the people list.")
            } else {
                s.blocked.forEachIndexed { i, b ->
                    if (i > 0) GroupDivider()
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(b.name, style = SibType.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
                        SibButton("Unblock", { scope.launch { social.unblock(b.uid) } }, kind = ButtonKind.QUIET, compact = true)
                    }
                }
            }
        }
        Group("Chat") { NavItem("Community rules", "What everyone agrees to", { showRules = true }) }
        Gap(32.dp)
        SibButton("Sign out", { scope.launch { social.signOut(); onSignedOut() } }, Modifier.fillMaxWidth(), ButtonKind.QUIET)
        Spacer(Modifier.size(8.dp))
        SibButton("Delete my account", { confirmDelete = true }, Modifier.fillMaxWidth(), ButtonKind.DANGER)
        Gap(40.dp)
    }

    if (showRules) RulesDialog(onAgree = null, onClose = { showRules = false })
    if (confirmDelete && social != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.raised,
            title = { Text("Delete your account?", style = SibType.title, color = c.ink) },
            text = {
                Text(
                    "Your messages are removed, you leave every shared deadline, and shared deadlines you own are deleted for everyone. The deadlines on this phone stay, as ordinary private ones.",
                    style = SibType.body, color = c.inkSoft,
                )
            },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    scope.launch {
                        when (val r = social.deleteAccount(context)) {
                            is SocialResult.Ok -> onSignedOut()
                            is SocialResult.Err -> error = r.error.text
                        }
                    }
                }) { Text("Delete", style = SibType.bodyStrong, color = c.vermilion) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Keep it", style = SibType.body, color = c.ink) } },
        )
    }
}
