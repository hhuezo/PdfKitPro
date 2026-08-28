package com.hhuezo.pdfconverter.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class PdfToWordResult(
    val file: File,
    val usedOcr: Boolean,
)

class PdfToWordConverter(context: Context) {

    private val appContext = context.applicationContext

    fun convert(
        uri: Uri,
        pageIndices: List<Int>,
        outputFile: File,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): PdfToWordResult {
        require(pageIndices.isNotEmpty()) { "No hay páginas para convertir" }
        ensurePdfBoxInitialized(appContext)

        val sourceCopy = File(
            appContext.cacheDir,
            "toword_src_${System.currentTimeMillis()}.pdf",
        )
        var usedOcr = false
        try {
            copyUriToFile(uri, sourceCopy)
            val pages = ArrayList<String>(pageIndices.size)
            PDDocument.load(sourceCopy).use { document ->
                val stripper = PDFTextStripper().apply { sortByPosition = true }
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    pageIndices.forEachIndexed { index, pageIndex ->
                        require(pageIndex in 0 until document.numberOfPages)
                        val pageNumber = pageIndex + 1
                        stripper.startPage = pageNumber
                        stripper.endPage = pageNumber
                        var text = stripper.getText(document).trim()
                        if (text.isEmpty()) {
                            text = ocrPage(sourceCopy, pageIndex, recognizer)
                            if (text.isNotEmpty()) usedOcr = true
                        }
                        pages += text
                        onProgress(index + 1, pageIndices.size)
                    }
                } finally {
                    runCatching { recognizer.close() }
                }
            }
            if (pages.all { it.isBlank() }) {
                error("empty-text")
            }
            DocxWriter.write(pages, outputFile)
            return PdfToWordResult(file = outputFile, usedOcr = usedOcr)
        } finally {
            sourceCopy.delete()
        }
    }

    private fun ocrPage(
        pdfFile: File,
        pageIndex: Int,
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
    ): String {
        val pfd = android.os.ParcelFileDescriptor.open(
            pdfFile,
            android.os.ParcelFileDescriptor.MODE_READ_ONLY,
        )
        val bitmap = PdfRenderer(pfd).use { renderer ->
            renderer.openPage(pageIndex).use { page ->
                val width = OCR_WIDTH_PX
                val height = (page.height.toFloat() / page.width.toFloat() * width)
                    .toInt()
                    .coerceAtLeast(1)
                val pageBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                pageBitmap.eraseColor(Color.WHITE)
                page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                pageBitmap
            }
        }
        return try {
            val visionText = Tasks.await(
                recognizer.process(InputImage.fromBitmap(bitmap, 0)),
                OCR_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
            visionText.text.trim()
        } catch (error: Exception) {
            Log.w(TAG, "OCR de página ${pageIndex + 1} no disponible: ${error.message}")
            ""
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun copyUriToFile(uri: Uri, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            outputFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("No se pudo abrir el PDF")
        if (outputFile.length() < 5L) {
            error("El PDF está vacío o no se pudo leer")
        }
    }

    companion object {
        private const val TAG = "PdfToWord"
        private const val OCR_WIDTH_PX = 1280
        private const val OCR_TIMEOUT_SECONDS = 45L
        private val initialized = AtomicBoolean(false)

        const val DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

        private fun ensurePdfBoxInitialized(context: Context) {
            if (initialized.compareAndSet(false, true)) {
                PDFBoxResourceLoader.init(context)
            }
        }
    }
}
