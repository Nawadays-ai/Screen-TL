package com.example.screentranslator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.WindowInsets
import kotlin.math.roundToInt

/** Renders translated OCR blocks while keeping screenshot coordinates 1:1. */
class TranslationOverlayView(context: Context) : View(context) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; alpha = 255 }
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

    /**
     * How far a panel may grow beyond the control it covers.
     *
     * Indonesian text is routinely longer than the Japanese it replaces, so a panel that cannot
     * grow would have to shrink its font on most screens. 1.6x is enough headroom for a long line
     * or two while still leaving most of the screen untouched.
     */
    private val maxWidthRatio = 1.6f
    private val bubbleWidthRatio = 2.80f

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
        this.sourceWidth = sourceWidth.coerceAtLeast(1)
        this.sourceHeight = sourceHeight.coerceAtLeast(1)
        this.toleranceRatio = toleranceRatio.coerceIn(1f, 3f)
        renderItems = translations.mapNotNull { buildRenderItem(it, this.sourceWidth, this.sourceHeight) }
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
        val coordinateOffsetY = if (toleranceRatio > 1.5f) klipStatusBarOffsetPx() else 0f

        renderItems.forEachIndexed { index, renderItem ->
            val left = renderItem.left * scaleX
            val top = renderItem.top * scaleY + coordinateOffsetY
            val right = renderItem.right * scaleX
            val bottom = renderItem.bottom * scaleY + coordinateOffsetY
            if (right <= left || bottom <= top) return@forEachIndexed
            val box = RectF(left, top, right, bottom)
            val radius = ((bottom - top) * 0.12f).coerceIn(2f, 7f)
            drawPanel(canvas, box, renderItem.item.backgroundColor, radius)
        }

        renderItems.forEachIndexed { _, renderItem ->
            val left = renderItem.left * scaleX
            val top = renderItem.top * scaleY + coordinateOffsetY
            val right = renderItem.right * scaleX
            val bottom = renderItem.bottom * scaleY + coordinateOffsetY
            if (right <= left || bottom <= top) return@forEachIndexed
            textPaint.textScaleX = 1f
            textPaint.textSize = renderItem.textSize * scaleY
            textPaint.color = chooseTextColor(renderItem.item.backgroundColor)
            textPaint.alpha = 255
            val padding = renderItem.horizontalPadding * scaleX
            val lineHeight = renderItem.lineSpacing * scaleY
            val totalTextHeight = lineHeight * renderItem.lines.size
            val startTop = top + ((bottom - top - totalTextHeight) / 2f).coerceAtLeast(0f)
            val metrics = textPaint.fontMetrics
            val firstBaseline = startTop - metrics.ascent
            renderItem.lines.forEachIndexed { lineIndex, line ->
                val lineWidth = textPaint.measureText(line)
                val lineLeft = left + ((right - left - lineWidth) / 2f).coerceAtLeast(padding)
                canvas.drawText(line, lineLeft, firstBaseline + lineIndex * lineHeight, textPaint)
            }
        }
        textPaint.textScaleX = 1f; textPaint.color = Color.WHITE; textPaint.alpha = 255
    }

    private fun klipStatusBarOffsetPx(): Float {
        val inset = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) rootWindowInsets?.getInsets(WindowInsets.Type.statusBars())?.top ?: 0 else {
            @Suppress("DEPRECATION") rootWindowInsets?.systemWindowInsetTop ?: 0
        }
        return inset.toFloat().coerceAtLeast(0f)
    }

    private fun buildOverlapGroups() {
        // Each box now carries its own sampled control colour, so there is nothing to merge.
        itemGroups = emptyList()
        groupColors = emptyList()
    }

    private fun chooseTextColor(backgroundColor: Int): Int {
        val luminance = 0.2126f * Color.red(backgroundColor) + 0.7152f * Color.green(backgroundColor) + 0.0722f * Color.blue(backgroundColor)
        return if (luminance < 150f) Color.WHITE else Color.BLACK
    }

    /**
     * Draws one solid panel that takes on the sampled colour of the control it covers.
     *
     * The previous version layered a four-stop gradient, a white wash and a black wash to fake
     * frosted glass. On top of a game screen that read as a translucent blob rather than as part
     * of the interface, and the stacked washes also washed out the text. One solid fill keeps the
     * control's own colour, so the overlay reads as that control showing its translation.
     *
     * The border is only drawn when the panel would otherwise blend into what surrounds it.
     */
    private fun drawPanel(canvas: Canvas, box: RectF, baseColor: Int, radius: Float) {
        val luminance = 0.2126f * Color.red(baseColor) + 0.7152f * Color.green(baseColor) + 0.0722f * Color.blue(baseColor)
        // Light controls need a slightly deeper fill so white text stays legible; dark ones stay
        // close to the original so the control does not turn into a black hole.
        val fill = if (luminance > 160f) adjustColor(baseColor, 0.62f) else adjustColor(baseColor, 0.86f)

        backgroundPaint.shader = null
        backgroundPaint.alpha = 255
        backgroundPaint.color = fill
        canvas.drawRoundRect(box, radius, radius, backgroundPaint)

        // Only a faint edge on a light panel, where the fill could otherwise disappear into a pale
        // control. Dark panels keep the control's own edge and need nothing added.
        if (luminance > 160f) {
            borderPaint.color = Color.argb(38, 0, 0, 0)
            borderPaint.strokeWidth = (width / 1080f).coerceAtLeast(1f)
            canvas.drawRoundRect(box, radius, radius, borderPaint)
        }
    }

    private fun adjustColor(color: Int, factor: Float): Int = Color.rgb((Color.red(color) * factor).roundToIntSafe(), (Color.green(color) * factor).roundToIntSafe(), (Color.blue(color) * factor).roundToIntSafe())
    private fun Float.roundToIntSafe(): Int = roundToInt().coerceIn(0, 255)

    private fun buildRenderItem(item: TranslationOverlayItem, width: Int, height: Int): RenderItem? {
        val baseLeft = item.left.coerceIn(0, width - 1).toFloat(); val baseTop = item.top.coerceIn(0, height - 1).toFloat()
        val baseRight = item.right.coerceIn(baseLeft.toInt() + 1, width).toFloat(); val baseBottom = item.bottom.coerceIn(baseTop.toInt() + 1, height).toFloat()
        if (baseRight <= baseLeft || baseBottom <= baseTop) return null
        val originalWidth = baseRight - baseLeft; val originalHeight = baseBottom - baseTop
        val isBubble = item.orientation == TextLayoutAnalyzer.WritingOrientation.VERTICAL
        val boxHeight = (originalHeight * toleranceRatio).coerceAtLeast(originalHeight)
        val toleranceTop = (baseTop - (boxHeight - originalHeight) / 2f).coerceAtLeast(0f)
        val toleranceBottom = (toleranceTop + boxHeight).coerceAtMost(height.toFloat())
        val horizontalPadding = (boxHeight * if (isBubble) 0.08f else horizontalPaddingRatio).coerceIn(3f, if (isBubble) 22f else 16f)
        val verticalPadding = (boxHeight * verticalPaddingRatio).coerceIn(2f, 8f)
        val baseTextSize = (if (item.sourceTextSizePx > 0f) item.sourceTextSizePx else originalHeight * 0.72f).coerceIn(minTextSizePx, maxTextSizePx)
        val normalized = normalizeParagraph(item.translatedText); if (normalized.isEmpty()) return null

        textPaint.textScaleX = 1f
        textPaint.textSize = baseTextSize
        val measuredWidth = normalized.split('\n').maxOfOrNull { textPaint.measureText(it) } ?: 0f
        val availableScreenWidth = (width - 8f).coerceAtLeast(originalWidth)

        val minAllowedWidth = horizontalPadding * 2f + minTextSizePx
        val originalTextWidth = (originalWidth - horizontalPadding * 2f).coerceAtLeast(1f)

        // Grow only to the right, anchored on the control's left edge, so the panel reads as that
        // control revealing more room rather than as a new box hovering over the screen. A
        // vertical bubble keeps its own width rule: it has to fit the column it was carved from.
        var boxLeft = baseLeft
        var boxRight: Float
        if (isBubble) {
            val bubbleWidth = maxOf(originalWidth * bubbleWidthRatio, measuredWidth + horizontalPadding * 2f)
                .coerceAtMost(availableScreenWidth)
            boxRight = (baseLeft + bubbleWidth).coerceAtMost(width.toFloat())
            if (boxRight - boxLeft < originalWidth) boxLeft = (boxRight - originalWidth).coerceAtLeast(0f)
        } else {
            val maxWidth = (originalWidth * maxWidthRatio).coerceAtMost(availableScreenWidth)
            val wanted = (measuredWidth + horizontalPadding * 2f).coerceIn(originalWidth, maxWidth)
            boxRight = (baseLeft + wanted).coerceAtMost(width.toFloat())
        }
        boxRight = boxRight.coerceAtLeast(boxLeft + originalWidth)

        var boxWidth = boxRight - boxLeft
        var maxTextWidth = (boxWidth - horizontalPadding * 2f).coerceAtLeast(1f)

        // If growing would land on top of a neighbouring control, give the room back and let the
        // font shrink instead. The original control keeps its shape, which is the whole point.
        if (boxWidth > originalWidth && collidesWithOtherBox(boxLeft, toleranceTop, boxRight, toleranceBottom)) {
            boxWidth = originalWidth
            boxRight = boxLeft + originalWidth
            maxTextWidth = originalTextWidth
        }

        var finalTextSize = baseTextSize
        var lines = wrapText(normalized, maxTextWidth, finalTextSize)
        val availableHeight = (boxHeight - verticalPadding * 2f).coerceAtLeast(1f)
        var lineSpacing = finalTextSize * 1.16f
        var totalHeight = lineSpacing * lines.size
        if (totalHeight > availableHeight && totalHeight > 0f) {
            val fitScale = (availableHeight / totalHeight).coerceAtLeast(minFontScale)
            finalTextSize = (finalTextSize * fitScale).coerceIn(minTextSizePx, baseTextSize)
            lines = wrapText(normalized, maxTextWidth, finalTextSize)
        }
        textPaint.textSize = finalTextSize
        lineSpacing = finalTextSize * 1.16f
        lines = wrapText(normalized, maxTextWidth, finalTextSize)
        return RenderItem(item, boxLeft, toleranceTop, boxRight, toleranceBottom, boxRight, finalTextSize, horizontalPadding, lines, lineSpacing)
    }

    /**
     * Reports whether [probe] would overlap any other unit's box.
     *
     * Overlap groups are not used for this: only pairs whose intersection is thin are treated as a
     * collision, so a long paragraph and the line directly beneath it do not cancel each other's
     * growth.
     */
    private fun collidesWithOtherBox(probeLeft: Float, probeTop: Float, probeRight: Float, probeBottom: Float): Boolean {
        val probe = RectF(probeLeft, probeTop, probeRight, probeBottom)
        return renderItems.any { other ->
            if (other.item.left == probeLeft.toInt() && other.item.top == probeTop.toInt()) return@any false
            val rect = other.boxRect()
            if (!RectF.intersects(probe, rect)) return@any false
            val overlapX = minOf(probe.right, rect.right) - maxOf(probe.left, rect.left)
            val overlapY = minOf(probe.bottom, rect.bottom) - maxOf(probe.top, rect.top)
            overlapX > 2f && overlapY > 2f
        }
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
    val orientation: TextLayoutAnalyzer.WritingOrientation = TextLayoutAnalyzer.WritingOrientation.HORIZONTAL
)
