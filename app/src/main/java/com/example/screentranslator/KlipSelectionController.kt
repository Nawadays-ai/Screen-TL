package com.example.screentranslator

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.graphics.PixelFormat
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object KlipSelectionController {
    private const val TAG = "ScreenTL-Klip"
    private const val MIN_SELECTION_PX = 8
    private const val CAPTURE_SETTLE_MS = 80L

    @Volatile var isActive: Boolean = false
        private set
    @Volatile var hasClipOverlay: Boolean = false
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private var maskView: KlipMaskView? = null
    private var windowManager: WindowManager? = null
    private var hostRoot: View? = null
    private var hostParams: WindowManager.LayoutParams? = null
    private var service: FloatingService? = null
    private var clipOverlayView: KlipResultOverlayView? = null
    private var selectionExists = false

    fun start(context: Context, sourceView: View) {
        if (isActive) return
        val owner = findFloatingService(context) ?: run {
            toast(context, "Klip belum siap. Translator belum aktif.")
            return
        }
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
        selectionExists = false
        hasClipOverlay = false
        isActive = true
        service = owner
        windowManager = wm
        hostRoot = root
        hostParams = (root.layoutParams as? WindowManager.LayoutParams)?.let {
            WindowManager.LayoutParams().apply { copyFrom(it) }
        }

        val display = owner.resources.displayMetrics
        val selectionView = KlipMaskView(
            owner,
            onConfirm = { rect -> finishSelection(rect, capture, ocr, translator) },
            onCancel = { cancel() }
        ) { exists -> selectionExists = exists }
        maskView = selectionView

        try {
            if (root.isAttachedToWindow) wm.removeView(root)
            val maskParams = WindowManager.LayoutParams(
                display.widthPixels.coerceAtLeast(1),
                display.heightPixels.coerceAtLeast(1),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }
            wm.addView(selectionView, maskParams)
            hostParams?.let { wm.addView(root, it) }
            setClipCancelVisible(root, true)
            toast(owner, "Klip aktif — geser untuk memilih area")
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
        val owner = service ?: return

        readField<ScreenCaptureManager>(owner, "screenCaptureManager")?.cancelPendingCapture()

        clipOverlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        clipOverlayView = null

        cleanupMaskOnly()
        isActive = false
        hasClipOverlay = false
        selectionExists = false
        hostRoot?.let { setClipCancelVisible(it, false) }
        service = null

        toast(owner, "Klip dibatalkan")
    }

    private fun finishSelection(
        rect: Rect,
        capture: ScreenCaptureManager,
        ocr: OcrManager,
        translator: TranslationManager
    ) {
        val owner = service ?: return
        val selection = Rect(rect)

        if (selection.width() < MIN_SELECTION_PX || selection.height() < MIN_SELECTION_PX) {
            return
        }

        // Keep the mask visible while capture/OCR/translation are running.
        // The selected interior is transparent, so the crop remains untouched.
        selectionExists = true
        setClipCancelVisible(owner.findViewByIdRoot(), true)
        toast(owner, "Area dikonfirmasi. Memproses…")

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

                    Log.i(TAG, "Klip capture ready; crop=${crop.width}x${crop.height}")
                    toast(owner, "Memproses OCR area Klip…")

                    ocr.recognize(
                        crop,
                        onSuccess = { detected ->
                            Log.i(TAG, "Klip OCR completed; blocks=${detected.size}")

                            if (detected.isEmpty()) {
                                crop.recycle()
                                fail("OCR tidak menemukan teks di area Klip")
                                return@recognize
                            }

                            val combinedSource = detected
                                .map { it.text.trim() }
                                .filter { it.isNotBlank() }
                                .joinToString("\n\n")

                            if (combinedSource.isBlank()) {
                                crop.recycle()
                                fail("Teks Klip kosong")
                                return@recognize
                            }

                            translateKlip(
                                owner = owner,
                                translator = translator,
                                sourceText = combinedSource,
                                selection = selection,
                                crop = crop
                            )
                        },
                        onFailure = { exception ->
                            runCatching { crop.recycle() }
                            fail("OCR Klip gagal: ${exception.message ?: "Unknown error"}")
                        }
                    )
                } catch (e: Exception) {
                    runCatching { bitmap.recycle() }
                    fail("Gagal memproses area Klip: ${e.message ?: "Unknown error"}")
                }
            }

            if (!requested) {
                fail("Gagal mengambil screenshot")
            }
        }, CAPTURE_SETTLE_MS)
    }

    private fun translateKlip(
        owner: Context,
        translator: TranslationManager,
        sourceText: String,
        selection: Rect,
        crop: Bitmap
    ) {
        if (!isActive) {
            crop.recycle()
            return
        }

        try {
            translator.translate(
                sourceText,
                onSuccess = { translated ->
                    if (!isActive) {
                        crop.recycle()
                        return@translate
                    }

                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    val source = readField<String>(owner, "sourceLanguage") ?: "Jepang"
                    val target = readField<String>(owner, "targetLanguage") ?: "Indonesia"

                    TranslationHistory.add(
                        buildString {
                            append("[").append(time).append("]\n")
                            append("TL: ").append(translator.getProviderName()).append(" (Klip)\n")
                            append(source).append(" → ").append(target).append("\n\n")
                            append(sourceText)
                            append("\n→ ")
                            append(translated)
                        }
                    )

                    showResult(
                        owner = owner,
                        translatedText = translated,
                        selection = selection,
                        crop = crop
                    )
                    isActive = false
                    hasClipOverlay = true
                    hostRoot?.let { setClipCancelVisible(it, true) }
                    toast(owner, "Klip selesai")
                },
                onFailure = { exception ->
                    if (!isActive) {
                        crop.recycle()
                        return@translate
                    }

                    val message = "Gagal diterjemahkan: ${exception.message ?: "Unknown error"}"
                    showResult(
                        owner = owner,
                        translatedText = message,
                        selection = selection,
                        crop = crop
                    )
                    isActive = false
                    hasClipOverlay = true
                    hostRoot?.let { setClipCancelVisible(it, true) }
                    toast(owner, "Klip selesai dengan error terjemahan")
                }
            )
        } catch (e: Exception) {
            runCatching { crop.recycle() }
            fail("Terjemahan Klip gagal: ${e.message ?: "Unknown error"}")
        }
    }

    private fun showResult(
        owner: Context,
        translatedText: String,
        selection: Rect,
        crop: Bitmap
    ) {
        val wm = windowManager ?: run {
            crop.recycle()
            ScreenTLPerformanceTrace.current()?.finish("klip result window unavailable")
            return
        }

        clipOverlayView?.let { existing ->
            runCatching { wm.removeView(existing) }
        }

        val view = KlipResultOverlayView(
            context = owner,
            selection = selection,
            selectedBitmap = crop,
            translatedText = translatedText,
            onClose = { cancel() }
        )
        clipOverlayView = view

        val display = owner.resources.displayMetrics
        val params = WindowManager.LayoutParams(
            display.widthPixels.coerceAtLeast(1),
            display.heightPixels.coerceAtLeast(1),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        runCatching {
            ScreenTLPerformanceTrace.current()?.mark("display_start")
            wm.addView(view, params)
            // Remove the mask only after the result window has been attached.
            cleanupMaskOnly()
            ScreenTLPerformanceTrace.current()?.mark("displayed")
            ScreenTLPerformanceTrace.current()?.finish("klip displayed")
        }.onFailure {
            clipOverlayView = null
            crop.recycle()
            Log.e(TAG, "Failed to show Klip result overlay", it)
            ScreenTLPerformanceTrace.current()?.finish("klip display failed")
            cleanupMaskOnly()
        }
    }

    private fun cropBitmap(bitmap: Bitmap, selection: Rect): Bitmap? {
        val screenW = bitmap.width
        val screenH = bitmap.height

        val displayW =
            hostRoot?.resources?.displayMetrics?.widthPixels?.coerceAtLeast(1) ?: screenW
        val displayH =
            hostRoot?.resources?.displayMetrics?.heightPixels?.coerceAtLeast(1) ?: screenH

        val scaleX = screenW.toFloat() / displayW.toFloat()
        val scaleY = screenH.toFloat() / displayH.toFloat()

        val left = (selection.left * scaleX).toInt().coerceIn(0, screenW - 1)
        val top = (selection.top * scaleY).toInt().coerceIn(0, screenH - 1)
        val right = (selection.right * scaleX).toInt().coerceIn(left + 1, screenW)
        val bottom = (selection.bottom * scaleY).toInt().coerceIn(top + 1, screenH)

        if (right - left < MIN_SELECTION_PX || bottom - top < MIN_SELECTION_PX) {
            return null
        }

        return Bitmap.createBitmap(
            bitmap,
            left,
            top,
            right - left,
            bottom - top
        )
    }

    private fun cleanupMaskOnly() {
        maskView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        maskView = null
        hostRoot?.let { root -> setClipCancelVisible(root, false) }
        service?.let { runCatching { invokePrivate(it, "updateRemoveOverlayButton") } }
    }

    private fun setClipCancelVisible(root: View, visible: Boolean) {
        root.findViewById<View>(R.id.btnRemoveOverlay)?.visibility =
            if (visible) View.VISIBLE else View.GONE
    }

    private fun fail(message: String) {
        val owner = service
        ScreenTLPerformanceTrace.current()?.finish("klip failed")

        clipOverlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        clipOverlayView = null

        cleanupMaskOnly()
        isActive = false
        hasClipOverlay = false
        selectionExists = false
        service = null

        toast(owner, message)
    }

    private fun findFloatingService(context: Context): FloatingService? {
        var current: Context? = context
        for (i in 0 until 8) {
            val candidate = current
            when (candidate) {
                is FloatingService -> return candidate
                is ContextWrapper -> current = candidate.baseContext
                else -> return null
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> readField(target: Any, name: String): T? =
        runCatching {
            val field = target.javaClass.getDeclaredField(name)
            field.isAccessible = true
            field.get(target) as? T
        }.getOrNull()

    private fun invokePrivate(target: Any, name: String) {
        val method = target.javaClass.getDeclaredMethod(name)
        method.isAccessible = true
        method.invoke(target)
    }

    private fun Context.findViewByIdRoot(): View =
        hostRoot ?: View(this)

    private fun toast(context: Context?, message: String) {
        context ?: return
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private class KlipMaskView(
        context: Context,
        private val onConfirm: (Rect) -> Unit,
        private val onCancel: () -> Unit,
        private val onSelectionChanged: (Boolean) -> Unit
    ) : View(context) {

        private val density = resources.displayMetrics.density
        private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x88000000.toInt()
            style = Paint.Style.FILL
        }
        private val selectionFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.TRANSPARENT
            style = Paint.Style.FILL
        }
        private val selectionStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f * density
        }
        private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF263238.toInt()
            style = Paint.Style.FILL
        }
        private val buttonStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }
        private val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        private val handleRadius = 10f * density
        private val buttonSize = 48f * density
        private val buttonGap = 8f * density
        private val outsideGap = 10f * density
        private val minSelection = 40f * density

        private var selection: RectF? = null
        private var downX = 0f
        private var downY = 0f
        private var startRect = RectF()
        private var mode = Mode.NONE
        private var activeHandle = Handle.NONE

        private enum class Mode { NONE, CREATE, MOVE, RESIZE }
        private enum class Handle { NONE, TL, TR, BL, BR }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)

            val rect = selection ?: return
            canvas.drawRect(rect, selectionFillPaint)
            canvas.drawRect(rect, selectionStrokePaint)

            drawHandle(canvas, rect.left, rect.top)
            drawHandle(canvas, rect.right, rect.top)
            drawHandle(canvas, rect.left, rect.bottom)
            drawHandle(canvas, rect.right, rect.bottom)

            val controls = controlRects(rect)
            drawButton(canvas, controls.first, "✓")
            drawButton(canvas, controls.second, "×")
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y

                    val rect = selection
                    if (rect != null) {
                        val controls = controlRects(rect)

                        if (controls.first.contains(event.x, event.y)) {
                            onConfirm(Rect(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt()))
                            return true
                        }

                        if (controls.second.contains(event.x, event.y)) {
                            onCancel()
                            return true
                        }

                        activeHandle = hitHandle(rect, event.x, event.y)
                        if (activeHandle != Handle.NONE) {
                            mode = Mode.RESIZE
                            startRect.set(rect)
                            return true
                        }

                        if (rect.contains(event.x, event.y)) {
                            mode = Mode.MOVE
                            startRect.set(rect)
                            return true
                        }
                    }

                    mode = Mode.CREATE
                    selection = RectF(event.x, event.y, event.x, event.y)
                    startRect.set(selection!!)
                    onSelectionChanged(true)
                    invalidate()
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    when (mode) {
                        Mode.CREATE -> updateCreate(event.x, event.y)
                        Mode.MOVE -> updateMove(event.x - downX, event.y - downY)
                        Mode.RESIZE -> updateResize(event.x, event.y)
                        else -> Unit
                    }
                    invalidate()
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (mode == Mode.CREATE || mode == Mode.RESIZE || mode == Mode.MOVE) {
                        selection?.let {
                            normalizeAndClamp(it)
                            if (it.width() < minSelection || it.height() < minSelection) {
                                selection = null
                                onSelectionChanged(false)
                            }
                        }
                    }

                    mode = Mode.NONE
                    activeHandle = Handle.NONE
                    invalidate()
                    return true
                }
            }

            return true
        }

        private fun updateCreate(x: Float, y: Float) {
            val r = selection ?: return
            r.left = minOf(downX, x)
            r.top = minOf(downY, y)
            r.right = maxOf(downX, x)
            r.bottom = maxOf(downY, y)
            normalizeAndClamp(r)
        }

        private fun updateMove(dx: Float, dy: Float) {
            val r = selection ?: return
            val w = startRect.width()
            val h = startRect.height()
            val left = (startRect.left + dx).coerceIn(0f, width - w)
            val top = (startRect.top + dy).coerceIn(0f, height - h)
            r.set(left, top, left + w, top + h)
            normalizeAndClamp(r)
        }

        private fun updateResize(x: Float, y: Float) {
            val r = selection ?: return
            when (activeHandle) {
                Handle.TL -> {
                    r.left = x.coerceIn(0f, startRect.right - minSelection)
                    r.top = y.coerceIn(0f, startRect.bottom - minSelection)
                }
                Handle.TR -> {
                    r.right = x.coerceIn(startRect.left + minSelection, width.toFloat())
                    r.top = y.coerceIn(0f, startRect.bottom - minSelection)
                }
                Handle.BL -> {
                    r.left = x.coerceIn(0f, startRect.right - minSelection)
                    r.bottom = y.coerceIn(startRect.top + minSelection, height.toFloat())
                }
                Handle.BR -> {
                    r.right = x.coerceIn(startRect.left + minSelection, width.toFloat())
                    r.bottom = y.coerceIn(startRect.top + minSelection, height.toFloat())
                }
                else -> Unit
            }
            normalizeAndClamp(r)
        }

        private fun normalizeAndClamp(r: RectF) {
            r.left = r.left.coerceIn(0f, width.toFloat())
            r.right = r.right.coerceIn(0f, width.toFloat())
            r.top = r.top.coerceIn(0f, height.toFloat())
            r.bottom = r.bottom.coerceIn(0f, height.toFloat())
            if (r.right < r.left) {
                val temp = r.left; r.left = r.right; r.right = temp
            }
            if (r.bottom < r.top) {
                val temp = r.top; r.top = r.bottom; r.bottom = temp
            }
            ensureControlBand(r)
        }

        private fun ensureControlBand(r: RectF) {
            val band = buttonSize + outsideGap
            val verticalAvailable = r.top >= band || height - r.bottom >= band
            val horizontalAvailable = r.left >= buttonSize * 2f + buttonGap + outsideGap || width - r.right >= buttonSize * 2f + buttonGap + outsideGap
            if (verticalAvailable || horizontalAvailable) return
            val newBottom = height.toFloat() - band
            if (newBottom - r.top >= minSelection) r.bottom = newBottom else {
                r.top = 0f
                r.bottom = newBottom.coerceAtLeast(minSelection)
            }
        }

        private fun hitHandle(r: RectF, x: Float, y: Float): Handle {
            val hit = handleRadius * 1.8f
            return when {
                distance(x, y, r.left, r.top) <= hit -> Handle.TL
                distance(x, y, r.right, r.top) <= hit -> Handle.TR
                distance(x, y, r.left, r.bottom) <= hit -> Handle.BL
                distance(x, y, r.right, r.bottom) <= hit -> Handle.BR
                else -> Handle.NONE
            }
        }

        private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float = kotlin.math.hypot(x1 - x2, y1 - y2)

        private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
            canvas.drawCircle(x, y, handleRadius, handlePaint)
            canvas.drawCircle(x, y, handleRadius, selectionStrokePaint)
        }

        private fun controlRects(r: RectF): Pair<RectF, RectF> {
            val totalW = buttonSize * 2f + buttonGap
            val candidates = listOf(
                RectF(r.left, r.top - outsideGap - buttonSize, r.left + totalW, r.top - outsideGap),
                RectF(r.left, r.bottom + outsideGap, r.left + totalW, r.bottom + outsideGap + buttonSize),
                RectF(r.right + outsideGap, r.top, r.right + outsideGap + totalW, r.top + buttonSize),
                RectF(r.left - outsideGap - totalW, r.top, r.left - outsideGap, r.top + buttonSize)
            )
            val band = candidates.firstOrNull { candidate -> inside(candidate) && !RectF.intersects(candidate, r) }
                ?: RectF((width - totalW).coerceAtLeast(0f), (height - buttonSize).coerceAtLeast(0f), width.toFloat(), height.toFloat())
            return Pair(
                RectF(band.left, band.top, band.left + buttonSize, band.bottom),
                RectF(band.left + buttonSize + buttonGap, band.top, band.right, band.bottom)
            )
        }

        private fun inside(rect: RectF): Boolean = rect.left >= 0f && rect.top >= 0f && rect.right <= width.toFloat() && rect.bottom <= height.toFloat()

        private fun drawButton(canvas: Canvas, rect: RectF, label: String) {
            canvas.drawRoundRect(rect, 10f * density, 10f * density, buttonPaint)
            canvas.drawRoundRect(rect, 10f * density, 10f * density, buttonStrokePaint)
            val y = rect.centerY() - (buttonTextPaint.ascent() + buttonTextPaint.descent()) / 2f
            canvas.drawText(label, rect.centerX(), y, buttonTextPaint)
        }
    }
}
