package com.example.screentranslator

class TranslationManager(
    private val sourceLanguage: String,
    private val targetLanguage: String,
    manualProvider: String = ApiSettings.PROVIDER_ML_KIT
) {

    private val provider: TranslationProvider = createProvider(manualProvider)

    fun prepare(onReady: () -> Unit, onFailure: (Exception) -> Unit) {
        provider.prepare(onReady, onFailure)
    }

    fun translate(text: String, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        provider.translate(text, onSuccess, onFailure)
    }

    fun close() {
        provider.close()
    }

    private fun createProvider(manualProvider: String): TranslationProvider {
        // A verified custom Gemini key has priority over the manual provider selector.
        if (ApiSettings.isGeminiEnabled()) {
            val key = ApiSettings.getGeminiKey()
            if (!key.isNullOrBlank()) {
                return GeminiTranslationProvider(key, sourceLanguage, targetLanguage)
            }
        }

        return when (manualProvider) {
            ApiSettings.PROVIDER_DEEPL -> {
                val key = ApiSettings.getDeepLKey()
                if (ApiSettings.isDeepLEnabled() && !key.isNullOrBlank()) {
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
