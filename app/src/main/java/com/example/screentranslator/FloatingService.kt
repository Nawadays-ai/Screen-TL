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
import android.widget.FrameLayout
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
        private const val REALTIME_INTERVAL_MS = 1_200L
        private const val REALTIME_CAPTURE_DELAY_MS = 150L
        private const val MENU_GAP_DP = 8
        private const val FAB_SIZE_DP = 48
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var fabMain: FloatingActionButton
    private lateinit var layoutSubMenu: LinearLayout
    private lateinit var btnRealtime: Button
    private lateinit var btnManual: Button
    private lateinit var btnRemoveOverlay: Button
    private lateinit var btnExit: Button

    private var params: WindowManager.LayoutParams? = null
    private var overlayView: TranslationOverlayView? = null
    private var isRealtimeActive = false
    private var isRealtimeBusy = false
    private var isRealtimePreparing = false
    private var realtimeGeneration = 0
    private var isSubMenuVisible = false
    private var manualTranslationPending = false
    private var manualOverlayVisible = false
    private var fabScreenX = 100
    private var fabScreenY = 300
    private val mainHandler = Handler()
    private var manualCaptureTimeout: Runnable? = null
    private var manualProcessTimeout: Runnable? = null
    private var realtimeLoop: Runnable? = null

    private var screenCaptureManager: ScreenCaptureManager? = null
    private var ocrManager: OcrManager? = null
    private var sourceLanguage: String = "Jepang"
    private var targetLanguage: String = "Indonesia"
    private var translationManager: TranslationManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        TranslationHistory.initialize(applicationContext)
        sourceLanguage = intent?.getStringExtra("EXTRA_SOURCE_LANG") ?: "Jepang"
        targetLanguage = intent?.getStringExtra("EXTRA_TARGET_LANG") ?: "Indonesia"

        Log.i(TAG, "Service started: $sourceLanguage -> $targetLanguage")

        if (ocrManager == null) ocrManager = OcrManager(sourceLanguage)
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
                if (!started) showToast("Screen Capture gagal dimulai")
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
        btnRemoveOverlay = floatingView.findViewById(R.id.btnRemoveOverlay)
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
            dp(FAB_SIZE_DP),
            dp(FAB_SIZE_DP),
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = fabScreenX
            y = fabScreenY
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
                        if (isSubMenuVisible) hideSubMenu()
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
                        params!!.x = (initialX + dx).coerceAtLeast(0)
                        params!!.y = (initialY + dy).coerceAtLeast(0)
                        fabScreenX = params!!.x
                        fabScreenY = params!!.y
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
            if (isSubMenuVisible) hideSubMenu() else showSubMenu()
        }
    }

    private fun setupClickListeners() {
        btnRealtime.setOnClickListener { startRealtimeTranslation() }
        btnManual.setOnClickListener {
            hideSubMenu()
            triggerManualTranslation()
        }
        btnRemoveOverlay.setOnClickListener {
            removeTranslationOverlay()
            showToast("Overlay dihapus")
        }
        btnExit.setOnClickListener { stopSelf() }
    }

    private fun showSubMenu() {
        if (isRealtimeActive) return
        isSubMenuVisible = true
        layoutSubMenu.visibility = View.VISIBLE
        positionSubMenu()
    }

    private fun hideSubMenu() {
        isSubMenuVisible = false
        layoutSubMenu.visibility = View.GONE
        params?.let {
            it.width = dp(FAB_SIZE_DP)
            it.height = dp(FAB_SIZE_DP)
            it.x = fabScreenX
            it.y = fabScreenY
            setFabLayoutGravity()
            windowManager.updateViewLayout(floatingView, it)
        }
    }

    private fun positionSubMenu() {
        layoutSubMenu.post {
            if (!isSubMenuVisible || !::floatingView.isInitialized) return@post

            val menuWidth = layoutSubMenu.measuredWidth.coerceAtLeast(dp(156))
            val menuHeight = layoutSubMenu.measuredHeight
            val gap = dp(MENU_GAP_DP)
            val fabSize = dp(FAB_SIZE_DP)
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels

            val onLeft = fabScreenX < screenWidth / 2
            val nearBottom = fabScreenY > screenHeight / 2
            val placeRight = onLeft
            val placeAbove = nearBottom

            val rootWidth = if (placeRight || !placeRight) {
                menuWidth + gap + fabSize
            } else {
                menuWidth + gap + fabSize
            }
            val rootHeight = if (placeAbove) {
                menuHeight + gap + fabSize
            } else {
                maxOf(menuHeight, fabSize)
            }

            val rootX = if (placeRight) {
                fabScreenX.coerceAtLeast(0)
            } else {
                (fabScreenX - menuWidth - gap).coerceAtLeast(0)
            }

            val rootY = if (placeAbove) {
                (fabScreenY - menuHeight - gap).coerceAtLeast(0)
            } else {
                fabScreenY.coerceAtLeast(0)
            }

            params?.let {
                it.width = rootWidth
                it.height = rootHeight
                it.x = rootX
                it.y = rootY
                windowManager.updateViewLayout(floatingView, it)
            }

            val menuParams = layoutSubMenu.layoutParams as FrameLayout.LayoutParams
            val fabParams = fabMain.layoutParams as FrameLayout.LayoutParams
            menuParams.leftMargin = 0
            menuParams.topMargin = 0
            fabParams.leftMargin = 0
            fabParams.topMargin = 0

            menuParams.gravity = when {
                placeAbove && placeRight -> Gravity.START or Gravity.BOTTOM
                placeAbove && !placeRight -> Gravity.END or Gravity.BOTTOM
                !placeAbove && placeRight -> Gravity.START or Gravity.CENTER_VERTICAL
                else -> Gravity.END or Gravity.CENTER_VERTICAL
            }

            fabParams.gravity = when {
                placeAbove && placeRight -> Gravity.END or Gravity.BOTTOM
                placeAbove && !placeRight -> Gravity.END or Gravity.BOTTOM
                !placeAbove && placeRight -> Gravity.START or Gravity.CENTER_VERTICAL
                else -> Gravity.END or Gravity.CENTER_VERTICAL
            }

            layoutSubMenu.layoutParams = menuParams
            fabMain.layoutParams = fabParams
        }
    }

    private fun setFabLayoutGravity() {
        val fabParams = fabMain.layoutParams as FrameLayout.LayoutParams
        fabParams.gravity = Gravity.CENTER
        fabParams.leftMargin = 0
        fabParams.topMargin = 0
        fabMain.layoutParams = fabParams
    }

    private fun startRealtimeTranslation() {
        if (manualTranslationPending) {
            showToast("Manual TL masih diproses")
            return
        }

        val manager = screenCaptureManager
        val ocr = ocrManager
        val translator = translationManager
        if (manager == null || ocr == null || translator == null) {
            Log.e(TAG, "Realtime start aborted: manager not ready")
            showToast("Real-Time belum siap")
            return
        }
        if (isRealtimeActive) return

        isRealtimeActive = true
        isRealtimeBusy = false
        isRealtimePreparing = true
        realtimeGeneration++
        val generation = realtimeGeneration
        hideSubMenu()
        fabMain.setImageResource(android.R.drawable.ic_media_pause)
        removeTranslationOverlay()
        showToast("Real-Time Translator Aktif")
        Log.i(TAG, "Realtime translation started: generation=$generation")

        try {
            translator.prepare(
                onReady = {
                    if (!isRealtimeActive || generation != realtimeGeneration) return@prepare
                    isRealtimePreparing = false
                    Log.i(TAG, "Realtime translation model ready")
                    scheduleRealtimeCapture(generation, 0L)
                },
                onFailure = { exception ->
                    Log.e(TAG, "Realtime translation model preparation failed", exception)
                    if (generation == realtimeGeneration) {
                        stopRealtimeTranslation("Model terjemahan gagal")
                    }
                }
            )
        } catch (exception: Exception) {
            Log.e(TAG, "Realtime translation preparation threw an exception", exception)
            stopRealtimeTranslation("Gagal menyiapkan Real-Time")
        }
    }

    private fun scheduleRealtimeCapture(generation: Int, delayMs: Long) {
        realtimeLoop?.let(mainHandler::removeCallbacks)
        realtimeLoop = Runnable {
            if (!isRealtimeActive || generation != realtimeGeneration) return@Runnable
            if (manualTranslationPending || isRealtimePreparing || isRealtimeBusy) {
                scheduleRealtimeCapture(generation, REALTIME_INTERVAL_MS)
                return@Runnable
            }

            removeTranslationOverlay()
            mainHandler.postDelayed({
                if (!isRealtimeActive || generation != realtimeGeneration) return@postDelayed
                requestRealtimeCapture(generation)
            }, REALTIME_CAPTURE_DELAY_MS)
        }
        mainHandler.postDelayed(realtimeLoop!!, delayMs)
    }

    private fun requestRealtimeCapture(generation: Int) {
        val manager = screenCaptureManager ?: run {
            Log.e(TAG, "Realtime capture aborted: capture manager is null")
            stopRealtimeTranslation("Screen Capture tidak siap")
            return
        }
        if (!isRealtimeActive || generation != realtimeGeneration) return

        isRealtimeBusy = true
        val requested = manager.captureOnce { bitmap ->
            if (!isRealtimeActive || generation != realtimeGeneration) {
                bitmap.recycle()
                isRealtimeBusy = false
                return@captureOnce
            }

            Log.i(TAG, "Realtime frame captured: ${bitmap.width}x${bitmap.height}")
            val ocr = ocrManager
            val translator = translationManager
            if (ocr == null || translator == null) {
                bitmap.recycle()
                isRealtimeBusy = false
                scheduleRealtimeCapture(generation, REALTIME_INTERVAL_MS)
                return@captureOnce
            }

            try {
                ocr.recognize(
                    bitmap = bitmap,
                    onSuccess = { detectedTexts ->
                        if (!isRealtimeActive || generation != realtimeGeneration) {
                            bitmap.recycle()
                            isRealtimeBusy = false
                            return@recognize
                        }
                        Log.i(TAG, "Realtime OCR completed: ${detectedTexts.size} lines")
                        if (detectedTexts.isEmpty()) {
                            bitmap.recycle()
                            isRealtimeBusy = false
                            scheduleRealtimeCapture(generation, REALTIME_INTERVAL_MS)
                            return@recognize
                        }
                        translateRealtimeTexts(
                            translator,
                            detectedTexts,
                            0,
                            mutableListOf(),
                            bitmap.width,
                            bitmap.height,
                            generation
                        ) {
                            bitmap.recycle()
                            isRealtimeBusy = false
                            if (isRealtimeActive && generation == realtimeGeneration) {
                                scheduleRealtimeCapture(generation, REALTIME_INTERVAL_MS)
                            }
                        }
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Realtime OCR failed", exception)
                        bitmap.recycle()
                        isRealtimeBusy = false
                        if (isRealtimeActive && generation == realtimeGeneration) {
                            scheduleRealtimeCapture(generation, REALTIME_INTERVAL_MS)
                        }
                    }
                )
            } catch (exception: Exception) {
                Log.e(TAG, "Realtime OCR invocation threw an exception", exception)
                bitmap.recycle()
                isRealtimeBusy = false
                if (isRealtimeActive && generation == realtimeGeneration) {
                    scheduleRealtimeCapture(generation, REALTIME_INTERVAL_MS)
                }
            }
        }

        Log.i(TAG, "Realtime captureOnce returned=$requested")
        if (!requested) {
            isRealtimeBusy = false
            if (isRealtimeActive && generation == realtimeGeneration) {
                scheduleRealtimeCapture(generation, REALTIME_INTERVAL_MS)
            }
        }
    }

    private fun translateRealtimeTexts(
        translator: TranslationManager,
        texts: List<DetectedText>,
        index: Int,
        overlayResults: MutableList<TranslationOverlayItem>,
        sourceWidth: Int,
        sourceHeight: Int,
        generation: Int,
        onComplete: () -> Unit
    ) {
        if (!isRealtimeActive || generation != realtimeGeneration) {
            onComplete()
            return
        }
        if (index >= texts.size) {
            if (overlayResults.isNotEmpty()) {
                showTranslationOverlay(overlayResults, sourceWidth, sourceHeight)
                Log.i(TAG, "Realtime overlay updated: ${overlayResults.size} items")
            }
            onComplete()
            return
        }

        val currentText = texts[index]
        try {
            translator.translate(
                currentText.text,
                onSuccess = { translatedText ->
                    if (isRealtimeActive && generation == realtimeGeneration) {
                        overlayResults.add(
                            TranslationOverlayItem(
                                translatedText,
                                currentText.left,
                                currentText.top,
                                currentText.right,
                                currentText.bottom
                            )
                        )
                    }
                    translateRealtimeTexts(
                        translator,
                        texts,
                        index + 1,
                        overlayResults,
                        sourceWidth,
                        sourceHeight,
                        generation,
                        onComplete
                    )
                },
                onFailure = { exception ->
                    Log.e(TAG, "Realtime translation failed for '${currentText.text}'", exception)
                    translateRealtimeTexts(
                        translator,
                        texts,
                        index + 1,
                        overlayResults,
                        sourceWidth,
                        sourceHeight,
                        generation,
                        onComplete
                    )
                }
            )
        } catch (exception: Exception) {
            Log.e(TAG, "Realtime translation invocation threw an exception", exception)
            translateRealtimeTexts(
                translator,
                texts,
                index + 1,
                overlayResults,
                sourceWidth,
                sourceHeight,
                generation,
                onComplete
            )
        }
    }

    private fun stopRealtimeTranslation(message: String = "Real-Time Translator Diberhentikan") {
        if (!isRealtimeActive && realtimeLoop == null) return
        isRealtimeActive = false
        isRealtimePreparing = false
        isRealtimeBusy = false
        realtimeGeneration++
        realtimeLoop?.let(mainHandler::removeCallbacks)
        realtimeLoop = null
        screenCaptureManager?.cancelPendingCapture()
        removeTranslationOverlay()
        fabMain.setImageResource(android.R.drawable.ic_menu_compass)
        showToast(message)
        Log.i(TAG, "Realtime translation stopped")
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
        removeTranslationOverlay()

        val requested = manager.captureOnce { bitmap ->
            cancelManualCaptureTimeout()
            Log.i(TAG, "Capture callback received: ${bitmap.width}x${bitmap.height}")
            showToast("Screenshot didapat. Memproses OCR...")
            startManualProcessTimeout(manager)

            try {
                ocr.recognize(
                    bitmap = bitmap,
                    onSuccess = { detectedTexts ->
                        Log.i(TAG, "OCR callback: ${detectedTexts.size} lines")
                        if (detectedTexts.isEmpty()) {
                            bitmap.recycle()
                            finishManualTranslation("OCR tidak menemukan teks")
                            return@recognize
                        }
                        try {
                            translator.prepare(
                                onReady = {
                                    Log.i(TAG, "Translation model ready; translating ${detectedTexts.size} lines")
                                    translateTexts(
                                        translator,
                                        detectedTexts,
                                        0,
                                        mutableListOf(),
                                        mutableListOf(),
                                        bitmap.width,
                                        bitmap.height
                                    )
                                    bitmap.recycle()
                                },
                                onFailure = { exception ->
                                    bitmap.recycle()
                                    Log.e(TAG, "Translation model preparation failed", exception)
                                    finishManualTranslation("Model terjemahan gagal: ${exception.message ?: "Unknown error"}")
                                }
                            )
                        } catch (exception: Exception) {
                            bitmap.recycle()
                            Log.e(TAG, "Translation preparation threw an exception", exception)
                            finishManualTranslation("Gagal menyiapkan translator: ${exception.message ?: "Unknown error"}")
                        }
                    },
                    onFailure = { exception ->
                        bitmap.recycle()
                        Log.e(TAG, "OCR callback failed", exception)
                        finishManualTranslation("OCR gagal: ${exception.message ?: "Unknown error"}")
                    }
                )
            } catch (exception: Exception) {
                bitmap.recycle()
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
                showToast("Proses terjemahan terlalu lama. Coba Manual TL lagi.")
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
                manualOverlayVisible = true
                updateRemoveOverlayButton()
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
                            translatedText,
                            currentText.left,
                            currentText.top,
                            currentText.right,
                            currentText.bottom
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
            if (::floatingView.isInitialized) floatingView.visibility = View.VISIBLE
            showToast(message)
        }
    }

    private fun updateRemoveOverlayButton() {
        runOnMainThread {
            if (!::btnRemoveOverlay.isInitialized) return@runOnMainThread
            btnRemoveOverlay.visibility = if (manualOverlayVisible) View.VISIBLE else View.GONE
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
            manualOverlayVisible = false
            updateRemoveOverlayButton()
        }
    }

    private fun showToast(message: String) {
        runOnMainThread {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun runOnMainThread(action: () -> Unit) {
        Handler(mainLooper).post(action)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun startForegroundServiceNotification() {
        val channelId = "screen_translator_channel"
        val channelName = "Screen Translator"
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Screen Translator")
            .setContentText("Translator aktif")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        stopRealtimeTranslation()
        cancelManualCaptureTimeout()
        cancelManualProcessTimeout()
        screenCaptureManager?.cancelPendingCapture()
        removeTranslationOverlay()
        translationManager?.close()
        screenCaptureManager?.release()

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
