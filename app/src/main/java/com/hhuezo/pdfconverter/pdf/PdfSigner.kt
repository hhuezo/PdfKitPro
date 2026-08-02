package com.hhuezo.pdfconverter.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

data class PdfStampOverlay(
    val pageIndex: Int,
    /** Normalized left edge (0..1) in the upright page as shown by PdfRenderer. */
    val left: Float,
    /** Normalized top edge (0..1) in the upright page as shown by PdfRenderer. */
    val top: Float,
    /** Normalized width (0..1 of displayed page width). */
    val width: Float,
    /** Normalized height (0..1 of displayed page height). */
    val height: Float,
    val bitmap: Bitmap,
)

/**
 * Stamps signature/text bitmaps onto the original PDF page content streams.
 * Does not rasterize pages — only appends image XObjects.
 *
 * Coordinates match Android [PdfRenderer] display space (origin top-left, upright page).
 */
class PdfSigner(context: Context) {

    private val appContext = context.applicationContext

    fun applyStamps(uri: Uri, overlays: List<PdfStampOverlay>, outputFile: File) {
        ensurePdfBoxInitialized(appContext)

        val sourceCopy = File(appContext.cacheDir, "sign_src_${System.currentTimeMillis()}.pdf")
        try {
            copyUriToFile(uri, sourceCopy)
            if (overlays.isEmpty()) {
                sourceCopy.copyTo(outputFile, overwrite = true)
                return
            }

            // PdfRenderer sizes are the same coordinate space used by the Compose preview.
            val displaySizes = readRendererPageSizes(sourceCopy)

            PDDocument.load(sourceCopy).use { document ->
                val byPage = overlays.groupBy { it.pageIndex }
                byPage.forEach { (pageIndex, pageOverlays) ->
                    if (pageIndex !in 0 until document.numberOfPages) return@forEach
                    val page = document.getPage(pageIndex)
                    val displaySize = displaySizes[pageIndex]
                        ?: displaySizeFromPage(page)
                    stampPage(document, page, pageOverlays, displaySize)
                }

                outputFile.parentFile?.mkdirs()
                if (outputFile.exists()) outputFile.delete()
                document.save(outputFile)
            }
        } finally {
            sourceCopy.delete()
        }
    }

    private fun stampPage(
        document: PDDocument,
        page: PDPage,
        overlays: List<PdfStampOverlay>,
        displaySize: Pair<Float, Float>,
    ) {
        PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.APPEND,
            true,
            true,
        ).use { content ->
            overlays.forEach { overlay ->
                if (overlay.bitmap.isRecycled) return@forEach
                val (dispW, dispH) = displaySize
                val leftPx = overlay.left.coerceIn(0f, 1f) * dispW
                val topPx = overlay.top.coerceIn(0f, 1f) * dispH
                val widthPx = overlay.width.coerceIn(0.01f, 1f) * dispW
                val heightPx = overlay.height.coerceIn(0.01f, 1f) * dispH

                val preparedBitmap = prepareBitmapForPageRotation(overlay.bitmap, page.rotation)
                try {
                    val pngBytes = bitmapToPng(preparedBitmap) ?: return@forEach
                    val pdImage = PDImageXObject.createFromByteArray(document, pngBytes, "sig")
                    val rect = displayRectToPdfRect(
                        page = page,
                        leftPx = leftPx,
                        topPx = topPx,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        displayW = dispW,
                        displayH = dispH,
                    )
                    content.drawImage(
                        pdImage,
                        rect.lowerLeftX,
                        rect.lowerLeftY,
                        rect.width,
                        rect.height,
                    )
                } finally {
                    if (preparedBitmap !== overlay.bitmap && !preparedBitmap.isRecycled) {
                        preparedBitmap.recycle()
                    }
                }
            }
        }
    }

    /**
     * Converts a rectangle from PdfRenderer display space (top-left origin, upright)
     * into an axis-aligned PDF user-space rectangle on the page crop box.
     */
    private fun displayRectToPdfRect(
        page: PDPage,
        leftPx: Float,
        topPx: Float,
        widthPx: Float,
        heightPx: Float,
        displayW: Float,
        displayH: Float,
    ): PDRectangle {
        val corners = listOf(
            leftPx to topPx,
            leftPx + widthPx to topPx,
            leftPx to topPx + heightPx,
            leftPx + widthPx to topPx + heightPx,
        ).map { (x, y) -> displayPointToPdf(page, x, y, displayW, displayH) }

        val minX = corners.minOf { it.first }
        val maxX = corners.maxOf { it.first }
        val minY = corners.minOf { it.second }
        val maxY = corners.maxOf { it.second }

        return PDRectangle(minX, minY, max(1f, maxX - minX), max(1f, maxY - minY))
    }

    /**
     * Maps a point in PdfRenderer page pixels/points (origin top-left of upright page)
     * to PDF user space.
     */
    private fun displayPointToPdf(
        page: PDPage,
        x: Float,
        y: Float,
        displayW: Float,
        displayH: Float,
    ): Pair<Float, Float> {
        val crop = page.cropBox
        val rotation = ((page.rotation % 360) + 360) % 360

        // Normalize against the renderer size, then scale into crop box display axes.
        val nx = (x / displayW.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val ny = (y / displayH.coerceAtLeast(1f)).coerceIn(0f, 1f)

        val cropW = crop.width
        val cropH = crop.height
        val llx = crop.lowerLeftX
        val lly = crop.lowerLeftY
        val urx = crop.upperRightX
        val ury = crop.upperRightY

        return when (rotation) {
            90 -> {
                // Upright display: width=cropH, height=cropW
                val dx = nx * cropH
                val dy = ny * cropW
                (llx + dy) to (ury - dx)
            }
            180 -> {
                val dx = nx * cropW
                val dy = ny * cropH
                (urx - dx) to (ury - dy)
            }
            270 -> {
                val dx = nx * cropH
                val dy = ny * cropW
                (urx - dy) to (lly + dx)
            }
            else -> {
                val dx = nx * cropW
                val dy = ny * cropH
                (llx + dx) to (ury - dy)
            }
        }
    }

    /**
     * Page /Rotate causes viewers to rotate content. Counter-rotate the stamp bitmap
     * so it still appears upright in the PdfRenderer-style preview.
     */
    private fun prepareBitmapForPageRotation(source: Bitmap, pageRotation: Int): Bitmap {
        val rotation = ((pageRotation % 360) + 360) % 360
        if (rotation == 0) return source
        // Counter-rotate so that after the viewer applies page.rotation it looks upright.
        val counterClockwise = (360 - rotation) % 360
        val matrix = Matrix().apply { postRotate(counterClockwise.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun displaySizeFromPage(page: PDPage): Pair<Float, Float> {
        val crop = page.cropBox
        val rotation = ((page.rotation % 360) + 360) % 360
        return if (rotation == 90 || rotation == 270) {
            crop.height to crop.width
        } else {
            crop.width to crop.height
        }
    }

    private fun readRendererPageSizes(file: File): Map<Int, Pair<Float, Float>> {
        val sizes = mutableMapOf<Int, Pair<Float, Float>>()
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            sizes[i] = page.width.toFloat() to page.height.toFloat()
                        }
                    }
                }
            }
        }
        return sizes
    }

    private fun bitmapToPng(bitmap: Bitmap): ByteArray? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val copy = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        }
        return ByteArrayOutputStream().use { out ->
            if (!copy.compress(Bitmap.CompressFormat.PNG, 100, out)) return null
            out.toByteArray()
        }
    }

    private fun copyUriToFile(uri: Uri, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            outputFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("No se pudo copiar el PDF original")
        if (outputFile.length() < 5L) {
            error("El PDF original está vacío o no se pudo leer")
        }
    }

    companion object {
        private val initialized = AtomicBoolean(false)

        private fun ensurePdfBoxInitialized(context: Context) {
            if (initialized.compareAndSet(false, true)) {
                PDFBoxResourceLoader.init(context)
            }
        }

        fun textBitmap(
            text: String,
            textSizePx: Float = 72f,
            color: Int = android.graphics.Color.BLACK,
        ): Bitmap {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                this.textSize = textSizePx
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val padding = (textSizePx * 0.25f).toInt().coerceAtLeast(8)
            val width = (paint.measureText(text) + padding * 2).toInt().coerceAtLeast(1)
            val fm = paint.fontMetrics
            val height = ((fm.bottom - fm.top) + padding * 2).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawText(text, padding.toFloat(), padding - fm.top, paint)
            return bitmap
        }
    }
}
