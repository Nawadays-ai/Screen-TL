package com.example.screentranslator

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/** Lightweight stage timing for diagnostics; never records OCR/translation text. */
class ScreenTLPerformanceTrace(private val operation: String) {
    companion object {
        private const val TAG = "ScreenTL-Perf"
        private val mainHandler = Handler(Looper.getMainLooper())
        @Volatile private var active: ScreenTLPerformanceTrace? = null
        private var finishRunnable: Runnable? = null

        fun start(operation: String): ScreenTLPerformanceTrace {
            val trace = ScreenTLPerformanceTrace(operation)
            active = trace
            return trace
        }

        fun current(): ScreenTLPerformanceTrace? = active
    }

    private val startedAt = SystemClock.elapsedRealtime()
    private val events = mutableListOf<Pair<String, Long>>()

    fun mark(stage: String) {
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        synchronized(events) { events.add(stage to elapsed) }
        Log.i(TAG, "$operation | +${elapsed}ms | $stage")
    }

    fun finish(result: String = "completed") {
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val snapshot = synchronized(events) { events.toList() }
        Log.i(TAG, "$operation | TOTAL ${elapsed}ms | $result")
        PerformanceLogStore.add(
            PerformanceLogEntry(
                timestamp = System.currentTimeMillis(),
                operation = operation,
                captureMs = durationBetween(snapshot, "capture_request", "screenshot_ready"),
                ocrMs = durationBetween(snapshot, "ocr_start", "ocr_complete"),
                translationMs = durationBetweenFirstToLast(snapshot, "translation_request", setOf("translation_response", "translation_failed")),
                displayMs = durationBetween(snapshot, "display_start", "displayed"),
                totalMs = elapsed,
                result = result
            )
        )
        if (active === this) active = null
    }

    fun finishWhenIdle(delayMs: Long = 500L, result: String = "completed") {
        finishRunnable?.let(mainHandler::removeCallbacks)
        finishRunnable = Runnable { finish(result) }
        mainHandler.postDelayed(finishRunnable!!, delayMs)
    }

    private fun durationBetween(events: List<Pair<String, Long>>, start: String, end: String): Long? {
        val startAt = events.firstOrNull { it.first == start }?.second ?: return null
        val endAt = events.firstOrNull { it.first == end && it.second >= startAt }?.second ?: return null
        return (endAt - startAt).coerceAtLeast(0L)
    }

    private fun durationBetweenFirstToLast(events: List<Pair<String, Long>>, start: String, ends: Set<String>): Long? {
        val startAt = events.firstOrNull { it.first == start }?.second ?: return null
        val endAt = events.lastOrNull { it.first in ends && it.second >= startAt }?.second ?: return null
        return (endAt - startAt).coerceAtLeast(0L)
    }
}
