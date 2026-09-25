package com.example.screentranslator

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HistoryActivity : AppCompatActivity() {
    private lateinit var tvHistory: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        TranslationHistory.initialize(applicationContext)

        findViewById<ImageButton>(R.id.btnBackHistory).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnClearHistory).setOnClickListener {
            TranslationHistory.clear()
            updateHistory()
        }
        tvHistory = findViewById(R.id.tvHistory)
        updateHistory()
    }

    override fun onResume() {
        super.onResume()
        if (::tvHistory.isInitialized) updateHistory()
    }

    private fun updateHistory() {
        val entries = TranslationHistory.getAll()
        tvHistory.text = if (entries.isEmpty()) {
            "Belum ada hasil terjemahan."
        } else {
            entries.joinToString("\n\n--------------------\n\n")
        }
    }
}
