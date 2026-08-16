package com.hhuezo.pdfconverter.ui.scan

import android.content.Context
import android.util.Log
import com.hhuezo.pdfconverter.util.loadOrientedBitmap
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * Adds an invisible OCR text layer to a PDF already created by the scan flow.
 * Does not change how the page images are generated.
 */
internal class ScanOcrTextLayer(context: Context) {

    private val appContext = context.applicationContext

    fun embed(imagePaths: List<String>, pdfFile: File) {
        if (imagePaths.isEmpty() || !pdfFile.exists()) return
        ensurePdfBoxInitialized(appContext)

        OnDeviceOcr().use { ocr ->
            PDDocument.load(pdfFile).use { document ->
                val font = loadEmbeddedFont(document)
                val pageCount = min(document.numberOfPages, imagePaths.size)
                for (index in 0 until pageCount) {
                    val bitmap = loadOrientedBitmap(imagePaths[index], maxSidePx = OCR_MAX_SIDE)
                        ?: continue
                    try {
                        val tokens = runCatching { ocr.recognize(bitmap) }
                            .getOrDefault(PageOcrResult.Empty)
                            .tokens
                        if (tokens.isEmpty()) continue
                        writePageText(document, document.getPage(index), font, bitmap, tokens)
                    } finally {
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }
                document.save(pdfFile)
            }
        }
    }

    private fun writePageText(
        document: PDDocument,
        page: PDPage,
        font: PDFont,
        bitmap: android.graphics.Bitmap,
        tokens: List<OcrToken>,
    ) {
        val pageW = page.cropBox.width
        val pageH = page.cropBox.height
        val scale = min(pageW / bitmap.width, pageH / bitmap.height)
        val drawW = bitmap.width * scale
        val drawH = bitmap.height * scale
        val imagePdfX = (pageW - drawW) / 2f
        val imagePdfY = (pageH - drawH) / 2f
        val scaleX = drawW / bitmap.width.coerceAtLeast(1)
        val scaleY = drawH / bitmap.height.coerceAtLeast(1)

        PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.APPEND,
            true,
            true,
        ).use { content ->
            content.setRenderingMode(RenderingMode.NEITHER)
            for (token in tokens) {
                val safe = encodeForFont(font, token.text) ?: continue
                val wordW = token.width * scaleX
                val wordH = token.height * scaleY
                if (wordW < 1f || wordH < 1f) continue

                val pageLeft = imagePdfX + token.left * scaleX
                val pageBottom = imagePdfY + (bitmap.height - token.top - token.height) * scaleY
                val fontSize = wordH.coerceIn(2f, 200f)
                val baseline = pageBottom + fontSize * 0.12f
                val nativeWidth = try {
                    font.getStringWidth(safe) / 1000f * fontSize
                } catch (_: Exception) {
                    0f
                }
                val horizScale = if (nativeWidth > 0.01f) {
                    (wordW / nativeWidth).coerceIn(0.15f, 4f)
                } else {
                    1f
                }

                try {
                    content.beginText()
                    content.setFont(font, fontSize)
                    content.setTextMatrix(
                        Matrix(horizScale, 0f, 0f, 1f, pageLeft, baseline),
                    )
                    content.showText(safe)
                    content.endText()
                } catch (error: Exception) {
                    runCatching { content.endText() }
                    Log.w(TAG, "No se pudo escribir «$safe»: ${error.message}")
                }
            }
        }
    }

    private fun encodeForFont(font: PDFont, text: String): String? {
        if (text.isBlank()) return null
        return try {
            font.encode(text)
            text
        } catch (_: Exception) {
            val filtered = buildString {
                for (ch in text) {
                    val piece = ch.toString()
                    val ok = try {
                        font.encode(piece)
                        true
                    } catch (_: Exception) {
                        false
                    }
                    if (ok) append(ch)
                }
            }.trim()
            filtered.ifBlank { null }
        }
    }

    private fun loadEmbeddedFont(document: PDDocument): PDFont {
        val fontFile = findSystemFontFile() ?: return PDType1Font.HELVETICA
        return try {
            PDType0Font.load(
                document,
                ByteArrayInputStream(fontFile.readBytes()),
                true,
            )
        } catch (error: Exception) {
            Log.w(TAG, "No se pudo incrustar ${fontFile.name}: ${error.message}")
            PDType1Font.HELVETICA
        }
    }

    private fun findSystemFontFile(): File? {
        val dir = File("/system/fonts")
        val preferred = listOf(
            "Roboto-Regular.ttf",
            "Roboto.ttf",
            "NotoSans-Regular.ttf",
            "DroidSans.ttf",
        )
        for (name in preferred) {
            val file = File(dir, name)
            if (file.exists() && file.canRead()) return file
        }
        return null
    }

    companion object {
        private const val TAG = "PdfKitProScan"
        private const val OCR_MAX_SIDE = 2500
        private val initialized = AtomicBoolean(false)

        private fun ensurePdfBoxInitialized(context: Context) {
            if (initialized.compareAndSet(false, true)) {
                PDFBoxResourceLoader.init(context)
            }
        }
    }
}
