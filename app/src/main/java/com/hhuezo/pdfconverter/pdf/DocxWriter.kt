package com.hhuezo.pdfconverter.pdf

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Minimal Office Open XML (.docx) writer. No Apache POI. */
object DocxWriter {

    fun write(pages: List<String>, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()
        ZipOutputStream(outputFile.outputStream().buffered()).use { zip ->
            zip.putXml("[Content_Types].xml", contentTypes)
            zip.putXml("_rels/.rels", rootRels)
            zip.putXml("word/_rels/document.xml.rels", documentRels)
            zip.putXml("word/document.xml", documentXml(pages))
        }
        if (!outputFile.exists() || outputFile.length() < 32L) {
            error("No se pudo generar el archivo Word")
        }
    }

    private fun ZipOutputStream.putXml(path: String, xml: String) {
        putNextEntry(ZipEntry(path))
        write(xml.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun documentXml(pages: List<String>): String {
        val body = buildString {
            pages.forEachIndexed { index, pageText ->
                if (index > 0) {
                    append(pageBreakParagraph)
                }
                val paragraphs = pageText
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .split('\n')
                if (paragraphs.all { it.isBlank() }) {
                    append(emptyParagraph)
                } else {
                    paragraphs.forEach { line ->
                        append(textParagraph(line))
                    }
                }
            }
            append(sectionProperties)
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
$body
  </w:body>
</w:document>
"""
    }

    private fun textParagraph(text: String): String {
        val escaped = escapeXml(sanitize(text))
        return """    <w:p>
      <w:r>
        <w:t xml:space="preserve">$escaped</w:t>
      </w:r>
    </w:p>
"""
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

    private const val emptyParagraph = "    <w:p/>\n"
    private const val pageBreakParagraph = """    <w:p>
      <w:r>
        <w:br w:type="page"/>
      </w:r>
    </w:p>
"""
    private const val sectionProperties = """    <w:sectPr>
      <w:pgSz w:w="12240" w:h="15840"/>
      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720"/>
    </w:sectPr>
"""

    private const val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>
"""

    private const val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>
"""

    private const val documentRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>
"""
}
