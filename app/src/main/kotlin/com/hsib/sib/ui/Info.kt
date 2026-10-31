package com.hsib.sib.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** The small eye next to a title. Tap it and a short plain explanation of that feature opens right below. */
@Composable
fun InfoEye(open: Boolean, onToggle: () -> Unit, what: String, modifier: Modifier = Modifier) {
    val c = SibTheme.colors
    Box(
        modifier.size(40.dp).clip(CircleShape).background(if (open) c.violet.copy(alpha = 0.16f) else c.violet.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClickLabel = if (open) "Hide the explanation" else "Explain") { onToggle() }
            .semantics { contentDescription = "What is $what" },
        contentAlignment = Alignment.Center,
    ) { SibIconView(SibIcon.EYE, size = 22.dp, tint = c.violet) }
}

/** A title with its eye close beside it, and the explanation under it when the eye is open. */
@Composable
fun InfoTitle(title: String, info: String, modifier: Modifier = Modifier, style: TextStyle = SibType.bodyStrong) {
    val c = SibTheme.colors
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = style, color = c.ink, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(6.dp))
            InfoEye(open, { open = !open }, title)
        }
        if (open) InfoCard(info)
    }
}

@Composable
fun InfoCard(text: String, modifier: Modifier = Modifier) {
    val c = SibTheme.colors
    Text(text, style = SibType.body, color = c.ink, modifier = modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(20.dp)).background(c.violet.copy(alpha = 0.08f)).padding(16.dp))
}
