package com.example.screentranslator

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log

class ScreenCaptureManager(
    private val context: Context,
    private val resultCode: Int,
    private val data: Intent
) {

    companion object {
        private const val TAG = "ScreenTL-Capture"

        /**
         * How long a capture waits for a genuinely new frame before falling back to the most
         * recent remembered one.
         *
         * MediaProjection only delivers a frame when screen content changes, so a capture
         * requested after the UI has already settled would otherwise wait forever and the
         * caller's timeout would fire with nothing to show.
         */
        private const val CAPTURE_FALLBACK_DELAY_MS = 400L
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureWidth = 0
    private var captureHeight = 0
    private var captureDensity = 0

    @Volatile
    private var captureRequested = false

    @Volatile
    private var onImageCaptured: ((Bitmap) -> Unit)? = null

    @Volatile
    private var captureTrace: ScreenTLPerformanceTrace? = null

    /**
     * Most recent decoded idle frame. Populated only while [rememberingFrame] is armed, because
     * decoding every idle frame would cost a full-screen bitmap copy on each screen update.
     */
    @Volatile
    private var latestFrame: Bitmap? = null

    @Volatile
    private var rememberingFrame = false

    @Volatile
    private var fallbackRunnable: Runnable? = null

    private val handlerThread = HandlerThread("ScreenCaptureThread").apply { start() }
    private val handler = Handler(handlerThread.looper)

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.w(TAG, "MediaProjection stopped by system/user")
            stop()
        }
    }

    fun start(): Boolean {
        return try {
            Log.i(TAG, "Starting MediaProjection. resultCode=$resultCode")
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            val projection = mediaProjection
            if (projection == null) {
                Log.e(TAG, "MediaProjection returned null")
                return false
            }
            projection.registerCallback(projectionCallback, handler)
            val metrics = currentRealMetrics()
            Log.i(TAG, "Capture display metrics: ${metrics.widthPixels}x${metrics.heightPixels}, density=${metrics.densityDpi}")
            configureDisplay(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
            virtualDisplay != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen capture", e)
            stop()
            false
        }
    }

    fun ensureCurrentDisplayConfiguration(): Boolean {
        if (mediaProjection == null || virtualDisplay == null) return false
        return try {
            val metrics = currentRealMetrics()
            val changed = metrics.widthPixels != captureWidth || metrics.heightPixels != captureHeight || metrics.densityDpi != captureDensity
            if (!changed) return true
            Log.i(TAG, "Display configuration changed: ${captureWidth}x$captureHeight -> ${metrics.widthPixels}x${metrics.heightPixels}; resizing capture surface")
            captureRequested = false
            onImageCaptured = null
            captureTrace = null
            cancelCaptureFallback()
            reconfigureDisplay(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reconfigure capture for current display", e)
            false
        }
    }

    // Keep the callback as the final parameter so existing trailing-lambda callers remain valid.
    // The trace overload is explicit to avoid Kotlin binding a trailing lambda to the trace parameter.
    fun captureOnce(callback: (Bitmap) -> Unit): Boolean {
        return captureOnceInternal(callback, null)
    }

    fun captureOnce(callback: (Bitmap) -> Unit, trace: ScreenTLPerformanceTrace?): Boolean {
        return captureOnceInternal(callback, trace)
    }

    private fun captureOnceInternal(callback: (Bitmap) -> Unit, trace: ScreenTLPerformanceTrace?): Boolean {
        val activeTrace = trace ?: ScreenTLPerformanceTrace.start("TranslateFlow")
        activeTrace.mark("capture_request")
        if (!ensureCurrentDisplayConfiguration()) {
            Log.e(TAG, "captureOnce rejected: current display configuration is unavailable")
            activeTrace.mark("capture_rejected_no_display")
            activeTrace.finish("capture rejected")
            return false
        }
        if (mediaProjection == null || imageReader == null) {
            Log.e(TAG, "captureOnce rejected: projection=${mediaProjection != null}, imageReader=${imageReader != null}")
            activeTrace.mark("capture_rejected_resources")
            activeTrace.finish("capture rejected")
            return false
        }
        if (captureRequested) {
            Log.w(TAG, "captureOnce rejected: another capture is already pending")
            activeTrace.mark("capture_rejected_busy")
            // Finish here as well: leaving this trace active would strand it and let a later
            // flow's marks land on a trace nobody ever reports.
            activeTrace.finish("capture rejected (busy)")
            return false
        }
        onImageCaptured = callback
        captureTrace = activeTrace
        captureRequested = true
        scheduleCaptureFallback(activeTrace)
        Log.i(TAG, "captureOnce requested; waiting for next ImageReader frame")
        return true
    }

    /**
     * Starts remembering delivered frames so a later capture can fall back to the most recent
     * one. Call this before the UI settle delay, so the frame produced by the menu closing is
     * the one remembered.
     */
    fun armCaptureFallback() {
        rememberingFrame = true
    }

    /** Stops remembering and releases the remembered frame. Safe to call more than once. */
    fun disarmCaptureFallback() {
        rememberingFrame = false
        synchronized(latestFrameLock) {
            latestFrame?.let { if (!it.isRecycled) it.recycle() }
            latestFrame = null
        }
    }

    private val latestFrameLock = Any()

    private fun scheduleCaptureFallback(trace: ScreenTLPerformanceTrace) {
        cancelCaptureFallback()
        val runnable = Runnable {
            fallbackRunnable = null
            if (!captureRequested) return@Runnable
            // Take the remembered frame atomically so a concurrent disarm cannot recycle it
            // between the read and the copy below.
            val remembered = synchronized(latestFrameLock) {
                val frame = latestFrame
                latestFrame = null
                frame
            }
            if (remembered == null || remembered.isRecycled) {
                Log.i(TAG, "Capture fallback skipped: no remembered frame available yet")
                return@Runnable
            }
            captureRequested = false
            captureTrace = null
            val callback = onImageCaptured
            onImageCaptured = null
            Log.i(TAG, "Capture fallback: reusing last remembered frame ${remembered.width}x${remembered.height}")
            trace.mark("capture_fallback_used")
            // Report it under the normal stage name so the capture timing is still recorded.
            trace.mark("screenshot_ready ${remembered.width}x${remembered.height}")
            val handedOut = try {
                Bitmap.createBitmap(remembered)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy remembered frame for capture fallback", e)
                return@Runnable
            } finally {
                if (!remembered.isRecycled) remembered.recycle()
            }
            if (callback != null) callback.invoke(handedOut) else handedOut.recycle()
        }
        fallbackRunnable = runnable
        handler.postDelayed(runnable, CAPTURE_FALLBACK_DELAY_MS)
    }

    private fun cancelCaptureFallback() {
        fallbackRunnable?.let(handler::removeCallbacks)
        fallbackRunnable = null
    }

    fun cancelPendingCapture() {
        captureRequested = false
        onImageCaptured = null
        captureTrace = null
        cancelCaptureFallback()
        Log.i(TAG, "Pending capture cancelled")
    }

    fun stop() {
        Log.i(TAG, "Stopping screen capture")
        captureRequested = false
        onImageCaptured = null
        captureTrace = null
        cancelCaptureFallback()
        disarmCaptureFallback()
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        captureWidth = 0
        captureHeight = 0
        captureDensity = 0
    }

    fun release() {
        stop()
        handlerThread.quitSafely()
    }

    private fun currentRealMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        val display = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        @Suppress("DEPRECATION")
        display.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun configureDisplay(width: Int, height: Int, density: Int) {
        val reader = createImageReader(width, height)
        val projection = mediaProjection ?: run { reader.close(); return }
        val display = projection.createVirtualDisplay(
            "ScreenTranslatorCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler
        )
        if (display == null) {
            Log.e(TAG, "createVirtualDisplay() returned null")
            reader.close()
            return
        }
        imageReader = reader
        virtualDisplay = display
        captureWidth = width
        captureHeight = height
        captureDensity = density
        Log.i(TAG, "VirtualDisplay configured: ${width}x${height}, density=$density")
    }

    private fun reconfigureDisplay(width: Int, height: Int, density: Int): Boolean {
        val display = virtualDisplay ?: return false
        val newReader = createImageReader(width, height)
        val oldReader = imageReader
        return try {
            display.resize(width, height, density)
            display.setSurface(newReader.surface)
            imageReader = newReader
            captureWidth = width
            captureHeight = height
            captureDensity = density
            oldReader?.close()
            Log.i(TAG, "VirtualDisplay resized: ${width}x${height}, density=$density")
            true
        } catch (e: Exception) {
            newReader.close()
            Log.e(TAG, "Failed to resize VirtualDisplay", e)
            false
        }
    }

    private fun decodeFrame(image: Image, width: Int, height: Int): Bitmap {
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val bitmapWidth = width + rowPadding / pixelStride
        val bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(plane.buffer)
        val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
        if (cropped !== bitmap) bitmap.recycle()
        return cropped
    }

    private fun createImageReader(width: Int, height: Int): ImageReader {
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ availableReader ->
            val image = availableReader.acquireLatestImage() ?: run {
                Log.w(TAG, "ImageReader signaled but acquireLatestImage() returned null")
                return@setOnImageAvailableListener
            }

            if (!captureRequested) {
                if (rememberingFrame) {
                    try {
                        val remembered = decodeFrame(image, width, height)
                        synchronized(latestFrameLock) {
                            latestFrame?.let { if (!it.isRecycled) it.recycle() }
                            latestFrame = remembered
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to remember idle frame", e)
                    }
                }
                image.close()
                return@setOnImageAvailableListener
            }

            captureRequested = false
            cancelCaptureFallback()
            val trace = captureTrace
            var fresh: Bitmap? = null
            try {
                Log.i(TAG, "Capturing frame: image=${image.width}x${image.height}")
                trace?.mark("image_reader_frame")
                val decoded = decodeFrame(image, width, height)
                Log.i(TAG, "Fresh screen frame captured successfully: ${decoded.width}x${decoded.height}")
                trace?.mark("screenshot_ready ${decoded.width}x${decoded.height}")
                fresh = decoded
            } catch (e: Exception) {
                trace?.mark("capture_conversion_failed")
                Log.e(TAG, "Failed to convert ImageReader frame to Bitmap", e)
            } finally {
                image.close()
            }

            val callback = onImageCaptured
            onImageCaptured = null
            captureTrace = null
            val ready = fresh
            if (callback != null && ready != null) callback.invoke(ready) else ready?.recycle()
        }, handler)
        return reader
    }
}
