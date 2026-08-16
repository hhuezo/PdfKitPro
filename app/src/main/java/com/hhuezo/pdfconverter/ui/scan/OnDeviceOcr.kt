package com.hhuezo.pdfconverter.ui.scan

import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

internal data class OcrToken(
    val text: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

internal data class PageOcrResult(
    val tokens: List<OcrToken>,
) {
    fun isEmpty(): Boolean = tokens.isEmpty()

    companion object {
        val Empty = PageOcrResult(tokens = emptyList())
    }
}

/** On-device Latin OCR (ML Kit bundled). No network and no Cloud API. */
internal class OnDeviceOcr : AutoCloseable {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun recognize(bitmap: Bitmap): PageOcrResult {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return PageOcrResult.Empty
        }

        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = try {
            Tasks.await(recognizer.process(image), TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (error: Exception) {
            Log.w(TAG, "OCR local no disponible: ${error.message}")
            return PageOcrResult.Empty
        }

        val tokens = buildList {
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    for (element in line.elements) {
                        val box = element.boundingBox ?: continue
                        val value = sanitize(element.text)
                        if (value.isEmpty()) continue
                        val width = box.width().toFloat()
                        val height = box.height().toFloat()
                        if (width < 1f || height < 1f) continue
                        add(
                            OcrToken(
                                text = value,
                                left = box.left.toFloat(),
                                top = box.top.toFloat(),
                                width = width,
                                height = height,
                            ),
                        )
                    }
                }
            }
        }
        return PageOcrResult(tokens)
    }

    override fun close() {
        runCatching { recognizer.close() }
    }

    companion object {
        private const val TAG = "PdfKitProScan"
        private const val TIMEOUT_SECONDS = 45L

        private fun sanitize(raw: String): String {
            val builder = StringBuilder(raw.length)
            for (ch in raw) {
                when {
                    ch == '\n' || ch == '\r' || ch == '\t' -> builder.append(' ')
                    ch.isISOControl() -> Unit
                    else -> builder.append(ch)
                }
            }
            return builder.toString().trim()
        }
    }
}
