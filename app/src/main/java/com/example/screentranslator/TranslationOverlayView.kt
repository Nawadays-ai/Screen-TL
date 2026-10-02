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
import android.view.WindowInsets
import kotlin.math.roundToInt

/** Renders translated OCR blocks while keeping screenshot coordinates 1:1. */
class TranslationOverlayView(context: Context) : View(context) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; alpha = 255 }
    private val featherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; alpha = 255 }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f }
    private val featherRadiusRatio = 0.025f
    /** How much of the sampled band-to-band luminance difference the gradient actually follows. */
    private val gradientBlend = 0.45f
    /** Hard cap on gradient drift, in luminance, so one panel can never span the full tonal range. */
    private val maxGradientDrift = 22f
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

    /**
     * How far a panel overhangs the control it covers, as a fraction of that control's height.
     *
     * Coverage is the panel's first job: a patch that stops inside the source control leaves the
     * game's own glyphs showing at the edge, which is the one failure that makes the translation
     * unreadable. ML Kit's box hugs the detected glyphs, and a glyph's outline and antialiased
     * rim sit outside that box, so a panel laid out on the box alone leaves a thin band of source
     * text visible along whichever edge the box came up short on. This rim is what closes it.
     *
     * 5% of the control's height lands in the 4-6% band the Tahap 2r notes call for, which is a
     * few pixels on game text and imperceptible against the panel it extends. It is deliberately
     * not larger: the panel has to stay the control's size, and a generous rim would start
     * covering whatever the control sits next to.
     */
    private val coveragePaddingRatio = 0.05f
    private val minCoveragePaddingPx = 2f
    private val maxCoveragePaddingPx = 6f

    /**
     * The width ratio below which a neighbour is a separate control rather than another line.
     *
     * Lines of one paragraph differ in width, so the merge cannot demand equal widths, but the
     * spread within a paragraph is bounded: a line is a line, and even a short final line stays
     * well past half the longest. A box far below that is a different kind of thing — `◆説明`
     * above the body it titles, a short label above a block — and merging it in is what produced
     * one grey slab covering label, body and effect lines at once instead of three controls.
     */
    private val siblingWidthRatio = 0.55f
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
     * How far a panel may slide left to stay on screen, as a fraction of its own width.
     *
     * The panel is anchored on the control's left edge and grows rightwards, so a long
     * translation runs into the edge of the display and is forced onto another line even when the
     * left side of the screen is empty. Sliding recovers that space before wrapping does.
     *
     * 20% is deliberately modest: enough to rescue the common case of a box near the right edge,
     * small enough that a panel never travels across whatever sits to its left.
     */
    private val leftSlideRatio = 0.20f

    /**
     * Tahap 1 — text stroke and shadow ratios.
     *
     * The stroke keeps a glyph legible on a panel that happens to sit close to the text colour,
     * and the shadow lifts the glyph off whatever texture is behind the panel edge. Both scale
     * with the rendered text size so a shrunken font gets a proportionally smaller rim.
     */
    private val strokeWidthRatio = 0.045f
    private val shadowRadiusRatio = 0.03f
    private val shadowOffsetRatio = 0.018f
    private val shadowColor = Color.argb(102, 0, 0, 0)

    private data class RenderItem(
        val item: TranslationOverlayItem,
        val patchSample: TextLayoutAnalyzer.PatchSample?,
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
    private var itemGroups: List<Int> = emptyList()
    private var groupRects: List<RectF> = emptyList()
    private var groupColors: List<Int> = emptyList()
    private var groupPatches: List<TextLayoutAnalyzer.PatchSample?> = emptyList()
    // Repopulated every frame by drawPanel; the text pass reads it back so its contrast decision is
    // made against the gradient stops that were really painted this frame.
    private var paintedStops: Array<IntArray?> = emptyArray()
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
        // Cleared first so nothing measures the new boxes against the previous frame's geometry.
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
        renderItems = measured.mapNotNull { buildRenderItem(it, this.sourceHeight, uniformScale) }
        buildOverlapGroups()
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
        itemGroups = emptyList()
        groupRects = emptyList()
        groupColors = emptyList()
        groupPatches = emptyList()
        paintedStops = emptyArray()
        visibility = View.GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (renderItems.isEmpty()) return
        val scaleX = width.toFloat() / sourceWidth.toFloat()
        val scaleY = height.toFloat() / sourceHeight.toFloat()
        val coordinateOffsetY = if (toleranceRatio > 1.5f) klipStatusBarOffsetPx() else 0f

        // One patch per group, not per box. A column of stacked lines is a single control, so it
        // gets a single background; drawing each line's own rounded rect left visible seams and
        // read as a stack of separate cards. The text pass below still runs per item.
        //
        // Each group's painted gradient stops are kept so the text pass can judge contrast against
        // the colour it actually sits on. A panel is no longer one flat colour, and choosing the
        // text colour from the pre-gradient average would let white text land on the lighter end of
        // a gradient and sink into it.
        val paintedStops = arrayOfNulls<IntArray>(groupRects.size)
        this.paintedStops = paintedStops
        groupRects.forEachIndexed { index, rect ->
            if (index >= groupColors.size) return@forEachIndexed
            val left = rect.left * scaleX
            val top = rect.top * scaleY + coordinateOffsetY
            val right = rect.right * scaleX
            val bottom = rect.bottom * scaleY + coordinateOffsetY
            if (right <= left || bottom <= top) return@forEachIndexed
            val box = RectF(left, top, right, bottom)
            val radius = ((bottom - top) * 0.12f).coerceIn(2f, 7f)
            paintedStops[index] = drawPanel(canvas, box, groupColors[index], radius, groupPatches.getOrNull(index))
        }

        renderItems.forEachIndexed { index, renderItem ->
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
                drawStyledLine(canvas, line, lineLeft, firstBaseline + lineIndex * lineHeight, renderItem.textSize * scaleY, textBackingLuminance(index))
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

    /**
     * Black or white, whichever contrasts more with the *worst* stop of the panel.
     *
     * A hard threshold is not enough once the panel is a gradient: the answer has to hold across the
     * whole range the gradient covers, not just at its midpoint. Black and white are not symmetric —
     * black text needs roughly luminance 80 to be readable, while white text degrades smoothly as
     * the panel lightens — so "which side of 150" is not the same question as "which reads better".
     * Both candidates are scored against the stop that favours them least and the better one wins,
     * which is a strict improvement on thresholding for every input, and identical to it when the
     * panel is flat.
     */
    private fun chooseTextColor(backingLuminance: Float): Int {
        val blackRatio = contrastRatio(0f, backingLuminance)
        val whiteRatio = contrastRatio(255f, backingLuminance)
        return if (whiteRatio >= blackRatio) Color.WHITE else Color.BLACK
    }

    /** WCAG relative-contrast ratio between two luminances. */
    private fun contrastRatio(first: Float, second: Float): Float {
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun luminanceOf(color: Int): Float =
        0.2126f * Color.red(color) + 0.7152f * Color.green(color) + 0.0722f * Color.blue(color)

    /**
     * Draws one line of translated text.
     *
     * The outline is drawn as a separate pass underneath the fill rather than through
     * [Paint.Style.FILL_AND_STROKE]. A Paint carries one colour for both passes, so
     * FILL_AND_STROKE can only ever outline a glyph in its own fill colour, which is not the
     * white-fill/black-outline recipe game UI actually uses. Drawing the line twice costs one
     * extra drawText per line and gives the outline its own colour.
     *
     * Manual overlay text is always solid black or white, filled opaque, with the opposite colour
     * as its outline — a sampled glyph colour is a mid-tone that can sit on top of the replacement
     * patch and read as grey, and the panel underneath is the only contrast that actually matters.
     * [textSize] is the rendered size, so stroke and shadow shrink together with a font that had
     * to fit its box.
     */
    private fun drawStyledLine(canvas: Canvas, line: String, x: Float, baseline: Float, textSize: Float, backingLuminance: Float) {
        val fill = chooseTextColor(backingLuminance)
        val strokeColor = if (fill == Color.BLACK) Color.WHITE else Color.BLACK

        textPaint.textSize = textSize
        textPaint.strokeJoin = Paint.Join.ROUND
        textPaint.strokeWidth = (textSize * strokeWidthRatio).coerceAtLeast(1f)
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
     * of the interface, and the stacked washes also washed out the text. The fill is now derived
     * only from the control's own sampled colour, so the overlay reads as that control showing its
     * translation.
     *
     * A vertical gradient built from the sampled top/bottom bands adds back the vertical colour
     * drift a real control has (a lit top edge, a shadowed bottom one). An opaque base fill goes
     * down before the gradient so coverage never depends on the shader's stops keeping alpha 255,
     * and a flush inner ring carries the same gradient so the panel edge does not hard-step.
     *
     * The border is only drawn when the panel would otherwise blend into what surrounds it.
     */
    private fun drawPanel(canvas: Canvas, box: RectF, paintedColor: Int, radius: Float, patch: TextLayoutAnalyzer.PatchSample?): IntArray {
        val feather = (minOf(box.width(), box.height()) * featherRadiusRatio).coerceIn(2f, 3f)
        val topColor = if (patch != null) gradientEnd(paintedColor, luminanceOf(patch.topColor)) else paintedColor
        val bottomColor = if (patch != null) gradientEnd(paintedColor, luminanceOf(patch.bottomColor)) else paintedColor
        val middleColor = bandAverage(topColor, bottomColor)
        val stops = intArrayOf(topColor, middleColor, bottomColor)
        val positions = floatArrayOf(0f, 0.5f, 1f)

        // An opaque base goes down first. Paint.setAlpha does not force the colours a shader
        // produces to be opaque — the gradient stops own their own alpha — so panel coverage must
        // not depend on every stop being constructed correctly forever. This underlay makes show-
        // through structurally impossible: whatever the gradient does afterwards, it lands on
        // pixels this fill has already made opaque.
        backgroundPaint.shader = null
        backgroundPaint.color = paintedColor
        backgroundPaint.alpha = 255
        canvas.drawRoundRect(box, radius, radius, backgroundPaint)

        backgroundPaint.shader = LinearGradient(box.left, box.top, box.left, box.bottom.coerceAtLeast(box.top + 1f), stops, positions, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(box, radius, radius, backgroundPaint)

        // The ring is inset by half its own stroke width, so the stroke lands exactly flush with
        // the panel edge and no part of it falls on a pixel the panel has not already covered. It
        // carries the same gradient as the fill, otherwise the 4-6px of flat painted colour at the
        // edge would hard-step against the drift it is meant to soften.
        //
        // The ring used to also set a BlurMaskFilter. It is a no-op on the hardware canvas, so all
        // it ever did was carry a hazard: a mask filter is the classic trigger for a software-layer
        // fallback, and a software layer on a full-screen window composites differently. Measured
        // device pixels showed the panel blending with the game at roughly 0.8 opacity while this
        // file contained nothing but alpha 255, which is exactly the signature that hypothesis
        // predicts — so the filter is gone rather than kept-and-hoped.
        featherPaint.style = Paint.Style.STROKE
        featherPaint.strokeWidth = feather * 2f
        featherPaint.alpha = 255
        featherPaint.shader = LinearGradient(box.left, box.top, box.left, box.bottom.coerceAtLeast(box.top + 1f), stops, positions, Shader.TileMode.CLAMP)
        val ring = RectF(box.left + feather, box.top + feather, (box.right - feather).coerceAtLeast(box.left + feather), (box.bottom - feather).coerceAtLeast(box.top + feather))
        canvas.drawRoundRect(ring, (radius - feather).coerceAtLeast(0f), (radius - feather).coerceAtLeast(0f), featherPaint)
        featherPaint.shader = null
        featherPaint.style = Paint.Style.FILL
        backgroundPaint.shader = null

        val luminance = luminanceOf(paintedColor)
        if (luminance > 160f) {
            borderPaint.color = Color.argb(38, 0, 0, 0)
            borderPaint.strokeWidth = (width / 1080f).coerceAtLeast(1f)
            canvas.drawRoundRect(box, radius, radius, borderPaint)
        }
        // The caller needs the colours actually under the text so contrast is judged against them
        // rather than against the flat average the panel started from.
        return intArrayOf(topColor, middleColor, bottomColor)
    }

    /**
     * One gradient end: the painted colour nudged toward a sampled band, but never far.
     *
     * Reproducing a real control's lit-top/shadowed-bottom spread exactly can sweep the panel from
     * near-black to near-white, and no single text colour stays readable across a range that wide —
     * the text would sink into whichever end it was chosen against. The drift is therefore damped
     * and then capped by [maxGradientDrift], keeping the whole gradient inside one narrow band of
     * the painted colour. The panel still looks dimensional; the text can still be one solid
     * black-or-white, which is the whole point of the Tahap 2 text rule.
     */
    private fun gradientEnd(paintedColor: Int, sampledLuminance: Float): Int {
        val painted = luminanceOf(paintedColor)
        val drift = ((sampledLuminance - painted) * gradientBlend).coerceIn(-maxGradientDrift, maxGradientDrift)
        return shiftLuminance(paintedColor, drift)
    }

    /** Moves a colour along the grey axis by [delta] luminance, keeping its hue ratio. */
    private fun shiftLuminance(color: Int, delta: Float): Int {
        if (delta == 0f) return color
        val factor = (luminanceOf(color) + delta).coerceAtLeast(0f) / luminanceOf(color).coerceAtLeast(1f)
        return Color.rgb(
            (Color.red(color) * factor).roundToIntSafe(),
            (Color.green(color) * factor).roundToIntSafe(),
            (Color.blue(color) * factor).roundToIntSafe()
        )
    }

    /** Channel-wise mean of the two gradient ends, used as the panel's mid stop. */
    private fun bandAverage(first: Int, second: Int): Int = Color.rgb(
        (Color.red(first) + Color.red(second)) / 2,
        (Color.green(first) + Color.green(second)) / 2,
        (Color.blue(first) + Color.blue(second)) / 2
    )

    /**
     * The panel luminance the text of one item has to stay legible against.
     *
     * A panel is a vertical gradient, so there is no single panel colour: the text spans the top and
     * bottom stops. The darkest stop is returned because it is the one that decides the answer for
     * white text and the one that is easiest for black text, so it brackets both ends of the range
     * rather than sampling it.
     *
     * Falls back to the group's painted colour when the panel was not drawn this frame (a group
     * filtered out for being degenerate), so the text is never measured against a null.
     */
    private fun textBackingLuminance(index: Int): Float {
        val groupId = itemGroups.getOrElse(index) { index }
        val stops = paintedStops.getOrNull(groupId)
        if (stops == null || stops.size < 3) return luminanceOf(groupColors.getOrElse(groupId) { Color.BLACK })
        return stops.minOf { luminanceOf(it) }
    }
    /**
     * The colour the panel is actually filled with for a given sampled background.
     *
     * Both the panel and the text are painted at full alpha — nothing in the overlay is
     * translucent, and a Patch that let the game show through would be unreadable on top of busy
     * art. A light control is darkened here instead of being covered with a veil, so the panel
     * stays opaque while gaining enough contrast for the text sitting on it.
     *
     * Kept in one place because the text has to make the same decision: a fill colour chosen for
     * the game's own light control turns unreadable once the panel is darkened underneath it, so
     * both sides have to agree on the painted result rather than each re-deriving it.
     */
    private fun paintedPanelColor(baseColor: Int): Int =
        if (luminanceOf(baseColor) > 160f) adjustColor(baseColor, 0.62f) else adjustColor(baseColor, 0.86f)

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
        val coveragePadding: Float,
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
        val measuredWidth = textPaint.measureText(normalized)
        val availableScreenWidth = (width - 8f).coerceAtLeast(originalWidth)

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

        // Slide left when growing right would push the panel off the screen.
        //
        // Slide left only when the right side runs out of room.
        //
        // The panel is anchored on the control's left edge and grows rightwards, so a translation
        // wider than the control can run into the edge of the display and have nowhere to go but
        // wrap onto another line. When that happens, the space to the left of the control — usually
        // empty, because the control itself starts there — is what makes one line enough.
        //
        // Sliding is the fallback, not the default. A panel that shifts whenever there is room ends
        // up sitting left of the text it is translating, which reads as misplaced. So the overflow
        // past the right edge is measured first: if the panel already fits, it does not move at
        // all. Only an overflow is paid off by sliding, and only up to [leftSlideRatio] of the
        // panel's own width, after which the remaining overflow wraps onto another line — a cap
        // that keeps a panel from ever travelling across a name plate or portrait beside it.
        val overflow = (boxRight - width.toFloat()).coerceAtLeast(0f)
        if (overflow > 0f) {
            val allowedSlide = (boxRight - boxLeft) * leftSlideRatio
            val slide = minOf(overflow, allowedSlide)
            boxLeft -= slide
            boxRight -= slide
        }

        // Coverage rim: the panel is pushed out past the detected glyphs on every side, so the
        // source text's own outline and antialiased rim cannot survive as a fringe along the edge
        // of the patch. Applied after the slide so the rim can never drag the panel back off
        // screen, and clamped so it can only ever widen the box, never shrink it below the
        // control it is covering.
        val coveragePadding = (originalBoxHeight * coveragePaddingRatio).coerceIn(minCoveragePaddingPx, maxCoveragePaddingPx)
        boxLeft = (boxLeft - coveragePadding).coerceAtLeast(0f)
        boxRight = (boxRight + coveragePadding).coerceAtMost(width.toFloat())
        if (boxRight - boxLeft < originalWidth) boxRight = (boxLeft + originalWidth).coerceAtMost(width.toFloat())

        val maxTextWidth = (boxRight - boxLeft - horizontalPadding * 2f).coerceAtLeast(1f)
        val originalBoxTop = (baseTop - (originalBoxHeight - originalHeight) / 2f).coerceAtLeast(0f)

        // A box is allowed to grow over a neighbour without giving the space back.
        //
        // This used to hand the room back as soon as the grown box intersected another one, and
        // on a column of stacked dialogue lines that made every line yield to the next: the first
        // to grow was penalised for it, the one below was penalised for being long, and the column
        // came out ragged with panels landing on top of each other. Adjacent lines are how dialogue
        // is laid out in the first place, so the overlap is the normal case, not a collision. The
        // shared font scale already stops one paragraph from starving its neighbours, and the
        // per-item clip keeps each line inside its own patch.
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
            coveragePadding, maxPanelHeight, available, required
        )
    }

    private fun buildRenderItem(m: MeasuredItem, height: Int, uniformScale: Float): RenderItem? {
        val item = m.item
        val normalized = normalizeParagraph(item.translatedText); if (normalized.isEmpty()) return null
        val finalTextSize = (m.baseTextSize * uniformScale).coerceIn(minTextSizePx, m.baseTextSize)
        val lines = wrapText(normalized, m.maxTextWidth, finalTextSize)
        val needed = finalTextSize * lineSpacingRatio * lines.size + m.verticalPadding * 2f

        // The panel has to cover the control it replaces, not merely its own translation.
        //
        // Sizing it purely from the text let a short translation produce a short panel, and the
        // rest of the control's own glyphs then stayed visible below the patch — measured as a row
        // with zero replaced pixels and 51-269 pixels of untouched source ink. The Indonesian is
        // routinely the shorter of the two here, so this is the common case, not an edge one. The
        // floor is the control's height plus the coverage rim, which is enough to close it without
        // the panel growing past what the ceiling allows; past that ceiling the text shrinks
        // instead, exactly as before.
        val coverageFloor = m.originalBoxHeight + m.coveragePadding * 2f
        val settledHeight = minOf(maxOf(needed, coverageFloor), m.maxPanelHeight)

        // Centre the grown panel on the original control, then shift it back inside the screen.
        // The shift is applied to both edges at once so a panel that cannot fit above its control
        // slides down whole rather than losing its bottom edge to the screen boundary.
        var top = (m.originalBoxTop - (settledHeight - m.originalBoxHeight) / 2f).coerceAtLeast(0f)
        if (top + settledHeight > height.toFloat()) top = (height.toFloat() - settledHeight).coerceAtLeast(0f)
        val lineSpacing = finalTextSize * lineSpacingRatio
        return RenderItem(item, item.patchSample, m.boxLeft, top, m.boxRight, top + settledHeight, finalTextSize, m.horizontalPadding, lines, lineSpacing)
    }

    /**
     * Groups boxes that touch and gives each group one shared panel colour.
     *
     * Dialogue drawn as a stack of lines is a column of boxes that overlap by a few pixels, and
     * each one samples the control it sits on slightly differently — sampling noise alone can put
     * two adjacent lines a visible step apart. Painted independently they read as separate cards
     * stacked on a background they clearly share.
     *
     * Only a *vertical* stack counts. Dialogue set as a column of lines, and a paragraph set as
     * a block, are both one control and want one patch; two controls that merely sit side by side
     * are two controls, and merging their patches would erase a boundary the player relies on to
     * tell them apart. Grouping therefore needs a real vertical run — a neighbour whose top sits
     * above this box's bottom and whose left edge lines up — not just any intersection.
     *
     * The patch of a group is the union of its boxes, drawn once. The text inside stays per item:
     * each line keeps its own translation, its own width and its own alignment, and only the
     * background they sit on is shared.
     */
    private fun buildOverlapGroups() {
        if (renderItems.isEmpty()) { itemGroups = emptyList(); groupRects = emptyList(); groupColors = emptyList(); groupPatches = emptyList(); return }
        val parent = IntArray(renderItems.size) { it }
        fun find(value: Int): Int { var x = value; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
        fun union(a: Int, b: Int) { val rootA = find(a); val rootB = find(b); if (rootA != rootB) parent[rootB] = rootA }
        for (i in renderItems.indices) {
            val a = renderItems[i].boxRect()
            for (j in i + 1 until renderItems.size) {
                val b = renderItems[j].boxRect()
                if (RectF.intersects(a, b) && stacksVertically(a, b)) union(i, j)
            }
        }
        val rootToGroup = linkedMapOf<Int, Int>(); val groups = IntArray(renderItems.size)
        renderItems.indices.forEach { index -> val root = find(index); groups[index] = rootToGroup.getOrPut(root) { rootToGroup.size } }
        itemGroups = groups.toList()
        val sums = Array(rootToGroup.size) { FloatArray(4) }
        val bounds = Array(rootToGroup.size) { RectF() }
        renderItems.forEachIndexed { index, item ->
            val group = itemGroups[index]
            val color = paintedPanelColor(item.item.backgroundColor)
            sums[group][0] = sums[group][0] + Color.red(color)
            sums[group][1] = sums[group][1] + Color.green(color)
            sums[group][2] = sums[group][2] + Color.blue(color)
            sums[group][3] = sums[group][3] + 1f
            val box = item.boxRect()
            val target = bounds[group]
            if (sums[group][3] == 1f) { target.set(box) } else {
                target.left = minOf(target.left, box.left); target.top = minOf(target.top, box.top)
                target.right = maxOf(target.right, box.right); target.bottom = maxOf(target.bottom, box.bottom)
            }
        }
        groupRects = bounds.toList()
        groupColors = sums.map { sum ->
            val count = sum[3].coerceAtLeast(1f)
            Color.rgb((sum[0] / count).toInt().coerceIn(0, 255), (sum[1] / count).toInt().coerceIn(0, 255), (sum[2] / count).toInt().coerceIn(0, 255))
        }
        // The gradient is read off the group's own extremes, not off whichever member happens to be
        // first: a three-line dialogue is one panel, and taking the first line's bottom band would
        // stretch the colour of the top line's lower edge across the other two. Same rule the
        // vertical merge in OcrManager applies to merged columns.
        groupPatches = (0 until rootToGroup.size).map { group ->
            val members = renderItems.indices.filter { itemGroups[it] == group }
            val topmost = members.minByOrNull { renderItems[it].top }
            val bottommost = members.maxByOrNull { renderItems[it].bottom }
            val topColor = topmost?.let { renderItems[it].patchSample?.topColor }
            val bottomColor = bottommost?.let { renderItems[it].patchSample?.bottomColor }
            // One member missing its sample must not cost the whole group its gradient: fall back to
            // the other end, then to the painted background, rather than dropping the patch.
            when {
                topColor != null && bottomColor != null -> TextLayoutAnalyzer.PatchSample(topColor, bottomColor)
                topColor != null -> TextLayoutAnalyzer.PatchSample(topColor, topColor)
                bottomColor != null -> TextLayoutAnalyzer.PatchSample(bottomColor, bottomColor)
                else -> null
            }
        }
    }

    /**
     * Whether two boxes belong to the same run of text rather than being two separate controls.
     *
     * Two boxes are one control when they overlap and sit at different heights — one above the
     * other. How much they overlap horizontally is not required: ML Kit splits a single block of
     * dialogue into lines that differ in width, and the widest line reaches well past the
     * narrowest, so demanding a large horizontal overlap broke those runs apart and left every
     * line with its own patch. A shared left edge, which is what a control drawn by the game
     * actually has, is what tells the two apart instead.
     *
     * A vertical gap of up to a glyph width is still the same control: that is roughly the line
     * pitch between two lines of the same box.
     */
    private fun stacksVertically(a: RectF, b: RectF): Boolean {
        val overlapX = minOf(a.right, b.right) - maxOf(a.left, b.left)
        if (overlapX <= 0f) return false
        val narrowest = minOf(a.width(), b.width())
        val widest = maxOf(a.width(), b.width())

        // Two boxes of very different widths are not two lines of one control.
        //
        // Lines of a paragraph differ in width, so equal widths cannot be required, but the spread
        // is bounded: a short final line still stays well past half the longest one. Something far
        // below that is a different kind of thing sharing a column — the `◆説明` label sitting
        // above the body it titles, a heading above a block — and merging it is what produced one
        // grey slab spanning a label, a paragraph and a row of effect lines instead of the three
        // separate controls the game actually draws. The player reads those as separate regions,
        // so the overlay has to keep them separate too.
        if (widest > 0f && narrowest / widest < siblingWidthRatio) return false

        val overlapY = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        val verticalRun = a.top < b.top || b.top < a.top
        // Touching counts, and a small vertical gap is the normal line pitch of a control.
        return verticalRun && overlapY >= -narrowest
    }

    /**
     * Flattens a translated block into a single run of text.
     *
     * Line breaks coming back from the translator are treated as plain whitespace rather than as
     * structure. DeepL has no notion of the game's line layout - it translates a line of
     * Japanese and returns a sentence, and it is free to return that sentence broken across
     * lines wherever it likes. Honouring those breaks produced the opposite of what the panel
     * wants: a sentence that would have fitted on one line arrived pre-split into three short
     * ones, which needed a taller panel, and that taller panel then squeezed the block
     * underneath it.
     *
     * The source is a Japanese or Chinese line, which carries no meaningful break of its own,
     * so there is nothing to preserve. wrapText decides the line breaks from the width that
     * was actually available.
     */
    private fun normalizeParagraph(text: String): String = text.replace(Regex("[\\s]+"), " ").trim()

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
        itemGroups = emptyList()
        groupRects = emptyList()
        groupColors = emptyList()
        groupPatches = emptyList()
        paintedStops = emptyArray()
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
    val alignment: TextLayoutAnalyzer.TextAlignment = TextLayoutAnalyzer.TextAlignment.CENTER,
    val patchSample: TextLayoutAnalyzer.PatchSample? = null
)
