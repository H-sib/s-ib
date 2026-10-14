package com.hsib.sib.eval

import com.hsib.sib.core.extract.Extractor
import com.hsib.sib.core.extract.Role
import com.hsib.sib.core.model.SourceType
import com.hsib.sib.core.model.Tier
import com.hsib.sib.core.time.DateOrder
import com.hsib.sib.core.time.TemporalContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

data class GoldLine(val ref: LocalDate, val text: String, val roles: List<Role>)

object Evaluation {
    private fun role(letter: String) = when (letter.trim()) {
        "D" -> Role.DEADLINE
        "E" -> Role.EVENT
        "S" -> Role.START
        "P" -> Role.PUBLISHED
        "H" -> Role.HISTORY
        "T" -> Role.TENTATIVE
        else -> Role.OTHER
    }

    fun loadGold(): List<GoldLine> {
        val stream = Evaluation::class.java.getResourceAsStream("/gold.tsv") ?: error("gold.tsv missing")
        return stream.bufferedReader().readLines().filter { it.isNotBlank() }.map { line ->
            val p = line.split('\t')
            GoldLine(LocalDate.parse(p[0]), p[1], p[2].split(',').map(::role))
        }
    }

    class Row(val gold: Role, val predicted: Role, val tier: Tier?, val confidence: Double?, val text: String)

    class Result(val name: String, val rows: List<Row>, val mismatched: List<String>)

    fun run(name: String, extractor: Extractor, gold: List<GoldLine>): Result {
        val rows = ArrayList<Row>()
        val bad = ArrayList<String>()
        for (g in gold) {
            val ctx = TemporalContext(ZonedDateTime.of(g.ref, LocalTime.of(10, 0), ZoneId.of("UTC")), dateOrder = DateOrder.DMY)
            val analysis = Extractor.analyze(g.text, ctx)
            if (analysis.mentions.size != g.roles.size) {
                bad += "${g.text}  -> found ${analysis.mentions.size} mentions, expected ${g.roles.size}: " +
                    analysis.mentions.joinToString { "'${it.mention.text}'" }
                continue
            }
            val ex = extractor.extract(g.text, ctx, SourceType.DEMO)
            if (ex.outcomes.size != g.roles.size) { bad += "${g.text}  -> outcome count differs"; continue }
            ex.outcomes.forEachIndexed { i, o ->
                val cand = ex.candidates.firstOrNull { it.id == o.candidateId }
                rows += Row(g.roles[i], o.role, cand?.tier, cand?.confidence, o.text + " | " + g.text)
            }
        }
        return Result(name, rows, bad)
    }

    private fun f1(tp: Int, fp: Int, fn: Int): Triple<Double, Double, Double> {
        val p = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
        val r = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
        return Triple(p, r, if (p + r == 0.0) 0.0 else 2 * p * r / (p + r))
    }

    fun report(r: Result): String = buildString {
        val rows = r.rows
        val roles = Role.values().toList()
        appendLine("=== ${r.name} ===")
        appendLine("gold mentions evaluated: ${rows.size}   lines skipped (tagger/gold count mismatch): ${r.mismatched.size}")
        r.mismatched.forEach { appendLine("  MISMATCH: $it") }

        val acc = rows.count { it.gold == it.predicted }.toDouble() / rows.size
        appendLine("role accuracy: ${"%.3f".format(acc)}")
        var macro = 0.0
        appendLine("per-role precision / recall / F1:")
        for (role in roles) {
            val tp = rows.count { it.gold == role && it.predicted == role }
            val fp = rows.count { it.gold != role && it.predicted == role }
            val fn = rows.count { it.gold == role && it.predicted != role }
            val (p, rc, f) = f1(tp, fp, fn)
            macro += f
            appendLine("  %-10s P=%.3f R=%.3f F1=%.3f  (n=%d)".format(role.name, p, rc, f, rows.count { it.gold == role }))
        }
        appendLine("macro F1: ${"%.3f".format(macro / roles.size)}")

        appendLine("confusion (rows = gold, columns = predicted): " + roles.joinToString(" ") { it.name.take(4) })
        for (g in roles) {
            appendLine("  %-5s ".format(g.name.take(5)) + roles.joinToString(" ") { p -> "%4d".format(rows.count { it.gold == g && it.predicted == p }) })
        }

        val actionable = rows.filter { it.gold.actionable }
        val nonAction = rows.filter { !it.gold.actionable }
        val suggest = rows.filter { it.tier == Tier.SUGGEST }
        val anyTier = rows.filter { it.tier != null }
        val tpS = suggest.count { it.gold.actionable }
        val fpS = suggest.size - tpS
        val fnS = actionable.size - tpS
        val (pS, rS, fS) = f1(tpS, fpS, fnS)
        appendLine("SUGGEST tier: precision=%.3f recall=%.3f F1=%.3f (TP=%d FP=%d FN=%d)".format(pS, rS, fS, tpS, fpS, fnS))
        val tpA = anyTier.count { it.gold.actionable }
        val recallAny = tpA.toDouble() / actionable.size
        appendLine("any tier (SUGGEST or MAYBE): recall=%.3f   missed-deadline rate=%.3f".format(recallAny, 1 - recallAny))
        val falseAny = anyTier.filter { !it.gold.actionable && it.gold != Role.TENTATIVE }
        appendLine("false prompts (non-actionable, non-tentative given any tier): %d of %d mentions = %.1f per 100 mentions".format(falseAny.size, rows.size, 100.0 * falseAny.size / rows.size))
        val falseSuggest = suggest.filter { !it.gold.actionable }
        appendLine("false SUGGEST-tier prompts: %d (%.1f per 100 non-actionable mentions)".format(falseSuggest.size, 100.0 * falseSuggest.size / nonAction.size))
        val tentativeShown = anyTier.count { it.gold == Role.TENTATIVE }
        appendLine("tentative mentions shown as MAYBE: $tentativeShown of ${rows.count { it.gold == Role.TENTATIVE }}")

        val cands = anyTier.filter { it.confidence != null }
        if (cands.isNotEmpty()) {
            var ece = 0.0
            var brier = 0.0
            val bins = 10
            for (b in 0 until bins) {
                val inBin = cands.filter { val c = it.confidence!!; c >= b / bins.toDouble() && (c < (b + 1) / bins.toDouble() || (b == bins - 1)) }
                if (inBin.isEmpty()) continue
                val meanConf = inBin.map { it.confidence!! }.average()
                val accBin = inBin.count { it.gold.actionable }.toDouble() / inBin.size
                ece += inBin.size.toDouble() / cands.size * abs(meanConf - accBin)
            }
            for (c in cands) brier += (c.confidence!! - (if (c.gold.actionable) 1.0 else 0.0)).let { it * it }
            appendLine("calibration over %d candidates: ECE=%.3f  Brier=%.3f".format(cands.size, ece, brier / cands.size))
        }
        val errors = rows.filter { it.gold != it.predicted }
        if (errors.isNotEmpty()) {
            appendLine("role errors (${errors.size}):")
            errors.take(40).forEach { appendLine("  gold=${it.gold} predicted=${it.predicted}  ${it.text}") }
        }
    }
}
