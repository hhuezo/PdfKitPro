package com.hhuezo.pdfconverter.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.hhuezo.pdfconverter.util.clampBitmapSize
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.OperatorName
import com.tom_roush.pdfbox.contentstream.operator.state.Concatenate
import com.tom_roush.pdfbox.contentstream.operator.state.Restore
import com.tom_roush.pdfbox.contentstream.operator.state.Save
import com.tom_roush.pdfbox.contentstream.operator.state.SetGraphicsStateParameters
import com.tom_roush.pdfbox.contentstream.operator.state.SetMatrix
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDTransparencyGroup
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
        try {
            copyUriToFile(uri, sourceCopy)
            val pages = ArrayList<List<DocxBlock>>(pageIndices.size)
            var pageWidthTwips = 12240
            var pageHeightTwips = 15840

            PDDocument.load(sourceCopy).use { document ->
                val firstPage = document.getPage(pageIndices.first())
                val (twipsW, twipsH) = pageSizeTwips(firstPage)
                pageWidthTwips = twipsW
                pageHeightTwips = twipsH
                val contentWidthTwips =
                    (pageWidthTwips - DocxWriter.PAGE_MARGIN_TWIPS * 2).coerceAtLeast(1440)

                pageIndices.forEachIndexed { index, pageIndex ->
                    require(pageIndex in 0 until document.numberOfPages)
                    val page = document.getPage(pageIndex)
                    pages += buildPage(
                        document = document,
                        page = page,
                        pageIndex = pageIndex,
                        sourceCopy = sourceCopy,
                        contentWidthTwips = contentWidthTwips,
                    )
                    onProgress(index + 1, pageIndices.size)
                }
            }

            val hasContent = pages.any { page ->
                page.any { block ->
                    when (block) {
                        is DocxBlock.Picture -> true
                        is DocxBlock.Paragraph -> block.runs.any { it.text.isNotBlank() }
                    }
                }
            }
            if (!hasContent) error("empty-text")

            DocxWriter.write(
                DocxDocument(
                    pageWidthTwips = pageWidthTwips,
                    pageHeightTwips = pageHeightTwips,
                    pages = pages,
                ),
                outputFile,
            )
            return PdfToWordResult(file = outputFile, usedOcr = false)
        } finally {
            sourceCopy.delete()
        }
    }

    private fun buildPage(
        document: PDDocument,
        page: PDPage,
        pageIndex: Int,
        sourceCopy: File,
        contentWidthTwips: Int,
    ): List<DocxBlock> {
        val collector = PageTextCollector().apply {
            startPage = pageIndex + 1
            endPage = pageIndex + 1
            sortByPosition = true
        }
        collector.getText(document)
        val positions = collector.positions.filter { position ->
            !position.unicode.isNullOrEmpty()
        }
        val textLength = positions.sumOf { it.unicode.length }
        val images = extractImages(page)
        val pageBox = page.cropBox
        val rotation = page.rotation % 360
        val (displayWidth, displayHeight) = displayPageSize(pageBox.width, pageBox.height, rotation)
        val pageArea = displayWidth * displayHeight

        // Only rasterize when there is no extractable text. A title or a short
        // verse must stay as text on this Word page, not become a picture.
        if (textLength == 0) {
            val rendered = renderPageBitmap(sourceCopy, pageIndex)
            if (rendered != null) {
                return listOf(rendered.useEncodedPicture(preferJpeg = true).toDocxPicture())
            }
            return images
                .sortedWith(compareBy<PlacedImage> { it.y }.thenBy { it.x })
                .mapNotNull { it.toDocxPicture() }
        }

        val usableImages = images.filter { placed ->
            placed.displayWidth * placed.displayHeight <= pageArea * BACKGROUND_IMAGE_RATIO
        }
        val bands = splitColumnBands(positions, displayWidth)
        val blocks = mutableListOf<DocxBlock>()
        bands.forEachIndexed { index, band ->
            val columnBlocks = buildColumnBlocks(
                positions = positions,
                images = usableImages,
                band = band,
                contentWidthTwips = contentWidthTwips,
            )
            if (columnBlocks.isEmpty()) return@forEachIndexed
            if (index > 0 && blocks.isNotEmpty()) {
                val first = columnBlocks.first()
                if (first is DocxBlock.Paragraph) {
                    blocks += first.copy(spaceBeforeTwips = max(first.spaceBeforeTwips, 280))
                    blocks += columnBlocks.drop(1)
                } else {
                    blocks += columnBlocks
                }
            } else {
                blocks += columnBlocks
            }
        }
        return blocks
    }

    private fun buildColumnBlocks(
        positions: List<TextPosition>,
        images: List<PlacedImage>,
        band: ColumnBand,
        contentWidthTwips: Int,
    ): List<DocxBlock> {
        val colPositions = positions.filter { band.contains(glyphCenterX(it)) }
        val colImages = images.filter { image ->
            band.contains(image.x + image.displayWidth * 0.5f)
        }
        val lineItems = clusterLines(colPositions).mapNotNull { line ->
            line.toPageItem(
                columnLeft = band.start,
                columnWidth = band.width,
                contentWidthTwips = contentWidthTwips,
            )
        }
        val imageItems = colImages.mapNotNull { placed ->
            val picture = placed.toDocxPicture() ?: return@mapNotNull null
            PageItem.Image(x = placed.x, y = placed.y, picture = picture)
        }
        return (lineItems + imageItems)
            .sortedWith(compareBy<PageItem> { it.y }.thenBy { it.x })
            .map { item ->
                when (item) {
                    is PageItem.Line -> item.paragraph
                    is PageItem.Image -> item.picture
                }
            }
    }

    /**
     * Detects a two-page spread (or two text columns) by looking for an empty
     * vertical gutter near the center. Each band is then read top-to-bottom,
     * left column first, so facing pages are not interleaved.
     */
    private fun splitColumnBands(
        positions: List<TextPosition>,
        pageWidth: Float,
    ): List<ColumnBand> {
        val full = listOf(ColumnBand(start = 0f, end = pageWidth, includesEnd = true))
        if (positions.size < MIN_GLYPHS_FOR_COLUMNS || pageWidth <= 0f) return full

        val bins = COLUMN_HISTOGRAM_BINS
        val counts = IntArray(bins)
        for (position in positions) {
            val cx = glyphCenterX(position).coerceIn(0f, pageWidth - 0.01f)
            val bin = ((cx / pageWidth) * bins).toInt().coerceIn(0, bins - 1)
            counts[bin] += position.unicode.length.coerceAtLeast(1)
        }
        val quiet = max(1, positions.size / 90)
        val searchStart = (bins * 0.28f).toInt()
        val searchEnd = (bins * 0.72f).toInt()
        var bestStart = -1
        var bestEnd = -1
        var bestLen = 0
        var index = searchStart
        while (index <= searchEnd) {
            if (counts[index] <= quiet) {
                var end = index
                while (end <= searchEnd && counts[end] <= quiet) end++
                val len = end - index
                if (len > bestLen) {
                    bestLen = len
                    bestStart = index
                    bestEnd = end
                }
                index = end
            } else {
                index++
            }
        }
        if (bestLen < MIN_GUTTER_BINS) return full

        val splitX = ((bestStart + bestEnd) / 2f / bins) * pageWidth
        val left = positions.filter { glyphCenterX(it) < splitX }
        val right = positions.filter { glyphCenterX(it) >= splitX }
        val leftChars = left.sumOf { it.unicode.length }
        val rightChars = right.sumOf { it.unicode.length }
        if (leftChars < MIN_COLUMN_CHARS || rightChars < MIN_COLUMN_CHARS) return full
        return listOf(
            ColumnBand(start = 0f, end = splitX, includesEnd = false),
            ColumnBand(start = splitX, end = pageWidth, includesEnd = true),
        )
    }

    private fun glyphCenterX(position: TextPosition): Float =
        position.x + position.width.coerceAtLeast(0f) * 0.5f

    private fun extractImages(page: PDPage): List<PlacedImage> {
        return runCatching {
            val collector = PageImageCollector()
            collector.processPage(page)
            collector.images
                .filter { image ->
                    min(image.displayWidth, image.displayHeight) >= MIN_IMAGE_DISPLAY_PT &&
                        max(image.xObject.width, image.xObject.height) >= MIN_IMAGE_PX
                }
                .take(MAX_IMAGES_PER_PAGE)
        }.onFailure { error ->
            Log.w(TAG, "No se pudieron extraer imágenes: ${error.message}")
        }.getOrDefault(emptyList())
    }

    private fun clusterLines(positions: List<TextPosition>): List<TextLine> {
        if (positions.isEmpty()) return emptyList()
        val lineTolerance = positions
            .map { it.height.coerceAtLeast(1f) }
            .average()
            .toFloat()
            .coerceAtLeast(2f) * 0.6f

        val buckets = mutableListOf<MutableList<TextPosition>>()
        for (position in positions.sortedWith(compareBy<TextPosition> { it.y }.thenBy { it.x })) {
            val line = buckets.lastOrNull()
            if (line != null && abs(line.first().y - position.y) <= lineTolerance) {
                line += position
            } else {
                buckets += mutableListOf(position)
            }
        }

        var previousY = Float.NEGATIVE_INFINITY
        return buckets.map { glyphs ->
            val sorted = glyphs.sortedBy { it.x }
            val y = sorted.minOf { it.y }
            val gapFromPrevious = if (previousY.isFinite()) y - previousY else 0f
            previousY = y
            TextLine(
                glyphs = sorted,
                y = y,
                x = sorted.first().x,
                gapFromPrevious = gapFromPrevious,
            )
        }
    }

    private fun TextLine.toPageItem(
        columnLeft: Float,
        columnWidth: Float,
        contentWidthTwips: Int,
    ): PageItem.Line? {
        val runs = buildRuns(glyphs)
        if (runs.isEmpty()) return null
        val left = (glyphs.minOf { it.x } - columnLeft).coerceAtLeast(0f)
        val right = (glyphs.maxOf { it.x + it.width.coerceAtLeast(0.5f) } - columnLeft)
            .coerceAtLeast(left + 1f)
        val width = (right - left).coerceAtLeast(1f)
        val center = (left + right) / 2f
        val avgHeight = glyphs
            .map { it.height.coerceAtLeast(it.fontSizeInPt * 0.7f) }
            .average()
            .toFloat()
            .coerceAtLeast(8f)

        val align = when {
            left > columnWidth * 0.42f &&
                right > columnWidth * 0.80f &&
                width < columnWidth * 0.58f -> DocxAlign.RIGHT
            abs(center - columnWidth / 2f) < columnWidth * 0.08f &&
                left > columnWidth * 0.16f &&
                width < columnWidth * 0.72f -> DocxAlign.CENTER
            else -> DocxAlign.LEFT
        }
        val indentTwips = if (align == DocxAlign.LEFT && left > columnWidth * 0.08f) {
            ((left / columnWidth) * contentWidthTwips)
                .roundToInt()
                .coerceIn(0, contentWidthTwips / 2)
        } else {
            0
        }
        val spaceBefore = when {
            gapFromPrevious > avgHeight * 2.2f -> 240
            gapFromPrevious > avgHeight * 1.5f -> 120
            else -> 0
        }

        return PageItem.Line(
            x = left,
            y = y,
            paragraph = DocxBlock.Paragraph(
                runs = runs,
                align = align,
                indentTwips = indentTwips,
                spaceBeforeTwips = spaceBefore,
                spaceAfterTwips = 40,
            ),
        )
    }

    private fun buildRuns(glyphs: List<TextPosition>): List<DocxRun> {
        val runs = mutableListOf<DocxRun>()
        val buffer = StringBuilder()
        var currentSize = 11f
        var currentBold = false
        var currentItalic = false
        var currentFamily = "Calibri"
        var previous: TextPosition? = null

        fun flush() {
            val text = buffer.toString()
            if (text.isNotEmpty()) {
                runs += DocxRun(
                    text = text,
                    fontSizePt = currentSize,
                    bold = currentBold,
                    italic = currentItalic,
                    fontFamily = currentFamily,
                )
            }
            buffer.clear()
        }

        for (glyph in glyphs) {
            val unicode = glyph.unicode ?: continue
            val size = effectiveFontPt(glyph)
            val (bold, italic) = fontStyle(glyph.font)
            val family = mapFontFamily(glyph.font?.name)
            val prev = previous
            if (prev != null) {
                val gap = glyph.x - (prev.x + prev.width)
                val spaceWidth = prev.widthOfSpace.takeIf { it > 0.4f }
                    ?: (effectiveFontPt(prev) * 0.28f)
                if (gap > spaceWidth * 0.35f) {
                    val spaces = (gap / spaceWidth).roundToInt().coerceIn(1, 8)
                    buffer.append(" ".repeat(spaces))
                }
            }
            val styleChanged = buffer.isNotEmpty() &&
                (abs(size - currentSize) >= 0.6f ||
                    bold != currentBold ||
                    italic != currentItalic ||
                    family != currentFamily)
            if (styleChanged) flush()
            if (buffer.isEmpty()) {
                currentSize = size
                currentBold = bold
                currentItalic = italic
                currentFamily = family
            }
            buffer.append(unicode)
            previous = glyph
        }
        flush()
        return runs.filter { it.text.isNotEmpty() }
    }

    private fun PlacedImage.toDocxPicture(): DocxBlock.Picture? {
        return runCatching {
            if (xObject.isStencil) return@runCatching null
            val longSide = max(xObject.width, xObject.height)
            val subsample = max(1, longSide / MAX_IMAGE_LONG_SIDE_PX)
            val bitmap = xObject.getImage(null, subsample) ?: return@runCatching null
            if (bitmap.width < MIN_IMAGE_PX && bitmap.height < MIN_IMAGE_PX) return@runCatching null
            bitmap.toEncodedPicture().toDocxPicture()
        }.onFailure { error ->
            Log.w(TAG, "No se pudo decodificar una imagen: ${error.message}")
        }.getOrNull()
    }

    private fun renderPageBitmap(pdfFile: File, pageIndex: Int): Bitmap? {
        return runCatching {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd).use { renderer ->
                renderer.openPage(pageIndex).use { page ->
                    val (width, height) = clampBitmapSize(
                        requestedWidth = PAGE_RENDER_WIDTH_PX,
                        pageWidth = page.width,
                        pageHeight = page.height,
                    )
                    val pageBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    pageBitmap.eraseColor(Color.WHITE)
                    page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pageBitmap
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "No se pudo renderizar la página ${pageIndex + 1}: ${error.message}")
        }.getOrNull()
    }

    private fun Bitmap.useEncodedPicture(preferJpeg: Boolean): EncodedPicture {
        return try {
            toEncodedPicture(preferJpeg)
        } finally {
            if (!isRecycled) recycle()
        }
    }

    private fun EncodedPicture.toDocxPicture(): DocxBlock.Picture =
        DocxBlock.Picture(
            bytes = bytes,
            widthPx = widthPx,
            heightPx = heightPx,
            extension = extension,
        )

    private fun Bitmap.toEncodedPicture(preferJpeg: Boolean = false): EncodedPicture {
        val useJpeg = preferJpeg || (width * height > JPEG_PIXEL_THRESHOLD)
        val stream = ByteArrayOutputStream()
        if (useJpeg) {
            compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        } else {
            compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return EncodedPicture(
            bytes = stream.toByteArray(),
            widthPx = width,
            heightPx = height,
            extension = if (useJpeg) "jpeg" else "png",
        )
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

    private class PageTextCollector : PDFTextStripper() {
        val positions = mutableListOf<TextPosition>()

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            positions += textPositions
        }
    }

    private class PageImageCollector : PDFStreamEngine() {
        val images = mutableListOf<PlacedImage>()

        init {
            addOperator(Concatenate())
            addOperator(SetGraphicsStateParameters())
            addOperator(Save())
            addOperator(Restore())
            addOperator(SetMatrix())
        }

        override fun processOperator(operator: Operator, operands: List<COSBase>) {
            if (operator.name != OperatorName.DRAW_OBJECT) {
                super.processOperator(operator, operands)
                return
            }
            val objectName = operands.firstOrNull() as? COSName ?: return
            val resources = resources ?: return
            when (val xobject = resources.getXObject(objectName)) {
                is PDImageXObject -> {
                    val ctm = graphicsState.currentTransformationMatrix
                    val rect = mappedUnitSquare(ctm)
                    val crop = currentPage?.cropBox ?: return
                    val rotation = currentPage?.rotation ?: 0
                    val (x, y, displayW, displayH) = toDisplayRect(
                        pdfLeft = rect[0],
                        pdfBottom = rect[1],
                        pdfRight = rect[2],
                        pdfTop = rect[3],
                        cropLeft = crop.lowerLeftX,
                        cropBottom = crop.lowerLeftY,
                        cropWidth = crop.width,
                        cropHeight = crop.height,
                        rotation = rotation,
                    )
                    images += PlacedImage(
                        x = x,
                        y = y,
                        displayWidth = displayW,
                        displayHeight = displayH,
                        xObject = xobject,
                    )
                }
                is PDTransparencyGroup -> showTransparencyGroup(xobject)
                is PDFormXObject -> showForm(xobject)
            }
        }
    }

    private data class ColumnBand(
        val start: Float,
        val end: Float,
        val includesEnd: Boolean,
    ) {
        val width: Float get() = (end - start).coerceAtLeast(1f)

        fun contains(x: Float): Boolean {
            if (x < start) return false
            return if (includesEnd) x <= end else x < end
        }
    }

    private data class TextLine(
        val glyphs: List<TextPosition>,
        val y: Float,
        val x: Float,
        val gapFromPrevious: Float,
    )

    private sealed class PageItem {
        abstract val x: Float
        abstract val y: Float

        data class Line(
            override val x: Float,
            override val y: Float,
            val paragraph: DocxBlock.Paragraph,
        ) : PageItem()

        data class Image(
            override val x: Float,
            override val y: Float,
            val picture: DocxBlock.Picture,
        ) : PageItem()
    }

    private data class PlacedImage(
        val x: Float,
        val y: Float,
        val displayWidth: Float,
        val displayHeight: Float,
        val xObject: PDImageXObject,
    )

    private data class EncodedPicture(
        val bytes: ByteArray,
        val widthPx: Int,
        val heightPx: Int,
        val extension: String,
    )

    companion object {
        private const val TAG = "PdfToWord"
        private const val PAGE_RENDER_WIDTH_PX = 1400
        private const val MIN_IMAGE_PX = 32
        private const val MIN_IMAGE_DISPLAY_PT = 10f
        private const val MAX_IMAGES_PER_PAGE = 12
        private const val MAX_IMAGE_LONG_SIDE_PX = 1600
        private const val BACKGROUND_IMAGE_RATIO = 0.68f
        private const val JPEG_PIXEL_THRESHOLD = 120_000
        private const val JPEG_QUALITY = 82
        private const val COLUMN_HISTOGRAM_BINS = 48
        private const val MIN_GUTTER_BINS = 2
        private const val MIN_GLYPHS_FOR_COLUMNS = 30
        private const val MIN_COLUMN_CHARS = 25
        private val initialized = AtomicBoolean(false)

        const val DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

        private fun ensurePdfBoxInitialized(context: Context) {
            if (initialized.compareAndSet(false, true)) {
                PDFBoxResourceLoader.init(context)
            }
        }

        private fun pageSizeTwips(page: PDPage): Pair<Int, Int> {
            val box = page.cropBox
            val rotation = page.rotation % 360
            val (widthPt, heightPt) = displayPageSize(box.width, box.height, rotation)
            val width = (widthPt * 20f).roundToInt().coerceIn(2880, 31680)
            val height = (heightPt * 20f).roundToInt().coerceIn(2880, 31680)
            return width to height
        }

        private fun displayPageSize(
            cropWidth: Float,
            cropHeight: Float,
            rotation: Int,
        ): Pair<Float, Float> {
            return if (rotation == 90 || rotation == 270) {
                cropHeight to cropWidth
            } else {
                cropWidth to cropHeight
            }
        }

        private fun effectiveFontPt(position: TextPosition): Float {
            val pt = position.fontSizeInPt
            val fromScale = position.xScale
            val fromHeight = position.height
            val candidate = when {
                pt >= 5f -> pt
                fromScale >= 5f -> fromScale
                fromHeight >= 4f -> fromHeight
                else -> 11f
            }
            return candidate.coerceIn(6f, 72f)
        }

        private fun fontStyle(font: PDFont?): Pair<Boolean, Boolean> {
            val name = font?.name.orEmpty()
            val italic = BOLD_ITALIC_ITALIC.containsMatchIn(name)
            val bold = BOLD_ITALIC_BOLD.containsMatchIn(name)
            return bold to italic
        }

        private fun mapFontFamily(pdfFontName: String?): String {
            val name = pdfFontName.orEmpty().lowercase()
            return when {
                "courier" in name || "mono" in name -> "Courier New"
                "times" in name || "georgia" in name || "garamond" in name -> "Times New Roman"
                "arial" in name || "helvetica" in name || "roboto" in name ||
                    "calibri" in name || "sans" in name -> "Arial"
                else -> "Calibri"
            }
        }

        private fun mappedUnitSquare(ctm: Matrix): FloatArray {
            val xs = floatArrayOf(
                ctm.translateX,
                ctm.translateX + ctm.scaleX,
                ctm.translateX + ctm.shearX,
                ctm.translateX + ctm.scaleX + ctm.shearX,
            )
            val ys = floatArrayOf(
                ctm.translateY,
                ctm.translateY + ctm.shearY,
                ctm.translateY + ctm.scaleY,
                ctm.translateY + ctm.scaleY + ctm.shearY,
            )
            return floatArrayOf(xs.min(), ys.min(), xs.max(), ys.max())
        }

        private data class DisplayRect(
            val x: Float,
            val y: Float,
            val width: Float,
            val height: Float,
        )

        private fun toDisplayRect(
            pdfLeft: Float,
            pdfBottom: Float,
            pdfRight: Float,
            pdfTop: Float,
            cropLeft: Float,
            cropBottom: Float,
            cropWidth: Float,
            cropHeight: Float,
            rotation: Int,
        ): DisplayRect {
            val rx = pdfLeft - cropLeft
            val ry = pdfBottom - cropBottom
            val rw = (pdfRight - pdfLeft).coerceAtLeast(1f)
            val rh = (pdfTop - pdfBottom).coerceAtLeast(1f)
            return when (rotation % 360) {
                90 -> DisplayRect(
                    x = ry,
                    y = cropWidth - rx - rw,
                    width = rh,
                    height = rw,
                )
                180 -> DisplayRect(
                    x = cropWidth - rx - rw,
                    y = ry,
                    width = rw,
                    height = rh,
                )
                270 -> DisplayRect(
                    x = cropHeight - ry - rh,
                    y = rx,
                    width = rh,
                    height = rw,
                )
                else -> DisplayRect(
                    x = rx,
                    y = cropHeight - ry - rh,
                    width = rw,
                    height = rh,
                )
            }
        }

        private val BOLD_ITALIC_BOLD =
            Regex("""(?i)(bold|black|heavy|semibold|demibold|extrabold|boldmt|-bd\b)""")
        private val BOLD_ITALIC_ITALIC =
            Regex("""(?i)(italic|oblique|obli)""")
    }
}
