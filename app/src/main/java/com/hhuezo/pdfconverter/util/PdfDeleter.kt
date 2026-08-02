package com.hhuezo.pdfconverter.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

object PdfDeleter {

    /**
     * Attempts to delete the PDF pointed by [uri] from storage.
     * Returns true if the provider reported a successful delete.
     */
    fun delete(context: Context, uri: Uri): Boolean {
        return runCatching {
            when (uri.scheme) {
                "content" -> {
                    if (DocumentsContract.isDocumentUri(context, uri)) {
                        DocumentsContract.deleteDocument(context.contentResolver, uri)
                    } else {
                        context.contentResolver.delete(uri, null, null) > 0
                    }
                }
                "file" -> {
                    val path = uri.path ?: return@runCatching false
                    File(path).takeIf { it.exists() }?.delete() == true
                }
                else -> false
            }
        }.getOrDefault(false)
    }
}
