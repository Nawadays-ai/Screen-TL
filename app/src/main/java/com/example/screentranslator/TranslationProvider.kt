package com.example.screentranslator

interface TranslationProvider {
    fun prepare(onReady: () -> Unit, onFailure: (Exception) -> Unit)
    fun translate(text: String, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit)
    fun close()

    /**
     * Translates several independent texts in one round trip.
     *
     * The default implementation translates one by one, so providers without a native batch
     * endpoint (ML Kit) keep their current behaviour and cost. Providers that do support it
     * override this.
     *
     * Every outcome is reported by the index of the requested text, so a partially failed batch
     * never leaves the caller guessing which entry came back. See [BatchTranslationResult].
     */
    fun translateBatch(
        texts: List<String>,
        onSuccess: (BatchTranslationResult) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (texts.isEmpty()) {
            onSuccess(BatchTranslationResult(emptyMap(), emptyMap()))
            return
        }
        if (texts.size == 1) {
            translate(
                text = texts.first(),
                onSuccess = { onSuccess(BatchTranslationResult(mapOf(0 to it), emptyMap())) },
                onFailure = { onSuccess(BatchTranslationResult(emptyMap(), mapOf(0 to (it.message ?: "Unknown error")))) }
            )
            return
        }
        sequentialFallback(texts, 0, mutableMapOf(), mutableMapOf(), onSuccess, onFailure)
    }

    private fun sequentialFallback(
        texts: List<String>,
        index: Int,
        translations: MutableMap<Int, String>,
        failures: MutableMap<Int, String>,
        onSuccess: (BatchTranslationResult) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (index >= texts.size) {
            onSuccess(BatchTranslationResult(translations.toMap(), failures.toMap()))
            return
        }
        translate(
            text = texts[index],
            onSuccess = { value ->
                translations[index] = value
                sequentialFallback(texts, index + 1, translations, failures, onSuccess, onFailure)
            },
            onFailure = { exception ->
                failures[index] = exception.message ?: "Unknown error"
                sequentialFallback(texts, index + 1, translations, failures, onSuccess, onFailure)
            }
        )
    }
}

/**
 * Outcome of one translation batch.
 *
 * [translations] and [failures] are keyed by the index of the text in the request. A text appears
 * in exactly one of them, so the caller can always attribute a result or a failure to the unit
 * that produced it. The `onFailure` callback is reserved for failures that concern the request as
 * a whole (HTTP error, timeout, unparsable body), where no individual index can be blamed.
 *
 * [fromCache] and [requests] are filled in by [TranslationManager], which owns the cache lookup
 * and counts transport calls; a provider leaves both at their defaults.
 */
data class BatchTranslationResult(
    val translations: Map<Int, String>,
    val failures: Map<Int, String>,
    val fromCache: Set<Int> = emptySet(),
    val requests: Int = 0
) {
    val hasFailures: Boolean get() = failures.isNotEmpty()
}
