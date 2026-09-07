package com.example.screentranslator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * Renders Manual TL directly over the source text region. Geometry and source
 * font size come from TextLayoutAnalyzer; the sampled background color covers
 * the source instead of drawing a fixed black rectangle.
 */
class TranslationOverlayView(context: Context) : View(context) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        isSubpixelText = true
    }

    private val horizontalPaddingRatio = 0.16f
    private val verticalPaddingRatio = 0.16f
    private val minTextSizePx = 8f
    private val maxTextSizePx = 96f

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
            val horizontalPadding = (boxHeight * horizontalPaddingRatio).coerceIn(2f, 18f)
            val verticalPadding = (boxHeight * verticalPaddingRatio).coerceIn(2f, 12f)
            val maxTextWidth = (boxWidth - horizontalPadding * 2f).coerceAtLeast(1f)
            val maxTextHeight = (boxHeight - verticalPadding * 2f).coerceAtLeast(1f)

            backgroundPaint.color = item.backgroundColor
            canvas.drawRect(RectF(left, top, right, bottom), backgroundPaint)

            val sourceFontSize = if (item.sourceTextSizePx > 0f) {
                item.sourceTextSizePx * scaleY
            } else {
                boxHeight * 0.62f
            }

            val textSize = findTextSize(
                item.translatedText,
                sourceFontSize.coerceIn(minTextSizePx, maxTextSizePx),
                maxTextWidth,
                maxTextHeight
            )
            textPaint.textSize = textSize

            val fittedText = fitSingleLine(item.translatedText, maxTextWidth)
            if (fittedText.isEmpty()) return@forEach

            val metrics = textPaint.fontMetrics
            val textHeight = metrics.descent - metrics.ascent
            val baseline = top + (boxHeight - textHeight) / 2f - metrics.ascent

            canvas.save()
            canvas.clipRect(left, top, right, bottom)
            canvas.drawText(fittedText, left + horizontalPadding, baseline, textPaint)
            canvas.restore()
        }
    }

    private fun findTextSize(
        text: String,
        sourceSize: Float,
        maxWidth: Float,
        maxHeight: Float
    ): Float {
        var size = sourceSize
        textPaint.textSize = size

        val width = textPaint.measureText(text)
        if (width > maxWidth && width > 0f) {
            size *= maxWidth / width
        }

        textPaint.textSize = size
        val metrics = textPaint.fontMetrics
        val height = metrics.descent - metrics.ascent
        if (height > maxHeight && height > 0f) {
            size *= maxHeight / height
        }

        return size.coerceIn(minTextSizePx, sourceSize)
    }

    private fun fitSingleLine(text: String, maxWidth: Float): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return ""

        if (textPaint.measureText(normalized) <= maxWidth) return normalized

        // The font has already been reduced to the source-compatible size. If a
        // translation is still exceptionally long, retain as much as possible
        // rather than letting it paint outside the source region.
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
    val bottom: Int,
    val sourceTextSizePx: Float = 0f,
    val backgroundColor: Int = Color.BLACK
)
