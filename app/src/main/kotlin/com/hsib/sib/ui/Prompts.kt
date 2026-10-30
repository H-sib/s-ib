package com.hsib.sib.ui

import com.hsib.sib.platform.DashboardArt
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** What the owner chose on a recommendation card. */
enum class Prompt { DASHBOARD_ON, DASHBOARD_LATER, READER_SETUP, READER_LATER }

/** The notification bar card as it will look, so the setting and the recommendation show what they turn on. */
@Composable
fun DashboardPreview(days: Int, title: String, sub: String, modifier: Modifier = Modifier, expanded: Boolean = false) {
    val context = LocalContext.current
    val mode = LocalThemeMode.current
    val bitmap = remember(days, title, sub, mode, expanded) { DashboardArt.preview(context, days, title, sub, mode, expanded) }
    Image(
        bitmap.asImageBitmap(), "Preview of the card: $title, $sub",
        modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height), contentScale = ContentScale.FillWidth,
    )
}

/** A gentle, dismissible recommendation on the home screen. At most one is shown at a time. */
@Composable
fun PromptCard(dashboard: Boolean, onChoose: (Prompt) -> Unit, modifier: Modifier = Modifier) {
    val c = SibTheme.colors
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(c.raised).padding(22.dp)) {
        Text(if (dashboard) "Pin your next deadline" else "Catch deadlines from chats", style = SibType.title, color = c.ink)
        Gap(4.dp)
        Text(
            if (dashboard) "A card in the notification bar, only when something needs you." else "H-sib Reminder can read the chat apps you pick, on this phone only.",
            style = SibType.body, color = c.inkSoft,
        )
        if (dashboard) {
            Gap(16.dp)
            DashboardPreview(3, "Signals lab report", "Fri, Oct 16  ·  in 3 days")
        }
        Gap(18.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionButton(if (dashboard) "Turn on" else "Set up", { onChoose(if (dashboard) Prompt.DASHBOARD_ON else Prompt.READER_SETUP) })
            SibButton("Not now", { onChoose(if (dashboard) Prompt.DASHBOARD_LATER else Prompt.READER_LATER) }, kind = ButtonKind.TEXT)
        }
    }
}
