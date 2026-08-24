package com.hhuezo.pdfconverter.data

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Idiomas soportados. Para añadir uno nuevo: entra aquí, en
 * `res/xml/locales_config.xml` y en `res/values-xx/strings.xml`.
 */
enum class AppLanguage(
    val tag: String,
    val nativeName: String,
) {
    Spanish("es", "Español"),
    English("en", "English"),
    ;

    fun toLocale(): Locale = Locale.forLanguageTag(tag)

    companion object {
        val Default = Spanish

        fun fromTag(tag: String?): AppLanguage {
            if (tag.isNullOrBlank()) return Default
            val primary = tag.substringBefore('-').lowercase(Locale.ROOT)
            return entries.firstOrNull { it.tag.equals(primary, ignoreCase = true) } ?: Default
        }

        fun current(): AppLanguage {
            val stored = AppCompatDelegate.getApplicationLocales()
            val tag = stored.get(0)?.toLanguageTag()
            return fromTag(tag)
        }

        fun apply(language: AppLanguage) {
            if (current() == language) return
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(language.tag),
            )
        }
    }
}
