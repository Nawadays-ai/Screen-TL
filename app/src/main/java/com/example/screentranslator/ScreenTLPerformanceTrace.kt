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

    fun mark(stage: String) {
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        Log.i(TAG, "$operation | +${elapsed}ms | $stage")
    }

    fun finish(result: String = "completed") {
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        Log.i(TAG, "$operation | TOTAL ${elapsed}ms | $result")
        if (active === this) active = null
    }

    fun finishWhenIdle(delayMs: Long = 500L, result: String = "completed") {
        finishRunnable?.let(mainHandler::removeCallbacks)
        finishRunnable = Runnable { finish(result) }
        mainHandler.postDelayed(finishRunnable!!, delayMs)
    }
}
