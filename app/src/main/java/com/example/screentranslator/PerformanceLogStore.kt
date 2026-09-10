package com.example.screentranslator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Stores timing metadata and sanitized diagnostics; never stores OCR or translation text. */
data class PerformanceLogEntry(
    val timestamp: Long,
    val operation: String,
    val captureMs: Long?,
    val ocrMs: Long?,
    val translationPrepareMs: Long?,
    val translationInferenceMs: Long?,
    val translationMs: Long?,
    val displayMs: Long?,
    val totalMs: Long,
    val result: String,
    val diagnostic: String? = null
) {
    val unaccountedMs: Long
        get() {
            val measured = listOf(captureMs, ocrMs, translationMs, displayMs).filterNotNull().sum()
            return (totalMs - measured).coerceAtLeast(0L)
        }
}

object PerformanceLogStore {
    private const val PREFS = "screen_tl_performance"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 50
    private var prefs: android.content.SharedPreferences? = null

    fun initialize(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun add(entry: PerformanceLogEntry) {
        val target = prefs ?: return
        val current = JSONArray(target.getString(KEY_ENTRIES, "[]"))
        val next = JSONArray()
        next.put(toJson(entry))
        for (i in 0 until minOf(current.length(), MAX_ENTRIES - 1)) next.put(current.getJSONObject(i))
        target.edit().putString(KEY_ENTRIES, next.toString()).apply()
    }

    @Synchronized
    fun getAll(): List<PerformanceLogEntry> {
        val target = prefs ?: return emptyList()
        val array = JSONArray(target.getString(KEY_ENTRIES, "[]"))
        return buildList(array.length()) {
            for (i in 0 until array.length()) add(fromJson(array.getJSONObject(i)))
        }
    }

    @Synchronized
    fun clear() { prefs?.edit()?.remove(KEY_ENTRIES)?.apply() }

    private fun toJson(entry: PerformanceLogEntry): JSONObject = JSONObject().apply {
        put("timestamp", entry.timestamp)
        put("operation", entry.operation)
        put("captureMs", entry.captureMs ?: JSONObject.NULL)
        put("ocrMs", entry.ocrMs ?: JSONObject.NULL)
        put("translationPrepareMs", entry.translationPrepareMs ?: JSONObject.NULL)
        put("translationInferenceMs", entry.translationInferenceMs ?: JSONObject.NULL)
        put("translationMs", entry.translationMs ?: JSONObject.NULL)
        put("displayMs", entry.displayMs ?: JSONObject.NULL)
        put("totalMs", entry.totalMs)
        put("result", entry.result)
        put("diagnostic", entry.diagnostic ?: JSONObject.NULL)
    }

    private fun fromJson(json: JSONObject): PerformanceLogEntry = PerformanceLogEntry(
        timestamp = json.optLong("timestamp"),
        operation = json.optString("operation", "TranslateFlow"),
        captureMs = json.optLongOrNull("captureMs"),
        ocrMs = json.optLongOrNull("ocrMs"),
        translationPrepareMs = json.optLongOrNull("translationPrepareMs"),
        translationInferenceMs = json.optLongOrNull("translationInferenceMs"),
        translationMs = json.optLongOrNull("translationMs"),
        displayMs = json.optLongOrNull("displayMs"),
        totalMs = json.optLong("totalMs"),
        result = json.optString("result", "completed"),
        diagnostic = json.optStringOrNull("diagnostic")
    )

    private fun JSONObject.optLongOrNull(name: String): Long? = if (isNull(name) || !has(name)) null else optLong(name)
    private fun JSONObject.optStringOrNull(name: String): String? = if (isNull(name) || !has(name)) null else optString(name).takeIf { it.isNotBlank() }
}
