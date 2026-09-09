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
        val perfTrace = ScreenTLPerformanceTrace.current()
        perfTrace?.mark("translation_prepare_start provider=${getProviderName()}")
        provider.prepare(
            onReady = {
                perfTrace?.mark("translation_prepare_ready")
                mainHandler.post(onReady)
            },
            onFailure = { exception ->
                perfTrace?.mark("translation_prepare_failed")
                mainHandler.post { onFailure(exception) }
            }
        )
    }

    fun translate(text: String, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val perfTrace = ScreenTLPerformanceTrace.current()
        perfTrace?.mark("translation_request provider=${getProviderName()} chars=${text.length}")
        provider.translate(
            text = text,
            onSuccess = { translated ->
                perfTrace?.mark("translation_response chars=${translated.length}")
                // The caller owns the final display point. This prevents the
                // performance log from ending before the overlay is actually visible.
                mainHandler.post { onSuccess(translated) }
            },
            onFailure = { exception ->
                perfTrace?.mark("translation_failed")
                mainHandler.post { onFailure(exception) }
            }
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
        if (ApiSettings.isGeminiEnabled()) {
            val key = ApiSettings.getGeminiKey()
            if (!key.isNullOrBlank()) return GeminiTranslationProvider(key, sourceLanguage, targetLanguage)
        }
        if (ApiSettings.isDeepLEnabled()) {
            val key = ApiSettings.getDeepLKey()
            if (!key.isNullOrBlank()) return DeepLTranslationProvider(key, sourceLanguage, targetLanguage)
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
        override fun prepare(onReady: () -> Unit, onFailure: (Exception) -> Unit) { onFailure(IllegalStateException(message)) }
        override fun translate(text: String, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) { onFailure(IllegalStateException(message)) }
        override fun close() = Unit
    }
}
