package com.example.screentranslator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var spinnerSourceLang: Spinner
    private lateinit var spinnerTargetLang: Spinner
    private lateinit var spinnerRealtimeApi: Spinner
    private lateinit var spinnerManualApi: Spinner
    private lateinit var btnPlay: Button

    private val languages = arrayOf("Jepang", "Mandarin (China)", "Inggris", "Indonesia")
    private val realtimeApis = arrayOf("Google ML Kit (On-Device/Gratis)")
    private val manualApis = arrayOf("Google ML Kit", "DeepL API", "Gemini AI")

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (checkOverlayPermission()) {
            requestMediaProjection()
        } else {
            Toast.makeText(this, "Izin Tampilan di Atas Aplikasi Lain Diperlukan!", Toast.LENGTH_LONG).show()
        }
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startFloatingService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Izin Rekam Layar Ditolak!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinnerSourceLang = findViewById(R.id.spinnerSourceLang)
        spinnerTargetLang = findViewById(R.id.spinnerTargetLang)
        spinnerRealtimeApi = findViewById(R.id.spinnerRealtimeApi)
        spinnerManualApi = findViewById(R.id.spinnerManualApi)
        btnPlay = findViewById(R.id.btnPlay)

        setupSpinners()

        btnPlay.setOnClickListener {
            if (!checkOverlayPermission()) {
                requestOverlayPermission()
            } else {
                requestMediaProjection()
            }
        }
    }

    private fun setupSpinners() {
        val langAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languages)
        spinnerSourceLang.adapter = langAdapter
        spinnerTargetLang.adapter = langAdapter
        
        // Default: Sumber Jepang -> Target Indonesia
        spinnerSourceLang.setSelection(0)
        spinnerTargetLang.setSelection(3)

        val rtAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, realtimeApis)
        spinnerRealtimeApi.adapter = rtAdapter

        val manualAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, manualApis)
        spinnerManualApi.adapter = manualAdapter
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun requestMediaProjection() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startFloatingService(resultCode: Int, data: Intent) {
        val intent = Intent(this, FloatingService::class.java).apply {
            putExtra("EXTRA_RESULT_CODE", resultCode)
            putExtra("EXTRA_DATA", data)
            putExtra("EXTRA_SOURCE_LANG", spinnerSourceLang.selectedItem.toString())
            putExtra("EXTRA_TARGET_LANG", spinnerTargetLang.selectedItem.toString())
            putExtra("EXTRA_REALTIME_API", spinnerRealtimeApi.selectedItem.toString())
            putExtra("EXTRA_MANUAL_API", spinnerManualApi.selectedItem.toString())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        // Minimalkan aplikasi agar langsung masuk ke game
        moveTaskToBack(true)
    }
}
