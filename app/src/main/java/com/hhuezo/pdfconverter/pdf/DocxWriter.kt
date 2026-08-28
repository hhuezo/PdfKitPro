package com.hhuezo.pdfconverter.pdf

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

internal enum class DocxAlign { LEFT, CENTER, RIGHT }

internal data class DocxRun(
    val text: String,
    val fontSizePt: Float = 11f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val fontFamily: String = "Calibri",
)

internal sealed class DocxBlock {
    data class Paragraph(
        val runs: List<DocxRun>,
        val align: DocxAlign = DocxAlign.LEFT,
        val indentTwips: Int = 0,
        val spaceBeforeTwips: Int = 0,
        val spaceAfterTwips: Int = 40,
    ) : DocxBlock()

    data class Picture(
        val bytes: ByteArray,
        val widthPx: Int,
        val heightPx: Int,
        val extension: String,
    ) : DocxBlock()
}

internal data class DocxDocument(
    val pageWidthTwips: Int,
    val pageHeightTwips: Int,
    val pages: List<List<DocxBlock>>,
)

/** Minimal Office Open XML (.docx) writer. No Apache POI. */
object DocxWriter {

    internal fun write(document: DocxDocument, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val pictures = mutableListOf<PackedPicture>()
        val bodyXml = buildBody(document, pictures)

        ZipOutputStream(outputFile.outputStream().buffered()).use { zip ->
            zip.putXml("[Content_Types].xml", contentTypesXml(pictures))
            zip.putXml("_rels/.rels", rootRels)
            zip.putXml("word/_rels/document.xml.rels", documentRelsXml(pictures))
            zip.putXml("word/document.xml", wrapDocument(bodyXml))
            pictures.forEach { picture ->
                zip.putBytes("word/${picture.zipTarget}", picture.bytes)
            }
        }
        if (!outputFile.exists() || outputFile.length() < 32L) {
            error("No se pudo generar el archivo Word")
        }
    }

    private fun buildBody(
        document: DocxDocument,
        pictures: MutableList<PackedPicture>,
    ): String {
        val margin = PAGE_MARGIN_TWIPS
        val contentWidthTwips =
            (document.pageWidthTwips - margin * 2).coerceAtLeast(1440)
        val contentHeightTwips =
            (document.pageHeightTwips - margin * 2).coerceAtLeast(1440)

        return buildString {
            document.pages.forEachIndexed { pageIndex, blocks ->
                if (pageIndex > 0) append(pageBreakParagraph)
                if (blocks.isEmpty()) {
                    append(emptyParagraph)
                } else {
                    blocks.forEach { block ->
                        when (block) {
                            is DocxBlock.Paragraph -> append(paragraphXml(block))
                            is DocxBlock.Picture -> {
                                val packed = packPicture(block, pictures.size + 1)
                                pictures += packed
                                append(
                                    pictureParagraphXml(
                                        packed = packed,
                                        widthPx = block.widthPx,
                                        heightPx = block.heightPx,
                                        maxWidthEmu = twipsToEmu(contentWidthTwips),
                                        maxHeightEmu = twipsToEmu(contentHeightTwips),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            append(sectionProperties(document.pageWidthTwips, document.pageHeightTwips))
        }
    }

    private fun paragraphXml(paragraph: DocxBlock.Paragraph): String {
        val runsXml = paragraph.runs.joinToString("") { runXml(it) }
        if (runsXml.isEmpty()) return emptyParagraph
        return buildString {
            append("    <w:p>\n      <w:pPr>\n")
            append("        <w:spacing w:before=\"")
            append(paragraph.spaceBeforeTwips.coerceAtLeast(0))
            append("\" w:after=\"")
            append(paragraph.spaceAfterTwips.coerceAtLeast(0))
            append("\"/>\n")
            if (paragraph.align != DocxAlign.LEFT) {
                append("        <w:jc w:val=\"")
                append(if (paragraph.align == DocxAlign.CENTER) "center" else "right")
                append("\"/>\n")
            }
            if (paragraph.indentTwips > 0 && paragraph.align == DocxAlign.LEFT) {
                append("        <w:ind w:left=\"")
                append(paragraph.indentTwips)
                append("\"/>\n")
            }
            append("      </w:pPr>\n")
            append(runsXml)
            append("    </w:p>\n")
        }
    }

    private fun runXml(run: DocxRun): String {
        val text = escapeXml(sanitize(run.text))
        if (text.isEmpty()) return ""
        val halfPoints = (run.fontSizePt * 2f).roundToInt().coerceIn(12, 144)
        val family = escapeXml(run.fontFamily.ifBlank { "Calibri" })
        return buildString {
            append("      <w:r>\n        <w:rPr>\n")
            append("          <w:rFonts w:ascii=\"")
            append(family)
            append("\" w:hAnsi=\"")
            append(family)
            append("\" w:cs=\"")
            append(family)
            append("\"/>\n")
            if (run.bold) append("          <w:b/>\n")
            if (run.italic) append("          <w:i/>\n")
            append("          <w:sz w:val=\"")
            append(halfPoints)
            append("\"/>\n          <w:szCs w:val=\"")
            append(halfPoints)
            append("\"/>\n        </w:rPr>\n")
            append("        <w:t xml:space=\"preserve\">")
            append(text)
            append("</w:t>\n      </w:r>\n")
        }
    }

    private fun packPicture(picture: DocxBlock.Picture, index: Int): PackedPicture {
        val ext = when (picture.extension.lowercase()) {
            "jpg", "jpeg" -> "jpeg"
            else -> "png"
        }
        return PackedPicture(
            relId = "rIdImg$index",
            zipTarget = "media/image$index.$ext",
            bytes = picture.bytes,
            contentType = if (ext == "jpeg") "image/jpeg" else "image/png",
        )
    }

    private fun pictureParagraphXml(
        packed: PackedPicture,
        widthPx: Int,
        heightPx: Int,
        maxWidthEmu: Long,
        maxHeightEmu: Long,
    ): String {
        var cx = pxToEmu(widthPx.coerceAtLeast(1))
        var cy = pxToEmu(heightPx.coerceAtLeast(1))
        if (cx > maxWidthEmu && cx > 0L) {
            cy = cy * maxWidthEmu / cx
            cx = maxWidthEmu
        }
        if (cy > maxHeightEmu && cy > 0L) {
            cx = cx * maxHeightEmu / cy
            cy = maxHeightEmu
        }
        cx = cx.coerceAtLeast(9525L)
        cy = cy.coerceAtLeast(9525L)
        val docPrId = packed.relId.removePrefix("rIdImg")
        return """    <w:p>
      <w:r>
        <w:drawing>
          <wp:inline distT="0" distB="0" distL="0" distR="0">
            <wp:extent cx="$cx" cy="$cy"/>
            <wp:effectExtent l="0" t="0" r="0" b="0"/>
            <wp:docPr id="$docPrId" name="Picture $docPrId"/>
            <wp:cNvGraphicFramePr>
              <a:graphicFrameLocks xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" noChangeAspect="1"/>
            </wp:cNvGraphicFramePr>
            <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                <pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
                  <pic:nvPicPr>
                    <pic:cNvPr id="0" name="${packed.zipTarget.substringAfterLast('/')}"/>
                    <pic:cNvPicPr/>
                  </pic:nvPicPr>
                  <pic:blipFill>
                    <a:blip r:embed="${packed.relId}"/>
                    <a:stretch>
                      <a:fillRect/>
                    </a:stretch>
                  </pic:blipFill>
                  <pic:spPr>
                    <a:xfrm>
                      <a:off x="0" y="0"/>
                      <a:ext cx="$cx" cy="$cy"/>
                    </a:xfrm>
                    <a:prstGeom prst="rect">
                      <a:avLst/>
                    </a:prstGeom>
                  </pic:spPr>
                </pic:pic>
              </a:graphicData>
            </a:graphic>
          </wp:inline>
        </w:drawing>
      </w:r>
    </w:p>
"""
    }

    private fun wrapDocument(body: String): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing">
  <w:body>
$body
  </w:body>
</w:document>
"""

    private fun sectionProperties(widthTwips: Int, heightTwips: Int): String {
        val landscape = widthTwips > heightTwips
        val orient = if (landscape) """ w:orient="landscape"""" else ""
        return """    <w:sectPr>
      <w:pgSz w:w="$widthTwips" w:h="$heightTwips"$orient/>
      <w:pgMar w:top="$PAGE_MARGIN_TWIPS" w:right="$PAGE_MARGIN_TWIPS" w:bottom="$PAGE_MARGIN_TWIPS" w:left="$PAGE_MARGIN_TWIPS" w:header="720" w:footer="720"/>
    </w:sectPr>
"""
    }

    private fun contentTypesXml(pictures: List<PackedPicture>): String {
        val defaults = buildString {
            appendLine("""  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
            appendLine("""  <Default Extension="xml" ContentType="application/xml"/>""")
            if (pictures.any { it.contentType == "image/png" }) {
                appendLine("""  <Default Extension="png" ContentType="image/png"/>""")
            }
            if (pictures.any { it.contentType == "image/jpeg" }) {
                appendLine("""  <Default Extension="jpeg" ContentType="image/jpeg"/>""")
            }
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
$defaults  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>
"""
    }

    private fun documentRelsXml(pictures: List<PackedPicture>): String {
        val imageRels = pictures.joinToString("\n") { picture ->
            """  <Relationship Id="${picture.relId}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="${picture.zipTarget}"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
$imageRels
</Relationships>
"""
    }

    private fun ZipOutputStream.putXml(path: String, xml: String) {
        putNextEntry(ZipEntry(path))
        write(xml.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.putBytes(path: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(path))
        write(bytes)
        closeEntry()
    }

    private fun sanitize(text: String): String {
        return buildString(text.length) {
            text.forEach { ch ->
                val code = ch.code
                if (ch == '\t' || ch == '\n' || code in 0x20..0xD7FF || code in 0xE000..0xFFFD) {
                    append(ch)
                }
            }
        }
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun pxToEmu(px: Int): Long = px.toLong() * EMU_PER_INCH / PX_DPI

    private fun twipsToEmu(twips: Int): Long = twips.toLong() * EMU_PER_TWIP

    private data class PackedPicture(
        val relId: String,
        val zipTarget: String,
        val bytes: ByteArray,
        val contentType: String,
    )

    private const val emptyParagraph = "    <w:p/>\n"
    private const val pageBreakParagraph = """    <w:p>
      <w:r>
        <w:br w:type="page"/>
      </w:r>
    </w:p>
"""
    private const val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>
"""

    const val PAGE_MARGIN_TWIPS = 720
    private const val EMU_PER_INCH = 914400L
    private const val EMU_PER_TWIP = 635L
    private const val PX_DPI = 96L
}
