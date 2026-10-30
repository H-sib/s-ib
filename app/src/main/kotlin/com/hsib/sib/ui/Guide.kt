package com.hsib.sib.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One thing the app can do, explained in a few short steps. Every feature has one, so nothing has to be found by accident. */
data class GuideTopic(val title: String, val line: String, val steps: List<String>)

object Guide {
    fun topics(online: Boolean): List<GuideTopic> = buildList {
        add(GuideTopic("Add a deadline", "Four ways in, all read on this phone.", listOf(
            "Tap Add and type or paste a sentence such as: Report due 18 Nov, 11:59 PM, about 6 hours.",
            "Share a message, screenshot, photo or PDF to the app from any other app.",
            "Use Photo or Scan for a notice on paper.",
            "Check what was understood, change anything, and save.",
        )))
        add(GuideTopic("Review suggestions", "Nothing becomes a deadline until you say yes.", listOf(
            "Dates found in what you shared wait in Review.",
            "Each one shows the sentence it came from and why it was chosen. Tap Check this for doubts.",
            "Confirm, change, snooze for three days, or dismiss.",
        )))
        add(GuideTopic("The plan", "It tells you when to begin and when it does not fit.", listOf(
            "Latest safe start is the last day you can begin and still finish comfortably, even if the job runs long.",
            "The load chart spreads the work over the days you have. Each week is separated.",
            "If it does not fit, Options shows what would help: more time, a smaller job, or a later date.",
            "Set your time in Settings, Reminders.",
        )))
        add(GuideTopic("Reminders", "A few, at sensible times, never in quiet hours.", listOf(
            "Choose a daily budget and quiet hours in Settings, Reminders.",
            "Open Reminder health check if a reminder ever comes late. Some phones hold back apps to save battery.",
            "Done on a deadline can keep a note or a photo as proof.",
        )))
        add(GuideTopic("Close ones", "A private space for the dates that matter most.", listOf(
            "Anniversaries, birthdays and date nights live apart, with their own colours.",
            "Their reminders never show details on the lock screen, and they are never shared or put in a calendar file.",
            "Turn the space off in Settings, Close ones.",
        )))
        add(GuideTopic("Chats and notifications", "Find deadlines in your messages, only if you ask.", listOf(
            "Open Settings, Chats and notifications and read what it does.",
            "Allow access in Android's list, then tick the apps to read.",
            "Notification text is read on the phone and not kept. Suggestions still wait in Review.",
        )))
        add(GuideTopic("Card, widget and tile", "Your next deadline without opening the app.", listOf(
            "Notification bar card: a colourful card with a ring and the next deadlines, and Open and Done. It is on by default and changes colour as the date nears. Settings lets you turn it off or keep it after Clear all.",
            "Widget: long-press the home screen, choose Widgets, add Next up.",
            "Tile: pull the shade down twice, tap the pencil, drag the tile into your panel.",
        )))
        add(GuideTopic("Back up and restore", "Encrypted before it leaves the phone.", listOf(
            "Settings, Backup and restore: choose a folder, for example in Google Drive.",
            "Write down the recovery key. Without it a backup cannot be opened.",
            "On a new phone choose Restore, pick the folder and type the key.",
        )))
        if (online) {
            add(GuideTopic("Share with friends", "Reminders and a chat for everyone you add.", listOf(
                "Open Friends and sign in with Google. It is optional.",
                "Open a deadline, tap People, and share it. Send the invite code to friends.",
                "Friends tap Join with a code. Each person keeps their own plan and reminders.",
                "Only the owner changes the title and date. Everyone can chat, block and report.",
            )))
        } else {
            add(GuideTopic("Send to a friend", "A calendar file anyone can open.", listOf(
                "Open a deadline and tap Send a calendar file.",
                "Choose any app. The file holds the title and date only.",
            )))
        }
        add(GuideTopic("Looks and motion", "Four themes, and calm if you want it.", listOf(
            "Settings, Appearance: Light, Twilight, Black and Neon.",
            "Reduce motion skips animations. Intro speed sets how long the opening plays.",
        )))
    }
}

/** The Guide: a list of topics, then one topic at a time. Reached from Settings. */
@Composable
fun GuideScreen(online: Boolean, onBack: () -> Unit) {
    val c = SibTheme.colors
    val topics = Guide.topics(online)
    var open by rememberSaveable { mutableIntStateOf(-1) }
    val topic = topics.getOrNull(open)
    if (topic == null) {
        Page("Guide", onBack) {
            Text("Everything the app can do, in a few lines each.", style = SibType.body, color = c.inkSoft)
            Gap(20.dp)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                topics.forEachIndexed { i, t -> NavRow(t.title, t.line, { open = i }) }
            }
            Gap(40.dp)
        }
    } else {
        Page(topic.title, { open = -1 }) {
            Text(topic.line, style = SibType.headline, color = c.ink)
            Gap(20.dp)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                topic.steps.forEachIndexed { i, step ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.raised).padding(18.dp), verticalAlignment = Alignment.Top) {
                        Text("${i + 1}", style = SibType.number.copy(fontSize = 22.sp), color = c.violet, modifier = Modifier.padding(end = 16.dp))
                        Text(step, style = SibType.body, color = c.ink, modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.size(40.dp))
        }
    }
}
