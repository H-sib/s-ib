package com.hsib.sib.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.hsib.sib.R
import com.hsib.sib.data.ThemeMode
import com.hsib.sib.ui.paletteFor
import java.io.File

/** How pressing a deadline is, which sets the colour of the whole card. */
internal enum class Tone { CALM, SOON, URGENT }

/** Everything the card shows, already worked out. */
internal class CardData(
    val tone: Tone,
    val count: String,
    val title: String,
    val sub: String,
    /** How full the ring is, 0 to 1. */
    val progress: Float,
    /** Days until the date, for the row of dots. Negative when it has passed. */
    val daysLeft: Int,
    val others: List<Other>,
    val mode: ThemeMode,
) {
    class Other(val tone: Tone, val title: String, val whenText: String)
}

/**
 * Draws the notification bar card as a picture, so it can use the app's own typeface and shapes, which the notification shade's
 * text views cannot. The collapsed card is one row: a ring with the days left, the title, and a Done pill. The expanded card adds a
 * row of dots counting down to the date, the next few deadlines, and Open and Done. The colour runs from violet through amber to
 * coral as the date gets close. The areas that can be tapped sit over the picture as plain views, in the same proportions.
 */
internal object DashboardArt {
    /** Height of the expanded card is split in these parts: the main row, the list, the buttons. Must match notif_dash_big.xml. */
    const val HERO_PARTS = 4f
    const val LIST_PARTS = 4f
    const val BUTTON_PARTS = 2f

    private val faces = HashMap<Int, Typeface>()

    /** Sora at the weight asked for, or the closest thing the phone can do. */
    private fun face(context: Context, weight: Int): Typeface = synchronized(faces) {
        faces.getOrPut(weight) {
            runCatching {
                val file = File(context.cacheDir, "sora.ttf")
                if (!file.exists()) context.resources.openRawResource(R.font.sora).use { input -> file.outputStream().use { input.copyTo(it) } }
                Typeface.Builder(file).setFontVariationSettings("'wght' $weight").build()
            }.getOrNull()
                ?: Typeface.create(ResourcesCompat.getFont(context, R.font.sora) ?: Typeface.DEFAULT, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun hex(s: String) = Color.parseColor(s)

    private fun pair(tone: Tone, mode: ThemeMode): Pair<Int, Int> = when (tone) {
        Tone.CALM -> if (mode == ThemeMode.NEON) hex("#00E5FF") to hex("#7B61FF") else hex("#8B7BFF") to hex("#39D5FF")
        Tone.SOON -> hex("#FFC145") to hex("#FF7A45")
        Tone.URGENT -> hex("#FF5D73") to hex("#FF2E93")
    }

    private fun alpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    fun widthPx(context: Context): Int {
        val dm = context.resources.displayMetrics
        return (((dm.widthPixels / dm.density) - 56f).coerceIn(260f, 420f) * dm.density).toInt()
    }

    fun small(context: Context, d: CardData): Bitmap = render(context, d, expanded = false)
    fun big(context: Context, d: CardData): Bitmap = render(context, d, expanded = true)

    /** A sample for the settings page and the recommendation card. */
    fun preview(context: Context, days: Int, title: String, sub: String, mode: ThemeMode, expanded: Boolean): Bitmap {
        val tone = when { days <= 1 -> Tone.URGENT; days <= 4 -> Tone.SOON; else -> Tone.CALM }
        val d = CardData(
            tone, if (days <= 0) "Today" else days.toString(), title, sub, if (days < 0) 1f else (1f - days.coerceIn(0, 30) / 30f).coerceIn(0.04f, 1f), days,
            listOf(CardData.Other(Tone.SOON, "Quiz on Friday", "in 5 days"), CardData.Other(Tone.CALM, "Lab notebook", "in 9 days")), mode,
        )
        return if (expanded) big(context, d) else small(context, d)
    }

    private fun render(context: Context, d: CardData, expanded: Boolean): Bitmap {
        val den = context.resources.displayMetrics.density
        val w = widthPx(context)
        val h = ((if (expanded) 240f else 72f) * den).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bitmap)
        val mode = if (d.mode == ThemeMode.LIGHT) ThemeMode.DARK else d.mode
        val palette = paletteFor(mode)
        val (a, b) = pair(d.tone, mode)
        val ink = palette.ink.toArgb()
        val soft = palette.inkSoft.toArgb()
        val dark = hex("#14112B")
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
        val radius = 22f * den
        val card = RectF(0f, 0f, w.toFloat(), h.toFloat())

        // The card: the theme's own dark, washed with the tone from the top left, and a hairline edge that fades across.
        paint.color = palette.paper.toArgb()
        cv.drawRoundRect(card, radius, radius, paint)
        paint.shader = LinearGradient(0f, 0f, w * 0.95f, h.toFloat(), intArrayOf(alpha(a, 78), alpha(b, 22), Color.TRANSPARENT), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        cv.drawRoundRect(card, radius, radius, paint)
        paint.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, alpha(a, 170), alpha(b, 50), Shader.TileMode.CLAMP)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.3f * den
        cv.drawRoundRect(RectF(0.65f * den, 0.65f * den, w - 0.65f * den, h - 0.65f * den), radius, radius, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL

        fun write(s: String, x: Float, baseline: Float, size: Float, weight: Int, color: Int, align: Paint.Align = Paint.Align.LEFT, maxWidth: Float = 0f) {
            text.typeface = face(context, weight)
            text.textSize = size * den
            text.color = color
            text.textAlign = align
            val shown = if (maxWidth > 0f) TextUtils.ellipsize(s, text, maxWidth, TextUtils.TruncateAt.END).toString() else s
            cv.drawText(shown, x, baseline, text)
        }

        fun pill(left: Float, top: Float, right: Float, bottom: Float, label: String, filled: Boolean) {
            val r = (bottom - top) / 2f
            val box = RectF(left, top, right, bottom)
            if (filled) {
                paint.color = Color.BLACK; paint.shader = LinearGradient(left, 0f, right, 0f, a, b, Shader.TileMode.CLAMP)
                cv.drawRoundRect(box, r, r, paint)
                paint.shader = null
            } else {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.4f * den
                paint.color = alpha(a, 200)
                cv.drawRoundRect(RectF(left + 0.7f * den, top + 0.7f * den, right - 0.7f * den, bottom - 0.7f * den), r, r, paint)
                paint.style = Paint.Style.FILL
            }
            write(label, (left + right) / 2f, (top + bottom) / 2f + 4.6f * den, 13f, 700, if (filled) dark else ink, Paint.Align.CENTER)
        }

        // ---- the main row: ring, title, and in the small card the Done pill ----
        val pad = 14f * den
        val heroH = if (expanded) h * HERO_PARTS / (HERO_PARTS + LIST_PARTS + BUTTON_PARTS) else h.toFloat()
        val ring = minOf(heroH - 2 * pad, 52f * den)
        val cx = pad + ring / 2f
        val cy = heroH / 2f
        val sw = 5f * den
        val oval = RectF(cx - ring / 2f + sw / 2f, cy - ring / 2f + sw / 2f, cx + ring / 2f - sw / 2f, cy + ring / 2f - sw / 2f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sw
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = alpha(ink, 30)
        cv.drawArc(oval, 0f, 360f, false, paint)
        val sweep = SweepGradient(cx, cy, intArrayOf(a, b, a), floatArrayOf(0f, 0.5f, 1f))
        sweep.setLocalMatrix(Matrix().apply { postRotate(-90f, cx, cy) })
        paint.color = Color.BLACK; paint.shader = sweep
        cv.drawArc(oval, -90f, 360f * d.progress.coerceIn(0.03f, 1f), false, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
        val numberSize = when { d.count.length > 3 -> 11f; d.count.length > 1 -> 15f; else -> 21f }
        write(d.count, cx, cy + numberSize * den * 0.36f, numberSize, 800, ink, Paint.Align.CENTER)

        val textX = pad + ring + 13f * den
        val rightEdge = if (expanded) w - pad else w - pad - 78f * den
        val titleSize = 16f
        if (expanded) {
            write(d.title, textX, cy - 11f * den, titleSize, 700, ink, maxWidth = rightEdge - textX)
            write(d.sub, textX, cy + 7f * den, 12f, 500, soft, maxWidth = rightEdge - textX)
            // A row of days: lit while there is time, a bigger glowing dot on the date itself.
            val n = 14
            val gap = 10.5f * den
            val dotY = cy + 25f * den
            for (i in 0 until n) {
                val x = textX + 3f * den + i * gap
                if (x > rightEdge) break
                when {
                    d.daysLeft < 0 || i < d.daysLeft.coerceAtMost(n) -> {
                        paint.color = Color.BLACK; paint.shader = LinearGradient(textX, 0f, textX + n * gap, 0f, a, b, Shader.TileMode.CLAMP)
                        cv.drawCircle(x, dotY, 2.4f * den, paint)
                        paint.shader = null
                    }
                    i == d.daysLeft -> {
                        paint.color = alpha(b, 60)
                        cv.drawCircle(x, dotY, 6.4f * den, paint)
                        paint.color = b
                        cv.drawCircle(x, dotY, 3.8f * den, paint)
                    }
                    else -> {
                        paint.color = alpha(ink, 38)
                        cv.drawCircle(x, dotY, 2f * den, paint)
                    }
                }
            }
        } else {
            write(d.title, textX, cy - 3f * den, titleSize, 700, ink, maxWidth = rightEdge - textX)
            write(d.sub, textX, cy + 15f * den, 12f, 500, soft, maxWidth = rightEdge - textX)
            pill(w - pad - 70f * den, cy - 18f * den, w - pad, cy + 18f * den, "Done", filled = true)
        }

        // ---- the expanded card's list and buttons ----
        if (expanded) {
            val listH = h * LIST_PARTS / (HERO_PARTS + LIST_PARTS + BUTTON_PARTS)
            paint.color = alpha(ink, 26)
            cv.drawRect(pad, heroH, w - pad, heroH + 1f * den, paint)
            val rowH = listH / 3f
            d.others.take(3).forEachIndexed { i, o ->
                val mid = heroH + rowH * i + rowH / 2f
                val (oa, ob) = pair(o.tone, mode)
                paint.color = Color.BLACK; paint.shader = LinearGradient(pad, 0f, pad + 12f * den, 0f, oa, ob, Shader.TileMode.CLAMP)
                cv.drawCircle(pad + 5f * den, mid, 4f * den, paint)
                paint.shader = null
                write(o.whenText, w - pad, mid + 4.4f * den, 12f, 500, soft, Paint.Align.RIGHT)
                text.typeface = face(context, 500); text.textSize = 12f * den
                val whenW = text.measureText(o.whenText)
                write(o.title, pad + 20f * den, mid + 4.6f * den, 13.5f, 600, ink, maxWidth = w - pad * 2 - 20f * den - whenW - 10f * den)
            }
            val buttonsTop = heroH + listH
            val buttonsH = h - buttonsTop
            val gap = 10f * den
            val each = (w - 2 * pad - gap) / 2f
            val top = buttonsTop + (buttonsH - 36f * den) / 2f - 2f * den
            pill(pad, top, pad + each, top + 36f * den, "Open", filled = false)
            pill(pad + each + gap, top, w - pad, top + 36f * den, "Done", filled = true)
        }
        return bitmap
    }
}
