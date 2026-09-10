package com.example.screentranslator

import android.content.Context

object ApiSettings {
    private const val PREFS = "screen_tl_api_settings"
    private const val KEY_MANUAL_PROVIDER = "manual_provider"
    private const val KEY_GEMINI_ENABLED = "gemini_enabled"
    private const val KEY_GEMINI_VERIFIED = "gemini_verified"
    private const val KEY_DEEPL_ENABLED = "deepl_enabled"
    private const val KEY_DEEPL_VERIFIED = "deepl_verified"
    private const val KEY_LOCAL_ENGINE = "local_engine"

    const val PROVIDER_ML_KIT = "Google ML Kit"
    const val PROVIDER_DEEPL = "DeepL API"
    const val PROVIDER_QWEN = "Qwen 0.5B Q4_0_4_4"
    const val PROVIDER_FIREFOX = "Firefox Translation"

    private lateinit var preferences: android.content.SharedPreferences
    private lateinit var keyStore: SecureApiKeyStore

    @Synchronized
    fun initialize(context: Context) {
        if (!::preferences.isInitialized) {
            val appContext = context.applicationContext
            preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            keyStore = SecureApiKeyStore(appContext)
        }
    }

    fun getManualProvider(): String {
        ensureInitialized()
        val stored = preferences.getString(KEY_MANUAL_PROVIDER, PROVIDER_ML_KIT) ?: PROVIDER_ML_KIT
        return if (stored == PROVIDER_ML_KIT) stored else PROVIDER_ML_KIT
    }

    fun setManualProvider(provider: String) {
        ensureInitialized()
        preferences.edit().putString(KEY_MANUAL_PROVIDER, PROVIDER_ML_KIT).apply()
    }

    fun getLocalEngine(): String = initialized().let { preferences.getString(KEY_LOCAL_ENGINE, PROVIDER_ML_KIT) ?: PROVIDER_ML_KIT }

    fun setLocalEngine(provider: String) {
        ensureInitialized()
        val value = when (provider) {
            PROVIDER_QWEN, PROVIDER_FIREFOX -> provider
            else -> PROVIDER_ML_KIT
        }
        preferences.edit().putString(KEY_LOCAL_ENGINE, value).apply()
        TranslationEngineRuntime.releaseActiveLocalEngines()
    }

    fun isLocalModelActive(): Boolean = getLocalEngine() != PROVIDER_ML_KIT

    fun getGeminiKey(): String? { ensureInitialized(); return keyStore.get("gemini_api_key") }
    fun setGeminiKey(key: String) { ensureInitialized(); keyStore.put("gemini_api_key", key.trim()); preferences.edit().putBoolean(KEY_GEMINI_VERIFIED, false).apply() }
    fun clearGeminiKey() { ensureInitialized(); keyStore.clear("gemini_api_key"); preferences.edit().putBoolean(KEY_GEMINI_VERIFIED, false).putBoolean(KEY_GEMINI_ENABLED, false).apply() }
    fun isGeminiVerified(): Boolean = initialized().let { preferences.getBoolean(KEY_GEMINI_VERIFIED, false) }
    fun setGeminiVerified(value: Boolean) { ensureInitialized(); preferences.edit().putBoolean(KEY_GEMINI_VERIFIED, value).apply() }
    fun isGeminiEnabled(): Boolean = initialized().let { preferences.getBoolean(KEY_GEMINI_ENABLED, false) }
    fun setGeminiEnabled(value: Boolean) { ensureInitialized(); preferences.edit().putBoolean(KEY_GEMINI_ENABLED, value).apply(); if (value) preferences.edit().putBoolean(KEY_DEEPL_ENABLED, false).apply() }
    fun getDeepLKey(): String? { ensureInitialized(); return keyStore.get("deepl_api_key") }
    fun setDeepLKey(key: String) { ensureInitialized(); keyStore.put("deepl_api_key", key.trim()); preferences.edit().putBoolean(KEY_DEEPL_VERIFIED, false).apply() }
    fun clearDeepLKey() { ensureInitialized(); keyStore.clear("deepl_api_key"); preferences.edit().putBoolean(KEY_DEEPL_VERIFIED, false).putBoolean(KEY_DEEPL_ENABLED, false).apply() }
    fun isDeepLVerified(): Boolean = initialized().let { preferences.getBoolean(KEY_DEEPL_VERIFIED, false) }
    fun setDeepLVerified(value: Boolean) { ensureInitialized(); preferences.edit().putBoolean(KEY_DEEPL_VERIFIED, value).apply() }
    fun isDeepLEnabled(): Boolean = initialized().let { preferences.getBoolean(KEY_DEEPL_ENABLED, false) }
    fun setDeepLEnabled(value: Boolean) { ensureInitialized(); preferences.edit().putBoolean(KEY_DEEPL_ENABLED, value).apply(); if (value) preferences.edit().putBoolean(KEY_GEMINI_ENABLED, false).apply() }

    private fun initialized(): Boolean { ensureInitialized(); return true }
    private fun ensureInitialized() { check(::preferences.isInitialized) { "ApiSettings.initialize(context) must be called first" } }
}
