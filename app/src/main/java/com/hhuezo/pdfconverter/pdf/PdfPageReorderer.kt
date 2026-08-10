package com.hhuezo.pdfconverter.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Rebuilds a PDF with pages in a new order.
 * [pageOrder] is a full permutation of 0-based page indices.
 */
class PdfPageReorderer(context: Context) {

    private val appContext = context.applicationContext

    fun reorderPages(uri: Uri, pageOrder: List<Int>, outputFile: File): Int {
        require(pageOrder.isNotEmpty()) { "No hay páginas para reordenar" }
        ensurePdfBoxInitialized(appContext)

        val sourceCopy = File(appContext.cacheDir, "reorder_src_${System.currentTimeMillis()}.pdf")
        try {
            copyUriToFile(uri, sourceCopy)
            PDDocument.load(sourceCopy).use { source ->
                val pageCount = source.numberOfPages
                require(pageCount > 1) { "Se necesitan al menos 2 páginas" }
                require(pageOrder.size == pageCount) {
                    "El orden debe incluir todas las páginas"
                }
                require(pageOrder.toSet().size == pageCount) {
                    "El orden contiene páginas duplicadas"
                }
                require(pageOrder.all { it in 0 until pageCount }) {
                    "Hay índices de página inválidos"
                }
                val identity = List(pageCount) { it }
                require(pageOrder != identity) { "El orden no ha cambiado" }

                PDDocument().use { dest ->
                    pageOrder.forEach { index ->
                        dest.importPage(source.getPage(index))
                    }
                    outputFile.parentFile?.mkdirs()
                    if (outputFile.exists()) outputFile.delete()
                    dest.save(outputFile)
                }
                return pageCount
            }
        } finally {
            sourceCopy.delete()
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
    }
}
