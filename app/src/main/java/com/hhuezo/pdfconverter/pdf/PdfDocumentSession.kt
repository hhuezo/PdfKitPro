package com.hhuezo.pdfconverter.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages a single open PDF via [PdfRenderer].
 * Rendering is synchronized because PdfRenderer is not thread-safe.
 */
class PdfDocumentSession(
    context: Context,
    val uri: Uri,
) : Closeable {

    private val appContext = context.applicationContext
    private val fileDescriptor: ParcelFileDescriptor =
        appContext.contentResolver.openFileDescriptor(uri, "r")
            ?: error("No se pudo abrir el descriptor del PDF")

    private val renderer = PdfRenderer(fileDescriptor)
    private val lock = Any()
    private val bitmapCache = ConcurrentHashMap<Int, Bitmap>()

    val pageCount: Int = renderer.pageCount

    fun pageAspectRatio(pageIndex: Int): Float = synchronized(lock) {
        renderer.openPage(pageIndex).use { page ->
            page.width.toFloat() / page.height.toFloat()
        }
    }

    fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap {
        require(pageIndex in 0 until pageCount)
        require(targetWidthPx > 0)

        bitmapCache[pageIndex]?.let { cached ->
            if (cached.width >= targetWidthPx && !cached.isRecycled) {
                return cached
            }
        }

        val rendered = synchronized(lock) {
            renderer.openPage(pageIndex).use { page ->
                val width = targetWidthPx
                val height = (page.height.toFloat() / page.width.toFloat() * width).toInt()
                    .coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }

        bitmapCache[pageIndex] = rendered
        return rendered
    }

    fun clearCache() {
        bitmapCache.values.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        bitmapCache.clear()
    }

    override fun close() {
        clearCache()
        synchronized(lock) {
            renderer.close()
            fileDescriptor.close()
        }
    }
}

