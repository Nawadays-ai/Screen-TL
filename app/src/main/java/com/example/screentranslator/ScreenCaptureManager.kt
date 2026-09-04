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

class ScreenCaptureManager(
    private val context: Context,
    private val resultCode: Int,
    private val data: Intent
) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var captureRequested = false
    private var onImageCaptured: ((Bitmap) -> Unit)? = null

    private val handlerThread = HandlerThread("ScreenCaptureThread").apply {
        start()
    }
    private val handler = Handler(handlerThread.looper)

    fun start(): Boolean {
        return try {
            val projectionManager =
                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                        as MediaProjectionManager

            mediaProjection =
                projectionManager.getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                return false
            }

            val metrics = DisplayMetrics()

            @Suppress("DEPRECATION")
            val display = context.getSystemService(Context.WINDOW_SERVICE)
                    as android.view.WindowManager

            @Suppress("DEPRECATION")
            display.defaultDisplay.getRealMetrics(metrics)

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            imageReader = ImageReader.newInstance(
                width,
                height,
                PixelFormat.RGBA_8888,
                2
            )

            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener

                if (!captureRequested) {
                    image.close()
                    return@setOnImageAvailableListener
                }

                captureRequested = false

                val planes = image.planes
                val plane = planes[0]

                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmapWidth = width + rowPadding / pixelStride

                val bitmap = Bitmap.createBitmap(
                    bitmapWidth,
                    height,
                    Bitmap.Config.ARGB_8888
                )

                bitmap.copyPixelsFromBuffer(plane.buffer)
                image.close()

                val croppedBitmap = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    width,
                    height
                )

                if (croppedBitmap !== bitmap) {
                    bitmap.recycle()
                }

                onImageCaptured?.invoke(croppedBitmap)

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

            mediaProjection?.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        stop()
                    }
                },
                handler
            )

            true

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun captureOnce(callback: (Bitmap) -> Unit): Boolean {
        if (mediaProjection == null || imageReader == null) {
            return false
        }

        onImageCaptured = callback
        captureRequested = true
        return true
    }

    fun stop() {
        captureRequested = false

        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        mediaProjection?.stop()
        mediaProjection = null
    }

    fun release() {
        stop()
        handlerThread.quitSafely()
    }
}
