package com.hhuezo.pdfconverter.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/**
 * Opens a PDF document requesting read + write + persistable grants when the
 * provider allows it, so "Guardar" can overwrite the same file later.
 */
class OpenWritablePdfDocument : ActivityResultContract<Array<String>, Uri?>() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (input.size == 1) input[0] else "*/*"
            if (input.isNotEmpty()) {
                putExtra(Intent.EXTRA_MIME_TYPES, input)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return intent?.takeIf { resultCode == Activity.RESULT_OK }?.data
    }
}
