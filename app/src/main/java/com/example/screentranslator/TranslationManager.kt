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

    companion object {
        /**
         * Maximum number of texts sent in one provider request.
         *
         * DeepL documents no cap on array length, so this is a guard rail rather than an API
         * limit: one phone screen never produces more, and staying bounded keeps a failure easy to
         * isolate. Lowering it later is a one-line change once real timings are known.
         */
        private const val MAX_BATCH_TEXTS = 20
    }

    fun prepare(onReady: () -> Unit, onFailure: (Exception) -> Unit, trace: ScreenTLPerformanceTrace? = null) {
        val perfTrace = trace ?: ScreenTLPerformanceTrace.current()
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
        onCacheHit: (() -> Unit)? = null,
        trace: ScreenTLPerformanceTrace? = null
    ) {
        val perfTrace = trace ?: ScreenTLPerformanceTrace.current()
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

    /**
     * Translates one session's unique cache misses using as few provider requests as possible.
     *
     * The order is deliberate and is the whole point of the feature:
     *
     * 1. every text is checked against [TranslationCache] on its own, so a hit never enters a
     *    batch. Cache keys stay per text, which is what keeps "translate only the skill" a hit
     *    after the whole screen was translated once.
     * 2. only the misses are sent, chunked by [MAX_BATCH_TEXTS].
     * 3. each returned translation is cached under its own key immediately, so the work survives
     *    even if a later chunk fails.
     * 4. a rejected request is retried by shrinking the group: partial results are resent as just
     *    the missing texts, a fully rejected request is halved and each half retried. A text is
     *    therefore resent at most once before its failure is recorded, so one bad text can never
     *    stall the rest of the session.
     *
     * [onSuccess] reports every input index exactly once, through [BatchTranslationResult].
     */
    fun translateBatch(
        texts: List<String>,
        onSuccess: (BatchTranslationResult) -> Unit,
        trace: ScreenTLPerformanceTrace? = null
    ) {
        val perfTrace = trace ?: ScreenTLPerformanceTrace.current()
        val resolved = LinkedHashMap<Int, String>()
        val failed = LinkedHashMap<Int, String>()
        val fromCache = LinkedHashSet<Int>()
        var requests = 0
        fun report() = mainHandler.post {
            onSuccess(BatchTranslationResult(LinkedHashMap(resolved), LinkedHashMap(failed), fromCache, requests))
        }

        if (texts.isEmpty()) {
            report()
            return
        }

        val cacheScope = getCacheScope()
        val misses = ArrayList<Int>()
        texts.forEachIndexed { index, text ->
            val cached = TranslationCache.get(cacheScope, sourceLanguage, targetLanguage, text)
            if (cached != null) {
                resolved[index] = cached
                fromCache.add(index)
                perfTrace?.mark("translation_cache_hit provider=${getProviderName()} chars=${text.length}")
            } else {
                misses.add(index)
            }
        }

        if (misses.isEmpty()) {
            perfTrace?.mark("translation_batch_end texts=${resolved.size} requests=0 failed=0")
            report()
            return
        }

        val chunks = misses.chunked(MAX_BATCH_TEXTS)
        perfTrace?.mark("translation_batch_start provider=${getProviderName()} texts=${misses.size} requests=${chunks.size}")
        processChunks(
            chunks = chunks,
            chunkIndex = 0,
            texts = texts,
            resolved = resolved,
            failed = failed,
            cacheScope = cacheScope,
            trace = perfTrace,
            onRequest = { requests++ }
        ) {
            perfTrace?.mark("translation_batch_end texts=${resolved.size} requests=$requests failed=${failed.size}")
            report()
        }
    }

    private fun processChunks(
        chunks: List<List<Int>>,
        chunkIndex: Int,
        texts: List<String>,
        resolved: MutableMap<Int, String>,
        failed: MutableMap<Int, String>,
        cacheScope: String,
        trace: ScreenTLPerformanceTrace?,
        onRequest: () -> Unit,
        onDone: () -> Unit
    ) {
        if (chunkIndex >= chunks.size) {
            onDone()
            return
        }
        val indexes = chunks[chunkIndex]
        val batchTexts = indexes.map { texts[it] }
        // One marker per transport call, so the performance log's request count stays honest.
        trace?.mark("translation_request provider=${getProviderName()} texts=${batchTexts.size} chars=${batchTexts.sumOf { it.length }}")
        requestGroup(batchTexts, indexes, resolved, failed, cacheScope, trace, allowSingleRetry = true, onRequest) {
            processChunks(chunks, chunkIndex + 1, texts, resolved, failed, cacheScope, trace, onRequest, onDone)
        }
    }

    /**
     * Translates one group of texts, shrinking the group whenever it comes back rejected.
     *
     * [allowSingleRetry] guards the recursion: when a group has shrunk to a single text it gets
     * exactly one more attempt, and after that its failure is recorded instead of retried again.
     */
    private fun requestGroup(
        batchTexts: List<String>,
        indexes: List<Int>,
        resolved: MutableMap<Int, String>,
        failed: MutableMap<Int, String>,
        cacheScope: String,
        trace: ScreenTLPerformanceTrace?,
        allowSingleRetry: Boolean,
        onRequest: () -> Unit,
        onDone: () -> Unit
    ) {
        onRequest()
        provider.translateBatch(
            texts = batchTexts,
            onSuccess = { result ->
                result.translations.forEach { (position, translated) ->
                    resolved[indexes[position]] = translated
                    TranslationCache.put(cacheScope, sourceLanguage, targetLanguage, batchTexts[position], translated)
                }
                trace?.mark("translation_response texts=${result.translations.size} failed=${result.failures.size}")

                val rejected = result.failures.keys.sorted()
                when {
                    rejected.isEmpty() -> onDone()

                    batchTexts.size == 1 -> {
                        if (allowSingleRetry) {
                            trace?.mark("translation_retry_request texts=1 reason=empty_result")
                            requestGroup(batchTexts, indexes, resolved, failed, cacheScope, trace, allowSingleRetry = false, onRequest, onDone)
                        } else {
                            failed[indexes[0]] = result.failures[0] ?: "Gagal diterjemahkan"
                            trace?.mark("translation_gave_up index=${indexes[0]}")
                            onDone()
                        }
                    }

                    else -> {
                        // Partial failure: resend only the missing texts, never the resolved ones.
                        val retryTexts = rejected.map { batchTexts[it] }
                        val retryIndexes = rejected.map { indexes[it] }
                        trace?.mark("translation_retry_request texts=${retryTexts.size} reason=partial")
                        requestGroup(retryTexts, retryIndexes, resolved, failed, cacheScope, trace, allowSingleRetry, onRequest, onDone)
                    }
                }
            },
            onFailure = { exception ->
                val reason = exception.message ?: "Unknown error"
                trace?.mark("translation_failed texts=${batchTexts.size}")
                when {
                    batchTexts.size == 1 && allowSingleRetry -> {
                        trace?.mark("translation_retry_request texts=1 reason=request_failed")
                        requestGroup(batchTexts, indexes, resolved, failed, cacheScope, trace, allowSingleRetry = false, onRequest, onDone)
                    }
                    batchTexts.size == 1 -> {
                        failed[indexes[0]] = reason
                        trace?.mark("translation_gave_up index=${indexes[0]}")
                        onDone()
                    }
                    else -> {
                        // The whole request was rejected. Halve it and retry both halves, which
                        // shrinks the offending payload and isolates the texts that are fine.
                        val middle = batchTexts.size / 2
                        trace?.mark("translation_split_retry texts=${batchTexts.size}")
                        requestGroup(
                            batchTexts.subList(0, middle),
                            indexes.subList(0, middle),
                            resolved, failed, cacheScope, trace, allowSingleRetry, onRequest
                        ) {
                            requestGroup(
                                batchTexts.subList(middle, batchTexts.size),
                                indexes.subList(middle, indexes.size),
                                resolved, failed, cacheScope, trace, allowSingleRetry, onRequest
                            ) { onDone() }
                        }
                    }
                }
            }
        )
    }


    fun getProviderName(): String = when (provider) {
        is OpenRouterTranslationProvider -> ApiSettings.PROVIDER_OPENROUTER
        is DeepLTranslationProvider -> ApiSettings.PROVIDER_DEEPL
        is MlKitTranslationProvider -> ApiSettings.PROVIDER_ML_KIT
        else -> provider::class.java.simpleName
    }

    private fun getCacheScope(): String = when (provider) {
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
        if (ApiSettings.isOpenRouterEnabled()) {
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
