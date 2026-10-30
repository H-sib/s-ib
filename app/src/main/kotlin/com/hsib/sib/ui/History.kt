package com.hsib.sib.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hsib.sib.core.model.Category
import com.hsib.sib.core.seal.SealEntry
import com.hsib.sib.domain.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** One history entry as a person would read it: a title, what it was, what it became, and who did it. */
data class HistoryLine(val title: String, val from: String?, val to: String?, val by: String)

private val dateValue = Regex("""^(\d{4}-\d{2}-\d{2})(?:T(\d{2}:\d{2})\S*)? (\S+) (date_only|date_time)$""")
private val plainDate = Regex("""^\d{4}-\d{2}-\d{2}$""")
private val effortValue = Regex("""^(\d+)/(\d+)/(\d+)$""")

private fun words(name: String) = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

/** Turns a stored value (a date with its zone, an enum name, an effort triple) into plain words. */
private fun friendly(field: String, raw: String, fmt: Fmt): String {
    if (raw.isBlank() || raw == "none") return "None"
    dateValue.matchEntire(raw)?.let { m ->
        val day = LocalDate.parse(m.groupValues[1])
        val time = m.groupValues[2]
        return if (m.groupValues[4] == "date_time" && time.isNotEmpty()) fmt.dayShort(day) + ", " + fmt.time(LocalTime.parse(time)) else fmt.dayShort(day)
    }
    if (plainDate.matches(raw)) return fmt.dayShort(LocalDate.parse(raw))
    if (raw.contains("..")) {
        val parts = raw.split("..")
        if (parts.size == 2 && plainDate.matches(parts[0]) && plainDate.matches(parts[1])) return fmt.range(LocalDate.parse(parts[0]), LocalDate.parse(parts[1]))
    }
    effortValue.matchEntire(raw)?.let { return fmt.minutes(it.groupValues[2].toInt()) }
    if (field == "category") Category.values().firstOrNull { it.name == raw }?.let { return it.label }
    if (field == "lead days") return raw.split(",").filter { it.isNotBlank() }.joinToString(", ") { "$it d" }.ifEmpty { "None" }
    if (raw.all { it.isUpperCase() || it == '_' }) return words(raw)
    return raw
}

private fun title(field: String, newValue: String): String = when (field) {
    "created" -> "Created"
    "title" -> "Renamed"
    "due" -> "Date changed"
    "window" -> "Date window changed"
    "category" -> "Kind changed"
    "importance" -> "Importance changed"
    "effort" -> "Estimate changed"
    "status" -> when (newValue) { "COMPLETED" -> "Marked done"; "ACTIVE" -> "Reopened"; "CANCELLED" -> "Cancelled"; else -> "Status changed" }
    "postponed" -> if (newValue == "none") "Resumed" else "Paused"
    "repeat" -> "Repeat changed"
    "person" -> "Person changed"
    "relation" -> "Relationship changed"
    "lead days" -> "Early reminders changed"
    "together since" -> "Start date changed"
    else -> words(field) + " changed"
}

/** Reasons that only repeat the title of the entry, so showing them would say the same thing twice. */
private val quietReasons = setOf(
    "edited", "created", "created by typing", "marked done", "reopened", "postponed", "renamed", "date changed", "date confirmed", "joined a shared deadline",
    "importance changed", "estimate changed", "notes changed",
)

fun describeHistory(e: SealEntry, fmt: Fmt): HistoryLine {
    val heading = title(e.field, e.newValue)
    val who = when (e.actor) {
        "USER" -> "You"
        "SUGGESTION" -> "A suggestion you accepted"
        "SYSTEM" -> "H-sib Reminder"
        "SHARED" -> "Shared deadline"
        else -> words(e.actor)
    }
    val note = e.reason.takeIf { it.isNotBlank() && it.lowercase() !in quietReasons }?.let { "  ·  " + it.replaceFirstChar { c -> c.uppercase() } }.orEmpty()
    if (e.field == "created") {
        // "Title | date" is how a new deadline is sealed: show the title, then when it was due.
        val split = e.newValue.lastIndexOf(" | ")
        val name = if (split >= 0) e.newValue.substring(0, split) else e.newValue
        val due = if (split >= 0) friendly("due", e.newValue.substring(split + 3), fmt) else null
        return HistoryLine(heading, name, due?.takeIf { it != "None" }?.let { "Due $it" }, who + note)
    }
    val showFrom = e.field != "status" && !(e.field == "postponed")
    return HistoryLine(
        heading,
        if (showFrom) friendly(e.field, e.oldValue, fmt) else null,
        if (e.field == "status") null else friendly(e.field, e.newValue, fmt),
        who + note,
    )
}

/** A history entry as a card: the day on the left, then what happened, then who did it. */
@Composable
fun HistoryEntryCard(e: SealEntry, fmt: Fmt, zone: ZoneId, modifier: Modifier = Modifier) {
    val c = SibTheme.colors
    val line = describeHistory(e, fmt)
    val day = Instant.ofEpochSecond(e.atEpochSec).atZone(zone).toLocalDate()
    val summary = listOfNotNull(line.title, line.from?.let { "from $it" }, line.to?.let { "to $it" }, "by " + line.by).joinToString(". ")
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.raised).padding(16.dp)
            .semantics(mergeDescendants = true) { contentDescription = fmt.dayShort(day) + ". " + summary },
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            Modifier.width(54.dp).clip(RoundedCornerShape(16.dp)).background(c.violetWash).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(day.dayOfMonth.toString(), style = SibType.number.copy(fontSize = 20.sp, lineHeight = 24.sp), color = c.violet)
            Text(fmt.monthShort(day), style = SibType.label, color = c.violet)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(line.title, style = SibType.title, color = c.ink)
            if (line.from != null && line.to != null && e.field != "created") {
                Spacer(Modifier.height(2.dp))
                Text("From  " + line.from, style = SibType.small, color = c.inkSoft)
                Text("To  " + line.to, style = SibType.bodyStrong.copy(fontSize = 14.sp), color = c.ink)
            } else {
                line.from?.let { Text(it, style = SibType.body, color = c.ink) }
                line.to?.let { Text(it, style = SibType.small, color = c.inkSoft) }
            }
            Spacer(Modifier.height(4.dp))
            Text(line.by, style = SibType.small.copy(fontSize = 12.sp), color = c.inkSoft)
        }
    }
}
