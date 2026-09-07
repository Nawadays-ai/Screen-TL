package com.example.screentranslator

import android.os.Handler
import android.os.Looper

class TranslationManager(
    private val sourceLanguage: String,
    private val targetLanguage: String,
    manualProvider: String = ApiSettings.PROVIDER_ML_KIT
) {

    private val provider: TranslationProvider = createProvider(manualProvider)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun prepare(onReady: () -> Unit, onFailure: (Exception) -> Unit) {
        provider.prepare(
            onReady = { mainHandler.post(onReady) },
            onFailure = { exception -> mainHandler.post { onFailure(exception) } }
        )
    }

    fun translate(text: String, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        provider.translate(
            text = text,
            onSuccess = { translated -> mainHandler.post { onSuccess(translated) } },
            onFailure = { exception -> mainHandler.post { onFailure(exception) } }
        )
    }

    fun getProviderName(): String = when (provider) {
        is GeminiTranslationProvider -> "Gemini AI"
        is DeepLTranslationProvider -> ApiSettings.PROVIDER_DEEPL
        is MlKitTranslationProvider -> ApiSettings.PROVIDER_ML_KIT
        else -> provider::class.java.simpleName
    }

    fun close() {
        provider.close()
    }

    private fun createProvider(manualProvider: String): TranslationProvider {
        // A verified/enabled custom API always takes priority over the manual selector.
        if (ApiSettings.isGeminiEnabled()) {
            val key = ApiSettings.getGeminiKey()
            if (!key.isNullOrBlank()) {
                return GeminiTranslationProvider(key, sourceLanguage, targetLanguage)
            }
        }

        // DeepL must also be an active override. Pressing "Gunakan" makes it
        // the actual provider regardless of the manual provider selector.
        if (ApiSettings.isDeepLEnabled()) {
            val key = ApiSettings.getDeepLKey()
            if (!key.isNullOrBlank()) {
                return DeepLTranslationProvider(key, sourceLanguage, targetLanguage)
            }
        }

        return when (manualProvider) {
            ApiSettings.PROVIDER_DEEPL -> {
                val key = ApiSettings.getDeepLKey()
                if (!key.isNullOrBlank() && ApiSettings.isDeepLVerified()) {
                    DeepLTranslationProvider(key, sourceLanguage, targetLanguage)
                } else {
                    MissingApiProvider("DeepL API belum diaktifkan di Settings")
                }
            }
            else -> MlKitTranslationProvider(sourceLanguage, targetLanguage)
        }
    }

    private class MissingApiProvider(private val message: String) : TranslationProvider {
        override fun prepare(onReady: () -> Unit, onFailure: (Exception) -> Unit) {
            onFailure(IllegalStateException(message))
        }

        override fun translate(text: String, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
            onFailure(IllegalStateException(message))
        }

        override fun close() = Unit
    }
}
