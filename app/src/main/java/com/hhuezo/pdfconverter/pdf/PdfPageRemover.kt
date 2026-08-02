package com.hhuezo.pdfconverter.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Removes selected pages from a PDF and writes a new document.
 * Page indices are 0-based.
 */
class PdfPageRemover(context: Context) {

    private val appContext = context.applicationContext

    fun removePages(uri: Uri, pageIndicesToRemove: List<Int>, outputFile: File) {
        require(pageIndicesToRemove.isNotEmpty()) { "No hay páginas para eliminar" }
        ensurePdfBoxInitialized(appContext)

        val sourceCopy = File(appContext.cacheDir, "delete_src_${System.currentTimeMillis()}.pdf")
        try {
            copyUriToFile(uri, sourceCopy)
            PDDocument.load(sourceCopy).use { document ->
                val pageCount = document.numberOfPages
                require(pageCount > 0) { "El PDF no tiene páginas" }

                val unique = pageIndicesToRemove
                    .filter { it in 0 until pageCount }
                    .toSortedSet()
                require(unique.isNotEmpty()) { "Ninguna página seleccionada es válida" }
                require(unique.size < pageCount) {
                    "No se pueden eliminar todas las páginas del PDF"
                }

                // Remove highest indices first so lower indices stay valid.
                unique.sortedDescending().forEach { index ->
                    document.removePage(index)
                }

                outputFile.parentFile?.mkdirs()
                if (outputFile.exists()) outputFile.delete()
                document.save(outputFile)
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
