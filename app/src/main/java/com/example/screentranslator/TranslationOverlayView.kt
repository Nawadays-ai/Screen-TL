package com.example.screentranslator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.view.WindowInsets
import kotlin.math.roundToInt

/** Renders translated OCR blocks while keeping screenshot coordinates 1:1. */
class TranslationOverlayView(context: Context) : View(context) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; alpha = 255 }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        isSubpixelText = true
        isLinearText = true
        alpha = 255
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
        val horizontalPadding: Float,
        val lines: List<String>,
        val lineSpacing: Float
    ) { fun boxRect() = RectF(left, top, right, bottom) }

    private var renderItems: List<RenderItem> = emptyList()
    private var groupColors: List<Int> = emptyList()
    private var itemGroups: List<Int> = emptyList()
    private var sourceWidth = 1
    private var sourceHeight = 1
    private var toleranceRatio = 1f

    fun setTranslations(translations: List<TranslationOverlayItem>, sourceWidth: Int, sourceHeight: Int) {
        setTranslations(translations, sourceWidth, sourceHeight, 1f)
    }

    /** toleranceRatio=1.0 is normal Manual TL; Klip starts at 1.80x. */
    fun setTranslations(translations: List<TranslationOverlayItem>, sourceWidth: Int, sourceHeight: Int, toleranceRatio: Float) {
        recyclePatches(renderItems)
        this.sourceWidth = sourceWidth.coerceAtLeast(1)
        this.sourceHeight = sourceHeight.coerceAtLeast(1)
        this.toleranceRatio = toleranceRatio.coerceIn(1f, 3f)
        renderItems = translations.mapNotNull { buildRenderItem(it, this.sourceWidth, this.sourceHeight) }
        buildOverlapGroups()
        visibility = if (renderItems.isEmpty()) View.GONE else View.VISIBLE
        invalidate()
    }

    fun clearTranslations() {
        recyclePatches(renderItems)
        renderItems = emptyList(); groupColors = emptyList(); itemGroups = emptyList()
        visibility = View.GONE; invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (renderItems.isEmpty()) return
        val scaleX = width.toFloat() / sourceWidth.toFloat()
        val scaleY = height.toFloat() / sourceHeight.toFloat()
        val coordinateOffsetY = if (toleranceRatio > 1.5f) klipStatusBarOffsetPx() else 0f

        renderItems.forEachIndexed { index, renderItem ->
            val left = renderItem.left * scaleX
            val top = renderItem.top * scaleY + coordinateOffsetY
            val right = renderItem.right * scaleX
            val bottom = renderItem.bottom * scaleY + coordinateOffsetY
            val fillRight = renderItem.fillRight * scaleX
            if (right <= left || bottom <= top || fillRight <= left) return@forEachIndexed
            val box = RectF(left, top, right, bottom)
            val fillBox = RectF(left, top, fillRight.coerceAtMost(right), bottom)
            val radius = ((bottom - top) * 0.10f).coerceIn(5f, 12f)
            val groupId = itemGroups.getOrElse(index) { index }
            val effectiveColor = groupColors.getOrElse(groupId) { darkenColor(renderItem.item.backgroundColor) }

            canvas.save()
            canvas.clipRect(box)
            val patch = renderItem.item.blurredPatch
            if (patch != null && !patch.isRecycled) {
                canvas.drawBitmap(patch, null, fillBox, bitmapPaint)
                glassPaint.color = Color.argb(92, Color.red(effectiveColor), Color.green(effectiveColor), Color.blue(effectiveColor))
                canvas.drawRoundRect(fillBox, radius, radius, glassPaint)
                glassPaint.color = Color.argb(38, 255, 255, 255)
                canvas.drawRoundRect(fillBox, radius, radius, glassPaint)
            } else {
                drawReconstructedBlur(canvas, fillBox, effectiveColor, radius)
            }
            borderPaint.color = Color.argb(145, 255, 255, 255)
            borderPaint.strokeWidth = 1.2f.coerceAtLeast(width / 1080f)
            canvas.drawRoundRect(fillBox, radius, radius, borderPaint)
            canvas.restore()
        }

        renderItems.forEachIndexed { index, renderItem ->
            val left = renderItem.left * scaleX
            val top = renderItem.top * scaleY + coordinateOffsetY
            val right = renderItem.right * scaleX
            val bottom = renderItem.bottom * scaleY + coordinateOffsetY
            if (right <= left || bottom <= top) return@forEachIndexed
            val groupId = itemGroups.getOrElse(index) { index }
            val effectiveColor = groupColors.getOrElse(groupId) { darkenColor(renderItem.item.backgroundColor) }
            textPaint.textScaleX = 1f
            textPaint.textSize = renderItem.textSize * scaleY
            textPaint.color = chooseTextColor(effectiveColor)
            textPaint.alpha = 255
            val padding = renderItem.horizontalPadding * scaleX
            val lineHeight = renderItem.lineSpacing * scaleY
            val totalTextHeight = lineHeight * renderItem.lines.size
            val startTop = top + ((bottom - top - totalTextHeight) / 2f).coerceAtLeast(0f)
            val metrics = textPaint.fontMetrics
            val firstBaseline = startTop - metrics.ascent
            renderItem.lines.forEachIndexed { lineIndex, line -> canvas.drawText(line, left + padding, firstBaseline + lineIndex * lineHeight, textPaint) }
        }
        textPaint.textScaleX = 1f; textPaint.color = Color.WHITE; textPaint.alpha = 255
    }

    private fun klipStatusBarOffsetPx(): Float {
        val inset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) rootWindowInsets?.getInsets(WindowInsets.Type.statusBars())?.top ?: 0 else {
            @Suppress("DEPRECATION") rootWindowInsets?.systemWindowInsetTop ?: 0
        }
        return inset.toFloat().coerceAtLeast(0f)
    }

    private fun buildOverlapGroups() {
        if (renderItems.isEmpty()) { itemGroups = emptyList(); groupColors = emptyList(); return }
        val parent = IntArray(renderItems.size) { it }
        fun find(value: Int): Int { var x = value; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
        fun union(a: Int, b: Int) { val rootA = find(a); val rootB = find(b); if (rootA != rootB) parent[rootB] = rootA }
        for (i in renderItems.indices) {
            val a = renderItems[i].boxRect()
            for (j in i + 1 until renderItems.size) if (RectF.intersects(a, renderItems[j].boxRect())) union(i, j)
        }
        val rootToGroup = linkedMapOf<Int, Int>(); val groups = IntArray(renderItems.size)
        renderItems.indices.forEach { index -> val root = find(index); groups[index] = rootToGroup.getOrPut(root) { rootToGroup.size } }
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
            Color.rgb((sum[0] / count).toInt().coerceIn(0, 255), (sum[1] / count).toInt().coerceIn(0, 255), (sum[2] / count).toInt().coerceIn(0, 255))
        }
    }

    private fun drawReconstructedBlur(canvas: Canvas, box: RectF, baseColor: Int, radius: Float) {
        val edge = adjustColor(baseColor, 0.94f); val deep = adjustColor(baseColor, 0.88f)
        backgroundPaint.alpha = 255
        backgroundPaint.shader = LinearGradient(box.left, box.top, box.right.coerceAtLeast(box.left + 1f), box.bottom, intArrayOf(edge, baseColor, deep, baseColor, deep), floatArrayOf(0f, 0.28f, 0.50f, 0.72f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(box, radius, radius, backgroundPaint); backgroundPaint.shader = null
    }

    private fun darkenColor(color: Int): Int {
        val luminance = 0.2126f * Color.red(color) + 0.7152f * Color.green(color) + 0.0722f * Color.blue(color)
        if (luminance < 45f) return color
        return adjustColor(color, 0.62f)
    }

    private fun chooseTextColor(effectiveColor: Int): Int {
        val luminance = 0.2126f * Color.red(effectiveColor) + 0.7152f * Color.green(effectiveColor) + 0.0722f * Color.blue(effectiveColor)
        return if (luminance < 150f) Color.WHITE else Color.BLACK
    }

    private fun adjustColor(color: Int, factor: Float): Int = Color.rgb((Color.red(color) * factor).roundToIntSafe(), (Color.green(color) * factor).roundToIntSafe(), (Color.blue(color) * factor).roundToIntSafe())
    private fun Float.roundToIntSafe(): Int = roundToInt().coerceIn(0, 255)

    private fun buildRenderItem(item: TranslationOverlayItem, width: Int, height: Int): RenderItem? {
        val baseLeft = item.left.coerceIn(0, width - 1).toFloat(); val baseTop = item.top.coerceIn(0, height - 1).toFloat()
        val baseRight = item.right.coerceIn(baseLeft.toInt() + 1, width).toFloat(); val baseBottom = item.bottom.coerceIn(baseTop.toInt() + 1, height).toFloat()
        if (baseRight <= baseLeft || baseBottom <= baseTop) return null
        val originalWidth = baseRight - baseLeft; val originalHeight = baseBottom - baseTop
        val baseWidth = (originalWidth * toleranceRatio).coerceAtLeast(originalWidth); val boxHeight = (originalHeight * toleranceRatio).coerceAtLeast(originalHeight)
        val toleranceLeft = baseLeft
        val toleranceTop = (baseTop - (boxHeight - originalHeight) / 2f).coerceAtLeast(0f)
        val toleranceRight = (baseLeft + baseWidth).coerceAtMost(width.toFloat())
        val toleranceBottom = (toleranceTop + boxHeight).coerceAtMost(height.toFloat())
        val horizontalPadding = (boxHeight * horizontalPaddingRatio).coerceIn(3f, 16f); val verticalPadding = (boxHeight * verticalPaddingRatio).coerceIn(2f, 8f)
        val baseTextSize = (if (item.sourceTextSizePx > 0f) item.sourceTextSizePx else originalHeight * 0.72f).coerceIn(minTextSizePx, maxTextSizePx)
        val normalized = normalizeParagraph(item.translatedText); if (normalized.isEmpty()) return null
        textPaint.textScaleX = 1f; textPaint.textSize = baseTextSize
        val measuredWidth = normalized.split('\n').maxOfOrNull { textPaint.measureText(it) } ?: 0f
        val allowedWidth = if (toleranceRatio > 1f) baseWidth else originalWidth * maxWidthRatio
        val maxBoxWidth = minOf(allowedWidth, (width - toleranceLeft - 4f).coerceAtLeast(originalWidth))
        val desiredWidth = (measuredWidth + horizontalPadding * 2f).coerceAtLeast(originalWidth)
        val boxWidth = desiredWidth.coerceAtMost(maxBoxWidth)
        val left = toleranceLeft; val right = (left + boxWidth).coerceAtMost(toleranceRight.coerceAtLeast(left + 1f)); val maxTextWidth = (right - left - horizontalPadding * 2f).coerceAtLeast(1f)
        var finalTextSize = baseTextSize; var lines = wrapText(normalized, maxTextWidth, finalTextSize); val lineSpacing = finalTextSize * 1.16f
        val availableHeight = (boxHeight - verticalPadding * 2f).coerceAtLeast(1f); val totalHeight = lineSpacing * lines.size
        if (totalHeight > availableHeight && totalHeight > 0f) {
            val fitScale = (availableHeight / totalHeight).coerceAtLeast(minFontScale)
            finalTextSize = (finalTextSize * fitScale).coerceIn(minTextSizePx, baseTextSize)
            lines = wrapText(normalized, maxTextWidth, finalTextSize)
        }
        textPaint.textSize = finalTextSize; val finalLineSpacing = finalTextSize * 1.16f; lines = wrapText(normalized, maxTextWidth, finalTextSize)
        val finalMeasuredWidth = lines.maxOfOrNull { textPaint.measureText(it) } ?: 0f; val fillWidth = (finalMeasuredWidth + horizontalPadding * 2f).coerceAtLeast(originalWidth).coerceAtMost(right - left)
        return RenderItem(item, left, toleranceTop, right, toleranceBottom, left + fillWidth, finalTextSize, horizontalPadding, lines, finalLineSpacing)
    }

    private fun normalizeParagraph(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n').split('\n').joinToString("\n") { it.replace(Regex("[ \\t]+"), " ").trim() }.trim()

    private fun wrapText(text: String, maxWidth: Float, textSize: Float): List<String> {
        textPaint.textSize = textSize
        val result = mutableListOf<String>()
        for (paragraphLine in text.split('\n')) {
            if (paragraphLine.isEmpty()) { result.add(""); continue }
            var current = ""
            for (word in paragraphLine.split(' ')) {
                if (word.isEmpty()) continue
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (current.isEmpty() || textPaint.measureText(candidate) <= maxWidth) current = candidate else { result.add(current); current = word }
            }
            if (current.isNotEmpty()) result.add(current)
        }
        return if (result.isEmpty()) listOf("") else result
    }

    private fun recyclePatches(items: List<RenderItem>) {
        items.forEach { item -> item.item.blurredPatch?.let { if (!it.isRecycled) it.recycle() } }
    }

    override fun onDetachedFromWindow() {
        recyclePatches(renderItems)
        renderItems = emptyList(); groupColors = emptyList(); itemGroups = emptyList(); super.onDetachedFromWindow()
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
    var blurredPatch: Bitmap? = null
)