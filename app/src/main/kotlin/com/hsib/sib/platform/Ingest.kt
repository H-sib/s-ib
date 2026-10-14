package com.hsib.sib.platform

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.format.DateFormat
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.hsib.sib.core.extract.Extraction
import com.hsib.sib.core.extract.Extractor
import com.hsib.sib.core.extract.Lexicon
import com.hsib.sib.core.extract.Linker
import com.hsib.sib.core.extract.PlainFingerprinter
import com.hsib.sib.core.extract.fingerprintKey
import com.hsib.sib.core.ingest.ChatExport
import com.hsib.sib.core.ingest.Ics
import com.hsib.sib.core.model.Candidate
import com.hsib.sib.core.model.Kind
import com.hsib.sib.core.model.Lifecycle
import com.hsib.sib.core.model.Reason
import com.hsib.sib.core.model.Relation
import com.hsib.sib.core.model.SourceType
import com.hsib.sib.core.model.Tier
import com.hsib.sib.core.time.DateOrder
import com.hsib.sib.core.time.TemporalContext
import com.hsib.sib.data.AppSettings
import com.hsib.sib.data.SettingsStore
import com.hsib.sib.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

data class ImportResult(
    val extraction: Extraction?,
    val saved: List<Candidate>,
    val label: String,
    val characters: Int,
    val pages: Int = 0,
    val messagesRead: Int = 0,
    val error: String? = null,
)

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
}

/**
 * Turns whatever the user shares into suggestions, entirely on the phone.
 *
 * The original text, image or file is held only while it is being read. What survives is the list of suggestions
 * (a title, a date and a one-sentence excerpt that expires), never the document or the conversation it came from.
 */
class Importer(
    private val context: Context,
    private val store: Store,
    private val settings: SettingsStore,
    private val extractor: Extractor,
) {
    companion object {
        const val MAX_FILE_BYTES = 25 * 1024 * 1024
        const val MAX_PDF_PAGES = 8
        const val MAX_TEXT_BYTES = 5 * 1024 * 1024

        /** Day and month order that the phone's own locale uses for short numeric dates. */
        fun localeOrder(locale: Locale = Locale.getDefault()): DateOrder {
            val p = DateFormat.getBestDateTimePattern(locale, "yMd")
            val m = p.indexOf('M')
            val d = p.indexOf('d')
            return if (m in 0 until d) DateOrder.MDY else DateOrder.DMY
        }
    }

    fun temporalContext(s: AppSettings, at: ZonedDateTime = ZonedDateTime.now()) =
        TemporalContext(at, s.dateOrder, localeOrder(), s.weekStart, s.weekend)

    /** Live preview for the add screen. Nothing is saved. */
    suspend fun preview(text: String): Extraction = withContext(Dispatchers.Default) {
        val s = settings.current()
        extractor.extract(text, temporalContext(s), SourceType.MANUAL, store.allObligations().filter { it.lifecycle == Lifecycle.ACTIVE }, emptySet())
    }

    suspend fun fromText(text: String, source: SourceType, label: String): ImportResult = withContext(Dispatchers.Default) {
        val s = settings.current()
        val existing = store.allObligations().filter { it.lifecycle == Lifecycle.ACTIVE }
        val dismissed = store.dismissedFingerprints()
        val ctx = temporalContext(s)
        if (ChatExport.parse(text, ctx.zone, s.dateOrder.let { if (it == DateOrder.AUTO) localeOrder() else it }).size >= 3) {
            val scan = ChatExport.scan(text, ctx.zone, ctx.order, extractor, ctx.reference, existing, dismissed)
            store.saveCandidates(scan.candidates, s.candidateTtlDays)
            return@withContext ImportResult(null, scan.candidates, "Chat export", text.length, messagesRead = scan.messagesRead)
        }
        val ex = extractor.extract(text, ctx, source, existing, dismissed)
        store.saveCandidates(ex.candidates, s.candidateTtlDays)
        ImportResult(ex, ex.candidates, label, text.length)
    }

    /** Only content:// sources are read. A file:// address would let another app point S-IB at its own private files. */
    private fun Uri.isContent() = scheme == ContentResolver.SCHEME_CONTENT

    suspend fun fromUri(uri: Uri, hintMime: String? = null): ImportResult = withContext(Dispatchers.IO) {
        if (!uri.isContent()) return@withContext fail("That source is not supported.")
        try {
            val name = displayName(uri).orEmpty()
            val mime = (hintMime ?: context.contentResolver.getType(uri)).orEmpty()
            val lower = name.lowercase()
            when {
                mime.startsWith("image/") -> fromImages(listOf(uri), SourceType.SHARE_IMAGE)
                mime == "application/pdf" || lower.endsWith(".pdf") -> fromPdf(uri)
                mime == "text/calendar" || lower.endsWith(".ics") -> fromIcs(readText(uri))
                lower.endsWith(".docx") || mime.contains("wordprocessingml") ->
                    fromText(DocText.docx(readBytes(uri)), SourceType.FILE, name.ifEmpty { "Word document" })
                lower.endsWith(".xlsx") || mime.contains("spreadsheetml") ->
                    fromText(DocText.xlsx(readBytes(uri)), SourceType.FILE, name.ifEmpty { "Spreadsheet" })
                lower.endsWith(".zip") || mime == "application/zip" || mime == "application/x-zip-compressed" -> {
                    val text = DocText.firstTextEntry(readBytes(uri)) ?: return@withContext fail("No text file was found inside that archive.")
                    fromText(text, SourceType.CHAT_EXPORT, "Chat export")
                }
                else -> fromText(readText(uri), SourceType.FILE, name.ifEmpty { "Text file" })
            }
        } catch (e: DocText.TooLargeException) {
            fail("That file is too large or too complex to read safely.")
        } catch (e: SecurityException) {
            fail("S-IB was not allowed to read that file.")
        } catch (e: Exception) {
            fail("That file could not be read.")
        }
    }

    suspend fun fromImages(uris: List<Uri>, source: SourceType): ImportResult = withContext(Dispatchers.Default) {
        val text = StringBuilder()
        for (u in uris.filter { it.isContent() }.take(MAX_PDF_PAGES)) {
            val bmp = decodeBitmap(u) ?: continue
            try {
                text.append(ocr(bmp)).append("\n\n")
            } finally {
                bmp.recycle()
            }
        }
        if (text.isBlank()) return@withContext fail("No text could be read from the picture.", pages = uris.size)
        fromText(text.toString(), source, "Photo").copy(pages = uris.size)
    }

    private suspend fun fromPdf(uri: Uri): ImportResult {
        val tmp = File.createTempFile("import", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_FILE_BYTES) return fail("That PDF is too large to read.")
                        out.write(buf, 0, n)
                    }
                }
            } ?: return fail("That PDF could not be opened.")
            val text = StringBuilder()
            var pages = 0
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { r ->
                    pages = r.pageCount
                    for (i in 0 until minOf(r.pageCount, MAX_PDF_PAGES)) {
                        r.openPage(i).use { page ->
                            val scale = 1600f / max(page.width, 1)
                            val bmp = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            try { text.append(ocr(bmp)).append("\n\n") } finally { bmp.recycle() }
                        }
                    }
                }
            }
            if (text.isBlank()) return fail("No text could be read from that PDF.", pages = pages)
            return fromText(text.toString(), SourceType.SHARE_PDF, "PDF").copy(pages = pages)
        } catch (_: Exception) {
            return fail("That PDF could not be read.")
        } finally {
            tmp.delete()
        }
    }

    private suspend fun fromIcs(text: String): ImportResult = withContext(Dispatchers.Default) {
        val s = settings.current()
        val zone = ZoneId.systemDefault()
        val items = Ics.parse(text, zone)
        val existing = store.allObligations().filter { it.lifecycle == Lifecycle.ACTIVE }
        val dismissed = store.dismissedFingerprints()
        val today = ZonedDateTime.now(zone).toLocalDate()
        val out = ArrayList<Candidate>()
        for (it in items.sortedBy { i -> i.due.local }.take(300)) {
            val day = it.due.dayIn(zone)
            if (day.isBefore(today)) continue
            val cat = Lexicon.categorize(it.title + " " + it.description)
            val link = Linker.link(it.title, cat, it.due, zone, false, false, existing)
            if (link.relation == Relation.DUPLICATE) continue
            val fp = PlainFingerprinter.of(fingerprintKey(it.title, day))
            if (fp in dismissed) continue
            out += Candidate(
                id = UUID.randomUUID().toString(), title = it.title.take(120), category = cat, kind = if (it.isTodo) Kind.DEADLINE else Kind.EVENT,
                due = it.due, fog = null, confidence = 0.97, tier = Tier.SUGGEST, relation = link.relation, relatedId = link.relatedId,
                reasons = listOfNotNull(Reason("Taken from a calendar entry, so the date and time are exact.", 1.0), link.reason),
                flags = emptySet(), sourceType = SourceType.ICS, excerpt = it.description.take(200), fingerprint = fp,
            )
        }
        store.saveCandidates(out, s.candidateTtlDays)
        ImportResult(null, out, "Calendar file", text.length)
    }

    // ---- reading ----------------------------------------------------------------------------------

    private fun fail(message: String, pages: Int = 0) = ImportResult(null, emptyList(), "", 0, pages, error = message)

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    private fun readBytes(uri: Uri, limit: Int = MAX_FILE_BYTES): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("cannot open")
        input.use {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(32 * 1024)
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                if (out.size() + n > limit) throw DocText.TooLargeException("file too large")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    private fun readText(uri: Uri): String = readBytes(uri, MAX_TEXT_BYTES).toString(Charsets.UTF_8)

    private fun decodeBitmap(uri: Uri, maxDim: Int = 2200): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val degrees = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull().let {
            when (it) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }
        if (degrees == 0f) return bmp
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
        if (rotated !== bmp) bmp.recycle()
        return rotated
    }

    private suspend fun ocr(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            return recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
        } finally {
            recognizer.close()
        }
    }
}
