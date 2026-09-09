package com.example.screentranslator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Stores timing metadata only; never stores OCR or translation text. */
data class PerformanceLogEntry(
    val timestamp: Long,
    val operation: String,
    val captureMs: Long?,
    val ocrMs: Long?,
    val translationMs: Long?,
    val displayMs: Long?,
    val totalMs: Long,
    val result: String
)

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
        for (i in 0 until minOf(current.length(), MAX_ENTRIES - 1)) {
            next.put(current.getJSONObject(i))
        }
        target.edit().putString(KEY_ENTRIES, next.toString()).apply()
    }

    @Synchronized
    fun getAll(): List<PerformanceLogEntry> {
        val target = prefs ?: return emptyList()
        val array = JSONArray(target.getString(KEY_ENTRIES, "[]"))
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                add(fromJson(array.getJSONObject(i)))
            }
        }
    }

    @Synchronized
    fun clear() {
        prefs?.edit()?.remove(KEY_ENTRIES)?.apply()
    }

    private fun toJson(entry: PerformanceLogEntry): JSONObject = JSONObject().apply {
        put("timestamp", entry.timestamp)
        put("operation", entry.operation)
        put("captureMs", entry.captureMs ?: JSONObject.NULL)
        put("ocrMs", entry.ocrMs ?: JSONObject.NULL)
        put("translationMs", entry.translationMs ?: JSONObject.NULL)
        put("displayMs", entry.displayMs ?: JSONObject.NULL)
        put("totalMs", entry.totalMs)
        put("result", entry.result)
    }

    private fun fromJson(json: JSONObject): PerformanceLogEntry = PerformanceLogEntry(
        timestamp = json.optLong("timestamp"),
        operation = json.optString("operation", "TranslateFlow"),
        captureMs = json.optLongOrNull("captureMs"),
        ocrMs = json.optLongOrNull("ocrMs"),
        translationMs = json.optLongOrNull("translationMs"),
        displayMs = json.optLongOrNull("displayMs"),
        totalMs = json.optLong("totalMs"),
        result = json.optString("result", "completed")
    )

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (isNull(name) || !has(name)) null else optLong(name)
}
