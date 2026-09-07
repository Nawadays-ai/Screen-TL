package com.example.screentranslator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * Renders Manual TL directly over the source text region.
 *
 * Important: translated text is fitted by changing Paint.textSize uniformly,
 * not by changing textScaleX. This keeps the glyph aspect ratio natural and
 * prevents the vertically-stretched / horizontally-squeezed appearance.
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

    private val horizontalPaddingRatio = 0.14f
    private val verticalPaddingRatio = 0.10f
    private val minTextSizePx = 8f
    private val maxTextSizePx = 96f
    private val maskAlpha = 245
    private val minFontScale = 0.62f

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

        // The overlay window is created in the same pixel coordinate space as
        // the captured frame. Keep only the normal width/height conversion as
        // a fallback for device/window edge cases.
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
            val horizontalPadding = (boxHeight * horizontalPaddingRatio).coerceIn(3f, 16f)
            val verticalPadding = (boxHeight * verticalPaddingRatio).coerceIn(2f, 8f)
            val maxTextWidth = (boxWidth - horizontalPadding * 2f).coerceAtLeast(1f)

            val sampled = item.backgroundColor
            backgroundPaint.color = Color.argb(
                maskAlpha,
                Color.red(sampled),
                Color.green(sampled),
                Color.blue(sampled)
            )
            canvas.drawRoundRect(
                RectF(left, top, right, bottom),
                (boxHeight * 0.08f).coerceIn(2f, 7f),
                (boxHeight * 0.08f).coerceIn(2f, 7f),
                backgroundPaint
            )

            // Start from the calibrated source size. If the translation is
            // longer, reduce textSize uniformly. Never use textScaleX: doing
            // so makes letters look unnaturally narrow and vertically stretched.
            val baseTextSize = if (item.sourceTextSizePx > 0f) {
                item.sourceTextSizePx * scaleY
            } else {
                boxHeight * 0.72f
            }.coerceIn(minTextSizePx, maxTextSizePx)

            textPaint.textScaleX = 1f
            textPaint.textSize = baseTextSize

            val measuredWidth = textPaint.measureText(item.translatedText)
            if (measuredWidth > maxTextWidth && measuredWidth > 0f) {
                val fitScale = (maxTextWidth / measuredWidth).coerceAtLeast(minFontScale)
                textPaint.textSize = (baseTextSize * fitScale)
                    .coerceIn(minTextSizePx, baseTextSize)
            }

            // If the translated text still does not fit at the minimum allowed
            // font scale, truncate only as a last resort. Aspect ratio remains
            // untouched because textScaleX stays at 1.0.
            val fittedText = fitSingleLine(item.translatedText, maxTextWidth)
            if (fittedText.isEmpty()) return@forEach

            val metrics = textPaint.fontMetrics
            val textHeight = metrics.descent - metrics.ascent
            val availableHeight = (boxHeight - verticalPadding * 2f).coerceAtLeast(1f)

            // Keep the visual glyphs inside the source line's mask. If the
            // calibrated font is slightly too tall, shrink it uniformly.
            if (textHeight > availableHeight && textHeight > 0f) {
                textPaint.textSize = (textPaint.textSize * (availableHeight / textHeight))
                    .coerceAtLeast(minTextSizePx)
            }

            val finalMetrics = textPaint.fontMetrics
            val finalTextHeight = finalMetrics.descent - finalMetrics.ascent
            val baseline = top + (boxHeight - finalTextHeight) / 2f - finalMetrics.ascent

            canvas.save()
            canvas.clipRect(left, top, right, bottom)
            canvas.drawText(fittedText, left + horizontalPadding, baseline, textPaint)
            canvas.restore()
        }

        textPaint.textScaleX = 1f
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
