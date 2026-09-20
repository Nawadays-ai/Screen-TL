package com.example.screentranslator

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.util.LinkedHashMap

/**
 * A small persistent LRU cache for successful translation responses.
 *
 * Entries are scoped to the effective provider and language pair. Source text and translations
 * remain on-device in this app-private SharedPreferences file and are never written to Logcat.
 */
object TranslationCache {

    private const val TAG = "ScreenTL-TranslationCache"
    private const val PREFS_NAME = "screen_tl_translation_cache"
    private const val KEY_ENTRIES = "entries_v1"
    private const val CACHE_VERSION = "v1"
    private const val MAX_DISK_ENTRIES = 500
    private const val MAX_MEMORY_ENTRIES = 100
    private const val MAX_DISK_BYTES = 256 * 1024

    private lateinit var preferences: android.content.SharedPreferences
    private var initialized = false

    private val diskEntries = LinkedHashMap<String, String>(MAX_DISK_ENTRIES, 0.75f, true)
    private val memoryEntries = LinkedHashMap<String, String>(MAX_MEMORY_ENTRIES, 0.75f, true)

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return

        preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromDisk()
        initialized = true
        Log.i(TAG, "Translation cache initialized: entries=${diskEntries.size}")
    }

    @Synchronized
    fun get(providerName: String, sourceLanguage: String, targetLanguage: String, sourceText: String): String? {
        ensureInitialized()
        val key = keyFor(providerName, sourceLanguage, targetLanguage, sourceText)
        memoryEntries[key]?.let { return it }

        val translated = diskEntries[key] ?: return null
        putMemory(key, translated)
        return translated
    }

    @Synchronized
    fun put(providerName: String, sourceLanguage: String, targetLanguage: String, sourceText: String, translatedText: String) {
        ensureInitialized()
        if (translatedText.isBlank()) return

        val key = keyFor(providerName, sourceLanguage, targetLanguage, sourceText)
        diskEntries[key] = translatedText
        putMemory(key, translatedText)
        trimDiskEntries()
        persist()
    }

    private fun keyFor(providerName: String, sourceLanguage: String, targetLanguage: String, sourceText: String): String {
        val normalizedText = normalizeSourceText(sourceText)
        val material = listOf(CACHE_VERSION, providerName, sourceLanguage, targetLanguage, normalizedText)
            .joinToString(separator = "\u0000")
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun normalizeSourceText(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
        .replace("\r\n", "\n")
        .lineSequence()
        .joinToString("\n") { line -> line.trim().replace(Regex("[\\t\\u000B\\u000C ]+"), " ") }
        .trim()

    private fun putMemory(key: String, translated: String) {
        memoryEntries[key] = translated
        while (memoryEntries.size > MAX_MEMORY_ENTRIES) {
            memoryEntries.remove(memoryEntries.entries.iterator().next().key)
        }
    }

    private fun loadFromDisk() {
        val raw = preferences.getString(KEY_ENTRIES, null) ?: return
        try {
            val entries = JSONArray(raw)
            for (index in 0 until entries.length()) {
                val entry = entries.getJSONObject(index)
                val key = entry.optString("key")
                val translated = entry.optString("translated")
                if (key.isNotBlank() && translated.isNotBlank()) diskEntries[key] = translated
            }
            trimDiskEntries()
        } catch (exception: Exception) {
            Log.w(TAG, "Ignoring unreadable translation cache", exception)
            preferences.edit().remove(KEY_ENTRIES).apply()
        }
    }

    private fun trimDiskEntries() {
        while (diskEntries.size > MAX_DISK_ENTRIES || serializedSizeBytes() > MAX_DISK_BYTES) {
            val eldestKey = diskEntries.entries.firstOrNull()?.key ?: return
            diskEntries.remove(eldestKey)
        }
    }

    private fun serializedSizeBytes(): Int = diskEntries.entries.sumOf { (key, translated) ->
        key.toByteArray(StandardCharsets.UTF_8).size + translated.toByteArray(StandardCharsets.UTF_8).size + 32
    }

    private fun persist() {
        val json = JSONArray()
        diskEntries.forEach { (key, translated) ->
            json.put(JSONObject().put("key", key).put("translated", translated))
        }
        preferences.edit().putString(KEY_ENTRIES, json.toString()).apply()
    }

    private fun ensureInitialized() {
        check(initialized) { "TranslationCache.initialize(context) must be called first" }
    }
}