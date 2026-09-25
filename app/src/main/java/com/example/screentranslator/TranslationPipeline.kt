package com.example.screentranslator

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Aggregated outcome of one translation session (Manual, Real-Time, or Klip).
 *
 * [overlayItems] holds one item per accepted OCR unit, in original unit order, and only for units
 * that produced a translation. [historyEntry] pairs every accepted unit with its result, including
 * failures. The metric fields follow the definitions in SMART_CACHE_PLAN.md.
 */
data class TranslationSessionResult(
    val overlayItems: List<TranslationOverlayItem>,
    val historyEntry: String,
    val ocrUnits: Int,
    val uniqueUnits: Int,
    val cacheHitUnits: Int,
    val uniqueCacheHits: Int,
    val uniqueMisses: Int,
    val providerRequests: Int
)

/**
 * Turns OCR units into translations through a deduplicated pipeline:
 *
 * `units -> normalize -> group by identity -> one translation per unique source -> fan out`
 *
 * Deduplication identity comes from [TranslationTextNormalizer], the same normalizer the
 * translation cache uses, so grouping always agrees with cache identity. Geometry never takes part
 * in the identity: the same text found in two places is resolved once and then rendered at both
 * locations, each with its own box and writing orientation.
 *
 * Resolution stays strictly sequential and unit by unit. Batching is deliberately out of scope
 * here, so [TranslationSessionResult.providerRequests] equals
 * [TranslationSessionResult.uniqueMisses].
 *
 * A pipeline instance owns per-run state; create a new one for every session.
 */
class TranslationPipeline(
    private val translator: TranslationManager,
    private val sourceLanguage: String,
    private val targetLanguage: String,
    private val modeLabel: String,
    private val isCancelled: () -> Boolean,
    private val trace: ScreenTLPerformanceTrace? = null
) {

    private data class UnitGroup(
        val identity: String,
        val units: MutableList<DetectedText> = mutableListOf(),
        var translation: String? = null,
        var failure: String? = null,
        var resolvedFromCache: Boolean = false
    )

    private val groups = LinkedHashMap<String, UnitGroup>()
    private val orderedUnits = mutableListOf<Pair<DetectedText, UnitGroup>>()

    fun run(units: List<DetectedText>, onFinished: (TranslationSessionResult) -> Unit) {
        groups.clear()
        orderedUnits.clear()

        units.forEach { unit ->
            val identity = TranslationTextNormalizer.normalize(unit.text)
            if (identity.isEmpty()) return@forEach
            val group = groups.getOrPut(identity) { UnitGroup(identity) }
            group.units.add(unit)
            orderedUnits.add(unit to group)
        }

        if (orderedUnits.isEmpty()) {
            onFinished(buildResult())
            return
        }

        resolveNext(groups.values.toList(), 0) { onFinished(buildResult()) }
    }

    /**
     * Resolves one unique source per step. Cancellation is only checked at step boundaries, so a
     * cancelled session stops before issuing further provider work while an in-flight call is
     * still allowed to settle.
     */
    private fun resolveNext(orderedGroups: List<UnitGroup>, index: Int, onDone: () -> Unit) {
        if (isCancelled() || index >= orderedGroups.size) {
            onDone()
            return
        }

        val group = orderedGroups[index]
        val representative = group.units.first()
        try {
            translator.translate(
                text = representative.text,
                onSuccess = { translated ->
                    group.translation = translated
                    resolveNext(orderedGroups, index + 1, onDone)
                },
                onFailure = { exception ->
                    group.failure = exception.message ?: "Unknown error"
                    resolveNext(orderedGroups, index + 1, onDone)
                },
                onCacheHit = { group.resolvedFromCache = true },
                trace = trace
            )
        } catch (exception: Exception) {
            group.failure = exception.message ?: "Unknown error"
            resolveNext(orderedGroups, index + 1, onDone)
        }
    }

    private fun buildResult(): TranslationSessionResult {
        val overlayItems = ArrayList<TranslationOverlayItem>(orderedUnits.size)
        val historyLines = ArrayList<String>(orderedUnits.size)

        orderedUnits.forEach { (unit, group) ->
            val translated = group.translation
            if (translated != null) {
                overlayItems.add(unit.toOverlayItem(translated))
                historyLines.add("${unit.text}\n→ $translated")
            } else {
                historyLines.add("${unit.text}\n→ [Gagal diterjemahkan: ${group.failure ?: "Unknown error"}]")
            }
        }

        var cacheHitUnits = 0
        var uniqueCacheHits = 0
        var uniqueMisses = 0
        groups.values.forEach { group ->
            if (group.resolvedFromCache) {
                cacheHitUnits += group.units.size
                uniqueCacheHits++
            } else {
                uniqueMisses++
            }
        }

        val ocrUnits = orderedUnits.size
        val uniqueUnits = groups.size
        val providerRequests = uniqueMisses
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val providerLabel = buildString {
            append(translator.getProviderName())
            if (modeLabel.isNotBlank()) append(" (").append(modeLabel).append(")")
        }
        val historyEntry = buildString {
            append("[").append(time).append("]\n")
            append("TL: ").append(providerLabel).append("\n")
            append("OCR Units: ").append(ocrUnits)
                .append(" | Unique Units: ").append(uniqueUnits)
                .append(" | Cache-hit Units: ").append(cacheHitUnits)
                .append(" | Unique Cache Hits: ").append(uniqueCacheHits)
                .append(" | Unique Misses: ").append(uniqueMisses)
                .append(" | Provider Requests: ").append(providerRequests).append("\n")
            append(sourceLanguage).append(" → ").append(targetLanguage).append("\n\n")
            append(historyLines.joinToString("\n\n"))
        }

        return TranslationSessionResult(
            overlayItems = overlayItems,
            historyEntry = historyEntry,
            ocrUnits = ocrUnits,
            uniqueUnits = uniqueUnits,
            cacheHitUnits = cacheHitUnits,
            uniqueCacheHits = uniqueCacheHits,
            uniqueMisses = uniqueMisses,
            providerRequests = providerRequests
        )
    }

    private fun DetectedText.toOverlayItem(translatedText: String): TranslationOverlayItem =
        TranslationOverlayItem(
            translatedText = translatedText,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            sourceTextSizePx = sourceTextSizePx,
            backgroundColor = backgroundColor,
            orientation = orientation
        )
}
