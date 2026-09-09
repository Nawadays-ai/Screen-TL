package com.example.screentranslator

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
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
            reconfigureDisplay(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reconfigure capture for current display", e)
            false
        }
    }

    fun captureOnce(callback: (Bitmap) -> Unit, trace: ScreenTLPerformanceTrace? = null): Boolean {
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
            return false
        }
        onImageCaptured = callback
        captureTrace = activeTrace
        captureRequested = true
        Log.i(TAG, "captureOnce requested; waiting for next ImageReader frame")
        return true
    }

    fun cancelPendingCapture() {
        captureRequested = false
        onImageCaptured = null
        captureTrace = null
        Log.i(TAG, "Pending capture cancelled")
    }

    fun stop() {
        Log.i(TAG, "Stopping screen capture")
        captureRequested = false
        onImageCaptured = null
        captureTrace = null
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

    private fun createImageReader(width: Int, height: Int): ImageReader {
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ availableReader ->
            val image = availableReader.acquireLatestImage() ?: run {
                Log.w(TAG, "ImageReader signaled but acquireLatestImage() returned null")
                return@setOnImageAvailableListener
            }
            if (!captureRequested) {
                image.close()
                return@setOnImageAvailableListener
            }
            captureRequested = false
            try {
                val trace = captureTrace
                val plane = image.planes[0]
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val bitmapWidth = width + rowPadding / pixelStride
                Log.i(TAG, "Capturing frame: image=${image.width}x${image.height}, pixelStride=$pixelStride, rowStride=$rowStride, padding=$rowPadding, bitmap=${bitmapWidth}x$height")
                trace?.mark("image_reader_frame")
                val bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(plane.buffer)
                image.close()
                val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                if (croppedBitmap !== bitmap) bitmap.recycle()
                Log.i(TAG, "Fresh screen frame captured successfully: ${croppedBitmap.width}x${croppedBitmap.height}")
                trace?.mark("screenshot_ready ${croppedBitmap.width}x${croppedBitmap.height}")
                val callback = onImageCaptured
                onImageCaptured = null
                captureTrace = null
                callback?.invoke(croppedBitmap) ?: croppedBitmap.recycle()
            } catch (e: Exception) {
                image.close()
                captureTrace?.mark("capture_conversion_failed")
                Log.e(TAG, "Failed to convert ImageReader frame to Bitmap", e)
            }
        }, handler)
        return reader
    }
}
