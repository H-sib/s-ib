package com.hsib.sib.core.extract

/**
 * Things a person says out loud next to the date: "remind me 5 days before" and "about 3 to 5 hours". The date itself is read by
 * the normal extractor; this only picks up the reminder lead and a range of hours, which the normal reader does not.
 */
data class VoiceHints(val leadDays: List<Int>, val effortMin: Int?) {
    companion object {
        private val words = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "a" to 1)
        private val num = "(\\d{1,2}|a|one|two|three|four|five|six|seven|eight|nine|ten)"
        private val leadDays = Regex("\\b$num\\s+days?\\s+(?:before|ahead|earlier|in advance)\\b", RegexOption.IGNORE_CASE)
        private val leadWeek = Regex("\\b(?:a|one|1)\\s+week\\s+(?:before|ahead|earlier|in advance)\\b", RegexOption.IGNORE_CASE)
        private val range = Regex("\\b(\\d{1,3}(?:\\.\\d)?)\\s*(?:to|-|or)\\s*(\\d{1,3}(?:\\.\\d)?)\\s*(?:hours?|hrs?|h)\\b", RegexOption.IGNORE_CASE)

        private fun n(s: String): Int? = s.toIntOrNull() ?: words[s.lowercase()]

        fun parse(text: String): VoiceHints {
            val leads = buildList {
                leadDays.findAll(text).forEach { m -> n(m.groupValues[1])?.let { if (it in 1..60) add(it) } }
                if (leadWeek.containsMatchIn(text)) add(7)
            }.distinct().sortedDescending()
            val effort = range.find(text)?.let { m ->
                val lo = m.groupValues[1].toDouble()
                val hi = m.groupValues[2].toDouble()
                if (hi >= lo && hi <= 200) (((lo + hi) / 2) * 60).toInt() else null
            }
            return VoiceHints(leads, effort)
        }
    }
}
