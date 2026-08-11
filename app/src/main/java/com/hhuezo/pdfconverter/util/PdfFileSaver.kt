package com.hhuezo.pdfconverter.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

enum class PdfSaveOutcome {
    Overwritten,
    SavedAsCopy,
    Failed,
}

object PdfFileSaver {

    /**
     * Saves a PDF into Downloads/PdfKit Pro and returns the content [Uri], or null on failure.
     */
    fun saveToDownloads(context: Context, source: File, displayName: String): Uri? {
        return writeToDownloads(context, displayName) { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        }
    }

    /**
     * Copies a PDF [Uri] into Downloads/PdfKit Pro and returns the content [Uri], or null on failure.
     */
    fun saveUriToDownloads(context: Context, sourceUri: Uri, displayName: String): Uri? {
        val input = context.contentResolver.openInputStream(sourceUri) ?: return null
        return input.use { stream ->
            writeToDownloads(context, displayName) { output ->
                stream.copyTo(output)
            }
        }
    }

    private fun writeToDownloads(
        context: Context,
        displayName: String,
        write: (java.io.OutputStream) -> Unit,
    ): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/PdfKit Pro",
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use(write) ?: return null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    fun canWriteUri(context: Context, uri: Uri): Boolean {
        val resolver = context.contentResolver
        val persistedWrite = resolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isWritePermission
        }
        if (persistedWrite) return true
        return runCatching {
            resolver.openFileDescriptor(uri, "wt")?.use { true } == true
        }.getOrDefault(false)
    }

    /**
     * Overwrites [targetUri] with the contents of [source].
     * Requires write access from the document provider.
     */
    fun overwriteUri(context: Context, targetUri: Uri, source: File): Boolean {
        return runCatching {
            context.contentResolver.openOutputStream(targetUri, "wt")?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
    }

    /**
     * Tries to overwrite [originalUri]. If that fails, saves a copy to Downloads.
     */
    fun saveOverwritingOrCopy(
        context: Context,
        originalUri: Uri?,
        source: File,
        fallbackDisplayName: String,
    ): PdfSaveOutcome {
        if (originalUri != null && canWriteUri(context, originalUri)) {
            if (overwriteUri(context, originalUri, source)) {
                return PdfSaveOutcome.Overwritten
            }
        }
        val copyUri = saveToDownloads(context, source, fallbackDisplayName)
        return if (copyUri != null) PdfSaveOutcome.SavedAsCopy else PdfSaveOutcome.Failed
    }

    fun writeToUri(context: Context, targetUri: Uri, source: File): Boolean {
        return runCatching {
            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
    }
}
