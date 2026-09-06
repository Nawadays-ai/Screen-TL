package com.example.screentranslator

import android.content.Context
import android.util.Log
import org.json.JSONArray

object TranslationHistory {

    private const val TAG = "ScreenTL-History"
    private const val PREFS_NAME = "screen_tl_history"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 50

    private var initialized = false
    private lateinit var preferences: android.content.SharedPreferences
    private var listener: (() -> Unit)? = null

    @Synchronized
    fun initialize(context: Context) {
        if (!initialized) {
            preferences = context.applicationContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            initialized = true
            Log.i(TAG, "History initialized")
        }
    }

    @Synchronized
    fun add(entry: String) {
        ensureInitialized()

        val entries = getAll().toMutableList()
        entries.add(0, entry)

        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(entries.lastIndex)
        }

        val saved = save(entries)
        Log.i(TAG, "History add: saved=$saved, entries=${entries.size}")
        listener?.invoke()
    }

    @Synchronized
    fun getAll(): List<String> {
        ensureInitialized()

        val raw = preferences.getString(KEY_ENTRIES, null) ?: return emptyList()

        return try {
            val json = JSONArray(raw)
            buildList(json.length()) {
                for (index in 0 until json.length()) {
                    add(json.getString(index))
                }
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to read history", exception)
            emptyList()
        }
    }

    @Synchronized
    fun clear() {
        ensureInitialized()
        val cleared = preferences.edit().remove(KEY_ENTRIES).commit()
        Log.i(TAG, "History cleared: $cleared")
        listener?.invoke()
    }

    fun setListener(listener: (() -> Unit)?) {
        this.listener = listener
    }

    private fun save(entries: List<String>): Boolean {
        val json = JSONArray()
        entries.forEach { json.put(it) }

        return preferences.edit()
            .putString(KEY_ENTRIES, json.toString())
            .commit()
    }

    private fun ensureInitialized() {
        check(initialized) {
            "TranslationHistory.initialize(context) must be called first"
        }
    }
}
