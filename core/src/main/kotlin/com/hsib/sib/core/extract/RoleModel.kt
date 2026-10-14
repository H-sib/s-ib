package com.hsib.sib.core.extract

import com.hsib.sib.core.time.TemporalContext
import com.hsib.sib.core.time.TemporalMention
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.temporal.ChronoUnit
import kotlin.math.exp

/** What a date in a sentence is doing there. */
enum class Role { DEADLINE, EVENT, START, PUBLISHED, HISTORY, TENTATIVE, OTHER;
    val actionable: Boolean get() = this == DEADLINE || this == EVENT
}

/** Turns one date mention inside one sentence into named binary features. The names are human readable on purpose. */
object Features {
    private val token = Regex("[\\p{L}][\\p{L}']*")
    private const val WINDOW = 6

    fun extract(sentence: Sentence, m: TemporalMention, ctx: TemporalContext): List<String> {
        val ms = (m.start - sentence.start).coerceIn(0, sentence.text.length)
        val me = (m.end - sentence.start).coerceIn(ms, sentence.text.length)
        val masked = StringBuilder(sentence.text).apply { for (i in ms until me) setCharAt(i, ' ') }.toString().lowercase()
        val out = LinkedHashSet<String>()

        val toks = token.findAll(masked).map { Triple(it.value, it.range.first, it.range.last + 1) }.toList()
        val left = toks.filter { it.third <= ms }.takeLast(WINDOW)
        val right = toks.filter { it.second >= me }.take(WINDOW)
        left.asReversed().forEachIndexed { i, t ->
            if (i == 0) out += "L1:${t.first}"
            if (i == 1) out += "L2:${t.first}"
            out += "L:${t.first}"
        }
        right.forEachIndexed { i, t ->
            if (i == 0) out += "R1:${t.first}"
            if (i == 1) out += "R2:${t.first}"
            out += "R:${t.first}"
        }
        if (left.size >= 2) out += "LB:${left[left.size - 2].first}_${left[left.size - 1].first}"
        if (right.size >= 2) out += "RB:${right[0].first}_${right[1].first}"

        for (h in Lexicon.hits(masked)) {
            out += "C:${h.cue}"
            if (h.end <= ms && ms - h.end <= 70) out += "CL:${h.cue}"
            if (h.start >= me && h.start - me <= 70) out += "CR:${h.cue}"
            if (h.end <= ms && ms - h.end <= 18) out += "CN:${h.cue}"
        }

        out += "T:${m.kind}"
        if (m.isPast) out += "PAST" else out += "FUTURE"
        if (m.hasTime) out += "HASTIME"
        val day = m.date ?: m.window?.earliest
        if (day != null) {
            val delta = ChronoUnit.DAYS.between(ctx.today, day)
            if (delta in 0..14) out += "SOON"
            if (delta > 200) out += "FAR"
            if (delta < -30) out += "LONGAGO"
        }
        for (f in m.flags) out += "F:$f"
        if (ms < 3) out += "SENT_START"
        if (me >= sentence.text.length - 2) out += "SENT_END"
        if (ms > 0 && sentence.text.substring(0, ms).trimEnd().endsWith(":")) out += "AFTER_COLON"
        if (sentence.text.trimEnd().endsWith("?")) out += "QUESTION"
        return out.toList()
    }

    /** Plain language for the explanation view. */
    fun describe(feature: String): String {
        val (kind, value) = feature.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return when (kind) {
            "L1" -> "the word \"$value\" right before the date"
            "L2" -> "the word \"$value\" two words before the date"
            "L" -> "\"$value\" shortly before the date"
            "R1" -> "the word \"$value\" right after the date"
            "R2" -> "the word \"$value\" two words after the date"
            "R" -> "\"$value\" shortly after the date"
            "LB" -> "the phrase \"${value.replace('_', ' ')}\" before the date"
            "RB" -> "the phrase \"${value.replace('_', ' ')}\" after the date"
            "C" -> "a ${value.lowercase()} wording in the sentence"
            "CL" -> "${value.lowercase()} wording before the date"
            "CR" -> "${value.lowercase()} wording after the date"
            "CN" -> "${value.lowercase()} wording right next to the date"
            "T" -> "the kind of date it is (${value.lowercase()})"
            "F" -> "a doubt about the date (${value.lowercase().replace('_', ' ')})"
            else -> when (feature) {
                "PAST" -> "the date is already in the past"
                "FUTURE" -> "the date is still ahead"
                "SOON" -> "the date is within two weeks"
                "FAR" -> "the date is far away"
                "LONGAGO" -> "the date is long past"
                "HASTIME" -> "a time of day is given"
                "SENT_START" -> "the date opens the sentence"
                "SENT_END" -> "the date closes the sentence"
                "AFTER_COLON" -> "the date follows a label"
                "QUESTION" -> "the sentence is a question"
                else -> feature.lowercase()
            }
        }
    }
}

@Serializable
data class RoleModelFile(
    val classes: List<String>,
    val features: List<String>,
    val weights: List<List<Float>>,
    val bias: List<Float>,
    val temperature: Float = 1f,
    val trainedOn: String = "",
)

data class Prediction(val probs: Map<Role, Double>, val logits: DoubleArray, val active: List<Int>) {
    val best: Role get() = probs.maxByOrNull { it.value }!!.key
    fun p(r: Role): Double = probs[r] ?: 0.0
}

data class Contribution(val feature: String, val delta: Double)

/** Multinomial logistic regression over named binary features, with a temperature for calibration. */
class RoleModel(private val file: RoleModelFile) {
    private val roles = file.classes.map { Role.valueOf(it) }
    private val index: Map<String, Int> = file.features.withIndex().associate { it.value to it.index }
    private val weights: Array<FloatArray> = Array(roles.size) { c -> FloatArray(file.features.size) { f -> file.weights[c][f] } }

    val featureCount: Int get() = file.features.size
    val trainedOn: String get() = file.trainedOn

    fun predict(features: Collection<String>): Prediction {
        val active = features.mapNotNull { index[it] }
        val logits = DoubleArray(roles.size) { c ->
            var s = file.bias[c].toDouble()
            for (f in active) s += weights[c][f]
            s
        }
        val t = file.temperature.toDouble().coerceAtLeast(0.05)
        val max = logits.max()
        val ex = DoubleArray(logits.size) { exp((logits[it] - max) / t) }
        val sum = ex.sum()
        val probs = HashMap<Role, Double>()
        roles.forEachIndexed { i, r -> probs[r] = ex[i] / sum }
        return Prediction(probs, logits, active)
    }

    /** The features that pushed [role] ahead of [versus], strongest first. */
    fun explain(pred: Prediction, features: Collection<String>, role: Role, versus: Role, top: Int = 3): List<Contribution> {
        val a = roles.indexOf(role)
        val b = roles.indexOf(versus)
        if (a < 0 || b < 0) return emptyList()
        return features.mapNotNull { name ->
            val f = index[name] ?: return@mapNotNull null
            Contribution(name, (weights[a][f] - weights[b][f]).toDouble())
        }.filter { it.delta > 0.15 }.sortedByDescending { it.delta }.take(top)
    }

    fun toFile(): RoleModelFile = file

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): RoleModel = RoleModel(json.decodeFromString(RoleModelFile.serializer(), text))

        fun toJson(file: RoleModelFile): String = json.encodeToString(RoleModelFile.serializer(), file)

        /** The model shipped inside the library, or null when it has not been trained yet. */
        fun loadDefault(): RoleModel? {
            val stream = RoleModel::class.java.getResourceAsStream("/com/hsib/sib/core/role_model.json") ?: return null
            return stream.use { fromJson(it.readBytes().toString(Charsets.UTF_8)) }
        }
    }
}

/** Hand set weights: the baseline used when no trained model exists, and the "rules only" row of the evaluation. */
object RulePrior {
    fun predict(features: Collection<String>): Map<Role, Double> {
        val score = Role.values().associateWith { 0.0 }.toMutableMap()
        fun add(r: Role, v: Double) { score[r] = score.getValue(r) + v }
        val f = features.toSet()
        if ("CL:DEADLINE" in f) add(Role.DEADLINE, 2.0)
        if ("CR:DEADLINE" in f) add(Role.DEADLINE, 1.5)
        if ("CN:DEADLINE" in f) add(Role.DEADLINE, 1.0)
        if ("L1:by" in f || "L1:before" in f || "L1:until" in f || "L1:till" in f) add(Role.DEADLINE, 1.5)
        if ("CL:EVENT" in f || "CR:EVENT" in f) add(Role.EVENT, 2.0)
        if ("CL:START" in f || "CR:START" in f) add(Role.START, 2.5)
        if ("CL:PUBLISHED" in f || "CR:PUBLISHED" in f) add(Role.PUBLISHED, 2.5)
        if ("PAST" in f) add(Role.HISTORY, 2.0)
        if ("C:HISTORY" in f) add(Role.HISTORY, 1.5)
        if ("C:TENTATIVE" in f) add(Role.TENTATIVE, 3.0)
        if ("C:PROMO" in f || "C:HOLIDAY" in f) add(Role.OTHER, 3.0)
        if ("PAST" in f) { add(Role.DEADLINE, -1.5); add(Role.EVENT, -1.5) }
        add(Role.OTHER, 0.8)
        val max = score.values.max()
        val ex = score.mapValues { exp(it.value - max) }
        val sum = ex.values.sum()
        return ex.mapValues { it.value / sum }
    }
}
