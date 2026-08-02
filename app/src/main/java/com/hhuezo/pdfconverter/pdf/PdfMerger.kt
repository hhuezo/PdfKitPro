package com.hhuezo.pdfconverter.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Merges multiple PDF files into one, preserving page order.
 */
class PdfMerger(context: Context) {

    private val appContext = context.applicationContext

    fun merge(uris: List<Uri>, outputFile: File) {
        require(uris.size >= 2) { "Se necesitan al menos 2 PDFs" }
        ensurePdfBoxInitialized(appContext)

        val sourceCopies = mutableListOf<File>()
        try {
            uris.forEachIndexed { index, uri ->
                val copy = File(
                    appContext.cacheDir,
                    "merge_src_${System.currentTimeMillis()}_$index.pdf",
                )
                copyUriToFile(uri, copy)
                sourceCopies += copy
            }

            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()

            val merger = PDFMergerUtility()
            sourceCopies.forEach { merger.addSource(it) }
            merger.destinationFileName = outputFile.absolutePath
            merger.mergeDocuments(null)

            if (!outputFile.exists() || outputFile.length() < 5L) {
                error("No se pudo generar el PDF unido")
            }
        } finally {
            sourceCopies.forEach { it.delete() }
        }
    }

    private fun copyUriToFile(uri: Uri, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            outputFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("No se pudo copiar un PDF de origen")
        if (outputFile.length() < 5L) {
            error("Un PDF de origen está vacío o no se pudo leer")
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
