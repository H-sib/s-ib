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
