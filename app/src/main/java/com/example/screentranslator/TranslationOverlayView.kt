package com.example.screentranslator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * Renders Manual TL directly over the source text region.
 *
 * The renderer keeps the source glyph aspect ratio intact. A translated line
 * may grow wider than the source, but only up to a controlled maximum width;
 * after that the font is reduced uniformly and, only as a last resort, text
 * is truncated.
 *
 * For Manual TL, the captured source frame can also be used to build a small
 * blurred replacement patch. This hides the original glyphs while preserving
 * the surrounding colors/texture instead of painting a flat rectangle.
 */
class TranslationOverlayView(context: Context) : View(context) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isFilterBitmap = true
    }

    private val blurBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isFilterBitmap = true
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
    private val maskAlphaWithoutBlur = 235
    private val maskTintAlphaWithBlur = 105
    private val minFontScale = 0.62f
    private val maxWidthRatio = 1.55f
    private val blurDownscale = 5

    private data class RenderItem(
        val item: TranslationOverlayItem,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val textSize: Float,
        val horizontalPadding: Float,
        val verticalPadding: Float,
        var blurredPatch: Bitmap? = null
    )

    private var renderItems: List<RenderItem> = emptyList()
    private var sourceWidth = 1
    private var sourceHeight = 1

    fun setTranslations(
        translations: List<TranslationOverlayItem>,
        sourceWidth: Int,
        sourceHeight: Int,
        sourceBitmap: Bitmap? = null
    ) {
        releaseBlurPatches()

        this.sourceWidth = sourceWidth.coerceAtLeast(1)
        this.sourceHeight = sourceHeight.coerceAtLeast(1)
        renderItems = translations.mapNotNull { item ->
            buildRenderItem(item, this.sourceWidth, this.sourceHeight, sourceBitmap)
        }

        visibility = if (renderItems.isEmpty()) View.GONE else View.VISIBLE
        invalidate()
    }

    fun clearTranslations() {
        releaseBlurPatches()
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
            if (right <= left || bottom <= top) return@forEach

            val box = RectF(left, top, right, bottom)
            val radius = ((bottom - top) * 0.08f).coerceIn(2f, 7f)

            canvas.save()
            canvas.clipRect(box)

            val blurredPatch = renderItem.blurredPatch
            if (blurredPatch != null && !blurredPatch.isRecycled) {
                canvas.drawBitmap(
                    blurredPatch,
                    null,
                    box,
                    blurBitmapPaint
                )

                // A light sampled-color tint keeps the blurred replacement
                // coherent with the local background without hiding the blur.
                val sampled = renderItem.item.backgroundColor
                backgroundPaint.color = Color.argb(
                    maskTintAlphaWithBlur,
                    Color.red(sampled),
                    Color.green(sampled),
                    Color.blue(sampled)
                )
                canvas.drawRoundRect(box, radius, radius, backgroundPaint)
            } else {
                val sampled = renderItem.item.backgroundColor
                backgroundPaint.color = Color.argb(
                    maskAlphaWithoutBlur,
                    Color.red(sampled),
                    Color.green(sampled),
                    Color.blue(sampled)
                )
                canvas.drawRoundRect(box, radius, radius, backgroundPaint)
            }

            textPaint.textScaleX = 1f
            textPaint.textSize = renderItem.textSize * scaleY

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
    }

    private fun buildRenderItem(
        item: TranslationOverlayItem,
        width: Int,
        height: Int,
        sourceBitmap: Bitmap?
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
            (width - 8).toFloat()
        ).coerceAtLeast(baseWidth)

        // The bounding box is allowed to expand horizontally, but only up to
        // 155% of the source width. This gives translations room without
        // letting one short source line turn into an excessively wide box.
        val desiredWidth = (measuredWidth + horizontalPadding * 2f)
            .coerceAtLeast(baseWidth)
        val boxWidth = desiredWidth.coerceAtMost(maxBoxWidth)

        // Keep the expanded box centered around the source line, then clamp it
        // to the screen edges. This avoids a long translation jumping to one
        // side of the source text.
        val sourceCenter = (baseLeft + baseRight) / 2f
        var left = sourceCenter - boxWidth / 2f
        left = left.coerceIn(0f, (width - boxWidth).coerceAtLeast(0f))
        val right = (left + boxWidth).coerceAtMost(width.toFloat())

        var finalTextSize = baseTextSize
        val maxTextWidth = (right - left - horizontalPadding * 2f).coerceAtLeast(1f)
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
        }

        val renderItem = RenderItem(
            item = item,
            left = left,
            top = baseTop,
            right = right,
            bottom = baseBottom,
            textSize = finalTextSize,
            horizontalPadding = horizontalPadding,
            verticalPadding = verticalPadding
        )

        if (sourceBitmap != null && !sourceBitmap.isRecycled) {
            renderItem.blurredPatch = createBlurredPatch(
                sourceBitmap,
                left.roundToIntSafe(),
                baseTop.roundToIntSafe(),
                right.roundToIntSafe(),
                baseBottom.roundToIntSafe()
            )
        }

        return renderItem
    }

    /**
     * A small downsample/upscale blur is intentionally used here rather than
     * RenderScript. It works on the app's minSdk 24 and avoids the deprecated
     * RenderScript API. The result is only used for small text masks, so the
     * processing cost stays bounded.
     */
    private fun createBlurredPatch(
        source: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Bitmap? {
        val safeLeft = left.coerceIn(0, source.width - 1)
        val safeTop = top.coerceIn(0, source.height - 1)
        val safeRight = right.coerceIn(safeLeft + 1, source.width)
        val safeBottom = bottom.coerceIn(safeTop + 1, source.height)
        val patchWidth = safeRight - safeLeft
        val patchHeight = safeBottom - safeTop
        if (patchWidth <= 1 || patchHeight <= 1) return null

        return try {
            val patch = Bitmap.createBitmap(
                source,
                safeLeft,
                safeTop,
                patchWidth,
                patchHeight
            )
            val smallWidth = (patchWidth / blurDownscale).coerceAtLeast(1)
            val smallHeight = (patchHeight / blurDownscale).coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(patch, smallWidth, smallHeight, true)
            if (small !== patch && !patch.isRecycled) patch.recycle()
            val blurred = Bitmap.createScaledBitmap(small, patchWidth, patchHeight, true)
            if (blurred !== small && !small.isRecycled) small.recycle()
            blurred
        } catch (_: Exception) {
            null
        }
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

    private fun releaseBlurPatches() {
        renderItems.forEach { item ->
            item.blurredPatch?.let {
                if (!it.isRecycled) it.recycle()
            }
            item.blurredPatch = null
        }
    }

    override fun onDetachedFromWindow() {
        releaseBlurPatches()
        super.onDetachedFromWindow()
    }

    private fun Float.roundToIntSafe(): Int = kotlin.math.round(this).toInt()
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
