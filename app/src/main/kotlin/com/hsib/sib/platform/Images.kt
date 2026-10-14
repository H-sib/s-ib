package com.hsib.sib.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Reads a picture the user chose and shrinks it so a receipt never takes more than a few hundred kilobytes. */
fun readScaledJpeg(context: Context, uri: Uri, maxDim: Int = 1280, quality: Int = 80): ByteArray? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim * 2) sample *= 2
    var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
    val degrees = resolver.openInputStream(uri)?.use {
        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    val scale = maxDim.toFloat() / max(bmp.width, bmp.height)
    if (scale < 1f || degrees != 0f) {
        val m = Matrix().apply { if (scale < 1f) postScale(scale, scale); if (degrees != 0f) postRotate(degrees) }
        val scaled = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (scaled !== bmp) bmp.recycle()
        bmp = scaled
    }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
    bmp.recycle()
    out.toByteArray()
}.getOrNull()
