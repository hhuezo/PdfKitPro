package com.hhuezo.pdfconverter.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

private const val IMPORT_DIR = "imported_pdfs"

fun Context.hasPersistedUriPermission(uri: Uri): Boolean {
    return contentResolver.persistedUriPermissions.any { permission ->
        permission.uri == uri && permission.isReadPermission
    }
}

fun Context.isAppImportedPdfUri(uri: Uri): Boolean {
    return importedPdfFileFromUri(uri) != null
}

/**
 * Returns a URI that PdfKit can open again later (recents).
 * Prefer a persisted SAF grant; otherwise copy into app files and return a FileProvider URI.
 */
fun Context.ensureDurablePdfAccess(uri: Uri, intentFlags: Int = 0): Uri {
    if (isAppImportedPdfUri(uri) && isPdfUriAccessible(uri)) {
        return uri
    }

    takePersistableReadPermission(uri, intentFlags)
    if (hasPersistedUriPermission(uri) && isPdfUriAccessible(uri)) {
        return uri
    }

    return importPdfToAppStorage(uri) ?: uri
}

fun Context.importPdfToAppStorage(source: Uri): Uri? {
    val info = queryPdfInfo(source)
    val dir = File(filesDir, IMPORT_DIR).apply { mkdirs() }
    val safeName = sanitizePdfFileName(info.displayName)
    val dest = File(dir, "${System.currentTimeMillis()}_$safeName")

    val copied = runCatching {
        contentResolver.openInputStream(source)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } != null
    }.getOrDefault(false)

    if (!copied || !dest.isFile || dest.length() <= 0L) {
        dest.delete()
        return null
    }

    return FileProvider.getUriForFile(
        this,
        "$packageName.fileprovider",
        dest,
    )
}

fun Context.deleteImportedPdfIfOwned(uri: Uri) {
    val file = importedPdfFileFromUri(uri) ?: return
    runCatching { if (file.exists()) file.delete() }
}

/** Deletes all temporary PDFs copied into app storage for the recents history. */
fun Context.clearImportedPdfStorage() {
    val dir = File(filesDir, IMPORT_DIR)
    if (!dir.isDirectory) return
    dir.listFiles()?.forEach { file ->
        runCatching { if (file.isFile) file.delete() }
    }
}

private fun Context.importedPdfFileFromUri(uri: Uri): File? {
    if (uri.scheme == "file") {
        val path = uri.path ?: return null
        val importRoot = File(filesDir, IMPORT_DIR).absolutePath
        return File(path).takeIf { it.isFile && it.absolutePath.startsWith(importRoot) }
    }

    if (uri.authority != "$packageName.fileprovider") return null
    val encodedPath = uri.path ?: return null
    val marker = "/files/"
    val index = encodedPath.indexOf(marker)
    if (index < 0) return null
    val relative = encodedPath.substring(index + marker.length)
    if (!relative.startsWith("$IMPORT_DIR/")) return null
    return File(filesDir, relative).takeIf { it.isFile }
}

private fun sanitizePdfFileName(raw: String): String {
    val trimmed = raw.trim().ifBlank { "documento.pdf" }
    val base = trimmed
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .take(80)
    return if (base.endsWith(".pdf", ignoreCase = true)) base else "$base.pdf"
}
