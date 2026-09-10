package com.example.screentranslator

import android.os.Handler
import android.os.Looper

class TranslationManager(
    private val sourceLanguage: String,
    private val targetLanguage: String,
    private val manualProvider: String = ApiSettings.PROVIDER_ML_KIT
) {
    @Volatile private var provider: TranslationProvider = createProvider()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val releaseCloser: () -> Unit = { switchProvider() }

    init { TranslationEngineRuntime.registerCloser(releaseCloser) }

    fun prepare(onReady: () -> Unit, onFailure: (Exception) -> Unit) {
        val perfTrace = ScreenTLPerformanceTrace.current()
        perfTrace?.mark("translation_prepare_start provider=${getProviderName()}")
        provider.prepare(
            onReady = { perfTrace?.mark("translation_prepare_ready"); mainHandler.post(onReady) },
            onFailure = { exception -> perfTrace?.mark("translation_prepare_failed"); mainHandler.post { onFailure(exception) } }
        )
    }

    fun translate(text: String, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val perfTrace = ScreenTLPerformanceTrace.current()
        perfTrace?.mark("translation_request provider=${getProviderName()} chars=${text.length}")
        provider.translate(
            text = text,
            onSuccess = { translated -> perfTrace?.mark("translation_response chars=${translated.length}"); mainHandler.post { onSuccess(translated) } },
            onFailure = { exception -> perfTrace?.mark("translation_failed"); mainHandler.post { onFailure(exception) } }
        )
    }

    fun getProviderName(): String = when (provider) {
        is GeminiTranslationProvider -> "Gemini AI"
        is DeepLTranslationProvider -> ApiSettings.PROVIDER_DEEPL
        is QwenTranslationProvider -> ApiSettings.PROVIDER_QWEN
        is MlKitTranslationProvider -> ApiSettings.PROVIDER_ML_KIT
        else -> provider::class.java.simpleName
    }

    fun close() {
        TranslationEngineRuntime.unregisterCloser(releaseCloser)
        provider.close()
    }

    @Synchronized
    private fun switchProvider() {
        provider.close()
        provider = createProvider()
    }

    private fun createProvider(): TranslationProvider {
        if (ApiSettings.isLocalModelActive()) {
            return when (ApiSettings.getLocalEngine()) {
                ApiSettings.PROVIDER_QWEN -> QwenTranslationProvider(sourceLanguage, targetLanguage)
                ApiSettings.PROVIDER_FIREFOX -> MissingLocalProvider("Firefox Translation belum tersedia pada build ini")
                else -> MlKitTranslationProvider(sourceLanguage, targetLanguage)
            }
        }
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
                if (!key.isNullOrBlank() && ApiSettings.isDeepLVerified()) DeepLTranslationProvider(key, sourceLanguage, targetLanguage)
                else MissingApiProvider("DeepL API belum diaktifkan di Settings")
            }
            else -> MlKitTranslationProvider(sourceLanguage, targetLanguage)
        }
    }

    private class MissingApiProvider(private val message: String) : TranslationProvider {
        override fun prepare(onReady: () -> Unit, onFailure: (Exception) -> Unit) { onFailure(IllegalStateException(message)) }
        override fun translate(text: String, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) { onFailure(IllegalStateException(message)) }
        override fun close() = Unit
    }

    private class MissingLocalProvider(private val message: String) : TranslationProvider {
        override fun prepare(onReady: () -> Unit, onFailure: (Exception) -> Unit) { onFailure(IllegalStateException(message)) }
        override fun translate(text: String, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) { onFailure(IllegalStateException(message)) }
        override fun close() = Unit
    }
}
