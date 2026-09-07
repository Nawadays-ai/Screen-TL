package com.example.screentranslator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * Draws translated text over the captured screen without consuming touch events.
 * Coordinates are based on the same display-sized bitmap used by OCR.
 */
class TranslationOverlayView(context: Context) : View(context) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
        alpha = 190
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        isSubpixelText = true
    }

    // Keep the overlay visually separated from the OCR box without making it bulky.
    private val horizontalPadding = 6f
    private val verticalPadding = 4f
    private val cornerRadius = 7f
    private val minTextSize = 9f
    private val maxTextSize = 30f

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

            val boxWidth = right - left
            val boxHeight = bottom - top
            val maxTextWidth = (boxWidth - horizontalPadding * 2f).coerceAtLeast(1f)
            val maxTextHeight = (boxHeight - verticalPadding * 2f).coerceAtLeast(1f)

            // OCR boxes are normally single text lines. Fit the translation into the
            // original box instead of letting a large font spill into neighboring UI.
            val textSize = findTextSize(item.translatedText, maxTextWidth, maxTextHeight)
            textPaint.textSize = textSize

            val fittedText = fitText(item.translatedText, maxTextWidth)
            if (fittedText.isEmpty()) return@forEach

            // A small expansion keeps short text from touching the rounded background,
            // while clamping to the screen prevents the overlay from drawing off-screen.
            val background = RectF(
                left,
                top,
                right,
                bottom
            )
            canvas.drawRoundRect(background, cornerRadius, cornerRadius, backgroundPaint)

            val metrics = textPaint.fontMetrics
            val baseline = top + (boxHeight - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent

            canvas.save()
            canvas.clipRect(left, top, right, bottom)
            canvas.drawText(
                fittedText,
                left + horizontalPadding,
                baseline,
                textPaint
            )
            canvas.restore()
        }
    }

    private fun findTextSize(text: String, maxWidth: Float, maxHeight: Float): Float {
        val boxBasedSize = (maxHeight * 0.58f).coerceIn(minTextSize, maxTextSize)
        textPaint.textSize = boxBasedSize

        if (textPaint.measureText(text) <= maxWidth) return boxBasedSize

        val widthBasedSize = (boxBasedSize * maxWidth / textPaint.measureText(text))
            .coerceIn(minTextSize, boxBasedSize)
        textPaint.textSize = widthBasedSize
        return widthBasedSize
    }

    private fun fitText(text: String, maxWidth: Float): String {
        val normalized = text.replace("\n", " ").trim()
        if (normalized.isEmpty()) return ""
        if (textPaint.measureText(normalized) <= maxWidth) return normalized

        val ellipsis = "…"
        var end = normalized.length
        while (end > 1 && textPaint.measureText(normalized.substring(0, end) + ellipsis) > maxWidth) {
            end--
        }
        return normalized.substring(0, end.coerceAtLeast(1)) + ellipsis
    }
}

data class TranslationOverlayItem(
    val translatedText: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)
