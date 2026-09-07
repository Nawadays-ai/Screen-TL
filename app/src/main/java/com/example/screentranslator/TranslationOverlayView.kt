package com.example.screentranslator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View

/**
 * Manual TL renderer.
 *
 * The translation starts exactly at the source line's left edge. If the
 * translation needs more room, the tolerance box grows only to the right.
 * The colored replacement area itself only covers the translated text plus
 * padding when the translation fits inside that tolerance. If the text still
 * exceeds the tolerance after fitting, the color reaches the tolerance edge.
 *
 * The background is reconstructed from colors sampled around the source text,
 * rather than copying raw screenshot pixels. This prevents the original glyphs
 * from being drawn twice.
 */
class TranslationOverlayView(context: Context) : View(context) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = 255
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        isSubpixelText = true
        isLinearText = true
    }

    private val horizontalPaddingRatio = 0.14f
    private val verticalPaddingRatio = 0.10f
    private val minTextSizePx = 8f
    private val maxTextSizePx = 96f
    private val minFontScale = 0.62f
    private val maxWidthRatio = 1.55f

    private data class RenderItem(
        val item: TranslationOverlayItem,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val fillRight: Float,
        val textSize: Float,
        val horizontalPadding: Float
    )

    private var renderItems: List<RenderItem> = emptyList()
    private var sourceWidth = 1
    private var sourceHeight = 1

    fun setTranslations(
        translations: List<TranslationOverlayItem>,
        sourceWidth: Int,
        sourceHeight: Int
    ) {
        this.sourceWidth = sourceWidth.coerceAtLeast(1)
        this.sourceHeight = sourceHeight.coerceAtLeast(1)
        renderItems = translations.mapNotNull { item ->
            buildRenderItem(item, this.sourceWidth, this.sourceHeight)
        }
        visibility = if (renderItems.isEmpty()) View.GONE else View.VISIBLE
        invalidate()
    }

    fun clearTranslations() {
        renderItems = emptyList()
        visibility = View.GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val scaleX = width.toFloat() / sourceWidth.toFloat()
        val scaleY = height.toFloat() / sourceHeight.toFloat()

        renderItems.forEach { renderItem ->
            val left = renderItem.left * scaleX
            val top = renderItem.top * scaleY
            val right = renderItem.right * scaleX
            val bottom = renderItem.bottom * scaleY
            val fillRight = renderItem.fillRight * scaleX
            if (right <= left || bottom <= top || fillRight <= left) return@forEach

            val box = RectF(left, top, right, bottom)
            val fillBox = RectF(left, top, fillRight.coerceAtMost(right), bottom)
            val radius = ((bottom - top) * 0.08f).coerceIn(2f, 7f)

            canvas.save()
            canvas.clipRect(box)
            drawReconstructedBlur(canvas, fillBox, renderItem.item, radius)

            textPaint.textScaleX = 1f
            textPaint.textSize = renderItem.textSize * scaleY
            textPaint.color = chooseTextColor(renderItem.item.backgroundColor)
            val maxTextWidth = (
                (right - left) - renderItem.horizontalPadding * 2f * scaleX
            ).coerceAtLeast(1f)
            val fittedText = fitSingleLine(renderItem.item.translatedText, maxTextWidth)

            if (fittedText.isNotEmpty()) {
                val metrics = textPaint.fontMetrics
                val textHeight = metrics.descent - metrics.ascent
                val baseline = top + (bottom - top - textHeight) / 2f - metrics.ascent
                canvas.drawText(
                    fittedText,
                    left + renderItem.horizontalPadding * scaleX,
                    baseline,
                    textPaint
                )
            }
            canvas.restore()
        }

        textPaint.textScaleX = 1f
        textPaint.color = Color.WHITE
    }

    private fun drawReconstructedBlur(
        canvas: Canvas,
        box: RectF,
        item: TranslationOverlayItem,
        radius: Float
    ) {
        val base = darkenColor(item.backgroundColor)
        // Keep the sampled color darker than the original source area while
        // retaining a gentle multi-stop gradient for the blur-like appearance.
        val edge = adjustColor(base, 0.94f)
        val deep = adjustColor(base, 0.88f)

        backgroundPaint.alpha = 255
        backgroundPaint.shader = LinearGradient(
            box.left,
            box.top,
            box.right.coerceAtLeast(box.left + 1f),
            box.bottom,
            intArrayOf(edge, base, deep, base, deep),
            floatArrayOf(0f, 0.28f, 0.50f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(box, radius, radius, backgroundPaint)
        backgroundPaint.shader = null
    }

    private fun darkenColor(color: Int): Int {
        val luminance = 0.2126f * Color.red(color) +
                0.7152f * Color.green(color) +
                0.0722f * Color.blue(color)
        if (luminance < 45f) return color
        return adjustColor(color, 0.82f)
    }

    private fun chooseTextColor(color: Int): Int {
        val background = darkenColor(color)
        val luminance = 0.2126f * Color.red(background) +
                0.7152f * Color.green(background) +
                0.0722f * Color.blue(background)
        return if (luminance < 150f) Color.WHITE else Color.BLACK
    }

    private fun adjustColor(color: Int, factor: Float): Int {
        return Color.rgb(
            (Color.red(color) * factor).roundToIntSafe(),
            (Color.green(color) * factor).roundToIntSafe(),
            (Color.blue(color) * factor).roundToIntSafe()
        )
    }

    private fun Float.roundToIntSafe(): Int = kotlin.math.round(this).toInt().coerceIn(0, 255)

    private fun buildRenderItem(
        item: TranslationOverlayItem,
        width: Int,
        height: Int
    ): RenderItem? {
        val baseLeft = item.left.coerceIn(0, width - 1).toFloat()
        val baseTop = item.top.coerceIn(0, height - 1).toFloat()
        val baseRight = item.right.coerceIn(baseLeft.toInt() + 1, width).toFloat()
        val baseBottom = item.bottom.coerceIn(baseTop.toInt() + 1, height).toFloat()
        if (baseRight <= baseLeft || baseBottom <= baseTop) return null

        val baseWidth = baseRight - baseLeft
        val boxHeight = baseBottom - baseTop
        val horizontalPadding = (boxHeight * horizontalPaddingRatio).coerceIn(3f, 16f)
        val verticalPadding = (boxHeight * verticalPaddingRatio).coerceIn(2f, 8f)

        val baseTextSize = if (item.sourceTextSizePx > 0f) {
            item.sourceTextSizePx
        } else {
            boxHeight * 0.72f
        }.coerceIn(minTextSizePx, maxTextSizePx)

        val normalized = item.translatedText.replace(Regex("\\s+"), " ").trim()
        textPaint.textScaleX = 1f
        textPaint.textSize = baseTextSize

        val measuredWidth = textPaint.measureText(normalized)
        val maxBoxWidth = minOf(
            baseWidth * maxWidthRatio,
            (width - baseLeft - 4f).coerceAtLeast(baseWidth)
        )

        val desiredWidth = (measuredWidth + horizontalPadding * 2f)
            .coerceAtLeast(baseWidth)
        val boxWidth = desiredWidth.coerceAtMost(maxBoxWidth)

        // Keep the source LEFT edge fixed. Extra translation room is allowed
        // only on the right; the box never shifts left of the source.
        val left = baseLeft
        val right = (left + boxWidth).coerceAtMost(width.toFloat())

        val maxTextWidth = (right - left - horizontalPadding * 2f).coerceAtLeast(1f)
        var finalTextSize = baseTextSize
        if (measuredWidth > maxTextWidth && measuredWidth > 0f) {
            val fitScale = (maxTextWidth / measuredWidth).coerceAtLeast(minFontScale)
            finalTextSize = (baseTextSize * fitScale)
                .coerceIn(minTextSizePx, baseTextSize)
        }

        textPaint.textSize = finalTextSize
        val finalMetrics = textPaint.fontMetrics
        val finalTextHeight = finalMetrics.descent - finalMetrics.ascent
        val availableHeight = (boxHeight - verticalPadding * 2f).coerceAtLeast(1f)
        if (finalTextHeight > availableHeight && finalTextHeight > 0f) {
            finalTextSize = (finalTextSize * (availableHeight / finalTextHeight))
                .coerceAtLeast(minTextSizePx)
            textPaint.textSize = finalTextSize
        }

        // Re-measure after any font-size adjustment. This determines how much
        // of the tolerance box should actually receive the replacement color.
        val finalMeasuredWidth = textPaint.measureText(normalized)
        val fillWidth = (finalMeasuredWidth + horizontalPadding * 2f)
            .coerceAtLeast(baseWidth)
            .coerceAtMost(right - left)

        return RenderItem(
            item = item,
            left = left,
            top = baseTop,
            right = right,
            bottom = baseBottom,
            fillRight = left + fillWidth,
            textSize = finalTextSize,
            horizontalPadding = horizontalPadding
        )
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

    override fun onDetachedFromWindow() {
        renderItems = emptyList()
        super.onDetachedFromWindow()
    }
}

data class TranslationOverlayItem(
    val translatedText: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val sourceTextSizePx: Float = 0f,
    val backgroundColor: Int = Color.BLACK,
    var blurredPatch: android.graphics.Bitmap? = null
)
