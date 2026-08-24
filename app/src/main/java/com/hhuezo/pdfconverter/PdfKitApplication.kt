package com.hhuezo.pdfconverter

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.hhuezo.pdfconverter.data.AppLanguage

class PdfKitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(AppLanguage.Default.tag),
            )
        }
    }
}
