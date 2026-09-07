package com.hhuezo.pdfconverter.util

import android.graphics.Bitmap
import kotlin.math.sqrt

/** RecordingCanvas rejects bitmaps over ~100 MB; stay under that with headroom. */
internal const val MAX_CANVAS_BITMAP_BYTES = 80L * 1024L * 1024L
internal const val MAX_BITMAP_DIMENSION = 4096

fun Bitmap.isSafeToDraw(): Boolean {
    if (isRecycled || width <= 0 || height <= 0) return false
    val bytes = runCatching { allocationByteCount.toLong() }.getOrElse { byteCount.toLong() }
    return bytes in 1..MAX_CANVAS_BITMAP_BYTES &&
        width <= MAX_BITMAP_DIMENSION &&
        height <= MAX_BITMAP_DIMENSION
}

fun clampBitmapSize(
    requestedWidth: Int,
    pageWidth: Int,
    pageHeight: Int,
): Pair<Int, Int> {
    val srcW = pageWidth.coerceAtLeast(1)
    val srcH = pageHeight.coerceAtLeast(1)
    val aspect = srcH.toFloat() / srcW.toFloat()
    var width = requestedWidth.coerceAtLeast(1)
    var height = (width * aspect).toInt().coerceAtLeast(1)

    if (width > MAX_BITMAP_DIMENSION || height > MAX_BITMAP_DIMENSION) {
        val scale = minOf(
            MAX_BITMAP_DIMENSION.toFloat() / width,
            MAX_BITMAP_DIMENSION.toFloat() / height,
        )
        width = (width * scale).toInt().coerceAtLeast(1)
        height = (height * scale).toInt().coerceAtLeast(1)
    }

    val bytes = width.toLong() * height.toLong() * 4L
    if (bytes > MAX_CANVAS_BITMAP_BYTES) {
        val scale = sqrt(MAX_CANVAS_BITMAP_BYTES.toDouble() / bytes.toDouble())
        width = (width * scale).toInt().coerceAtLeast(1)
        height = (height * scale).toInt().coerceAtLeast(1)
    }
    return width to height
}
