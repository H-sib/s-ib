package com.hsib.sib.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.hsib.sib.core.ingest.IcsWriter
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.model.Space
import com.hsib.sib.domain.Fmt
import java.io.File
import java.time.ZoneId

/**
 * Sharing a deadline with friends needs no server and no account. The recipient gets a standard calendar file that
 * any calendar app can open, plus a readable list in the message. Friends who use S-IB can open the file in S-IB.
 * Only what is needed to know the date is shared: title and date. Notes, effort, progress and history stay private.
 */
object Sharing {
    private fun safeName(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "deadline" }

    fun share(context: Context, items: List<Obligation>, zone: ZoneId, heading: String? = null) {
        // The Close ones space is private by design and is never shared.
        val shareable = items.filter { it.due != null && it.space == Space.WORK }.map { it.copy(notes = "") }
        if (shareable.isEmpty()) return
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - 24 * 3_600_000L
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        val file = File(dir, (if (shareable.size == 1) safeName(shareable[0].title) else "sib-deadlines") + ".ics")
        file.writeText(IcsWriter.write(shareable, zone))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val fmt = Fmt(context)
        val lines = shareable.joinToString("\n") { o ->
            val d = o.due!!
            val dateOnly = d.precision == Precision.DATE_ONLY
            "• ${o.title}: " + fmt.due(if (dateOnly) d.local else d.instant(zone).atZone(zone).toLocalDateTime(), dateOnly)
        }
        val text = (heading ?: if (shareable.size == 1) "A deadline to remember:" else "Deadlines to remember:") + "\n" + lines +
            "\n\nOpen the attached file to add them to your calendar."
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, shareable.first().title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share with friends").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
