package com.example.screentranslator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * Draws translated text directly over the OCR source region without consuming
 * touch events. The source region is fully covered so the original text is not
 * visible underneath the translation.
 */
class TranslationOverlayView(context: Context) : View(context) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
        alpha = 255
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        isSubpixelText = true
    }

    private val horizontalPadding = 6f
    private val verticalPadding = 4f
    private val cornerRadius = 5f
    private val minTextSize = 10f
    private val maxTextSize = 42f

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

            // Start from a font size based on the original OCR box. Only shrink it
            // when the translated text is wider than the original source region.
            val textSize = findTextSize(item.translatedText, maxTextWidth, maxTextHeight)
            textPaint.textSize = textSize
            val fittedText = fitText(item.translatedText, maxTextWidth)
            if (fittedText.isEmpty()) return@forEach

            // Opaque background completely replaces the original source pixels.
            val background = RectF(left, top, right, bottom)
            canvas.drawRoundRect(background, cornerRadius, cornerRadius, backgroundPaint)

            val metrics = textPaint.fontMetrics
            val textHeight = metrics.descent - metrics.ascent
            val baseline = top + (boxHeight - textHeight) / 2f - metrics.ascent

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
        val baseSize = (maxHeight * 0.62f).coerceIn(minTextSize, maxTextSize)
        textPaint.textSize = baseSize

        val measuredWidth = textPaint.measureText(text)
        if (measuredWidth <= maxWidth) return baseSize

        // Scale the font down only as much as necessary for longer translations.
        val widthBasedSize = (baseSize * maxWidth / measuredWidth)
            .coerceIn(minTextSize, baseSize)
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
