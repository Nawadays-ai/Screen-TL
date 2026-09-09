package com.example.screentranslator

import android.os.SystemClock
import android.util.Log

/** Lightweight stage timing for diagnostics; never records OCR/translation text. */
class ScreenTLPerformanceTrace(private val operation: String) {
    companion object {
        private const val TAG = "ScreenTL-Perf"
    }

    private val startedAt = SystemClock.elapsedRealtime()

    fun mark(stage: String) {
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        Log.i(TAG, "$operation | +${elapsed}ms | $stage")
    }

    fun finish(result: String = "completed") {
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        Log.i(TAG, "$operation | TOTAL ${elapsed}ms | $result")
    }
}
