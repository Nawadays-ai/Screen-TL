package com.example.screentranslator

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

class TranslationManager(
    sourceLanguage: String,
    targetLanguage: String
) {

    private val translator: Translator?

    init {

        val sourceCode = getLanguageCode(sourceLanguage)
        val targetCode = getLanguageCode(targetLanguage)

        translator =
            if (sourceCode != null &&
                targetCode != null &&
                sourceCode != targetCode
            ) {

                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceCode)
                    .setTargetLanguage(targetCode)
                    .build()

                Translation.getClient(options)

            } else {
                null
            }
    }

    fun prepare(
        onReady: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {

        val currentTranslator = translator

        if (currentTranslator == null) {
            onReady()
            return
        }

        val conditions = DownloadConditions.Builder()
            .build()

        currentTranslator
            .downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                onReady()
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    fun translate(
        text: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {

        if (translator == null) {
            onSuccess(text)
            return
        }

        translator
            .translate(text)
            .addOnSuccessListener { translatedText ->
                onSuccess(translatedText)
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    fun close() {
        translator?.close()
    }

    private fun getLanguageCode(
        language: String
    ): String? {

        return when (language) {

            "Jepang" ->
                TranslateLanguage.JAPANESE

            "Mandarin (China)" ->
                TranslateLanguage.CHINESE

            "Inggris" ->
                TranslateLanguage.ENGLISH

            "Indonesia" ->
                TranslateLanguage.INDONESIAN

            else ->
                null
        }
    }
}
