package com.example.folio.presentation

import android.content.Context

/** The appearance mode selected for the app. */
enum class AppTheme(val preferenceValue: String) {
    DARK("dark"),
    LIGHT("light"),
    SYSTEM("system");

    companion object {
        fun fromPreferenceValue(value: String?): AppTheme =
            entries.firstOrNull { it.preferenceValue == value } ?: SYSTEM
    }
}

/** Persists the app's appearance choice alongside other app preferences. */
class ThemePreferences(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    fun current(): AppTheme =
        AppTheme.fromPreferenceValue(preferences.getString(THEME_KEY, null))

    fun set(theme: AppTheme) {
        preferences.edit().putString(THEME_KEY, theme.preferenceValue).apply()
    }

    private companion object {
        const val THEME_KEY = "theme"
    }
}
