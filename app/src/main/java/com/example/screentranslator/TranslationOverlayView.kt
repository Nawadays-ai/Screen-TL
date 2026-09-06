package com.example.screentranslator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * Draws translated text over the captured screen without consuming touch events.
 * Coordinates are based on the same display-sized bitmap used by OCR.
 */
class TranslationOverlayView(context: Context) : View(context) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = 220
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.LEFT
    }

    private val padding = 8f
    private var items: List<TranslationOverlayItem> = emptyList()
    private var sourceWidth = 1
    private var sourceHeight = 1

    fun setTranslations(
        translations: List<TranslationOverlayItem>,
        sourceWidth: Int,
        sourceHeight: Int
    ) {
        items = translations
        this.sourceWidth = sourceWidth.coerceAtLeast(1)
        this.sourceHeight = sourceHeight.coerceAtLeast(1)
        visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        invalidate()
    }

    fun clearTranslations() {
        items = emptyList()
        visibility = View.GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val scaleX = width.toFloat() / sourceWidth.toFloat()
        val scaleY = height.toFloat() / sourceHeight.toFloat()

        items.forEach { item ->
            val left = item.left * scaleX
            val top = item.top * scaleY
            val right = item.right * scaleX
            val bottom = item.bottom * scaleY

            if (right <= left || bottom <= top) return@forEach

            val rect = RectF(left, top, right, bottom)
            backgroundPaint.setColor(android.graphics.Color.BLACK)
            canvas.drawRoundRect(rect, 6f, 6f, backgroundPaint)

            val boxHeight = (bottom - top).coerceAtLeast(12f)
            textPaint.textSize = (boxHeight * 0.72f).coerceIn(12f, 48f)

            val baseline = bottom - padding.coerceAtMost(boxHeight / 4f)
            val maxWidth = (right - left - padding * 2f).coerceAtLeast(1f)
            val translated = fitText(item.translatedText, maxWidth)

            canvas.drawText(
                translated,
                left + padding,
                baseline,
                textPaint
            )
        }
    }

    private fun fitText(text: String, maxWidth: Float): String {
        if (textPaint.measureText(text) <= maxWidth) return text

        var end = text.length
        while (end > 1 && textPaint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return text.substring(0, end.coerceAtLeast(1)) + "…"
    }
}

data class TranslationOverlayItem(
    val translatedText: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)
