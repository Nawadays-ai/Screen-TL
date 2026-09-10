package com.example.screentranslator

import android.content.Context

object TranslationModeSettings {
    const val NORMAL = "Normal"
    const val MANGA = "Manga"

    private const val PREFS = "screen_tl_mode_settings"
    private const val KEY_MODE = "translation_mode"

    private lateinit var preferences: android.content.SharedPreferences
    private lateinit var applicationContext: Context

    fun initialize(context: Context) {
        if (!::preferences.isInitialized) {
            applicationContext = context.applicationContext
            preferences = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun context(): Context {
        ensureInitialized()
        return applicationContext
    }

    fun getMode(): String {
        ensureInitialized()
        return preferences.getString(KEY_MODE, NORMAL) ?: NORMAL
    }

    fun setMode(mode: String) {
        ensureInitialized()
        preferences.edit().putString(KEY_MODE, if (mode == MANGA) MANGA else NORMAL).apply()
    }

    fun isMangaMode(): Boolean = getMode() == MANGA

    private fun ensureInitialized() {
        check(::preferences.isInitialized) { "TranslationModeSettings.initialize(context) must be called first" }
    }
}
