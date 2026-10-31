package com.hsib.sib.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * What changed in each release, biggest first, in plain words. Every release that raises versionCode adds an entry here;
 * tools/consistency-check.py fails the release if the current versionCode has none. A new install sees nothing from this.
 */
object WhatsNew {
    data class Entry(val versionCode: Int, val name: String, val items: List<String>)

    val entries = listOf(
        Entry(
            6, "1.3.0",
            listOf(
                "Four new colour themes: Cosmos, Dune, Forest and Ocean. Choose a language and an easy mode that keeps only the basics.",
                "Your steps are yours to edit, and the Plan card now lets you change what it suggests.",
                "The app can greet you by name, and a small hint offers help when you seem stuck. Both can be turned off.",
                "A focus timer, a small celebration when you finish, search in All deadlines, and backups that now include your class routine.",
                "Hide any part of Today you do not need.",
            ),
        ),
        Entry(
            5, "1.2.0",
            listOf(
                "Make it yours: set the app up for your study, your work or your daily life. Edit it, save it and share it with friends.",
                "Find a friend by username and chat first. They accept before anything is shared.",
                "Own plan, your own hours, four Today styles and a light daily line to get you going.",
                "A weekly class table for medical students, checklists of up to 50 items, and calendar reading.",
                "A livelier look on Plan, Review, Messages and the Guide.",
            ),
        ),
        Entry(
            4, "1.1.1",
            listOf(
                "A weekly class routine for medical students, Saturday to Thursday, with colours, rooms and times.",
                "A light line to get you going, written for the kind of deadline you have. Turn it off any time in Settings, Motivation.",
                "Checklists now hold up to 50 items with your own names, and times move an hour at a time.",
                "Optional: read Samsung or Google Calendar entries into Review, only with your permission.",
                "One Plan section where you choose your own hours.",
            ),
        ),
        Entry(
            3, "1.1.0",
            listOf(
                "A version for medical students: exam types by prof, subjects, study steps, a class diary and its own colours. Turn it on in Settings.",
                "You choose the hours, even 48, and you can plan each day yourself.",
                "Four ways to see your deadlines on Today: Focus, Swipe, Stack and Collage.",
                "A Messages tab, a clearer month calendar and a small eye next to each feature that explains it.",
                "Attach files, speak a deadline, and turn an exam timetable into one deadline or many.",
            ),
        ),
        Entry(
            2, "1.0.1",
            listOf(
                "A month view in Plan marks your deadlines on a calendar.",
                "Make a checklist inside one deadline, such as 7 assignments, and watch the progress bar fill as you tick them off.",
                "Today now shows your next few deadlines, not only one.",
                "Clearer words when there is not enough time, and for hours done.",
                "Friend is now a choice in Close ones.",
            ),
        ),
        Entry(
            1, "1.0",
            listOf(
                "Share a message, photo or file and the deadline is found for you.",
                "A plan that says when to start, and tells you when it does not fit.",
                "Four themes, a card for the notification bar, a widget and a tile.",
            ),
        ),
    )

    /** The entries a person has not seen yet, newest first. */
    fun unseen(lastSeen: Int, current: Int): List<Entry> = entries.filter { it.versionCode in (lastSeen + 1)..current }.sortedByDescending { it.versionCode }
}

/** Shown once on Today after an update. */
@Composable
fun WhatsNewCard(entries: List<WhatsNew.Entry>, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = SibTheme.colors
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(c.raised).padding(22.dp)) {
        for (e in entries) {
            Text("New in ${e.name}", style = SibType.title, color = c.ink)
            Gap(8.dp)
            for (item in e.items) {
                Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.Top) {
                    Text("•", style = SibType.body, color = c.violet, modifier = Modifier.padding(end = 10.dp))
                    Text(item, style = SibType.body, color = c.inkSoft)
                }
            }
            Gap(8.dp)
        }
        SibButton("Got it", onDismiss, kind = ButtonKind.QUIET)
    }
}
