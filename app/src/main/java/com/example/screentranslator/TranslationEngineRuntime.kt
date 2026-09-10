package com.example.screentranslator

object TranslationEngineRuntime {
    private val closers = LinkedHashSet<() -> Unit>()

    @Synchronized
    fun registerCloser(closer: () -> Unit) {
        closers.add(closer)
    }

    @Synchronized
    fun unregisterCloser(closer: () -> Unit) {
        closers.remove(closer)
    }

    @Synchronized
    fun releaseActiveLocalEngines() {
        closers.toList().forEach { runCatching { it() } }
    }
}
