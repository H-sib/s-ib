package com.hsib.sib.core.ingest

import com.hsib.sib.core.model.Kind
import com.hsib.sib.core.model.Lifecycle
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Precision
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Writes deadlines as a standard calendar file other apps can import. Everything in it is readable plain text. */
object IcsWriter {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val dateOnly = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * Escapes text for a calendar property. Every kind of line break becomes the two characters backslash and n, and other
     * control characters are dropped, so a title can never start a new property line (calendar injection).
     */
    private fun escape(s: String): String {
        val sb = StringBuilder(s.length + 8)
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when {
                ch == '\r' -> {
                    sb.append("\\n")
                    if (i + 1 < s.length && s[i + 1] == '\n') i++
                }
                ch == '\n' || ch == ' ' || ch == ' ' || ch == '\u0085' -> sb.append("\\n")
                ch == '\\' -> sb.append("\\\\")
                ch == ';' -> sb.append("\\;")
                ch == ',' -> sb.append("\\,")
                ch == '\t' -> sb.append(' ')
                Character.isISOControl(ch) -> Unit
                else -> sb.append(ch)
            }
            i++
        }
        return sb.toString()
    }

    /** Only characters that are safe in a calendar identifier. */
    private fun safeId(id: String) = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64).ifEmpty { "x" }

    /** Lines longer than 75 octets are folded, as the format requires, always between whole characters. */
    private fun fold(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= 75) return line
        val sb = StringBuilder()
        var count = 0
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val len = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (count + len > 75) {
                sb.append("\r\n ")
                count = 1
            }
            sb.appendCodePoint(cp)
            count += len
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    fun write(items: List<Obligation>, deviceZone: ZoneId, now: Instant = Instant.now()): String {
        val lines = ArrayList<String>()
        lines += "BEGIN:VCALENDAR"
        lines += "VERSION:2.0"
        lines += "PRODID:-//H-sib//S-IB//EN"
        lines += "CALSCALE:GREGORIAN"
        for (o in items) {
            val due = o.due ?: continue
            val event = o.kind == Kind.EVENT
            lines += if (event) "BEGIN:VEVENT" else "BEGIN:VTODO"
            lines += "UID:${safeId(o.id)}@sib"
            lines += "DTSTAMP:${stamp.format(now.atZone(ZoneOffset.UTC))}"
            lines += "SUMMARY:${escape(o.title)}"
            val prop = if (event) "DTSTART" else "DUE"
            lines += if (due.precision == Precision.DATE_ONLY) "$prop;VALUE=DATE:${dateOnly.format(due.local)}"
            else "$prop:${stamp.format(due.instant(deviceZone).atZone(ZoneOffset.UTC))}"
            if (o.notes.isNotBlank()) lines += "DESCRIPTION:${escape(o.notes)}"
            if (o.lifecycle == Lifecycle.COMPLETED) lines += "STATUS:" + (if (event) "CONFIRMED" else "COMPLETED")
            if (o.lifecycle == Lifecycle.CANCELLED) lines += "STATUS:CANCELLED"
            lines += if (event) "END:VEVENT" else "END:VTODO"
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n") { fold(it) } + "\r\n"
    }
}
