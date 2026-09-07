package com.example.screentranslator

import android.graphics.Bitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds small blurred screenshot patches between OCR and overlay rendering.
 * Manual TL translates asynchronously, so the original capture bitmap is
 * released by OcrManager before TranslationOverlayView is created.
 */
object OverlayBlurCache {

    private val cache = ConcurrentHashMap<String, Bitmap>()

    fun key(left: Int, top: Int, right: Int, bottom: Int): String =
        "$left:$top:$right:$bottom"

    fun put(left: Int, top: Int, right: Int, bottom: Int, bitmap: Bitmap) {
        val key = key(left, top, right, bottom)
        cache.put(key, bitmap)?.let { previous ->
            if (!previous.isRecycled) previous.recycle()
        }
    }

    fun take(left: Int, top: Int, right: Int, bottom: Int): Bitmap? =
        cache.remove(key(left, top, right, bottom))

    fun clear() {
        cache.values.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        cache.clear()
    }
}
