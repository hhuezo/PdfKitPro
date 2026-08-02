package com.hhuezo.pdfconverter.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

enum class ImageExportFormat {
    Jpg,
    Png,
}

data class PdfToImageResult(
    val files: List<File>,
)

class PdfToImageConverter(private val context: Context) {

    fun convert(
        uri: Uri,
        pageIndices: List<Int>,
        format: ImageExportFormat,
        maxWidthPx: Int = 1600,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): PdfToImageResult {
        require(pageIndices.isNotEmpty()) { "No hay páginas para convertir" }

        val outputDir = File(context.cacheDir, "pdf_images").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("No se pudo abrir el PDF")

        val files = mutableListOf<File>()
        PdfRenderer(pfd).use { renderer ->
            pageIndices.forEachIndexed { index, pageIndex ->
                require(pageIndex in 0 until renderer.pageCount)
                val bitmap = renderPage(renderer, pageIndex, maxWidthPx)
                val extension = if (format == ImageExportFormat.Jpg) "jpg" else "png"
                val file = File(outputDir, "pagina_${pageIndex + 1}.$extension")
                FileOutputStream(file).use { out ->
                    val compressFormat = if (format == ImageExportFormat.Jpg) {
                        Bitmap.CompressFormat.JPEG
                    } else {
                        Bitmap.CompressFormat.PNG
                    }
                    val quality = if (format == ImageExportFormat.Jpg) 92 else 100
                    bitmap.compress(compressFormat, quality, out)
                }
                bitmap.recycle()
                files.add(file)
                onProgress(index + 1, pageIndices.size)
            }
        }
        return PdfToImageResult(files = files)
    }

    private fun renderPage(renderer: PdfRenderer, pageIndex: Int, maxWidthPx: Int): Bitmap {
        return renderer.openPage(pageIndex).use { page ->
            val width = maxWidthPx.coerceAtLeast(1)
            val height = (page.height.toFloat() / page.width.toFloat() * width)
                .toInt()
                .coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }
}
