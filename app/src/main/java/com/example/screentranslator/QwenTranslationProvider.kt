package com.example.screentranslator

import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QwenTranslationProvider(
    private val sourceLanguage: String,
    private val targetLanguage: String
) : TranslationProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var model: LlamaModel? = null
    @Volatile private var loading = false

    @Synchronized
    override fun prepare(onReady: () -> Unit, onFailure: (Exception) -> Unit) {
        model?.let {
            onReady()
            return
        }
        if (loading) {
            onFailure(IllegalStateException("Model Qwen sedang dimuat"))
            return
        }
        if (!LocalModelStore.isQwenInstalled()) {
            onFailure(IllegalStateException("Model Qwen belum diunduh atau file model tidak valid"))
            return
        }

        loading = true
        scope.launch {
            try {
                // Keep the first device test deliberately conservative: the
                // 0.5B model is small, but KV-cache/context memory is still real
                // RAM. Two threads also avoid competing with OCR/UI on an
                // Helio G96 while keeping the local engine strictly single-loaded.
                val loaded = Llama.loadModel(
                    modelPath = LocalModelStore.qwenFile().absolutePath,
                    config = LlamaConfig(contextSize = 1024, threads = 2)
                )
                model = loaded
                withContext(Dispatchers.Main) { onReady() }
            } catch (error: Exception) {
                val diagnostic = runCatching { Llama.getSystemInfo() }.getOrDefault("system-info unavailable")
                withContext(Dispatchers.Main) {
                    onFailure(IllegalStateException("Qwen gagal memuat model: ${error.message ?: error::class.java.simpleName} | $diagnostic", error))
                }
            } finally {
                loading = false
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
                val result = Llama.complete(
                    loaded,
                    prompt = buildPrompt(text),
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
        val loaded = model
        model = null
        loading = false
        if (loaded != null) runCatching { Llama.releaseModel(loaded) }
        scope.cancel()
    }

    private fun buildPrompt(text: String): String = "Translate from $sourceLanguage to $targetLanguage. Return only the translation.\n\n$text"
}
