package com.hhuezo.pdfconverter.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Loads a bitmap from disk applying EXIF orientation so previews match how the photo was taken.
 */
fun loadOrientedBitmap(path: String, maxSidePx: Int = 512): Bitmap? {
    val file = File(path)
    if (!file.exists()) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)

    var sample = 1
    val longest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
    while (longest / sample > maxSidePx) {
        sample *= 2
    }

    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = BitmapFactory.decodeFile(path, opts) ?: return null

    val orientation = runCatching {
        ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        else -> return decoded
    }

    return try {
        val rotated = Bitmap.createBitmap(
            decoded,
            0,
            0,
            decoded.width,
            decoded.height,
            matrix,
            true,
        )
        if (rotated !== decoded && !decoded.isRecycled) decoded.recycle()
        rotated
    } catch (_: Exception) {
        decoded
    }
}
