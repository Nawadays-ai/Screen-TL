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
        model?.let { onReady(); return }
        if (loading) {
            onFailure(IllegalStateException("Model Qwen sedang dimuat"))
            return
        }

        val fileDiagnostic = LocalModelStore.qwenDiagnostic()
        if (!LocalModelStore.isQwenInstalled()) {
            onFailure(IllegalStateException("Model Qwen belum siap: $fileDiagnostic"))
            return
        }

        loading = true
        scope.launch {
            try {
                val loaded = Llama.loadModel(
                    modelPath = LocalModelStore.qwenFile().absolutePath,
                    config = LlamaConfig(contextSize = 1024, threads = 4)
                )
                model = loaded
                withContext(Dispatchers.Main) { onReady() }
            } catch (error: Exception) {
                val systemInfo = runCatching { Llama.getSystemInfo() }.getOrDefault("system-info unavailable")
                val diagnostic = "model=${LocalModelStore.qwenFile().name}; file=$fileDiagnostic; context=1024; threads=4; maxTokens=96; error=${error.message ?: error::class.java.name}; llama=$systemInfo"
                withContext(Dispatchers.Main) {
                    onFailure(IllegalStateException("Qwen gagal memuat model: ${error.message ?: error::class.java.simpleName}", error).also {
                        it.addSuppressed(IllegalStateException(diagnostic))
                    })
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
                    systemPrompt = "Translate only. Preserve meaning, names, numbers, punctuation and line breaks. No explanation.",
                    maxTokens = 96
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
