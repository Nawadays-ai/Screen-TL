package com.example.screentranslator

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PerformanceActivity : AppCompatActivity() {
    private lateinit var tvPerformance: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_performance)
        PerformanceLogStore.initialize(applicationContext)
        findViewById<Button>(R.id.btnBackPerformance).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnClearPerformance).setOnClickListener {
            PerformanceLogStore.clear()
            updatePerformance()
        }
        tvPerformance = findViewById(R.id.tvPerformance)
        updatePerformance()
    }

    override fun onResume() {
        super.onResume()
        if (::tvPerformance.isInitialized) updatePerformance()
    }

    private fun updatePerformance() {
        val entries = PerformanceLogStore.getAll()
        if (entries.isEmpty()) {
            tvPerformance.text = "Belum ada data performa.\n\nJalankan Manual TL atau Klip terlebih dahulu."
            return
        }

        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        tvPerformance.text = entries.joinToString("\n\n────────────────────\n\n") { entry ->
            buildString {
                append("[").append(formatter.format(Date(entry.timestamp))).append("] ")
                append(entry.operation).append(" — ").append(entry.result).append("\n")
                append("SS: ").append(formatMs(entry.captureMs)).append("\n")
                append("OCR: ").append(formatMs(entry.ocrMs)).append("\n")
                append("Terjemahan: ").append(formatMs(entry.translationMs)).append("\n")
                append("Tampilkan: ").append(formatMs(entry.displayMs)).append("\n")
                append("Lainnya: ").append(entry.unaccountedMs).append(" ms\n")
                append("TOTAL: ").append(entry.totalMs).append(" ms")
                entry.diagnostic?.let {
                    append("\nDiagnostik: ").append(it)
                }
            }
        }
    }

    private fun formatMs(value: Long?): String = value?.let { "$it ms" } ?: "—"
}
