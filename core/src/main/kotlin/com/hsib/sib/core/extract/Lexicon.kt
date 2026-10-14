package com.hsib.sib.core.extract

import com.hsib.sib.core.model.Category

/** Cue families. Each family is a small hand written lexicon; the learned model decides how much each one matters. */
enum class Cue { DEADLINE, EVENT, START, PUBLISHED, HISTORY, TENTATIVE, NEGATION, CHANGE, CANCEL, PROMO, HOLIDAY }

object Lexicon {

    private val phrases: Map<Cue, List<String>> = mapOf(
        Cue.DEADLINE to listOf(
            "due", "deadline", "submit", "submission", "hand in", "turn in", "last date", "last day", "final date",
            "closes", "closing", "close on", "must be", "no later than", "not later than", "cut-off", "cutoff",
            "expires", "expiry", "expire", "renew", "payable", "pay by", "apply by", "register by", "last chance",
            "needs to be", "need to", "have to", "required", "mandatory", "latest by", "on or before",
        ),
        Cue.EVENT to listOf(
            "exam", "examination", "test", "quiz", "midterm", "viva", "interview", "meeting", "appointment", "class",
            "lecture", "seminar", "webinar", "workshop", "orientation", "ceremony", "conference", "presentation",
            "defense", "defence", "hearing", "session", "call", "demo", "practical", "will take place", "will be held",
            "is scheduled", "scheduled for", "takes place", "taking place", "to be held", "kick-off", "kickoff",
            "scheduled", "reservation", "flight", "check-in", "visit",
        ),
        Cue.START to listOf(
            "begins", "begin", "starts", "start", "starting", "opens", "open", "opening", "commence", "commences",
            "launch", "launches", "kicks off", "term starts", "semester starts",
        ),
        Cue.PUBLISHED to listOf(
            "posted", "published", "dated", "issued", "last updated", "updated", "as of", "sent on", "announced",
            "date of issue", "published on", "circular", "reference no", "ref no", "created on", "uploaded",
        ),
        Cue.HISTORY to listOf(
            "was", "were", "had", "happened", "took place", "was held", "were held", "ended", "finished", "completed",
            "already", "yesterday", "ago", "previously", "earlier", "last week", "last month", "last night",
            "used to", "we met", "i went", "last time", "back in", "has passed", "was due", "was submitted",
            "i submitted", "i sent", "i did", "got back",
        ),
        Cue.TENTATIVE to listOf(
            "maybe", "might", "perhaps", "possibly", "probably", "tentative", "tentatively", "tbd", "tbc", "tba",
            "to be confirmed", "to be announced", "not sure", "i think", "could be", "may be", "hopefully", "if ",
            "expected", "likely", "unconfirmed", "provisional", "provisionally", "approximately", "around", "should we",
            "shall we", "how about", "what about", "let's", "lets",
        ),
        Cue.NEGATION to listOf("not", "no", "never", "n't", "without", "isn't", "wasn't", "won't", "don't", "doesn't"),
        Cue.CHANGE to listOf(
            "extended", "extension", "postponed", "rescheduled", "moved", "pushed back", "brought forward",
            "advanced", "revised", "changed", "new date", "now due", "now on", "instead", "delayed", "prepone",
            "preponed", "shifted", "updated deadline", "amended",
        ),
        Cue.CANCEL to listOf(
            "cancelled", "canceled", "called off", "no longer", "scrapped", "abandoned", "withdrawn", "dropped",
            "will not take place", "will not be held", "is off",
        ),
        Cue.PROMO to listOf(
            "sale", "discount", "offer", "% off", "coupon", "promo", "limited time", "limited-time", "deal",
            "free shipping", "unsubscribe", "newsletter", "special price", "flash sale", "save up to", "while stocks last",
        ),
        Cue.HOLIDAY to listOf("holiday", "public holiday", "day off", "bank holiday", "office closed", "closed for", "closed on"),
    )

    private val regexes: Map<Cue, List<Pair<String, Regex>>> = phrases.mapValues { (_, list) ->
        list.map { p -> p to Regex("(?<![\\p{L}])" + Regex.escape(p) + (if (p.endsWith(" ")) "" else "(?![\\p{L}])"), RegexOption.IGNORE_CASE) }
    }

    data class Hit(val cue: Cue, val phrase: String, val start: Int, val end: Int)

    /** All cue occurrences in [text] (a single sentence), with character offsets inside it. */
    fun hits(text: String): List<Hit> {
        val out = mutableListOf<Hit>()
        for ((cue, list) in regexes) {
            for ((phrase, rx) in list) {
                for (m in rx.findAll(text)) out += Hit(cue, phrase, m.range.first, m.range.last + 1)
            }
        }
        return out
    }

    private val categoryWords: List<Pair<Category, Regex>> = listOf(
        Category.EXAM to Regex("\\b(exam|exams|examination|test|quiz|midterm|mid-term|final|viva|entrance|admission test)\\b", RegexOption.IGNORE_CASE),
        Category.ASSIGNMENT to Regex("\\b(assignment|homework|report|essay|project|lab|thesis|paper|coursework|worksheet|problem set|dissertation|presentation slides)\\b", RegexOption.IGNORE_CASE),
        Category.APPLICATION to Regex("\\b(application|apply|applications|scholarship|admission|registration|register|enrol|enroll|enrolment|enrollment|visa|passport|form)\\b", RegexOption.IGNORE_CASE),
        Category.MEETING to Regex("\\b(meeting|call|interview|standup|stand-up|webinar|seminar|conference|presentation|workshop|demo|sync)\\b", RegexOption.IGNORE_CASE),
        Category.APPOINTMENT to Regex("\\b(appointment|doctor|dentist|clinic|checkup|check-up|vaccination|dentist|therapy|consultation)\\b", RegexOption.IGNORE_CASE),
        Category.BILL to Regex("\\b(pay|payment|bill|invoice|rent|subscription|renew|renewal|tax|premium|fee|fees|insurance|loan|emi)\\b", RegexOption.IGNORE_CASE),
        Category.PERSONAL to Regex("\\b(birthday|anniversary|wedding|party|trip|holiday|gift)\\b", RegexOption.IGNORE_CASE),
    )

    /** Picks the best category for a sentence by keyword; the first family that matches wins in a fixed priority order. */
    fun categorize(sentence: String): Category {
        // Exams and appointments are more specific than the generic "form" or "report" words, so check them first.
        val order = listOf(Category.EXAM, Category.APPOINTMENT, Category.BILL, Category.MEETING, Category.ASSIGNMENT, Category.APPLICATION, Category.PERSONAL)
        for (c in order) {
            val rx = categoryWords.first { it.first == c }.second
            if (rx.containsMatchIn(sentence)) return c
        }
        return Category.OTHER
    }
}
