package com.hsib.sib.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hsib.sib.core.extract.Extraction
import com.hsib.sib.core.urgency.UrgencyState

private const val SHOWN_CHARS = 1400

/**
 * The text that was read, with every date marked and a plain sentence under it saying why each one was taken or
 * left alone. This is the feature that lets someone check S-IB instead of trusting it.
 */
@Composable
fun ExplainedText(extraction: Extraction, modifier: Modifier = Modifier) {
    val c = SibTheme.colors
    val text = extraction.normalizedText
    val annotated = remember(extraction, c.dark) {
        buildAnnotatedString {
            var pos = 0
            val shown = text.take(SHOWN_CHARS)
            for (o in extraction.outcomes.sortedBy { it.start }) {
                if (o.start >= shown.length) break
                if (o.start < pos) continue
                append(shown.substring(pos, o.start))
                val end = minOf(o.end, shown.length)
                val style = if (o.accepted) SpanStyle(fontWeight = FontWeight.Bold, background = c.verdigrisWash, textDecoration = TextDecoration.Underline)
                else SpanStyle(fontStyle = FontStyle.Italic, background = c.ochreWash)
                withStyle(style) { append(shown.substring(o.start, end)) }
                pos = end
            }
            if (pos < shown.length) append(shown.substring(pos))
            if (text.length > SHOWN_CHARS) append("\n...")
        }
    }
    Column(modifier.fillMaxWidth()) {
        Text(
            annotated, style = SibType.body, color = c.ink,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(c.raised).padding(14.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text("Bold and underlined: taken as a deadline or event.   Italic on tan: left alone.", style = SibType.small, color = c.inkSoft)
        Spacer(Modifier.height(8.dp))
        extraction.outcomes.forEach { o ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                UrgencyGlyph(if (o.accepted) UrgencyState.DONE else UrgencyState.MOVED, size = 20.dp, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("“${o.text}”", style = SibType.bodyStrong, color = c.ink)
                    Text(o.explanation, style = SibType.small, color = c.inkSoft)
                }
            }
        }
    }
}
