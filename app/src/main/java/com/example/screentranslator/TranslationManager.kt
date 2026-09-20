package com.example.screentranslator

import android.os.Handler
import android.os.Looper
import kotlin.concurrent.thread

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

    fun translate(
        text: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit,
        onCacheHit: (() -> Unit)? = null
    ) {
        val perfTrace = ScreenTLPerformanceTrace.current()
        val providerName = getProviderName()
        val cacheScope = getCacheScope()
        val cachedTranslation = TranslationCache.get(cacheScope, sourceLanguage, targetLanguage, text)
        if (cachedTranslation != null) {
            perfTrace?.mark("translation_cache_hit provider=$providerName chars=${text.length}")
            mainHandler.post {
                onCacheHit?.invoke()
                onSuccess(cachedTranslation)
            }
            return
        }

        perfTrace?.mark("translation_request provider=$providerName chars=${text.length}")
        provider.translate(
            text = text,
            onSuccess = { translated ->
                TranslationCache.put(getCacheScope(), sourceLanguage, targetLanguage, text, translated)
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
        is OpenRouterTranslationProvider -> ApiSettings.PROVIDER_OPENROUTER
        is GeminiTranslationProvider -> "Gemini AI"
        is DeepLTranslationProvider -> ApiSettings.PROVIDER_DEEPL
        is MlKitTranslationProvider -> ApiSettings.PROVIDER_ML_KIT
        else -> provider::class.java.simpleName
    }

    private fun getCacheScope(): String = when (provider) {
        is GeminiTranslationProvider -> "Gemini AI:${GeminiTranslationProvider.getCurrentModel()}"
        is OpenRouterTranslationProvider -> "${ApiSettings.PROVIDER_OPENROUTER}:${ApiSettings.getOpenRouterBaseUrl()}:${ApiSettings.getOpenRouterModel()}"
        else -> getProviderName()
    }

    fun close() {
        // OkHttp may close TLS sockets while evicting its connection pool. This must
        // not run from Service.onDestroy(), which Android invokes on the main thread.
        thread(name = "ScreenTL-ProviderClose", isDaemon = true) {
            runCatching { provider.close() }
        }
    }

    private fun createProvider(manualProvider: String): TranslationProvider {
       if (ApiSettings.isOpenRouterEnabled()) {                    // ← TAMBAHAN BARU
        val key = ApiSettings.getOpenRouterKey()
        if (!key.isNullOrBlank()) {
            return OpenRouterTranslationProvider(
                apiKey = key,
                baseUrl = ApiSettings.getOpenRouterBaseUrl(),
                model = ApiSettings.getOpenRouterModel(),
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage
                )
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
