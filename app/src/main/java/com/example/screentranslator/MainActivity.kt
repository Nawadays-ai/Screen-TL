package com.example.screentranslator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var spinnerSourceLang: Spinner
    private lateinit var spinnerTargetLang: Spinner
    private lateinit var btnPlay: ImageButton
    private lateinit var btnHistory: ImageButton
    private lateinit var btnPerformance: ImageButton
    private lateinit var btnSettings: ImageButton
    private val languages = arrayOf("Jepang", "Mandarin (China)", "Inggris", "Indonesia")

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (checkOverlayPermission()) requestMediaProjection() else Toast.makeText(this, "Izin Tampilan di Atas Aplikasi Lain Diperlukan", Toast.LENGTH_LONG).show()
    }
    private val mediaProjectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) startFloatingService(result.resultCode, result.data!!)
        else Toast.makeText(this, "Izin Rekam Layar Ditolak", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        TranslationHistory.initialize(applicationContext)
        ApiSettings.initialize(applicationContext)
        LocalModelStore.initialize(applicationContext)
        PerformanceLogStore.initialize(applicationContext)
        spinnerSourceLang = findViewById(R.id.spinnerSourceLang)
        spinnerTargetLang = findViewById(R.id.spinnerTargetLang)
        btnPlay = findViewById(R.id.btnPlay)
        btnHistory = findViewById(R.id.btnHistory)
        btnPerformance = findViewById(R.id.btnPerformance)
        btnSettings = findViewById(R.id.btnSettings)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languages)
        spinnerSourceLang.adapter = adapter; spinnerTargetLang.adapter = adapter
        spinnerSourceLang.setSelection(0); spinnerTargetLang.setSelection(3)
        btnHistory.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        btnPerformance.setOnClickListener { startActivity(Intent(this, PerformanceActivity::class.java)) }
        btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        btnPlay.setOnClickListener { if (!checkOverlayPermission()) requestOverlayPermission() else requestMediaProjection() }
    }
    private fun checkOverlayPermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
    private fun requestOverlayPermission() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) overlayPermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
    private fun requestMediaProjection() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) projectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) else projectionManager.createScreenCaptureIntent()
        mediaProjectionLauncher.launch(captureIntent)
    }
    private fun startFloatingService(resultCode: Int, data: Intent) {
        ScreenCaptureSession.save(resultCode, data)
        val intent = Intent(this, FloatingService::class.java).apply {
            putExtra("EXTRA_SOURCE_LANG", spinnerSourceLang.selectedItem.toString())
            putExtra("EXTRA_TARGET_LANG", spinnerTargetLang.selectedItem.toString())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        moveTaskToBack(true)
    }
}
