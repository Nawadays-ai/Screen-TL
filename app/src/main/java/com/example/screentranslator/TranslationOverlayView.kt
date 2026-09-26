package com.example.screentranslator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.WindowInsets
import kotlin.math.abs
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
    private val lineSpacingRatio = 1.16f

    /**
     * How far a panel may grow vertically before the font has to shrink instead.
     *
     * Indonesian is routinely longer than the Japanese or Chinese it replaces, so a translation
     * usually wraps to more lines than the control it sits on. The panel therefore has to get
     * taller, not just wider: an opaque patch is the only thing standing between the translated
     * line and the game's own glyphs underneath it. 1.6x is enough headroom for the common case;
     * past that the font shrinks, which is cheaper than covering a third of the screen.
     */
    private val maxHeightRatio = 1.6f

    /** A vertical bubble is a tall control already, so it gets a tighter vertical ceiling. */
    private val bubbleHeightRatio = 1.25f

    /**
     * How far a panel may grow beyond the control it covers.
     *
     * Indonesian text is routinely longer than the Japanese it replaces, so a panel that cannot
     * grow would have to shrink its font on most screens. 1.6x is enough headroom for a long line
     * or two while still leaving most of the screen untouched.
     */
    private val maxWidthRatio = 1.6f
    private val bubbleWidthRatio = 2.80f

    /**
     * Tahap 1 — glyph style thresholds.
     *
     * The sampled fill is only trusted when it separates from the panel colour by this much
     * luminance; below it the text was too low-contrast against its own background for the
     * sampling to mean anything. The stroke is only drawn when it contrasts the fill clearly
     * enough to read as an outline instead of a fat anti-aliasing rim.
     */
    private val minFillSeparation = 60f
    private val minStrokeContrast = 40f
    private val strokeWidthRatio = 0.045f
    private val shadowRadiusRatio = 0.03f
    private val shadowOffsetRatio = 0.018f
    private val shadowColor = Color.argb(102, 0, 0, 0)

    /**
     * Floor for the outline so translated text never loses its edge on a busy background.
     *
     * The sampled fill alone is not enough: it is trusted against the panel colour, and when it
     * sits close to it the glyphs read as a smudge. A thin outline in whichever of black/white is
     * further from the fill is invisible when it is wrong, and it is what keeps white text on a
     * white panel or dark text on a dark panel legible.
     */
    private val minOutlineWidthRatio = 0.025f

    private data class RenderItem(
        val item: TranslationOverlayItem,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val textSize: Float,
        val horizontalPadding: Float,
        val lines: List<String>,
        val lineSpacing: Float
    ) { fun boxRect() = RectF(left, top, right, bottom) }
    private var renderItems: List<RenderItem> = emptyList()
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
        // Cleared first so collidesWithOtherBox does not measure new boxes against the geometry
        // of the previous frame. Previously the first frame after a capture had no collision
        // detection at all, and later frames compared against stale positions.
        renderItems = emptyList()
        // Two passes. A screen full of paragraphs is laid out twice: once to find out how much the
        // text actually needs, and once with a single font scale applied to every item.
        //
        // Sizing each item on its own produces a zig-zag instead of a page: every paragraph wraps
        // differently, so each one independently decides how far to shrink, and the screen ends up
        // a mix of large and tiny text. One shared scale is what makes a long paragraph force every
        // other paragraph down by the same amount rather than the one below it absorbing the cost.
        val measured = translations.mapNotNull { measureItem(it, this.sourceWidth, this.sourceHeight) }
        val uniformScale = uniformScaleFor(measured)
        renderItems = measured.mapNotNull { buildRenderItem(it, this.sourceWidth, this.sourceHeight, uniformScale) }
        visibility = if (renderItems.isEmpty()) View.GONE else View.VISIBLE
        invalidate()
    }

    /**
     * The single font scale every item on screen is rendered at.
     *
     * Each item reports the shrink it would need on its own. The tightest of those wins, so
     * nothing has to be clipped, and that factor is then applied to all of them: a paragraph that
     * fits keeps its size while a crowded one shrinks, and the two stay in proportion. A long
     * translation is allowed to run past its own panel here on purpose — the alternative is one
     * paragraph silently starving every paragraph under it.
     */
    private fun uniformScaleFor(measured: List<MeasuredItem>): Float {
        var worst = 1f
        for (item in measured) {
            val needed = item.requiredHeight
            if (needed > item.availableHeight && needed > 0f) {
                val scale = (item.availableHeight / needed).coerceIn(minFontScale, 1f)
                if (scale < worst) worst = scale
            }
        }
        return worst
    }

    fun clearTranslations() {
        renderItems = emptyList()
        visibility = View.GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (renderItems.isEmpty()) return
        val scaleX = width.toFloat() / sourceWidth.toFloat()
        val scaleY = height.toFloat() / sourceHeight.toFloat()
        val coordinateOffsetY = if (toleranceRatio > 1.5f) klipStatusBarOffsetPx() else 0f

        renderItems.forEachIndexed { _, renderItem ->
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
            // Keep every line inside the patch. A glyph that escapes the panel is drawn straight
            // over the game's own text, which is the one thing the panel is there to prevent, so
            // the clip is a backstop for the case the font cannot be shrunk far enough.
            canvas.save()
            canvas.clipRect(left, top, right, bottom)
            textPaint.textScaleX = 1f
            // Size is set before measuring: measureText and fontMetrics below both depend on it,
            // and drawStyledLine re-applies the same size for its own passes.
            textPaint.textSize = renderItem.textSize * scaleY
            val padding = renderItem.horizontalPadding * scaleX
            val lineHeight = renderItem.lineSpacing * scaleY
            val totalTextHeight = lineHeight * renderItem.lines.size
            val startTop = top + ((bottom - top - totalTextHeight) / 2f).coerceAtLeast(0f)
            val metrics = textPaint.fontMetrics
            val firstBaseline = startTop - metrics.ascent
            renderItem.lines.forEachIndexed { lineIndex, line ->
                val lineWidth = textPaint.measureText(line)
                val lineLeft = when (renderItem.item.alignment) {
                    TextLayoutAnalyzer.TextAlignment.LEFT -> left + padding
                    TextLayoutAnalyzer.TextAlignment.RIGHT -> (right - padding - lineWidth).coerceAtLeast(left + padding)
                    TextLayoutAnalyzer.TextAlignment.CENTER -> left + ((right - left - lineWidth) / 2f).coerceAtLeast(padding)
                }
                drawStyledLine(canvas, line, lineLeft, firstBaseline + lineIndex * lineHeight, renderItem.item, renderItem.textSize * scaleY)
            }
            canvas.restore()
        }
        textPaint.textScaleX = 1f; textPaint.color = Color.WHITE; textPaint.alpha = 255
        textPaint.style = Paint.Style.FILL
        textPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
    }

    private fun klipStatusBarOffsetPx(): Float {
        val inset = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) rootWindowInsets?.getInsets(WindowInsets.Type.statusBars())?.top ?: 0 else {
            @Suppress("DEPRECATION") rootWindowInsets?.systemWindowInsetTop ?: 0
        }
        return inset.toFloat().coerceAtLeast(0f)
    }

    private fun chooseTextColor(backgroundColor: Int): Int {
        val luminance = 0.2126f * Color.red(backgroundColor) + 0.7152f * Color.green(backgroundColor) + 0.0722f * Color.blue(backgroundColor)
        return if (luminance < 150f) Color.WHITE else Color.BLACK
    }

    private fun luminanceOf(color: Int): Float =
        0.2126f * Color.red(color) + 0.7152f * Color.green(color) + 0.0722f * Color.blue(color)

    /**
     * Draws one line of translated text in the style sampled from the game's own text.
     *
     * The outline is drawn as a separate pass underneath the fill rather than through
     * [Paint.Style.FILL_AND_STROKE]. A Paint carries one colour for both passes, so
     * FILL_AND_STROKE can only ever outline a glyph in its own fill colour, which is not the
     * white-fill/black-outline recipe game UI actually uses. Drawing the line twice costs one
     * extra drawText per line and gives the outline its own colour.
     *
     * When no style was sampled, or the sampled fill does not separate from the panel colour
     * enough ([minFillSeparation]), the classic white/black fill is used instead with a
     * contrasting outline, which stays readable on any panel. [textSize] is the rendered size, so
     * stroke and shadow shrink together with a font that had to fit its box.
     */
    private fun drawStyledLine(canvas: Canvas, line: String, x: Float, baseline: Float, item: TranslationOverlayItem, textSize: Float) {
        // Contrast is judged against the colour the panel is actually painted, not against the
        // colour that was sampled off the screen. drawPanel darkens a light control before filling
        // it, so a fill chosen against the original background can end up sitting almost on top of
        // the panel it is drawn on — which is how "the translation is hard to read" happens.
        val panelColor = paintedPanelColor(item.backgroundColor)
        val style = item.glyphStyle?.takeIf { abs(luminanceOf(it.fill) - luminanceOf(panelColor)) >= minFillSeparation }
        val fill = style?.fill ?: chooseTextColor(panelColor)
        val fillLuminance = luminanceOf(fill)

        // The outline colour is always the one furthest from the fill. Black or white is a safe
        // pick because whichever is wrong is the colour of the glyph's own interior, and a stroke
        // drawn in the fill's own colour reads as a slightly bolder letter rather than an outline.
        val contrastStroke = if (fillLuminance >= 128f) Color.BLACK else Color.WHITE
        val strokeColor: Int
        val drawStroke: Boolean
        if (style != null && style.hasStroke && abs(luminanceOf(style.stroke) - fillLuminance) >= minStrokeContrast) {
            strokeColor = style.stroke
            drawStroke = true
        } else {
            strokeColor = contrastStroke
            drawStroke = true
        }

        textPaint.textSize = textSize
        textPaint.strokeJoin = Paint.Join.ROUND
        textPaint.strokeWidth = (textSize * if (drawStroke) strokeWidthRatio else minOutlineWidthRatio)
            .coerceAtLeast(1f)
        textPaint.setShadowLayer(textSize * shadowRadiusRatio, textSize * shadowOffsetRatio, textSize * shadowOffsetRatio, shadowColor)

        // The outline goes down first so the fill covers its inner half; a stroke drawn after the
        // fill would eat into the letter and make it look bolder than the game's own text.
        textPaint.style = Paint.Style.STROKE
        textPaint.color = strokeColor
        textPaint.alpha = 255
        canvas.drawText(line, x, baseline, textPaint)

        textPaint.style = Paint.Style.FILL
        textPaint.color = fill
        textPaint.alpha = 255
        canvas.drawText(line, x, baseline, textPaint)
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
        val luminance = luminanceOf(baseColor)
        canvas.drawRoundRect(box, radius, radius, panelPaint(baseColor, luminance))

        // Only a faint edge on a light panel, where the fill could otherwise disappear into a pale
        // control. Dark panels keep the control's own edge and need nothing added.
        if (luminance > 160f) {
            borderPaint.color = Color.argb(38, 0, 0, 0)
            borderPaint.strokeWidth = (width / 1080f).coerceAtLeast(1f)
            canvas.drawRoundRect(box, radius, radius, borderPaint)
        }
    }

    /**
     * The colour the panel is actually filled with for a given sampled background.
     *
     * Kept in one place because the text has to make the same decision: a fill colour chosen for
     * the game's own light control turns unreadable once the panel is darkened underneath it, so
     * both sides have to agree on the painted result rather than each re-deriving it.
     */
    private fun paintedPanelColor(baseColor: Int): Int =
        if (luminanceOf(baseColor) > 160f) adjustColor(baseColor, 0.62f) else adjustColor(baseColor, 0.86f)

    private fun panelPaint(baseColor: Int, luminance: Float): Paint {
        backgroundPaint.shader = null
        backgroundPaint.alpha = 255
        // Light controls need a slightly deeper fill so white text stays legible; dark ones stay
        // close to the original so the control does not turn into a black hole.
        backgroundPaint.color = paintedPanelColor(baseColor)
        return backgroundPaint
    }

    private fun adjustColor(color: Int, factor: Float): Int = Color.rgb((Color.red(color) * factor).roundToIntSafe(), (Color.green(color) * factor).roundToIntSafe(), (Color.blue(color) * factor).roundToIntSafe())
    private fun Float.roundToIntSafe(): Int = roundToInt().coerceIn(0, 255)

    /** Everything pass 1 can learn about an item before a font scale is chosen for the screen. */
    private data class MeasuredItem(
        val item: TranslationOverlayItem,
        val baseLeft: Float,
        val baseTop: Float,
        val boxLeft: Float,
        val boxRight: Float,
        val originalWidth: Float,
        val originalBoxHeight: Float,
        val horizontalPadding: Float,
        val baseTextSize: Float,
        val maxTextWidth: Float,
        val originalBoxTop: Float,
        val verticalPadding: Float,
        val maxPanelHeight: Float,
        val availableHeight: Float,
        val requiredHeight: Float
    )

    /**
     * First pass: lay the item out at its own size and report how much room the text wants.
     *
     * Only the measurements matter here. The font is not chosen yet, because choosing it per item
     * is what makes a screen of paragraphs look ragged — [uniformScaleFor] compares these
     * measurements across every item and settles on one size for all of them.
     */
    private fun measureItem(item: TranslationOverlayItem, width: Int, height: Int): MeasuredItem? {
        val baseLeft = item.left.coerceIn(0, width - 1).toFloat(); val baseTop = item.top.coerceIn(0, height - 1).toFloat()
        val baseRight = item.right.coerceIn(baseLeft.toInt() + 1, width).toFloat(); val baseBottom = item.bottom.coerceIn(baseTop.toInt() + 1, height).toFloat()
        if (baseRight <= baseLeft || baseBottom <= baseTop) return null
        val originalWidth = baseRight - baseLeft; val originalHeight = baseBottom - baseTop
        val isBubble = item.orientation == TextLayoutAnalyzer.WritingOrientation.VERTICAL
        val originalBoxHeight = originalHeight * toleranceRatio
        val horizontalPadding = (originalBoxHeight * if (isBubble) 0.08f else horizontalPaddingRatio).coerceIn(3f, if (isBubble) 22f else 16f)
        val baseTextSize = (if (item.sourceTextSizePx > 0f) item.sourceTextSizePx else originalHeight * 0.72f).coerceIn(minTextSizePx, maxTextSizePx)
        val normalized = normalizeParagraph(item.translatedText); if (normalized.isEmpty()) return null

        textPaint.textScaleX = 1f
        textPaint.textSize = baseTextSize
        val measuredWidth = normalized.split('\n').maxOfOrNull { textPaint.measureText(it) } ?: 0f
        val availableScreenWidth = (width - 8f).coerceAtLeast(originalWidth)
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

        var maxTextWidth = (boxRight - boxLeft - horizontalPadding * 2f).coerceAtLeast(1f)

        // If growing would land on top of a neighbouring control, give the room back and let the
        // font shrink instead. The original control keeps its shape, which is the whole point.
        val originalBoxTop = (baseTop - (originalBoxHeight - originalHeight) / 2f).coerceAtLeast(0f)
        val originalBoxBottom = (originalBoxTop + originalBoxHeight).coerceAtMost(height.toFloat())
        if (boxRight - boxLeft > originalWidth && collidesWithOtherBox(boxLeft, originalBoxTop, boxRight, originalBoxBottom)) {
            boxRight = boxLeft + originalWidth
            maxTextWidth = originalTextWidth
        }

        val maxPanelHeight = (originalBoxHeight * if (isBubble) bubbleHeightRatio else maxHeightRatio)
            .coerceAtMost(height.toFloat())
        val verticalPadding = (originalBoxHeight * verticalPaddingRatio).coerceIn(2f, 8f)
        val ownLines = wrapText(normalized, maxTextWidth, baseTextSize)
        val required = baseTextSize * lineSpacingRatio * ownLines.size
        // The room the panel can ever give the text: it may grow to the ceiling, but no further.
        val available = (maxPanelHeight - verticalPadding * 2f).coerceAtLeast(1f)
        return MeasuredItem(
            item, baseLeft, baseTop, boxLeft, boxRight, originalWidth, originalBoxHeight,
            horizontalPadding, baseTextSize, maxTextWidth, originalBoxTop, verticalPadding,
            maxPanelHeight, available, required
        )
    }

    private fun buildRenderItem(m: MeasuredItem, width: Int, height: Int, uniformScale: Float): RenderItem? {
        val item = m.item
        val normalized = normalizeParagraph(item.translatedText); if (normalized.isEmpty()) return null
        val finalTextSize = (m.baseTextSize * uniformScale).coerceIn(minTextSizePx, m.baseTextSize)
        val lines = wrapText(normalized, m.maxTextWidth, finalTextSize)
        val needed = finalTextSize * lineSpacingRatio * lines.size + m.verticalPadding * 2f
        val settledHeight = minOf(needed, m.maxPanelHeight)

        // Centre the grown panel on the original control, then shift it back inside the screen.
        // The shift is applied to both edges at once so a panel that cannot fit above its control
        // slides down whole rather than losing its bottom edge to the screen boundary.
        var top = (m.originalBoxTop - (settledHeight - m.originalBoxHeight) / 2f).coerceAtLeast(0f)
        if (top + settledHeight > height.toFloat()) top = (height.toFloat() - settledHeight).coerceAtLeast(0f)
        val lineSpacing = finalTextSize * lineSpacingRatio
        return RenderItem(item, m.boxLeft, top, m.boxRight, top + settledHeight, finalTextSize, m.horizontalPadding, lines, lineSpacing)
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
    val orientation: TextLayoutAnalyzer.WritingOrientation = TextLayoutAnalyzer.WritingOrientation.HORIZONTAL,
    val glyphStyle: TextLayoutAnalyzer.GlyphStyle? = null,
    val alignment: TextLayoutAnalyzer.TextAlignment = TextLayoutAnalyzer.TextAlignment.CENTER
)
