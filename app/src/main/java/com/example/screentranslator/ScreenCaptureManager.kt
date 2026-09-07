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
    private var onImageCaptured: ((Bitmap) -> Unit)? = null

    private val handlerThread = HandlerThread("ScreenCaptureThread").apply {
        start()
    }
    private val handler = Handler(handlerThread.looper)

    fun start(): Boolean {
        return try {
            Log.i(TAG, "Starting MediaProjection. resultCode=$resultCode")

            val projectionManager =
                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection returned null")
                return false
            }

            val metrics = currentRealMetrics()
            Log.i(TAG, "Capture display metrics: ${metrics.widthPixels}x${metrics.heightPixels}, density=${metrics.densityDpi}")
            configureDisplay(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)

            mediaProjection?.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.w(TAG, "MediaProjection stopped by system/user")
                        stop()
                    }
                },
                handler
            )

            virtualDisplay != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen capture", e)
            stop()
            false
        }
    }

    /**
     * MediaProjection keeps the dimensions it was created with. Android can
     * rotate the physical display while this service remains alive, so make
     * sure the capture surface follows the current real display dimensions.
     */
    fun ensureCurrentDisplayConfiguration(): Boolean {
        if (mediaProjection == null) return false
        return try {
            val metrics = currentRealMetrics()
            val changed = metrics.widthPixels != captureWidth ||
                    metrics.heightPixels != captureHeight ||
                    metrics.densityDpi != captureDensity
            if (changed) {
                Log.i(TAG, "Display configuration changed: ${captureWidth}x$captureHeight -> ${metrics.widthPixels}x${metrics.heightPixels}; rebuilding capture surface")
                captureRequested = false
                onImageCaptured = null
                virtualDisplay?.release()
                virtualDisplay = null
                imageReader?.close()
                imageReader = null
                configureDisplay(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
            }
            virtualDisplay != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reconfigure capture for current display", e)
            false
        }
    }

    fun captureOnce(callback: (Bitmap) -> Unit): Boolean {
        if (!ensureCurrentDisplayConfiguration()) {
            Log.e(TAG, "captureOnce rejected: current display configuration is unavailable")
            return false
        }

        if (mediaProjection == null || imageReader == null) {
            Log.e(TAG, "captureOnce rejected: projection=${mediaProjection != null}, imageReader=${imageReader != null}")
            return false
        }

        if (captureRequested) {
            Log.w(TAG, "captureOnce rejected: another capture is already pending")
            return false
        }

        onImageCaptured = callback
        captureRequested = true
        Log.i(TAG, "captureOnce requested; waiting for next ImageReader frame")
        return true
    }

    fun cancelPendingCapture() {
        captureRequested = false
        onImageCaptured = null
        Log.i(TAG, "Pending capture cancelled")
    }

    fun stop() {
        Log.i(TAG, "Stopping screen capture")
        captureRequested = false

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
        onImageCaptured = null
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
        imageReader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: run {
                Log.w(TAG, "ImageReader signaled but acquireLatestImage() returned null")
                return@setOnImageAvailableListener
            }

            if (!captureRequested) {
                image.close()
                return@setOnImageAvailableListener
            }

            captureRequested = false

            try {
                val plane = image.planes[0]
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val bitmapWidth = width + rowPadding / pixelStride

                Log.i(TAG, "Capturing frame: image=${image.width}x${image.height}, pixelStride=$pixelStride, rowStride=$rowStride, padding=$rowPadding, bitmap=${bitmapWidth}x$height")

                val bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(plane.buffer)
                image.close()

                val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                if (croppedBitmap !== bitmap) bitmap.recycle()

                Log.i(TAG, "Fresh screen frame captured successfully: ${croppedBitmap.width}x${croppedBitmap.height}")
                onImageCaptured?.invoke(croppedBitmap)
            } catch (e: Exception) {
                image.close()
                Log.e(TAG, "Failed to convert ImageReader frame to Bitmap", e)
            }
        }, handler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenTranslatorCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )

        captureWidth = width
        captureHeight = height
        captureDensity = density

        if (virtualDisplay == null) {
            Log.e(TAG, "createVirtualDisplay() returned null")
            imageReader?.close()
            imageReader = null
        } else {
            Log.i(TAG, "VirtualDisplay configured: ${width}x${height}, density=$density")
        }
    }
}
