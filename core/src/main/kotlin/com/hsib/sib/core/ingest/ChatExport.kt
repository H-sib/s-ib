package com.hsib.sib.core.ingest

import com.hsib.sib.core.extract.Extractor
import com.hsib.sib.core.model.Candidate
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Relation
import com.hsib.sib.core.model.SourceType
import com.hsib.sib.core.time.DateOrder
import com.hsib.sib.core.time.TemporalContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class ChatMessage(val time: ZonedDateTime, val sender: String, val text: String)

data class ChatScan(val candidates: List<Candidate>, val messagesRead: Int, val messagesConsidered: Int)

/**
 * Reads an exported chat (the text file messaging apps can export for one conversation) and finds deadlines in it.
 *
 * Relative dates ("tomorrow", "next Friday") are resolved against when each message was SENT, not when it is
 * read, which is the single most common cause of wrong dates when old messages are processed later.
 * Sender names are never kept; only candidate deadlines leave this function.
 */
object ChatExport {
    const val MAX_LINES = 300_000

    private const val DASHES = "-\\u2013\\u2014"
    private val android = Regex("^(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2,4}),?\\s+(\\d{1,2})[:.](\\d{2})(?::(\\d{2}))?\\s*([AaPp]\\.?[Mm]\\.?)?\\s+[$DASHES]\\s+(.*)$")
    private val ios = Regex("^\\[(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2,4}),?\\s+(\\d{1,2})[:.](\\d{2})(?::(\\d{2}))?\\s*([AaPp]\\.?[Mm]\\.?)?\\]\\s+(.*)$")

    private class Header(val a: Int, val b: Int, val year: Int, val hour: Int, val minute: Int, val ampm: String?, val rest: String)

    private fun header(line: String): Header? {
        val m = android.find(line) ?: ios.find(line) ?: return null
        val g = m.groupValues
        val yearRaw = g[3].toInt()
        return Header(g[1].toInt(), g[2].toInt(), if (g[3].length <= 2) 2000 + yearRaw else yearRaw, g[4].toInt(), g[5].toInt(), g[7].ifEmpty { null }, g[8])
    }

    private fun clean(raw: String): String = raw.map {
        when (it) {
            ' ', ' ', ' ', ' ' -> ' '
            '‎', '‏', '﻿' -> ' '
            else -> it
        }
    }.joinToString("")

    fun parse(raw: String, zone: ZoneId, defaultOrder: DateOrder): List<ChatMessage> {
        val lines = clean(raw).replace("\r\n", "\n").split('\n')
        if (lines.size > MAX_LINES) return emptyList()
        val headers = lines.map { header(it) }
        // The order of day and month is inferred from the file itself: a first number above 12 can only be a day.
        var dayFirst = false
        var monthFirst = false
        for (h in headers) {
            if (h == null) continue
            if (h.a > 12) dayFirst = true
            if (h.b > 12) monthFirst = true
        }
        val dmy = when {
            dayFirst && !monthFirst -> true
            monthFirst && !dayFirst -> false
            else -> defaultOrder != DateOrder.MDY
        }
        val out = ArrayList<ChatMessage>()
        var cur: ChatMessage? = null
        fun flush() { cur?.let { out += it }; cur = null }
        for ((i, line) in lines.withIndex()) {
            val h = headers[i]
            if (h != null) {
                flush()
                val colon = h.rest.indexOf(": ")
                if (colon in 1..60) {
                    val day = if (dmy) h.a else h.b
                    val month = if (dmy) h.b else h.a
                    val date = runCatching { LocalDate.of(h.year, month, day) }.getOrNull() ?: continue
                    var hour = h.hour
                    h.ampm?.let { ap ->
                        val pm = ap.lowercase().startsWith("p")
                        hour = when { hour == 12 && !pm -> 0; hour == 12 -> 12; pm -> hour + 12; else -> hour }
                    }
                    if (hour > 23 || h.minute > 59) continue
                    cur = ChatMessage(ZonedDateTime.of(LocalDateTime.of(date, java.time.LocalTime.of(hour, h.minute)), zone), h.rest.substring(0, colon), h.rest.substring(colon + 2))
                }
            } else if (cur != null) {
                val c = cur!!
                cur = c.copy(text = c.text + "\n" + line)
            }
        }
        flush()
        return out
    }

    fun scan(
        raw: String,
        zone: ZoneId,
        textOrder: DateOrder,
        extractor: Extractor,
        now: ZonedDateTime,
        existing: List<Obligation> = emptyList(),
        dismissed: Set<String> = emptySet(),
        maxAgeDays: Long = 90,
    ): ChatScan {
        val messages = parse(raw, zone, textOrder)
        val cutoff = now.minusDays(maxAgeDays)
        val recent = messages.filter { !it.time.isBefore(cutoff) && !it.time.isAfter(now.plusDays(1)) }
        val found = ArrayList<Candidate>()
        for (m in recent) {
            val ctx = TemporalContext(m.time, dateOrder = textOrder)
            val ex = extractor.extract(m.text, ctx, SourceType.CHAT_EXPORT, existing, dismissed)
            for (c in ex.candidates) {
                val day = c.due?.dayIn(zone)
                // Something announced three weeks ago for "tomorrow" is long over by now.
                if (c.relation == Relation.NEW && day != null && day.isBefore(now.toLocalDate())) continue
                found += c
            }
        }
        val deduped = found.groupBy { it.fingerprint + "|" + it.relation + "|" + it.relatedId }
            .map { (_, l) -> l.maxByOrNull { it.confidence }!! }
            .sortedByDescending { it.confidence }
        return ChatScan(deduped, messages.size, recent.size)
    }
}
