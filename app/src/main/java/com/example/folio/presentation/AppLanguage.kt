package com.example.folio.presentation

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** The two locales Folio exposes in its in-app language selector. */
enum class AppLanguage(val tag: String) {
    PERSIAN("fa"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromTag(tag: String?) = entries.firstOrNull { it.tag == tag } ?: PERSIAN
    }
}

/**
 * Keeps the user's explicit choice and applies it through AppCompat.
 * AppCompat handles configuration updates and process restoration, while the
 * preference lets the settings screen show a deterministic selected value.
 */
class LanguagePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    fun current(): AppLanguage = AppLanguage.fromTag(preferences.getString(LANGUAGE_KEY, null))

    fun set(language: AppLanguage) {
        preferences.edit().putString(LANGUAGE_KEY, language.tag).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }

    companion object {
        private const val LANGUAGE_KEY = "language"
    }
}
