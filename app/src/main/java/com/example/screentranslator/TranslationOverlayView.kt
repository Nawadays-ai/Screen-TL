package com.example.screentranslator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * Renders Manual TL directly over the source text region. The overlay window
 * is created in the same pixel coordinate space as the captured frame, so the
 * renderer should not make the screen appear scaled or shifted.
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

    private val horizontalPaddingRatio = 0.18f
    private val verticalPaddingRatio = 0.20f
    private val minTextSizePx = 8f
    private val maxTextSizePx = 96f
    private val maskAlpha = 245

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

        // The window is intentionally created at sourceWidth x sourceHeight.
        // Keep the fallback scaling for device/window edge cases, but do not
        // introduce any centering or aspect-ratio compensation here.
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
            val horizontalPadding = (boxHeight * horizontalPaddingRatio).coerceIn(3f, 20f)
            val verticalPadding = (boxHeight * verticalPaddingRatio).coerceIn(3f, 14f)
            val maxTextWidth = (boxWidth - horizontalPadding * 2f).coerceAtLeast(1f)
            val maxTextHeight = (boxHeight - verticalPadding * 2f).coerceAtLeast(1f)

            // Make the replacement mask visually solid enough to reliably hide
            // the original glyphs. True backdrop blur is not used here because
            // RenderEffect blurs the overlay's own content, not the app behind it.
            val sampled = item.backgroundColor
            backgroundPaint.color = Color.argb(
                maskAlpha,
                Color.red(sampled),
                Color.green(sampled),
                Color.blue(sampled)
            )
            canvas.drawRoundRect(
                RectF(left, top, right, bottom),
                (boxHeight * 0.12f).coerceIn(2f, 10f),
                (boxHeight * 0.12f).coerceIn(2f, 10f),
                backgroundPaint
            )

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
