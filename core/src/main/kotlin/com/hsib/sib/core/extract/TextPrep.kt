package com.hsib.sib.core.extract

/** A span of the normalized text. Offsets are identical to the original text because normalization never changes length. */
data class Sentence(val start: Int, val end: Int, val text: String)

object TextPrep {

    private val quotedHeader = Regex("^\\s*(?:on .{5,120} wrote:|-{2,}\\s*original message\\s*-{2,}|from:\\s.*|sent from my .*|get outlook for .*)\\s*$", RegexOption.IGNORE_CASE)
    private val abbreviations = setOf("jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec", "a.m", "p.m", "no", "dr", "mr", "mrs", "ms", "prof", "vs", "etc", "approx", "st", "e.g", "i.e")

    /**
     * Same length as the input. Digits from any numeral system become ASCII digits, odd spaces become plain
     * spaces, and quoted reply text is blanked so old messages cannot produce new suggestions.
     */
    fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when {
                ch in '0'..'9' -> sb.append(ch)
                ch.code > 127 && Character.isDigit(ch) -> sb.append(('0'.code + Character.digit(ch, 10)).toChar())
                ch == ' ' || ch == ' ' || ch == ' ' || ch == ' ' -> sb.append(' ')
                ch == '​' || ch == '‌' || ch == '‍' || ch == '﻿' -> sb.append(' ')
                ch == '\r' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return blankQuotedReplies(sb.toString())
    }

    private fun blankQuotedReplies(text: String): String {
        if (!text.contains('\n') && !text.trimStart().startsWith(">")) return text
        val lines = text.split('\n')
        val out = StringBuilder(text.length)
        var inQuoted = false
        lines.forEachIndexed { idx, line ->
            if (!inQuoted && quotedHeader.matches(line) && (line.trimStart().startsWith("On ", true) || line.contains("Original Message", true))) {
                inQuoted = true
            }
            val blank = inQuoted || line.trimStart().startsWith(">") || quotedHeader.matches(line)
            out.append(if (blank) " ".repeat(line.length) else line)
            if (idx < lines.size - 1) out.append('\n')
        }
        return out.toString()
    }

    /** Splits into sentences and lines, keeping offsets into [text]. */
    fun sentences(text: String): List<Sentence> {
        val out = mutableListOf<Sentence>()
        var start = 0
        var i = 0
        fun emit(endExclusive: Int) {
            var s = start
            var e = endExclusive
            while (s < e && text[s].isWhitespace()) s++
            while (e > s && text[e - 1].isWhitespace()) e--
            if (e > s) out += Sentence(s, e, text.substring(s, e))
            start = endExclusive
        }
        while (i < text.length) {
            val c = text[i]
            if (c == '\n') {
                emit(i)
                start = i + 1
            } else if (c == '.' || c == '!' || c == '?' || c == ';') {
                if (isBoundary(text, i)) {
                    emit(i + 1)
                }
            }
            i++
        }
        emit(text.length)
        return out
    }

    private fun isBoundary(text: String, i: Int): Boolean {
        val c = text[i]
        val next = text.getOrNull(i + 1)
        if (next != null && !next.isWhitespace()) return false
        if (c == '.') {
            // Abbreviations ("Nov. 18", "p.m.") and numbers ("3.") are not sentence ends.
            var j = i - 1
            while (j >= 0 && (text[j].isLetter() || text[j] == '.')) j--
            val word = text.substring(j + 1, i).lowercase()
            if (word in abbreviations) return false
            if (j >= 0 && text[j].isDigit() && i - j <= 3) return false
            val after = text.substring(i + 1).trimStart()
            if (after.isNotEmpty() && after[0].isLowerCase()) return false
        }
        return true
    }
}
