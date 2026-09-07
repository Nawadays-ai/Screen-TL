package com.example.screentranslator

interface TranslationProvider {
    fun prepare(onReady: () -> Unit, onFailure: (Exception) -> Unit)
    fun translate(text: String, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit)
    fun close()
}
