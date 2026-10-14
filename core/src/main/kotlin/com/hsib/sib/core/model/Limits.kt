package com.hsib.sib.core.model

/**
 * Hard limits on what the app will store, applied at the point of saving whatever the source (typing, a shared file, a
 * restored backup). The interface limits input too, but this is the layer that cannot be bypassed.
 */
object Limits {
    const val TITLE = 200
    const val NOTES = 10_000
    const val PERSON = 80
    const val RELATION = 40
    const val LEAD_DAYS = 12
    const val MAX_LEAD = 365

    /** Pending suggestions kept at once, so repeated shares or a huge chat export cannot grow the database without bound. */
    const val MAX_PENDING = 300

    private fun isLineBreak(c: Char) = c == '\n' || c == '\r' || c == ' ' || c == ' ' || c == '\u0085'

    private fun cut(s: String, max: Int): String {
        if (s.length <= max) return s
        // Never leave half of a surrogate pair at the end.
        val end = if (Character.isHighSurrogate(s[max - 1])) max - 1 else max
        return s.substring(0, end)
    }

    /** One line of text: every control character and line break becomes a space, then trimmed and cut to [max]. */
    fun line(s: String, max: Int): String {
        val sb = StringBuilder(minOf(s.length, max + 1))
        for (c in s) sb.append(if (Character.isISOControl(c) || isLineBreak(c)) ' ' else c)
        return cut(sb.toString().trim(), max)
    }

    /** Free text: line breaks are kept as plain newlines, other control characters are dropped. */
    fun text(s: String, max: Int): String {
        val sb = StringBuilder(minOf(s.length, max + 1))
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\r' -> { sb.append('\n'); if (i + 1 < s.length && s[i + 1] == '\n') i++ }
                isLineBreak(c) -> sb.append('\n')
                c == '\t' -> sb.append(' ')
                Character.isISOControl(c) -> Unit
                else -> sb.append(c)
            }
            i++
        }
        return cut(sb.toString().trim(), max)
    }
}

/** The same deadline with every free text field cleaned and bounded. */
fun Obligation.clamped(): Obligation = copy(
    title = Limits.line(title, Limits.TITLE).ifEmpty { "Untitled" },
    notes = Limits.text(notes, Limits.NOTES),
    person = Limits.line(person, Limits.PERSON),
    relation = Limits.line(relation, Limits.RELATION),
    leadDays = leadDays.filter { it in 1..Limits.MAX_LEAD }.distinct().take(Limits.LEAD_DAYS),
)
