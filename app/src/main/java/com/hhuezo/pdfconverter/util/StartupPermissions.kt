package com.hhuezo.pdfconverter.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

fun requiredStartupPermissions(): List<String> = buildList {
    add(Manifest.permission.CAMERA)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}

fun missingStartupPermissions(context: Context): Array<String> {
    return requiredStartupPermissions().filter { permission ->
        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
    }.toTypedArray()
}

fun hasAllStartupPermissions(context: Context): Boolean =
    missingStartupPermissions(context).isEmpty()
