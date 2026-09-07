package com.example.screentranslator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.graphics.PixelFormat
import android.os.Build
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object KlipSelectionController {
    private const val TAG = "ScreenTL-Klip"
    private const val TOLERANCE_RATIO = 1.80f
    private const val MASK_COLOR = 0x88000000.toInt()
    private const val SELECTION_STROKE = 4f
    private const val MIN_SELECTION_PX = 8
    private const val CAPTURE_SETTLE_MS = 120L

    @Volatile
    var isActive: Boolean = false
        private set

    @Volatile
    var hasClipOverlay: Boolean = false
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private var maskView: KlipMaskView? = null
    private var windowManager: WindowManager? = null
    private var hostRoot: View? = null
    private var hostParams: WindowManager.LayoutParams? = null
    private var service: Any? = null
    private var clipOverlayView: TranslationOverlayView? = null
    private var selectionCompleted = false

    fun start(context: Context, sourceView: View) {
        if (isActive) return
        val owner = context
        val root = sourceView.rootView ?: return
        val wm = owner.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val capture = readField<ScreenCaptureManager>(owner, "screenCaptureManager")
        val ocr = readField<OcrManager>(owner, "ocrManager")
        val translator = readField<TranslationManager>(owner, "translationManager")
        if (capture == null || ocr == null || translator == null) {
            toast(owner, "Klip belum siap. Pastikan Translator aktif.")
            return
        }

        runCatching { invokePrivate(owner, "removeTranslationOverlay") }
        selectionCompleted = false
        hasClipOverlay = false
        isActive = true
        service = owner
        windowManager = wm
        hostRoot = root
        hostParams = (root.layoutParams as? WindowManager.LayoutParams)?.let { WindowManager.LayoutParams().apply { copyFrom(it) } }

        val display = owner.resources.displayMetrics
        val selectionView = KlipMaskView(owner) { rect ->
            if (!selectionCompleted) {
                selectionCompleted = true
                finishSelection(rect, capture, ocr, translator)
            }
        }
        maskView = selectionView

        try {
            if (root.isAttachedToWindow) wm.removeView(root)
            val maskParams = WindowManager.LayoutParams(
                display.widthPixels.coerceAtLeast(1),
                display.heightPixels.coerceAtLeast(1),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 0 }
            wm.addView(selectionView, maskParams)
            hostParams?.let { wm.addView(root, it) }
            setClipCancelVisible(root, true)
            toast(owner, "Klip aktif — sentuh dan geser area yang ingin diterjemahkan")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to activate Klip mask", e)
            cleanupMaskOnly()
            isActive = false
            hasClipOverlay = false
            service = null
            toast(owner, "Gagal mengaktifkan Klip: ${e.message ?: "Unknown error"}")
        }
    }

    fun cancel() {
        if (!isActive && !hasClipOverlay) return
        val owner = service as? Context
        readField<ScreenCaptureManager>(owner ?: return, "screenCaptureManager")?.cancelPendingCapture()
        clipOverlayView?.let { runCatching { windowManager?.removeView(it) } }
        clipOverlayView = null
        cleanupMaskOnly()
        isActive = false
        hasClipOverlay = false
        service = null
        hostRoot?.let { root -> setClipCancelVisible(root, false) }
        toast(owner, "Klip dibatalkan")
    }

    private fun finishSelection(rect: Rect, capture: ScreenCaptureManager, ocr: OcrManager, translator: TranslationManager) {
        val owner = service as? Context ?: return
        val selection = Rect(rect)
        cleanupMaskOnly()
        if (selection.width() < MIN_SELECTION_PX || selection.height() < MIN_SELECTION_PX) {
            fail("Area Klip terlalu kecil")
            return
        }
        toast(owner, "Area dipilih. Mengambil layar...")
        mainHandler.postDelayed({
            if (!isActive) return@postDelayed
            val requested = capture.captureOnce { bitmap ->
                try {
                    val crop = cropBitmap(bitmap, selection)
                    bitmap.recycle()
                    if (crop == null) {
                        fail("Area Klip berada di luar layar")
                        return@captureOnce
                    }
                    toast(owner, "Memproses OCR area Klip...")
                    ocr.recognize(crop, onSuccess = { detected ->
                        if (detected.isEmpty()) {
                            crop.recycle()
                            fail("OCR tidak menemukan teks di area Klip")
                            return@recognize
                        }
                        val sourceLeft = selection.left.coerceAtLeast(0)
                        val sourceTop = selection.top.coerceAtLeast(0)
                        val scaleToDisplayX = selection.width().toFloat() / crop.width.toFloat().coerceAtLeast(1f)
                        val scaleToDisplayY = selection.height().toFloat() / crop.height.toFloat().coerceAtLeast(1f)
                        val adjusted = detected.map { item ->
                            item.copy(
                                left = sourceLeft + (item.left * scaleToDisplayX).toInt(),
                                top = sourceTop + (item.top * scaleToDisplayY).toInt(),
                                right = sourceLeft + (item.right * scaleToDisplayX).toInt(),
                                bottom = sourceTop + (item.bottom * scaleToDisplayY).toInt(),
                                sourceTextSizePx = item.sourceTextSizePx * scaleToDisplayY
                            )
                        }
                        translateAll(owner, translator, adjusted, bitmapSizeFromSelection(selection), 0, mutableListOf(), mutableListOf(), crop)
                    }, onFailure = { exception ->
                        crop.recycle()
                        fail("OCR Klip gagal: ${exception.message ?: "Unknown error"}")
                    })
                } catch (e: Exception) {
                    runCatching { bitmap.recycle() }
                    fail("Gagal memproses area Klip: ${e.message ?: "Unknown error"}")
                }
            }
            if (!requested) fail("Gagal mengambil screenshot")
        }, CAPTURE_SETTLE_MS)
    }

    private fun translateAll(owner: Context, translator: TranslationManager, texts: List<DetectedText>, screenSize: Pair<Int, Int>, index: Int, historyParts: MutableList<String>, overlays: MutableList<TranslationOverlayItem>, crop: Bitmap) {
        if (!isActive) {
            texts.mapNotNull { it.blurredPatch }.forEach { patch -> runCatching { patch.recycle() } }
            runCatching { crop.recycle() }
            return
        }
        if (index >= texts.size) {
            runCatching { crop.recycle() }
            if (overlays.isEmpty()) { fail("Tidak ada hasil terjemahan"); return }
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val source = readField<String>(owner, "sourceLanguage") ?: "Jepang"
            val target = readField<String>(owner, "targetLanguage") ?: "Indonesia"
            TranslationHistory.add(buildString {
                append("[").append(time).append("]\n")
                append("TL: ").append(translator.getProviderName()).append(" (Klip)\n")
                append(source).append(" → ").append(target).append("\n\n")
                append(historyParts.joinToString("\n\n"))
            })
            showOverlay(owner, overlays, screenSize.first, screenSize.second)
            isActive = false
            hasClipOverlay = true
            hostRoot?.let { root -> setClipCancelVisible(root, true) }
            service = null
            toast(owner, "Klip selesai: ${overlays.size} blok diterjemahkan")
            return
        }
        val current = texts[index]
        try {
            translator.translate(current.text, onSuccess = { translated ->
                if (!isActive) { current.blurredPatch?.let { runCatching { it.recycle() } }; return@translate }
                historyParts.add("${current.text}\n→ $translated")
                overlays.add(current.toOverlayItem(translated))
                translateAll(owner, translator, texts, screenSize, index + 1, historyParts, overlays, crop)
            }, onFailure = { exception ->
                if (!isActive) { current.blurredPatch?.let { runCatching { it.recycle() } }; return@translate }
                historyParts.add("${current.text}\n→ [Gagal diterjemahkan: ${exception.message ?: "Unknown error"}]")
                translateAll(owner, translator, texts, screenSize, index + 1, historyParts, overlays, crop)
            })
        } catch (e: Exception) {
            if (!isActive) { current.blurredPatch?.let { runCatching { it.recycle() } }; return }
            historyParts.add("${current.text}\n→ [Gagal diterjemahkan: ${e.message ?: "Unknown error"}]")
            translateAll(owner, translator, texts, screenSize, index + 1, historyParts, overlays, crop)
        }
    }

    private fun showOverlay(context: Context, items: List<TranslationOverlayItem>, width: Int, height: Int) {
        val wm = windowManager ?: return
        val view = TranslationOverlayView(context)
        view.setTranslations(items, width, height, TOLERANCE_RATIO)
        val params = WindowManager.LayoutParams(width.coerceAtLeast(1), height.coerceAtLeast(1), if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 0 }
        clipOverlayView?.let { runCatching { wm.removeView(it) } }
        clipOverlayView = view
        runCatching { wm.addView(view, params) }.onFailure { clipOverlayView = null; Log.e(TAG, "Failed to show Klip overlay", it) }
    }

    private fun cropBitmap(bitmap: Bitmap, selection: Rect): Bitmap? {
        val screenW = bitmap.width; val screenH = bitmap.height
        val displayW = hostRoot?.resources?.displayMetrics?.widthPixels?.coerceAtLeast(1) ?: screenW
        val displayH = hostRoot?.resources?.displayMetrics?.heightPixels?.coerceAtLeast(1) ?: screenH
        val scaleX = screenW.toFloat() / displayW.toFloat(); val scaleY = screenH.toFloat() / displayH.toFloat()
        val left = (selection.left * scaleX).toInt().coerceIn(0, screenW - 1); val top = (selection.top * scaleY).toInt().coerceIn(0, screenH - 1)
        val right = (selection.right * scaleX).toInt().coerceIn(left + 1, screenW); val bottom = (selection.bottom * scaleY).toInt().coerceIn(top + 1, screenH)
        if (right - left < MIN_SELECTION_PX || bottom - top < MIN_SELECTION_PX) return null
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun bitmapSizeFromSelection(selection: Rect): Pair<Int, Int> {
        val root = hostRoot; val displayW = root?.resources?.displayMetrics?.widthPixels ?: selection.right; val displayH = root?.resources?.displayMetrics?.heightPixels ?: selection.bottom
        return Pair(displayW.coerceAtLeast(1), displayH.coerceAtLeast(1))
    }

    private fun cleanupMaskOnly() {
        maskView?.let { view -> runCatching { windowManager?.removeView(view) } }
        maskView = null
        hostRoot?.let { root -> setClipCancelVisible(root, false) }
        runCatching { service?.let { invokePrivate(it, "updateRemoveOverlayButton") } }
        selectionCompleted = false
    }

    private fun setClipCancelVisible(root: View, visible: Boolean) { root.findViewById<View>(R.id.btnRemoveOverlay)?.visibility = if (visible) View.VISIBLE else View.GONE }

    private fun fail(message: String) {
        val owner = service as? Context
        clipOverlayView?.let { runCatching { windowManager?.removeView(it) } }; clipOverlayView = null
        cleanupMaskOnly(); isActive = false; hasClipOverlay = false; service = null; toast(owner, message)
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> readField(target: Any, name: String): T? = runCatching { val field = target.javaClass.getDeclaredField(name); field.isAccessible = true; field.get(target) as? T }.getOrNull()
    private fun invokePrivate(target: Any, name: String) { val method = target.javaClass.getDeclaredMethod(name); method.isAccessible = true; method.invoke(target) }
    private fun toast(context: Context?, message: String) { context ?: return; Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }

    private class KlipMaskView(context: Context, private val onSelectionComplete: (Rect) -> Unit) : View(context) {
        private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MASK_COLOR; style = Paint.Style.FILL }
        private val selectionFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22000000; style = Paint.Style.FILL }
        private val selectionStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = SELECTION_STROKE }
        private var startX = 0f; private var startY = 0f; private var currentX = 0f; private var currentY = 0f; private var dragging = false
        override fun onDraw(canvas: Canvas) { canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint); if (!dragging) return; val rect = rectF(); canvas.drawRect(rect, selectionFillPaint); canvas.drawRect(rect, selectionStrokePaint) }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX = event.x; startY = event.y; currentX = startX; currentY = startY; dragging = true; invalidate(); return true }
                MotionEvent.ACTION_MOVE -> { currentX = event.x; currentY = event.y; invalidate(); return true }
                MotionEvent.ACTION_UP -> { currentX = event.x; currentY = event.y; val rect = RectF(rectF()); dragging = false; invalidate(); val out = Rect(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt()); if (out.width() >= MIN_SELECTION_PX && out.height() >= MIN_SELECTION_PX) onSelectionComplete(out); return true }
                MotionEvent.ACTION_CANCEL -> { dragging = false; invalidate(); return true }
            }
            return true
        }
        private fun rectF(): RectF = RectF(minOf(startX, currentX), minOf(startY, currentY), maxOf(startX, currentX), maxOf(startY, currentY))
    }
}

private fun DetectedText.toOverlayItem(translatedText: String): TranslationOverlayItem = TranslationOverlayItem(translatedText = translatedText, left = left, top = top, right = right, bottom = bottom, sourceTextSizePx = sourceTextSizePx, backgroundColor = backgroundColor, blurredPatch = blurredPatch).also { blurredPatch = null }
