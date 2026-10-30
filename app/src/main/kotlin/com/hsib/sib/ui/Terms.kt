package com.hsib.sib.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * What a person agrees to before the app does anything. Bump [VERSION] when the meaning changes and everyone is asked again.
 * The words are written to be true of this build: [measures] and [network] describe what the code really does.
 */
object Terms {
    const val VERSION = 1

    /** Three cards: the things worth knowing before the first deadline is added. */
    val facts = listOf(
        "Your data stays here" to "Deadlines are kept on this phone, locked with a key only this phone holds. No account is needed.",
        "It reads only what you hand it" to "Text, photos and files you share or paste. Chats and notifications only if you switch that on.",
        "Dates are suggestions" to "Nothing becomes a deadline until you confirm it. Check important dates yourself.",
    )

    fun measures(online: Boolean): List<String> = listOf(
        "Storage is an encrypted database. Its key is wrapped by the Android Keystore and cannot be copied off the phone.",
        "Photos kept as proof are encrypted separately, with their own key.",
        "Backups are encrypted on the phone before they are saved, with a recovery key only you hold. Anyone who changes or deletes a backup file is detected.",
        "There is no advertising, no analytics and no tracking.",
        "Android's own app backup is switched off, so nothing is copied to a cloud behind your back.",
        "Reminders hide their titles on the lock screen until you choose otherwise. Close ones always hide them.",
        "An optional app lock uses your fingerprint, face or screen lock. An optional switch blocks screenshots.",
        "Reading notifications needs two steps from you, ignores every app you did not tick, and keeps none of the text.",
        if (online) "The internet is used only if you sign in to share a deadline. What you share is sealed on your phone first, so the server cannot read it."
        else "This version asks for no internet permission, so it cannot send anything by itself.",
    )

    val terms = listOf(
        "The app helps you plan. It does not promise that a date it finds is right or that a reminder will always arrive. Reminders depend on your phone's battery and notification settings, and the app has a check for that.",
        "You are responsible for your deadlines and for what you share.",
        "Your backup is only as safe as your recovery key. If you lose both your phone and the key, nobody can recover it.",
        "The app is provided as it is, without a warranty, as far as the law allows.",
        "You must be 13 or older. Sharing with friends has its own community rules, which you accept the first time you use it.",
        "If these terms change in a way that matters, you will be asked again.",
        "The full terms of use and the privacy policy are online and linked from About. Tapping I accept means you agree to them, and the app keeps a record of when you did.",
    )
}

/**
 * The first thing a new person sees after the opening, before anything is stored: three plain facts, the full list of
 * security measures and the terms one tap away, and an explicit "I accept". Also shown, read only, from Settings.
 */
@Composable
fun TermsScreen(online: Boolean, acceptedOn: String?, onAccept: (() -> Unit)?, onBack: (() -> Unit)?) {
    val c = SibTheme.colors
    var read by rememberSaveable { mutableStateOf(false) }
    val body: @Composable () -> Unit = {
        Text(if (onAccept != null) "Before you start" else "Terms and security", style = SibType.display, color = c.ink)
        Gap(8.dp)
        Text(if (onAccept != null) "Three things to know." else "What you agreed to, and how your data is protected.", style = SibType.body, color = c.inkSoft)
        Gap(24.dp)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((title, line) in Terms.facts) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised).padding(20.dp)) {
                    Text(title, style = SibType.title, color = c.ink)
                    Gap(4.dp)
                    Text(line, style = SibType.body, color = c.inkSoft)
                }
            }
        }
        Gap(8.dp)
        More(Terms.measures(online).joinToString("\n\n"), label = "All security measures")
        More(Terms.terms.joinToString("\n\n"), label = "Terms of use")
        if (acceptedOn != null) {
            Gap(12.dp)
            Text("Accepted on $acceptedOn.", style = SibType.small, color = c.inkSoft)
        }
    }
    if (onAccept == null) {
        Page("Terms and security", onBack) { body(); Gap(40.dp) }
        return
    }
    Column(Modifier.fillMaxSize().background(c.paper).windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
        Column(Modifier.weight(1f).fillMaxWidth().cuedVerticalScroll().padding(horizontal = 28.dp)) {
            Gap(32.dp)
            body()
            Gap(24.dp)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp))
                    .clickable(role = Role.Checkbox) { read = !read }
                    .semantics { stateDescription = if (read) "Accepted" else "Not accepted" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(if (read) c.action else c.lifted),
                    contentAlignment = Alignment.Center,
                ) {
                    if (read) Canvas(Modifier.size(16.dp)) {
                        val w = size.width
                        drawLine(c.onAction, Offset(w * 0.1f, w * 0.55f), Offset(w * 0.4f, w * 0.85f), 3.5f, StrokeCap.Round)
                        drawLine(c.onAction, Offset(w * 0.4f, w * 0.85f), Offset(w * 0.92f, w * 0.15f), 3.5f, StrokeCap.Round)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Text("I have read this and accept the terms.", style = SibType.body, color = c.ink, modifier = Modifier.weight(1f))
            }
            Gap(10.dp)
            ActionButton("Accept and start", { onAccept() }, Modifier.fillMaxWidth(), enabled = read)
        }
    }
}
