package com.hsib.sib.platform

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalTime
import java.util.zip.ZipInputStream

/**
 * Text out of Word and Excel files.
 *
 * Files from the outside world are hostile until proven otherwise, so everything here is bounded: entry count,
 * decompressed size per entry and in total, nesting depth, cell count and output length. XML is read with Android's
 * pull parser, which does not resolve external entities, so there is no way to make it fetch a file or a URL.
 */
object DocText {
    const val MAX_ENTRIES = 1000
    const val MAX_ENTRY_BYTES = 20 * 1024 * 1024
    const val MAX_TOTAL_BYTES = 40 * 1024 * 1024
    const val MAX_CHARS = 600_000
    const val MAX_CELLS = 40_000
    const val MAX_DEPTH = 64

    class TooLargeException(message: String) : Exception(message)

    /** Reads the wanted entries from a zip, enforcing the limits. */
    fun unzip(bytes: ByteArray, wanted: (String) -> Boolean): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        var total = 0L
        var count = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                if (++count > MAX_ENTRIES) throw TooLargeException("too many entries")
                if (e.isDirectory || !wanted(e.name)) continue
                val buf = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                var size = 0
                while (true) {
                    val n = zin.read(chunk)
                    if (n < 0) break
                    size += n
                    total += n
                    if (size > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) throw TooLargeException("entry too large")
                    buf.write(chunk, 0, n)
                }
                out[e.name] = buf.toByteArray()
            }
        }
        return out
    }

    private fun parser(stream: InputStream): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(stream, "UTF-8")
    }

    fun docx(bytes: ByteArray): String {
        val parts = unzip(bytes) { it == "word/document.xml" || (it.startsWith("word/header") && it.endsWith(".xml")) || (it.startsWith("word/footnotes") && it.endsWith(".xml")) }
        val sb = StringBuilder()
        for ((_, xml) in parts.toSortedMap()) {
            try {
                val p = parser(ByteArrayInputStream(xml))
                var depth = 0
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT && sb.length < MAX_CHARS) {
                    when (ev) {
                        XmlPullParser.START_TAG -> {
                            if (++depth > MAX_DEPTH) throw TooLargeException("nesting too deep")
                            when (p.name) {
                                "w:p" -> if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
                                "w:tab" -> sb.append('\t')
                                "w:br" -> sb.append('\n')
                                "w:t" -> sb.append(p.nextText().also { depth-- })
                            }
                        }
                        XmlPullParser.END_TAG -> depth--
                    }
                    ev = p.next()
                }
            } catch (_: XmlPullParserException) {
                // A damaged part is skipped; whatever was read before it is still useful.
            }
            sb.append('\n')
        }
        return sb.toString().take(MAX_CHARS)
    }

    private val builtinDateFormats = setOf(14, 15, 16, 17, 18, 19, 20, 21, 22, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 45, 46, 47, 50, 51, 52, 53, 54, 55, 56, 57, 58)

    private fun isDateCode(code: String): Boolean {
        val stripped = code.replace(Regex("\"[^\"]*\""), "").replace(Regex("\\[[^\\]]*\\]"), "").replace(Regex("\\\\."), "")
        return stripped.any { it.lowercaseChar() in "ydh" } || Regex("(?i)(?<![a-z])m{1,5}(?![a-z])").containsMatchIn(stripped) && stripped.any { it.lowercaseChar() in "yd" }
    }

    private fun serialToText(v: Double): String {
        val day = Math.floor(v).toLong()
        val date = LocalDate.of(1899, 12, 30).plusDays(day)
        val frac = v - day
        return if (frac > 1e-6) {
            val t = LocalTime.ofSecondOfDay(Math.round(frac * 86400).coerceIn(0, 86399))
            "$date ${"%02d:%02d".format(t.hour, t.minute)}"
        } else date.toString()
    }

    fun xlsx(bytes: ByteArray): String {
        val parts = unzip(bytes) {
            it == "xl/sharedStrings.xml" || it == "xl/styles.xml" || it == "xl/workbook.xml" ||
                (it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml"))
        }
        val shared = ArrayList<String>()
        parts["xl/sharedStrings.xml"]?.let { xml ->
            runCatching {
                val p = parser(ByteArrayInputStream(xml))
                var cur: StringBuilder? = null
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT && shared.size < MAX_CELLS) {
                    if (ev == XmlPullParser.START_TAG) {
                        if (p.name == "si") cur = StringBuilder()
                        else if (p.name == "t" && cur != null) cur.append(p.nextText())
                    } else if (ev == XmlPullParser.END_TAG && p.name == "si") {
                        shared += cur.toString(); cur = null
                    }
                    ev = p.next()
                }
            }
        }
        // Which style indexes display a date or time.
        val dateStyles = HashSet<Int>()
        parts["xl/styles.xml"]?.let { xml ->
            runCatching {
                val custom = HashMap<Int, String>()
                val xfFormats = ArrayList<Int>()
                val p = parser(ByteArrayInputStream(xml))
                var inCellXfs = false
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG) {
                        when (p.name) {
                            "numFmt" -> custom[p.getAttributeValue(null, "numFmtId").toInt()] = p.getAttributeValue(null, "formatCode") ?: ""
                            "cellXfs" -> inCellXfs = true
                            "xf" -> if (inCellXfs) xfFormats += p.getAttributeValue(null, "numFmtId")?.toIntOrNull() ?: 0
                        }
                    } else if (ev == XmlPullParser.END_TAG && p.name == "cellXfs") inCellXfs = false
                    ev = p.next()
                }
                xfFormats.forEachIndexed { i, id -> if (id in builtinDateFormats || custom[id]?.let(::isDateCode) == true) dateStyles += i }
            }
        }
        val sb = StringBuilder()
        var cells = 0
        for ((name, xml) in parts.filterKeys { it.startsWith("xl/worksheets/") }.toSortedMap().entries.take(10)) {
            sb.append('\n')
            runCatching {
                val p = parser(ByteArrayInputStream(xml))
                val row = ArrayList<String>()
                var type: String? = null
                var style = 0
                var value: String? = null
                var inline: StringBuilder? = null
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT && sb.length < MAX_CHARS && cells < MAX_CELLS) {
                    if (ev == XmlPullParser.START_TAG) {
                        when (p.name) {
                            "row" -> row.clear()
                            "c" -> { type = p.getAttributeValue(null, "t"); style = p.getAttributeValue(null, "s")?.toIntOrNull() ?: 0; value = null; inline = null }
                            "v" -> value = p.nextText()
                            "is" -> inline = StringBuilder()
                            "t" -> if (inline != null) inline.append(p.nextText())
                        }
                    } else if (ev == XmlPullParser.END_TAG) {
                        when (p.name) {
                            "c" -> {
                                cells++
                                val text = when {
                                    inline != null -> inline.toString()
                                    type == "s" -> value?.toIntOrNull()?.let { shared.getOrNull(it) }
                                    type == "str" || type == "inlineStr" -> value
                                    value != null && style in dateStyles -> value.toDoubleOrNull()?.let(::serialToText)
                                    else -> value
                                }
                                if (!text.isNullOrBlank()) row += text.trim()
                            }
                            "row" -> if (row.isNotEmpty()) sb.append(row.joinToString("  |  ")).append('\n')
                        }
                    }
                    ev = p.next()
                }
            }
            if (name.isEmpty()) break
        }
        return sb.toString().take(MAX_CHARS)
    }

    /** First text file inside a zip, such as the one a chat app produces when it exports with media. */
    fun firstTextEntry(bytes: ByteArray): String? {
        val parts = unzip(bytes) { it.endsWith(".txt", ignoreCase = true) && !it.contains("..") }
        return parts.entries.firstOrNull()?.value?.toString(Charsets.UTF_8)
    }
}
