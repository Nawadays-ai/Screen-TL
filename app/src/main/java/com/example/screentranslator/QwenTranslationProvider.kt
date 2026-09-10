package com.example.screentranslator

import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QwenTranslationProvider(
    private val sourceLanguage: String,
    private val targetLanguage: String
) : TranslationProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var model: Any? = null

    override fun prepare(onReady: () -> Unit, onFailure: (Exception) -> Unit) {
        if (!LocalModelStore.isQwenInstalled()) {
            onFailure(IllegalStateException("Model Qwen belum diunduh"))
            return
        }
        scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    Llama.loadModel(
                        modelPath = LocalModelStore.qwenFile().absolutePath,
                        config = LlamaConfig(contextSize = 2048, threads = 4)
                    )
                }
                model = loaded
                withContext(Dispatchers.Main) { onReady() }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) { onFailure(error) }
            }
        }
    }

    override fun translate(text: String, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val loaded = model ?: run {
            onFailure(IllegalStateException("Model Qwen belum aktif"))
            return
        }
        scope.launch {
            try {
                val prompt = buildPrompt(text)
                val result = Llama.complete(
                    loaded,
                    prompt = prompt,
                    systemPrompt = "You are a translation engine. Translate only. Preserve meaning, names, numbers, punctuation and line breaks. Do not explain.",
                    maxTokens = 256
                )
                val translated = result.text.trim()
                if (translated.isBlank()) throw IllegalStateException("Qwen menghasilkan terjemahan kosong")
                withContext(Dispatchers.Main) { onSuccess(translated) }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) { onFailure(error) }
            }
        }
    }

    override fun close() {
        val loaded = model ?: return
        model = null
        scope.coroutineContext.cancel()
        runCatching { @Suppress("UNCHECKED_CAST") Llama.releaseModel(loaded as dev.ffmpegkit.llama.LlamaModel) }
    }

    private fun buildPrompt(text: String): String = "Translate from $sourceLanguage to $targetLanguage. Return only the translation.\n\n$text"
}
