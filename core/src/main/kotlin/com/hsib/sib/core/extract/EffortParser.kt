package com.hsib.sib.core.extract

/** Picks "about six hours" or "45 minutes" out of a sentence and returns the likely effort in minutes. */
object EffortParser {
    private val words = mapOf(
        "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0, "six" to 6.0,
        "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0, "eleven" to 11.0, "twelve" to 12.0,
        "fifteen" to 15.0, "twenty" to 20.0, "thirty" to 30.0, "forty-five" to 45.0, "sixty" to 60.0,
    )
    private val number = "(\\d+(?:[.,]\\d+)?|a|an|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|fifteen|twenty|thirty|forty-five|sixty)"
    private val hours = Regex("\\b$number\\s*(?:hours?|hrs?|h)\\b", RegexOption.IGNORE_CASE)
    private val minutes = Regex("\\b$number\\s*(?:minutes?|mins?|min)\\b", RegexOption.IGNORE_CASE)
    private val halfHour = Regex("\\bhalf an? hour\\b", RegexOption.IGNORE_CASE)
    private val halfDay = Regex("\\bhalf a day\\b", RegexOption.IGNORE_CASE)
    private val fullDay = Regex("\\b(?:a|one|1) (?:full )?day\\b(?! before| after| ago)", RegexOption.IGNORE_CASE)
    private val hint = Regex("\\b(need|needs|takes?|about|around|roughly|approximately|estimate|estimated|should take|will take|work|effort|~)\\b", RegexOption.IGNORE_CASE)

    private fun value(s: String): Double? = s.replace(',', '.').toDoubleOrNull() ?: words[s.lowercase()]

    /** Returns minutes, or null when the text does not say how long the work will take. */
    fun parse(text: String): Int? {
        if (!hint.containsMatchIn(text)) return null
        halfDay.find(text)?.let { return 240 }
        halfHour.find(text)?.let { return 30 }
        hours.find(text)?.let { m -> value(m.groupValues[1])?.let { return (it * 60).toInt().coerceIn(5, 60 * 200) } }
        minutes.find(text)?.let { m -> value(m.groupValues[1])?.let { return it.toInt().coerceIn(5, 60 * 200) } }
        fullDay.find(text)?.let { return 480 }
        return null
    }
}
