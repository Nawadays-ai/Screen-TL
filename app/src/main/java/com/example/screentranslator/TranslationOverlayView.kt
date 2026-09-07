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
 * Backgrounds and text are deliberately rendered in two passes. Every
 * replacement/background is drawn first; all translated text is drawn after
 * that pass. Therefore one translation can never be hidden by the background
 * of another translation when their boxes overlap.
 *
 * Intersecting tolerance boxes are also assigned one shared background color.
 * This makes a stacked cluster visually consistent even when each individual
 * translation only paints part of its tolerance width.
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
    ) {
        fun boxRect() = RectF(left, top, right, bottom)
    }

    private var renderItems: List<RenderItem> = emptyList()
    private var groupColors: List<Int> = emptyList()
    private var itemGroups: List<Int> = emptyList()
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
        buildOverlapGroups()
        visibility = if (renderItems.isEmpty()) View.GONE else View.VISIBLE
        invalidate()
    }

    fun clearTranslations() {
        renderItems = emptyList()
        groupColors = emptyList()
        itemGroups = emptyList()
        visibility = View.GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (renderItems.isEmpty()) return

        val scaleX = width.toFloat() / sourceWidth.toFloat()
        val scaleY = height.toFloat() / sourceHeight.toFloat()

        // PASS 1: every replacement/background first.
        renderItems.forEachIndexed { index, renderItem ->
            val left = renderItem.left * scaleX
            val top = renderItem.top * scaleY
            val right = renderItem.right * scaleX
            val bottom = renderItem.bottom * scaleY
            val fillRight = renderItem.fillRight * scaleX
            if (right <= left || bottom <= top || fillRight <= left) return@forEachIndexed

            val box = RectF(left, top, right, bottom)
            val fillBox = RectF(left, top, fillRight.coerceAtMost(right), bottom)
            val radius = ((bottom - top) * 0.08f).coerceIn(2f, 7f)
            val groupId = itemGroups.getOrElse(index) { index }
            val effectiveColor = groupColors.getOrElse(groupId) {
                darkenColor(renderItem.item.backgroundColor)
            }

            canvas.save()
            canvas.clipRect(box)
            drawReconstructedBlur(canvas, fillBox, effectiveColor, radius)
            canvas.restore()
        }

        // PASS 2: all translation text is drawn last. A neighboring box can
        // therefore never cover the text of an earlier item.
        renderItems.forEachIndexed { index, renderItem ->
            val left = renderItem.left * scaleX
            val top = renderItem.top * scaleY
            val right = renderItem.right * scaleX
            val bottom = renderItem.bottom * scaleY
            if (right <= left || bottom <= top) return@forEachIndexed

            val groupId = itemGroups.getOrElse(index) { index }
            val effectiveColor = groupColors.getOrElse(groupId) {
                darkenColor(renderItem.item.backgroundColor)
            }

            textPaint.textScaleX = 1f
            textPaint.textSize = renderItem.textSize * scaleY
            textPaint.color = chooseTextColor(effectiveColor)
            val maxTextWidth = (
                (right - left) - renderItem.horizontalPadding * 2f * scaleX
            ).coerceAtLeast(1f)
            val fittedText = fitSingleLine(renderItem.item.translatedText, maxTextWidth)
            if (fittedText.isEmpty()) return@forEachIndexed

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

        textPaint.textScaleX = 1f
        textPaint.color = Color.WHITE
    }

    /**
     * Builds connected overlap groups using the COMPLETE tolerance box, not
     * only the currently colored portion. If A overlaps B and B overlaps C,
     * all three share one color group to avoid visible seams in a text stack.
     */
    private fun buildOverlapGroups() {
        if (renderItems.isEmpty()) {
            itemGroups = emptyList()
            groupColors = emptyList()
            return
        }

        val parent = IntArray(renderItems.size) { it }

        fun find(value: Int): Int {
            var x = value
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }

        fun union(a: Int, b: Int) {
            val rootA = find(a)
            val rootB = find(b)
            if (rootA != rootB) parent[rootB] = rootA
        }

        for (i in renderItems.indices) {
            val a = renderItems[i].boxRect()
            for (j in i + 1 until renderItems.size) {
                val b = renderItems[j].boxRect()
                if (RectF.intersects(a, b)) union(i, j)
            }
        }

        val rootToGroup = linkedMapOf<Int, Int>()
        val groups = IntArray(renderItems.size)
        renderItems.indices.forEach { index ->
            val root = find(index)
            groups[index] = rootToGroup.getOrPut(root) { rootToGroup.size }
        }
        itemGroups = groups.toList()

        val sums = Array(rootToGroup.size) { FloatArray(4) }
        renderItems.forEachIndexed { index, item ->
            val group = itemGroups[index]
            val color = darkenColor(item.item.backgroundColor)
            sums[group][0] = sums[group][0] + Color.red(color)
            sums[group][1] = sums[group][1] + Color.green(color)
            sums[group][2] = sums[group][2] + Color.blue(color)
            sums[group][3] = sums[group][3] + 1f
        }

        groupColors = sums.map { sum ->
            val count = sum[3].coerceAtLeast(1f)
            Color.rgb(
                (sum[0] / count).toInt().coerceIn(0, 255),
                (sum[1] / count).toInt().coerceIn(0, 255),
                (sum[2] / count).toInt().coerceIn(0, 255)
            )
        }
    }

    private fun drawReconstructedBlur(
        canvas: Canvas,
        box: RectF,
        baseColor: Int,
        radius: Float
    ) {
        val edge = adjustColor(baseColor, 0.94f)
        val deep = adjustColor(baseColor, 0.88f)
        backgroundPaint.alpha = 255
        backgroundPaint.shader = LinearGradient(
            box.left,
            box.top,
            box.right.coerceAtLeast(box.left + 1f),
            box.bottom,
            intArrayOf(edge, baseColor, deep, baseColor, deep),
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

    private fun chooseTextColor(effectiveColor: Int): Int {
        val luminance = 0.2126f * Color.red(effectiveColor) +
                0.7152f * Color.green(effectiveColor) +
                0.0722f * Color.blue(effectiveColor)
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
        val desiredWidth = (measuredWidth + horizontalPadding * 2f).coerceAtLeast(baseWidth)
        val boxWidth = desiredWidth.coerceAtMost(maxBoxWidth)

        val left = baseLeft
        val right = (left + boxWidth).coerceAtMost(width.toFloat())
        val maxTextWidth = (right - left - horizontalPadding * 2f).coerceAtLeast(1f)

        var finalTextSize = baseTextSize
        if (measuredWidth > maxTextWidth && measuredWidth > 0f) {
            val fitScale = (maxTextWidth / measuredWidth).coerceAtLeast(minFontScale)
            finalTextSize = (baseTextSize * fitScale).coerceIn(minTextSizePx, baseTextSize)
        }

        textPaint.textSize = finalTextSize
        val finalMetrics = textPaint.fontMetrics
        val finalTextHeight = finalMetrics.descent - finalMetrics.ascent
        val availableHeight = (boxHeight - verticalPadding * 2f).coerceAtLeast(1f)
        if (finalTextHeight > availableHeight && finalTextHeight > 0f) {
            finalTextSize = (finalTextSize * (availableHeight / finalTextHeight)).coerceAtLeast(minTextSizePx)
            textPaint.textSize = finalTextSize
        }

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
        groupColors = emptyList()
        itemGroups = emptyList()
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
