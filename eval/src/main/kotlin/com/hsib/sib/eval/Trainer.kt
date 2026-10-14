package com.hsib.sib.eval

import com.hsib.sib.core.extract.Extractor
import com.hsib.sib.core.extract.Role
import com.hsib.sib.core.extract.RoleModelFile
import com.hsib.sib.core.time.DateOrder
import com.hsib.sib.core.time.TemporalContext
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

class Example(val feats: List<String>, val label: Int)

object Trainer {
    private val roles = Role.values().toList()

    fun featurize(samples: List<Sample>): Pair<List<Example>, Int> {
        val out = ArrayList<Example>()
        var dropped = 0
        for (s in samples) {
            val ctx = TemporalContext(s.reference, dateOrder = DateOrder.DMY)
            val a = Extractor.analyze(s.text, ctx)
            if (a.mentions.size != s.roles.size) { dropped++; continue }
            a.mentions.forEachIndexed { i, m -> out += Example(m.features, roles.indexOf(s.roles[i])) }
        }
        return out to dropped
    }

    private fun softmax(logits: DoubleArray, t: Double = 1.0): DoubleArray {
        val max = logits.max()
        val ex = DoubleArray(logits.size) { exp((logits[it] - max) / t) }
        val s = ex.sum()
        return DoubleArray(ex.size) { ex[it] / s }
    }

    fun train(
        trainSamples: List<Sample>,
        devSamples: List<Sample>,
        calibrationSamples: List<Sample>,
        epochs: Int = 25,
        minCount: Int = 3,
        l2: Double = 2e-5,
        dropout: Double = 0.12,
    ): Pair<RoleModelFile, String> {
        val (train, droppedTrain) = featurize(trainSamples)
        val (dev, droppedDev) = featurize(devSamples)
        val (cal, droppedCal) = featurize(calibrationSamples)

        val counts = HashMap<String, Int>()
        for (e in train) for (f in e.feats) counts[f] = (counts[f] ?: 0) + 1
        val vocab = counts.filter { it.value >= minCount }.keys.sorted()
        val index = vocab.withIndex().associate { it.value to it.index }
        val c = roles.size
        val f = vocab.size

        fun idx(e: Example) = e.feats.mapNotNull { index[it] }.toIntArray()
        val trainIdx = train.map { idx(it) }
        val devIdx = dev.map { idx(it) }
        val calIdx = cal.map { idx(it) }

        val w = Array(c) { DoubleArray(f) }
        val b = DoubleArray(c)
        val gw = Array(c) { DoubleArray(f) { 1e-8 } }
        val gb = DoubleArray(c) { 1e-8 }
        val lr = 0.25
        val rnd = java.util.Random(11)
        val order = (0 until train.size).toMutableList()

        fun logits(ids: IntArray): DoubleArray = DoubleArray(c) { k ->
            var s = b[k]
            for (i in ids) s += w[k][i]
            s
        }

        for (epoch in 1..epochs) {
            order.shuffle(rnd)
            for (n in order) {
                val all = trainIdx[n]
                // Feature dropout: forces the model to cope with phrasing it did not memorise.
                val ids = if (dropout > 0) all.filter { rnd.nextDouble() >= dropout }.toIntArray() else all
                val p = softmax(logits(ids))
                for (k in 0 until c) {
                    val err = p[k] - (if (train[n].label == k) 1.0 else 0.0)
                    gb[k] += err * err
                    b[k] -= lr * err / sqrt(gb[k])
                    for (i in ids) {
                        val g = err + l2 * w[k][i]
                        gw[k][i] += g * g
                        w[k][i] -= lr * g / sqrt(gw[k][i])
                    }
                }
            }
        }

        // Temperature scaling: the T that minimises log loss. Synthetic development data alone would make the model
        // overconfident on real text, so hand written calibration lines (kept out of the evaluation half) carry most of the weight.
        val devLogits = devIdx.map { logits(it) }
        val calLogits = calIdx.map { logits(it) }
        fun meanLoss(logs: List<DoubleArray>, ex: List<Example>, t: Double): Double {
            if (ex.isEmpty()) return 0.0
            var loss = 0.0
            for (n in ex.indices) loss -= ln(softmax(logs[n], t)[ex[n].label].coerceAtLeast(1e-12))
            return loss / ex.size
        }
        var bestT = 1.0
        var bestLoss = Double.MAX_VALUE
        var t = 0.5
        val calWeight = if (cal.isEmpty()) 0.0 else 0.7
        while (t <= 4.01) {
            val loss = (1 - calWeight) * meanLoss(devLogits, dev, t) + calWeight * meanLoss(calLogits, cal, t)
            if (loss < bestLoss) { bestLoss = loss; bestT = t }
            t += 0.05
        }

        fun accuracy(ex: List<Example>, ids: List<IntArray>): Double {
            var ok = 0
            for (n in ex.indices) {
                val l = logits(ids[n])
                if (l.indices.maxByOrNull { l[it] } == ex[n].label) ok++
            }
            return ok.toDouble() / ex.size
        }

        val summary = buildString {
            appendLine("train examples: ${train.size} (dropped samples: $droppedTrain), dev examples: ${dev.size} (dropped: $droppedDev), calibration mentions: ${cal.size} (dropped: $droppedCal)")
            appendLine("vocabulary: $f features (min count $minCount), classes: $c")
            appendLine("train accuracy: ${"%.4f".format(accuracy(train, trainIdx))}, synthetic dev accuracy: ${"%.4f".format(accuracy(dev, devIdx))}, calibration-lines accuracy: ${"%.4f".format(if (cal.isEmpty()) 0.0 else accuracy(cal, calIdx))}")
            appendLine("calibration temperature: ${"%.2f".format(bestT)}, weighted log loss: ${"%.4f".format(bestLoss)}")
        }

        val file = RoleModelFile(
            classes = roles.map { it.name },
            features = vocab,
            weights = w.map { row -> row.map { it.toFloat() } },
            bias = b.map { it.toFloat() },
            temperature = bestT.toFloat(),
            trainedOn = "synthetic templates: ${train.size} mentions; temperature fit on ${cal.size} hand written lines",
        )
        return file to summary
    }
}
