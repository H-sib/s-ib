package com.hsib.sib.core.ingest

import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Precision
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

data class IcsItem(
    val title: String,
    val description: String,
    val due: DueSpec,
    val isTodo: Boolean,
    val uid: String?,
)

/** Reads the parts of an iCalendar file a deadline app cares about. Bounded and forgiving: bad items are skipped. */
object Ics {
    const val MAX_CHARS = 2_000_000
    const val MAX_ITEMS = 5000

    fun parse(text: String, defaultZone: ZoneId): List<IcsItem> {
        if (text.length > MAX_CHARS) return emptyList()
        val lines = unfold(text)
        val out = ArrayList<IcsItem>()
        var kind: String? = null
        var props = ArrayList<Prop>()
        for (line in lines) {
            val upper = line.uppercase()
            when {
                upper == "BEGIN:VEVENT" || upper == "BEGIN:VTODO" -> { kind = upper.removePrefix("BEGIN:"); props = ArrayList() }
                (upper == "END:VEVENT" || upper == "END:VTODO") && kind != null -> {
                    toItem(kind, props, defaultZone)?.let { out += it }
                    kind = null
                    if (out.size >= MAX_ITEMS) return out
                }
                kind != null -> parseProp(line)?.let { props += it }
            }
        }
        return out
    }

    private class Prop(val name: String, val params: Map<String, String>, val value: String)

    private fun unfold(text: String): List<String> {
        val out = ArrayList<String>()
        for (raw in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && out.isNotEmpty()) out[out.size - 1] = out.last() + raw.substring(1)
            else if (raw.isNotEmpty()) out += raw
        }
        return out
    }

    private fun parseProp(line: String): Prop? {
        val colon = line.indexOf(':')
        if (colon <= 0) return null
        val head = line.substring(0, colon).split(';')
        val params = head.drop(1).mapNotNull {
            val eq = it.indexOf('=')
            if (eq > 0) it.substring(0, eq).uppercase() to it.substring(eq + 1).trim('"') else null
        }.toMap()
        return Prop(head[0].uppercase(), params, line.substring(colon + 1))
    }

    private fun unescape(s: String): String =
        s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private fun toItem(kind: String, props: List<Prop>, defaultZone: ZoneId): IcsItem? {
        fun get(name: String) = props.firstOrNull { it.name == name }
        if (get("STATUS")?.value?.uppercase() == "CANCELLED") return null
        val title = get("SUMMARY")?.value?.let(::unescape)?.trim().orEmpty()
        if (title.isEmpty()) return null
        val isTodo = kind == "VTODO"
        val dateProp = (if (isTodo) get("DUE") ?: get("DTSTART") else get("DTSTART")) ?: return null
        val due = parseDate(dateProp, defaultZone) ?: return null
        return IcsItem(title, get("DESCRIPTION")?.value?.let(::unescape)?.trim().orEmpty().take(500), due, isTodo, get("UID")?.value)
    }

    private fun parseDate(p: Prop, defaultZone: ZoneId): DueSpec? {
        val v = p.value.trim()
        return try {
            when {
                p.params["VALUE"]?.uppercase() == "DATE" || (v.length == 8 && v.all { it.isDigit() }) -> {
                    val d = LocalDate.of(v.substring(0, 4).toInt(), v.substring(4, 6).toInt(), v.substring(6, 8).toInt())
                    DueSpec(d.atTime(LocalTime.MIDNIGHT), defaultZone, Precision.DATE_ONLY)
                }
                v.length >= 15 && v[8] == 'T' -> {
                    val ldt = LocalDateTime.of(
                        v.substring(0, 4).toInt(), v.substring(4, 6).toInt(), v.substring(6, 8).toInt(),
                        v.substring(9, 11).toInt(), v.substring(11, 13).toInt(), v.substring(13, 15).toInt(),
                    )
                    when {
                        v.endsWith("Z") -> DueSpec(ldt, ZoneOffset.UTC, Precision.DATE_TIME)
                        p.params["TZID"] != null -> {
                            val zone = runCatching { ZoneId.of(p.params["TZID"]!!) }.getOrDefault(defaultZone)
                            DueSpec(ldt, zone, Precision.DATE_TIME)
                        }
                        else -> DueSpec(ldt, defaultZone, Precision.DATE_TIME, floating = true)
                    }
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
