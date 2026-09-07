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
    private val minTextScaleX = 0.72f

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

            // Keep the calibrated source font size. Do not reduce textSize
            // merely because a translation is wider than the source line.
            // Instead, use a modest horizontal text scale so the translation
            // keeps the same visual height as the source whenever possible.
            val sourceFontSize = if (item.sourceTextSizePx > 0f) {
                item.sourceTextSizePx * scaleY
            } else {
                boxHeight * 0.62f
            }.coerceIn(minTextSizePx, maxTextSizePx)

            textPaint.textSize = sourceFontSize
            textPaint.textScaleX = 1f

            val measuredWidth = textPaint.measureText(item.translatedText)
            if (measuredWidth > maxTextWidth && measuredWidth > 0f) {
                textPaint.textScaleX = (maxTextWidth / measuredWidth)
                    .coerceIn(minTextScaleX, 1f)
            }

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

        // Avoid leaking a horizontal scale into future Canvas/View operations.
        textPaint.textScaleX = 1f
    }

    private fun fitSingleLine(text: String, maxWidth: Float): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return ""

        if (textPaint.measureText(normalized) <= maxWidth) return normalized

        // The font size is intentionally never reduced here. If the translated
        // sentence is still too long after the horizontal scale floor, truncate
        // only as a last resort rather than making the translation vertically
        // smaller than the source.
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
