package com.hsib.sib.eval

import com.hsib.sib.core.extract.Extractor
import com.hsib.sib.core.extract.RoleModel
import java.io.File

private const val DEFAULT_MODEL_PATH = "../core/src/main/resources/com/hsib/sib/core/role_model.json"

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "train" -> train(args.getOrNull(1) ?: DEFAULT_MODEL_PATH)
        "report" -> report(args.getOrNull(1) ?: "report.txt")
        else -> println("usage: train [modelPath] | report [reportPath]")
    }
}

private fun train(outPath: String) {
    val gen1 = Generator(1)
    val train = List(30000) { gen1.sample() }
    val gen2 = Generator(2)
    val dev = List(4000) { gen2.sample() }
    val gold = Evaluation.loadGold()
    val calibration = gold.filterIndexed { i, _ -> i % 2 == 0 }.map {
        Sample(it.text, it.roles, java.time.ZonedDateTime.of(it.ref, java.time.LocalTime.of(10, 0), java.time.ZoneId.of("UTC")))
    }
    val (file, summary) = Trainer.train(train, dev, calibration)
    println(summary)
    val out = File(outPath)
    out.parentFile?.mkdirs()
    out.writeText(RoleModel.toJson(file))
    println("model written to ${out.absoluteFile} (${out.length() / 1024} KiB)")
}

private fun report(path: String) {
    val allGold = Evaluation.loadGold()
    // Even lines calibrate the temperature during training; odd lines are held out and form the headline numbers.
    val gold = allGold.filterIndexed { i, _ -> i % 2 == 1 }
    val model = RoleModel.loadDefault()
    val sb = StringBuilder()
    sb.appendLine("S-IB extraction evaluation")
    sb.appendLine("Gold set: ${allGold.size} hand written lines (eval/src/main/resources/gold.tsv); ${gold.size} held-out lines are scored below.")
    sb.appendLine("Training data: synthetic templates only, plus the other half of the gold lines for the calibration temperature.")
    sb.appendLine("The gold set was written by the same author as the generator, so these numbers are optimistic")
    sb.appendLine("and are not a claim about real world messages.")
    sb.appendLine()
    sb.appendLine(Evaluation.report(Evaluation.run("rules only (hand set weights), held-out lines", Extractor(model = null), gold)))
    if (model != null) {
        sb.appendLine(Evaluation.report(Evaluation.run("trained model, held-out lines (${model.featureCount} features; ${model.trainedOn})", Extractor(model = model), gold)))
    } else {
        sb.appendLine("No trained model found. Run: train")
    }
    println(sb)
    File(path).writeText(sb.toString())
}
