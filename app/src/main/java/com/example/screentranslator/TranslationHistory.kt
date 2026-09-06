package com.example.screentranslator

object TranslationHistory {

    private val entries = mutableListOf<String>()
    private var listener: (() -> Unit)? = null

    fun add(entry: String) {
        entries.add(0, entry)
        listener?.invoke()
    }

    fun getAll(): List<String> {
        return entries.toList()
    }

    fun clear() {
        entries.clear()
        listener?.invoke()
    }

    fun setListener(listener: (() -> Unit)?) {
        this.listener = listener
    }
}
