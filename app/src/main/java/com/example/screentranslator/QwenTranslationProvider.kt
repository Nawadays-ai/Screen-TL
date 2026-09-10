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
import kotlinx.coroutines.withTimeout

class QwenTranslationProvider(
    private val sourceLanguage: String,
    private val targetLanguage: String
) : TranslationProvider {
    companion object {
        private const val CONTEXT_SIZE = 1024
        private const val THREADS = 4
        private const val MAX_TOKENS = 96
        private const val INFERENCE_TIMEOUT_MS = 8_000L
    }

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
                    config = LlamaConfig(contextSize = CONTEXT_SIZE, threads = THREADS)
                )
                model = loaded
                withContext(Dispatchers.Main) { onReady() }
            } catch (error: Exception) {
                val systemInfo = runCatching { Llama.getSystemInfo() }.getOrDefault("system-info unavailable")
                val diagnostic = "model=${LocalModelStore.qwenFile().name}; file=$fileDiagnostic; context=$CONTEXT_SIZE; threads=$THREADS; maxTokens=$MAX_TOKENS; inferenceTimeoutMs=$INFERENCE_TIMEOUT_MS; error=${error.message ?: error::class.java.name}; llama=$systemInfo"
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
                val result = withTimeout(INFERENCE_TIMEOUT_MS) {
                    Llama.complete(
                        loaded,
                        prompt = buildPrompt(text),
                        systemPrompt = "Translate only. Preserve meaning, names, numbers, punctuation and line breaks. No explanation.",
                        maxTokens = MAX_TOKENS
                    )
                }
                val translated = result.text.trim()
                if (translated.isBlank()) throw IllegalStateException("Qwen menghasilkan terjemahan kosong")
                withContext(Dispatchers.Main) { onSuccess(translated) }
            } catch (error: Exception) {
                val timedOut = error is kotlinx.coroutines.TimeoutCancellationException
                val detail = if (timedOut) {
                    "Qwen inference timeout setelah ${INFERENCE_TIMEOUT_MS} ms; context=$CONTEXT_SIZE; threads=$THREADS; maxTokens=$MAX_TOKENS; model=${LocalModelStore.qwenFile().name}"
                } else {
                    "Qwen inference error=${error.message ?: error::class.java.name}; context=$CONTEXT_SIZE; threads=$THREADS; maxTokens=$MAX_TOKENS; model=${LocalModelStore.qwenFile().name}"
                }
                if (timedOut) {
                    // Do not leave a slow native inference/model alive after a timeout.
                    // The next manual operation will load a clean model again.
                    if (model === loaded) model = null
                    runCatching { Llama.releaseModel(loaded) }
                }
                withContext(Dispatchers.Main) {
                    onFailure(IllegalStateException(detail, error))
                }
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
