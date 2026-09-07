package com.example.screentranslator

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

class MlKitTranslationProvider(
    sourceLanguage: String,
    targetLanguage: String
) : TranslationProvider {

    private val translator: Translator?

    init {
        val sourceCode = getLanguageCode(sourceLanguage)
        val targetCode = getLanguageCode(targetLanguage)
        translator = if (sourceCode != null && targetCode != null && sourceCode != targetCode) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceCode)
                .setTargetLanguage(targetCode)
                .build()
            Translation.getClient(options)
        } else null
    }

    override fun prepare(onReady: () -> Unit, onFailure: (Exception) -> Unit) {
        val current = translator ?: run { onReady(); return }
        current.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { onReady() }
            .addOnFailureListener { onFailure(it) }
    }

    override fun translate(text: String, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val current = translator ?: run { onSuccess(text); return }
        current.translate(text)
            .addOnSuccessListener { onSuccess(it) }
            .addOnFailureListener { onFailure(it) }
    }

    override fun close() {
        translator?.close()
    }

    private fun getLanguageCode(language: String): String? = when (language) {
        "Jepang" -> TranslateLanguage.JAPANESE
        "Mandarin (China)" -> TranslateLanguage.CHINESE
        "Inggris" -> TranslateLanguage.ENGLISH
        "Indonesia" -> TranslateLanguage.INDONESIAN
        else -> null
    }
}
