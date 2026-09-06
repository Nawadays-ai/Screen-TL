package com.example.screentranslator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FloatingService : Service() {

    companion object {
        private const val TAG = "ScreenTL-Service"
        private const val MANUAL_CAPTURE_TIMEOUT_MS = 3_000L
        private const val MANUAL_PROCESS_TIMEOUT_MS = 30_000L
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var fabMain: FloatingActionButton
    private lateinit var layoutSubMenu: LinearLayout
    private lateinit var btnRealtime: Button
    private lateinit var btnManual: Button
    private lateinit var btnExit: Button

    private var params: WindowManager.LayoutParams? = null
    private var overlayView: TranslationOverlayView? = null
    private var isRealtimeActive = false
    private var isSubMenuVisible = false
    private var manualTranslationPending = false
    private val mainHandler = Handler()
    private var manualCaptureTimeout: Runnable? = null
    private var manualProcessTimeout: Runnable? = null

    private var screenCaptureManager: ScreenCaptureManager? = null
    private var ocrManager: OcrManager? = null
    private var sourceLanguage: String = "Jepang"
    private var targetLanguage: String = "Indonesia"
    private var translationManager: TranslationManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        TranslationHistory.initialize(applicationContext)

        sourceLanguage = intent?.getStringExtra("EXTRA_SOURCE_LANG") ?: "Jepang"
        targetLanguage = intent?.getStringExtra("EXTRA_TARGET_LANG") ?: "Indonesia"

        Log.i(TAG, "Service started: $sourceLanguage -> $targetLanguage")

        if (ocrManager == null) {
            ocrManager = OcrManager(sourceLanguage)
        }

        if (translationManager == null) {
            translationManager = TranslationManager(sourceLanguage, targetLanguage)
        }

        if (screenCaptureManager == null) {
            val resultCode = ScreenCaptureSession.resultCode
            val projectionData = ScreenCaptureSession.data

            if (projectionData != null) {
                screenCaptureManager = ScreenCaptureManager(this, resultCode, projectionData)
                val started = screenCaptureManager?.start() == true
                Log.i(TAG, "Screen capture manager start result=$started")

                if (!started) {
                    showToast("Screen Capture gagal dimulai")
                }
            } else {
                Log.e(TAG, "Screen capture data is missing")
                showToast("Data Rekam Layar tidak ditemukan")
            }
        }

        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val themedContext = ContextThemeWrapper(this, R.style.ScreenTranslatorOverlayTheme)

        floatingView = LayoutInflater.from(themedContext)
            .inflate(R.layout.layout_floating_widget, null)

        fabMain = floatingView.findViewById(R.id.fabMain)
        layoutSubMenu = floatingView.findViewById(R.id.layoutSubMenu)
        btnRealtime = floatingView.findViewById(R.id.btnRealtime)
        btnManual = floatingView.findViewById(R.id.btnManual)
        btnExit = floatingView.findViewById(R.id.btnExit)

        setupWindowManagerParams()
        setupTouchAndDragListener()
        setupClickListeners()
        windowManager.addView(floatingView, params)
    }

    private fun setupWindowManagerParams() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }
    }

    private fun setupTouchAndDragListener() {
        fabMain.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = true

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params!!.x
                        initialY = params!!.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) isClick = false
                        params!!.x = initialX + dx
                        params!!.y = initialY + dy
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) onFloatingButtonClicked()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun onFloatingButtonClicked() {
        if (isRealtimeActive) {
            stopRealtimeTranslation()
        } else {
            isSubMenuVisible = !isSubMenuVisible
            layoutSubMenu.visibility = if (isSubMenuVisible) View.VISIBLE else View.GONE
        }
    }

    private fun setupClickListeners() {
        btnRealtime.setOnClickListener { startRealtimeTranslation() }
        btnManual.setOnClickListener {
            layoutSubMenu.visibility = View.GONE
            isSubMenuVisible = false
            triggerManualTranslation()
        }
        btnExit.setOnClickListener { stopSelf() }
    }

    private fun startRealtimeTranslation() {
        isRealtimeActive = true
        layoutSubMenu.visibility = View.GONE
        isSubMenuVisible = false
        fabMain.setImageResource(android.R.drawable.ic_media_pause)
        showToast("Real-Time Translator Aktif")
    }

    private fun stopRealtimeTranslation() {
        isRealtimeActive = false
        fabMain.setImageResource(android.R.drawable.ic_menu_compass)
        showToast("Real-Time Translator Diberhentikan")
    }

    private fun triggerManualTranslation() {
        if (manualTranslationPending) {
            showToast("Terjemahan manual masih diproses")
            return
        }

        Log.i(TAG, "Manual translation requested")
        showToast("Mengambil gambar layar...")

        val manager = screenCaptureManager
        if (manager == null) {
            Log.e(TAG, "Manual translation aborted: capture manager is null")
            showToast("Screen Capture belum siap")
            return
        }

        val ocr = ocrManager
        if (ocr == null) {
            Log.e(TAG, "Manual translation aborted: OCR manager is null")
            showToast("OCR belum siap")
            return
        }

        val translator = translationManager
        if (translator == null) {
            Log.e(TAG, "Manual translation aborted: translator is null")
            showToast("Translator belum siap")
            return
        }

        manualTranslationPending = true

        // The overlay is a separate full-screen WindowManager surface. Remove it
        // completely before capture so the previous translation can never become
        // part of the next screenshot/OCR input.
        removeTranslationOverlay()

        // Keep the floating UI visible during capture. This matches the last
        // known working pipeline and avoids changing the window tree immediately
        // before ImageReader delivers the frame.
        val requested = manager.captureOnce { bitmap ->
            cancelManualCaptureTimeout()
            Log.i(TAG, "Capture callback received: ${bitmap.width}x${bitmap.height}")
            showToast("Screenshot didapat. Memproses OCR...")

            // Capture is finished; use a separate watchdog for OCR/model/translation.
            startManualProcessTimeout(manager)

            try {
                ocr.recognize(
                    bitmap = bitmap,
                    onSuccess = { detectedTexts ->
                        Log.i(TAG, "OCR callback: ${detectedTexts.size} lines")
                        if (detectedTexts.isEmpty()) {
                            finishManualTranslation("OCR tidak menemukan teks")
                            return@recognize
                        }

                        try {
                            Log.i(TAG, "Preparing translation model")
                            translator.prepare(
                                onReady = {
                                    val textsToTranslate = detectedTexts
                                    Log.i(TAG, "Translation model ready; translating ${textsToTranslate.size} lines")
                                    translateTexts(
                                        translator = translator,
                                        texts = textsToTranslate,
                                        index = 0,
                                        results = mutableListOf(),
                                        overlayResults = mutableListOf(),
                                        sourceWidth = bitmap.width,
                                        sourceHeight = bitmap.height
                                    )
                                },
                                onFailure = { exception ->
                                    Log.e(TAG, "Translation model preparation failed", exception)
                                    finishManualTranslation("Model terjemahan gagal: ${exception.message ?: "Unknown error"}")
                                }
                            )
                        } catch (exception: Exception) {
                            Log.e(TAG, "Translation preparation threw an exception", exception)
                            finishManualTranslation("Gagal menyiapkan translator: ${exception.message ?: "Unknown error"}")
                        }
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "OCR callback failed", exception)
                        finishManualTranslation("OCR gagal: ${exception.message ?: "Unknown error"}")
                    }
                )
            } catch (exception: Exception) {
                Log.e(TAG, "OCR invocation threw an exception", exception)
                finishManualTranslation("Proses OCR gagal: ${exception.message ?: "Unknown error"}")
            }
        }

        Log.i(TAG, "captureOnce returned=$requested")
        if (!requested) {
            manualTranslationPending = false
            showToast("Gagal mengambil screenshot")
            return
        }

        manualCaptureTimeout = Runnable {
            if (manualTranslationPending) {
                Log.e(TAG, "Manual capture timed out after ${MANUAL_CAPTURE_TIMEOUT_MS}ms")
                manager.cancelPendingCapture()
                manualTranslationPending = false
                showToast("Screenshot tidak masuk dalam 3 detik. Coba Manual TL lagi.")
            }
        }
        mainHandler.postDelayed(manualCaptureTimeout!!, MANUAL_CAPTURE_TIMEOUT_MS)
    }

    private fun startManualProcessTimeout(manager: ScreenCaptureManager) {
        cancelManualProcessTimeout()
        manualProcessTimeout = Runnable {
            if (manualTranslationPending) {
                Log.e(TAG, "Manual OCR/translation processing timed out after ${MANUAL_PROCESS_TIMEOUT_MS}ms")
                manager.cancelPendingCapture()
                manualTranslationPending = false
                runOnMainThread {
                    if (::floatingView.isInitialized) {
                        floatingView.visibility = View.VISIBLE
                    }
                    showToast("Proses terjemahan terlalu lama. Coba Manual TL lagi.")
                }
            }
        }
        mainHandler.postDelayed(manualProcessTimeout!!, MANUAL_PROCESS_TIMEOUT_MS)
    }

    private fun translateTexts(
        translator: TranslationManager,
        texts: List<DetectedText>,
        index: Int,
        results: MutableList<String>,
        overlayResults: MutableList<TranslationOverlayItem>,
        sourceWidth: Int,
        sourceHeight: Int
    ) {
        if (index >= texts.size) {
            val resultText = results.joinToString("\n\n")
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val historyEntry = buildString {
                append("[").append(time).append("]\n")
                append(sourceLanguage).append(" → ").append(targetLanguage).append("\n\n")
                append(resultText)
            }

            Log.i(TAG, "Translation completed; saving history entry with ${results.size} results")
            try {
                TranslationHistory.add(historyEntry)
                showTranslationOverlay(overlayResults, sourceWidth, sourceHeight)
                finishManualTranslation("Terjemahan selesai: ${results.size} baris. Overlay ditampilkan.")
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to save/display translation result", exception)
                finishManualTranslation("Terjemahan selesai tetapi hasil gagal ditampilkan: ${exception.message ?: "Unknown error"}")
            }
            return
        }

        val currentText = texts[index]
        Log.i(TAG, "Translating [${index + 1}/${texts.size}]: '${currentText.text}'")

        try {
            translator.translate(
                currentText.text,
                onSuccess = { translatedText ->
                    Log.i(TAG, "Translation success: '${currentText.text}' -> '$translatedText'")
                    results.add("${currentText.text}\n→ $translatedText")
                    overlayResults.add(
                        TranslationOverlayItem(
                            translatedText = translatedText,
                            left = currentText.left,
                            top = currentText.top,
                            right = currentText.right,
                            bottom = currentText.bottom
                        )
                    )
                    translateTexts(
                        translator,
                        texts,
                        index + 1,
                        results,
                        overlayResults,
                        sourceWidth,
                        sourceHeight
                    )
                },
                onFailure = { exception ->
                    Log.e(TAG, "Translation failed for '${currentText.text}'", exception)
                    results.add("${currentText.text}\n→ [Gagal diterjemahkan: ${exception.message ?: "Unknown error"}]")
                    translateTexts(
                        translator,
                        texts,
                        index + 1,
                        results,
                        overlayResults,
                        sourceWidth,
                        sourceHeight
                    )
                }
            )
        } catch (exception: Exception) {
            Log.e(TAG, "Translation invocation threw an exception", exception)
            results.add("${currentText.text}\n→ [Gagal diterjemahkan: ${exception.message ?: "Unknown error"}]")
            translateTexts(
                translator,
                texts,
                index + 1,
                results,
                overlayResults,
                sourceWidth,
                sourceHeight
            )
        }
    }

    private fun finishManualTranslation(message: String) {
        cancelManualCaptureTimeout()
        cancelManualProcessTimeout()
        manualTranslationPending = false
        runOnMainThread {
            if (::floatingView.isInitialized) {
                floatingView.visibility = View.VISIBLE
            }
            showToast(message)
        }
    }

    private fun cancelManualCaptureTimeout() {
        manualCaptureTimeout?.let(mainHandler::removeCallbacks)
        manualCaptureTimeout = null
    }

    private fun cancelManualProcessTimeout() {
        manualProcessTimeout?.let(mainHandler::removeCallbacks)
        manualProcessTimeout = null
    }

    private fun showTranslationOverlay(
        items: List<TranslationOverlayItem>,
        sourceWidth: Int,
        sourceHeight: Int
    ) {
        runOnMainThread {
            if (items.isEmpty()) return@runOnMainThread

            val overlay = overlayView ?: TranslationOverlayView(this).also {
                overlayView = it
                val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val overlayParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                }

                windowManager.addView(it, overlayParams)
            }

            overlay.setTranslations(items, sourceWidth, sourceHeight)
            Log.i(TAG, "Translation overlay updated: ${items.size} items")
        }
    }

    private fun removeTranslationOverlay() {
        runOnMainThread {
            overlayView?.let {
                try {
                    windowManager.removeView(it)
                } catch (_: IllegalArgumentException) {
                    // Already removed.
                }
            }
            overlayView = null
        }
    }

    private fun showToast(message: String) {
        runOnMainThread {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun runOnMainThread(action: () -> Unit) {
        android.os.Handler(mainLooper).post(action)
    }

    private fun startForegroundServiceNotification() {
        val channelId = "screen_translator_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Screen Translator Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Screen Translator Running")
            .setContentText("Tombol melayang siap digunakan.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        cancelManualCaptureTimeout()
        cancelManualProcessTimeout()
        screenCaptureManager?.cancelPendingCapture()
        removeTranslationOverlay()
        translationManager?.close()
        translationManager = null
        ocrManager?.close()
        ocrManager = null
        screenCaptureManager?.release()
        screenCaptureManager = null
        if (::floatingView.isInitialized) {
            try {
                windowManager.removeView(floatingView)
            } catch (_: IllegalArgumentException) {
                // Already removed.
            }
        }
        super.onDestroy()
    }
}
