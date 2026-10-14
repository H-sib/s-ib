package com.hsib.sib.core.extract

import com.hsib.sib.core.model.Candidate
import com.hsib.sib.core.model.Category
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Flag
import com.hsib.sib.core.model.FogWindow
import com.hsib.sib.core.model.Kind
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.model.Reason
import com.hsib.sib.core.model.Relation
import com.hsib.sib.core.model.SourceType
import com.hsib.sib.core.model.Tier
import com.hsib.sib.core.time.MentionKind
import com.hsib.sib.core.time.TemporalContext
import com.hsib.sib.core.time.TemporalMention
import com.hsib.sib.core.time.TemporalTagger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** What happened to one date found in the text, for the "why this date" view. */
data class MentionOutcome(
    val start: Int,
    val end: Int,
    val text: String,
    val role: Role,
    val accepted: Boolean,
    val explanation: String,
    val candidateId: String?,
)

data class Extraction(
    val candidates: List<Candidate>,
    val outcomes: List<MentionOutcome>,
    val normalizedText: String,
)

/** One date mention together with the sentence it sits in and the features the classifier sees. */
data class AnalyzedMention(
    val mention: TemporalMention,
    val sentence: Sentence,
    val sentenceIndex: Int,
    val features: List<String>,
)

data class Analysis(val text: String, val sentences: List<Sentence>, val mentions: List<AnalyzedMention>)

class Extractor(
    private val model: RoleModel? = RoleModel.loadDefault(),
    private val fingerprinter: Fingerprinter = PlainFingerprinter,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    companion object {
        const val SUGGEST_THRESHOLD = 0.80
        const val MAYBE_THRESHOLD = 0.45

        /** Shared by extraction, training and evaluation so all three see exactly the same features. */
        fun analyze(raw: String, ctx: TemporalContext): Analysis {
            val text = TextPrep.normalize(raw)
            val sentences = TextPrep.sentences(text)
            val mentions = TemporalTagger.tag(text, ctx).filter { it.kind != MentionKind.TIME_ONLY }.map { m ->
                val idx = sentences.indexOfLast { it.start <= m.start }.coerceAtLeast(0)
                val sentence = sentences.getOrNull(idx) ?: Sentence(0, text.length, text)
                AnalyzedMention(m, sentence, idx, Features.extract(sentence, m, ctx))
            }
            return Analysis(text, sentences, mentions)
        }
    }

    fun extract(
        raw: String,
        ctx: TemporalContext,
        source: SourceType,
        existing: List<Obligation> = emptyList(),
        dismissed: Set<String> = emptySet(),
    ): Extraction {
        val analysis = analyze(raw, ctx)
        val text = analysis.text
        val sentences = analysis.sentences
        val outcomes = mutableListOf<MentionOutcome>()
        val candidates = mutableListOf<Candidate>()
        val sourcePrior = when (source) {
            SourceType.MANUAL, SourceType.SHARE_TEXT, SourceType.PASTE, SourceType.SELECTION, SourceType.DEMO -> 1.0
            SourceType.CHAT_EXPORT, SourceType.FILE, SourceType.SHARE_PDF, SourceType.ICS -> 0.95
            SourceType.SCAN, SourceType.SHARE_IMAGE -> 0.9
        }

        for (a in analysis.mentions) {
            val m = a.mention
            val sIdx = a.sentenceIndex
            val sentence = a.sentence
            val feats = a.features
            val pred = model?.predict(feats) ?: Prediction(RulePrior.predict(feats), DoubleArray(0), emptyList())
            val best = pred.best
            val pAct = pred.p(Role.DEADLINE) + pred.p(Role.EVENT)
            val pTent = pred.p(Role.TENTATIVE)
            val hits = Lexicon.hits(sentence.text.lowercase())
            val cancelled = hits.any { it.cue == Cue.CANCEL }
            val changed = hits.any { it.cue == Cue.CHANGE }

            fun ignore(why: String) {
                outcomes += MentionOutcome(m.start, m.end, m.text, best, false, why, null)
            }

            val whyBest = explainRole(pred, feats, best)

            if (m.isPast && m.date != null && !m.date.isEqual(ctx.today) && !cancelled) {
                ignore("Ignored: ${m.text} is already in the past.")
                continue
            }

            val tentative = best == Role.TENTATIVE || (pTent > 0.35 && pAct > 0.2)
            val actionable = best.actionable || pAct >= MAYBE_THRESHOLD
            if (!tentative && !actionable) {
                ignore("Ignored: reads as ${roleWords(best)}. $whyBest".trim())
                continue
            }
            if (tentative && pTent + pAct < 0.4) {
                ignore("Ignored: too uncertain to suggest. $whyBest".trim())
                continue
            }

            var confidence = if (tentative) minOf(0.74, 0.5 * pTent + 0.5 * pAct + 0.1) else pAct
            confidence *= sourcePrior
            val reasons = mutableListOf<Reason>()
            if (!tentative) reasons += explanationReasons(pred, feats, if (pred.p(Role.DEADLINE) >= pred.p(Role.EVENT)) Role.DEADLINE else Role.EVENT)
            else reasons += Reason("The wording is tentative, so this stays a maybe.", 0.0)

            val flags = m.flags.toMutableSet()
            if (tentative) flags += Flag.TENTATIVE_WORDING
            if (Flag.AMBIGUOUS_ORDER in flags) { confidence *= 0.72; reasons += Reason("Day and month order is ambiguous in \"${m.text}\".", -0.3) }
            if (Flag.YEAR_INFERRED in flags) confidence *= 0.93
            if (Flag.VAGUE in flags) confidence *= 0.85
            if (Flag.WEEKDAY_MISMATCH in flags) { confidence *= 0.7; reasons += Reason("The weekday does not match the date written.", -0.3) }
            if (Flag.MIDNIGHT in flags) reasons += Reason("Midnight belongs to two days; check which one is meant.", 0.0)
            confidence = confidence.coerceIn(0.0, 0.99)

            val kind = if (pred.p(Role.DEADLINE) >= pred.p(Role.EVENT)) Kind.DEADLINE else Kind.EVENT
            val title = TitleBuilder.build(sentences, sIdx, m, text)
            val category = Lexicon.categorize(sentence.text + " " + title)
            val due = dueSpec(m, ctx)
            val fog = m.window?.let { FogWindow(it.earliest, it.latest) }
            val alternatives = m.alternatives.map { alt ->
                DueSpec(alt, m.zone ?: ctx.zone, if (m.hasTime) Precision.DATE_TIME else Precision.DATE_ONLY)
            }
            val day = m.date ?: m.window?.latest
            val fingerprint = fingerprinter.of(fingerprintKey(title, day))
            if (fingerprint in dismissed) {
                ignore("Skipped: you dismissed this before.")
                continue
            }

            var relation = Relation.NEW
            var relatedId: String? = null
            if (existing.isNotEmpty()) {
                val link = Linker.link(title, category, due, ctx.zone, hits.any { it.cue == Cue.CHANGE }, cancelled, existing)
                relation = link.relation
                relatedId = link.relatedId
                link.reason?.let { reasons += it }
            }
            if (cancelled && relation != Relation.CANCEL) {
                ignore("Ignored: the text says this was cancelled, and there is nothing matching to cancel.")
                continue
            }
            if (relation == Relation.DUPLICATE) {
                ignore("Skipped: you already track this.")
                continue
            }
            if (changed && relation == Relation.NEW) reasons += Reason("The wording suggests a change, but nothing you track matches.", 0.0)

            val tier = when {
                relation == Relation.CANCEL || relation == Relation.UPDATE -> if (confidence >= MAYBE_THRESHOLD) Tier.SUGGEST else Tier.MAYBE
                tentative -> Tier.MAYBE
                confidence >= SUGGEST_THRESHOLD -> Tier.SUGGEST
                confidence >= MAYBE_THRESHOLD -> Tier.MAYBE
                else -> null
            }
            if (tier == null) {
                ignore("Ignored: only ${(confidence * 100).toInt()}% sure. $whyBest".trim())
                continue
            }

            val id = newId()
            val excerpt = sentence.text.take(240)
            candidates += Candidate(
                id = id, title = title, category = category, kind = kind, due = due, fog = fog,
                confidence = confidence, tier = tier, relation = relation, relatedId = relatedId,
                reasons = reasons, flags = flags, sourceType = source, excerpt = excerpt,
                alternatives = alternatives, fingerprint = fingerprint,
            )
            outcomes += MentionOutcome(
                m.start, m.end, m.text, best, true,
                "Accepted as ${if (kind == Kind.DEADLINE) "a deadline" else "an event"} (${(confidence * 100).toInt()}%). " + reasons.firstOrNull()?.text.orEmpty(),
                id,
            )
        }

        // Several mentions can describe the same item (same day, similar wording); keep the strongest.
        val deduped = ArrayList<Candidate>()
        for (c in candidates.sortedByDescending { it.confidence }) {
            val day = c.due?.local?.toLocalDate()
            val twin = deduped.any { k ->
                k.relation == c.relation && k.relatedId == c.relatedId &&
                    (k.fingerprint == c.fingerprint || (k.due?.local?.toLocalDate() == day && Linker.similarity(k.title, c.title) >= 0.5))
            }
            if (!twin) deduped += c
        }
        val keptIds = deduped.map { it.id }.toSet()
        val fixedOutcomes = outcomes.map { o ->
            if (o.accepted && o.candidateId !in keptIds) o.copy(accepted = false, explanation = "Skipped: same item as a stronger suggestion.", candidateId = null) else o
        }
        return Extraction(deduped, fixedOutcomes, text)
    }

    private fun dueSpec(m: TemporalMention, ctx: TemporalContext): DueSpec? {
        val zone: ZoneId = m.zone ?: ctx.zone
        return when {
            m.kind == MentionKind.WINDOW && m.window != null ->
                DueSpec(LocalDateTime.of(m.window.latest, LocalTime.MIDNIGHT), zone, Precision.DATE_ONLY)
            m.date != null && m.time != null -> DueSpec(LocalDateTime.of(m.date, m.time), zone, Precision.DATE_TIME)
            m.date != null -> DueSpec(LocalDateTime.of(m.date, LocalTime.MIDNIGHT), zone, Precision.DATE_ONLY)
            else -> null
        }
    }

    private fun roleWords(r: Role) = when (r) {
        Role.START -> "a start or opening date"
        Role.PUBLISHED -> "a publication or issue date"
        Role.HISTORY -> "something that already happened"
        Role.TENTATIVE -> "a tentative suggestion"
        Role.OTHER -> "an ordinary date that is not an obligation"
        Role.DEADLINE -> "a deadline"
        Role.EVENT -> "an event"
    }

    private fun explainRole(pred: Prediction, feats: List<String>, role: Role): String {
        val m = model ?: return ""
        val versus = Role.values().filter { it != role }.maxByOrNull { pred.p(it) } ?: return ""
        val parts = m.explain(pred, feats, role, versus, 2).map { Features.describe(it.feature) }
        return if (parts.isEmpty()) "" else "Because of " + parts.joinToString(" and ") + "."
    }

    private fun explanationReasons(pred: Prediction, feats: List<String>, role: Role): List<Reason> {
        val m = model
        if (m == null) return listOf(Reason("Matches deadline wording near the date.", 1.0))
        val versus = Role.values().filter { it != role }.maxByOrNull { pred.p(it) } ?: Role.OTHER
        val parts = m.explain(pred, feats, role, versus, 3)
        if (parts.isEmpty()) return listOf(Reason("Looks like ${roleWords(role)} from the overall wording.", 0.5))
        return parts.map { Reason(Features.describe(it.feature).replaceFirstChar { c -> c.uppercase() }, it.delta) }
    }
}

/** Builds a short, human title from the sentence around a date. Users can always edit it. */
object TitleBuilder {
    private val fillers = Regex("^(?:\\s*(?:dear|hi|hello|hey)\\b[^,:\\n]{0,40}[,:]\\s*|\\s*(?:reminder|notice|important|urgent|fyi|note|update|announcement|re|fwd)\\s*[:\\-]\\s*)+", RegexOption.IGNORE_CASE)
    private val lead = Regex("^(?:please note that|note that|remember that|a reminder that|this is a reminder that|reminder that|we would like to remind you that|we remind you that|please|kindly|just|also|and|so|but|again|however|finally|btw|meanwhile)\\b[,:]?\\s+", RegexOption.IGNORE_CASE)
    private val trailing = Regex("(?:\\s+(?:is|are|was|will be|has been|have been|on|by|at|before|until|till|due|for|the|a|an|to|of|from|in|within|extended to|moved to|postponed to|rescheduled to|now|is now|should be|must be|is scheduled for|scheduled for|date|time|deadline is))+\\s*$", RegexOption.IGNORE_CASE)
    private val weakTitle = Regex("^(?:date|due date|deadline|time|when|on|due|last date|day)$", RegexOption.IGNORE_CASE)
    private val articleLead = Regex("^(?:the|your|a|an|all|our|my)\\s+", RegexOption.IGNORE_CASE)

    private val deadlineFor = Regex("(?:the\\s+)?(?:submission\\s+|application\\s+|registration\\s+|payment\\s+)?deadline\\s+(?:for|to submit|of|to)\\s+(?:the\\s+|your\\s+)?(.+?)(?:\\s+(?:is|has|will|was)\\b.*)?$", RegexOption.IGNORE_CASE)
    private val lastDateTo = Regex("last\\s+(?:date|day)\\s+(?:to|for|of)\\s+(?:the\\s+|your\\s+)?(.+?)(?:\\s+(?:is|has|will)\\b.*)?$", RegexOption.IGNORE_CASE)
    private val verbPhrase = Regex("(?:please\\s+|kindly\\s+|you\\s+(?:must|need to|have to|should)\\s+|students\\s+(?:must|should)\\s+|applicants\\s+(?:must|should)\\s+|remember to\\s+|don't forget to\\s+)?(submit|hand in|turn in|send|upload|complete|finish|file|pay|renew|register|apply|book|return|bring|prepare|review|sign|attend|confirm|collect|download)\\s+(.+?)(?:\\s+(?:by|before|on|until|till|no later than|due|at|within)\\b.*)?$", RegexOption.IGNORE_CASE)
    private val isDue = Regex("^(?:the\\s+|your\\s+|all\\s+)?(.+?)\\s+(?:is|are)\\s+due\\b.*$", RegexOption.IGNORE_CASE)
    private val heldOn = Regex("^(?:the\\s+|your\\s+)?(.+?)\\s+(will be held|will take place|takes place|is scheduled|is on|starts|begins|closes|opens|closing|ends)\\b.*$", RegexOption.IGNORE_CASE)

    fun build(sentences: List<Sentence>, index: Int, m: TemporalMention, fullText: String): String {
        val own = titleFromSentence(sentences[index], m)
        if (isStrong(own)) return own
        val prev = sentences.getOrNull(index - 1)
        if (prev != null) {
            val p = titleFromSentence(prev, null)
            if (isStrong(p)) return p
        }
        return own.ifBlank { "Untitled" }
    }

    private fun isStrong(s: String) = s.length >= 4 && !weakTitle.matches(s)

    private fun titleFromSentence(sentence: Sentence, m: TemporalMention?): String {
        var s = sentence.text
        if (m != null) {
            val a = (m.start - sentence.start).coerceIn(0, s.length)
            val b = (m.end - sentence.start).coerceIn(a, s.length)
            s = s.substring(0, a) + " " + s.substring(b)
        }
        s = s.replace(Regex("\\s+"), " ").trim()
        s = s.replace(fillers, "").trim()
        var prev: String
        do {
            prev = s
            s = s.replace(lead, "").trim()
        } while (s != prev)
        s = s.trim(' ', '.', ',', ';', ':', '-', '!', '?')

        deadlineFor.find(s)?.let { return finish(it.groupValues[1] + " deadline") }
        lastDateTo.find(s)?.let { return finish(it.groupValues[1]) }
        verbPhrase.find(s)?.let { return finish(it.groupValues[1] + " " + it.groupValues[2].replace(articleLead, "")) }
        isDue.find(s)?.let { return finish(it.groupValues[1]) }
        heldOn.find(s)?.let {
            val verb = it.groupValues[2].lowercase()
            val x = it.groupValues[1]
            return finish(if (verb in setOf("closes", "opens", "closing", "starts", "begins", "ends")) "$x $verb" else x)
        }
        return finish(s)
    }

    private fun finish(raw: String): String {
        var s = raw.replace(Regex("\\s+"), " ").trim()
        s = s.replace(trailing, "").trim(' ', '.', ',', ';', ':', '-', '(', ')')
        s = s.replace(Regex("^(?:the|your|a|an|all)\\s+", RegexOption.IGNORE_CASE), "").trim()
        if (s.length > 80) {
            val cut = s.lastIndexOf(' ', 80)
            s = s.substring(0, if (cut > 30) cut else 80).trim() + "..."
        }
        return s.replaceFirstChar { it.uppercase() }
    }
}

/** Matches a candidate against what the user already tracks. */
object Linker {
    data class Link(val relation: Relation, val relatedId: String?, val reason: Reason?)

    private val stop = setOf(
        "the", "a", "an", "and", "or", "of", "to", "for", "on", "in", "at", "by", "is", "are", "be", "your", "you", "our",
        "has", "have", "been", "will", "with", "this", "that", "all", "from", "date", "deadline", "due", "last", "new",
    )

    fun tokens(s: String): Set<String> =
        Regex("[\\p{L}\\p{N}]+").findAll(s.lowercase()).map { it.value }.filter { it !in stop && (it.length > 2 || it.all(Char::isDigit)) }.toSet()

    fun similarity(a: String, b: String): Double {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        return ta.intersect(tb).size.toDouble() / ta.union(tb).size
    }

    fun link(
        title: String,
        category: Category,
        due: DueSpec?,
        deviceZone: ZoneId,
        changeCue: Boolean,
        cancelCue: Boolean,
        existing: List<Obligation>,
    ): Link {
        var best: Obligation? = null
        var bestScore = 0.0
        val day: LocalDate? = due?.dayIn(deviceZone)
        for (o in existing) {
            if (o.lifecycle.name != "ACTIVE") continue
            val jac = similarity(title, o.title)
            val catBonus = if (o.category == category && category != Category.OTHER) 0.15 else 0.0
            val od = o.due?.dayIn(deviceZone)
            val prox = if (day != null && od != null) {
                val d = kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(day, od))
                when { d == 0L -> 0.15; d <= 14 -> 0.1; d <= 45 -> 0.05; else -> 0.0 }
            } else 0.0
            val score = 0.7 * jac + catBonus + prox
            if (score > bestScore) { bestScore = score; best = o }
        }
        val b = best
        if (b == null || bestScore < 0.5) return Link(Relation.NEW, null, null)
        val oldDay = b.due?.dayIn(deviceZone)
        return when {
            cancelCue -> Link(Relation.CANCEL, b.id, Reason("Matches \"${b.title}\", which you track.", bestScore))
            day != null && oldDay != null && day == oldDay -> Link(Relation.DUPLICATE, b.id, null)
            changeCue || bestScore >= 0.7 -> Link(Relation.UPDATE, b.id, Reason("Looks like a change to \"${b.title}\" (${(bestScore * 100).toInt()}% match).", bestScore))
            else -> Link(Relation.NEW, null, null)
        }
    }
}
