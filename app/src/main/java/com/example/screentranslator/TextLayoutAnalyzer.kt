package com.example.screentranslator

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.text.Text.Line
import com.google.mlkit.vision.text.Text.TextBlock
import kotlin.math.abs
import kotlin.math.roundToInt

/** Converts ML Kit paragraph/line geometry into rendering geometry for the Manual overlay. */
object TextLayoutAnalyzer {
    data class Result(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val sourceTextSizePx: Float,
        val backgroundColor: Int,
        val blurredPatch: Bitmap? = null
    )

    /**
     * ML Kit TextBlock is a layout container, not a guarantee that all lines form
     * one semantic paragraph. This heuristic separates list/stat-style blocks
     * from prose so Manual TL does not force unrelated lines into one paragraph.
     */
    fun shouldTreatAsParagraph(block: TextBlock): Boolean {
        val lines = block.lines
        if (lines.size < 2) return false

        val boxes = lines.mapNotNull { it.boundingBox }.filter { it.width() > 0 && it.height() > 0 }
        if (boxes.size < 2) return false

        val medianHeight = boxes.map { it.height() }.sorted()[boxes.size / 2].toFloat().coerceAtLeast(1f)
        val sortedByTop = boxes.sortedBy { it.top }
        val gaps = sortedByTop.zipWithNext().map { (a, b) -> (b.top - a.bottom).coerceAtLeast(0) }
        val medianGap = gaps.takeIf { it.isNotEmpty() }?.sorted()?.get(gaps.size / 2)?.toFloat() ?: 0f
        val compactSpacing = medianGap <= medianHeight * 0.65f

        val lefts = boxes.map { it.left.toFloat() }
        val leftMean = lefts.average().toFloat()
        val leftDeviation = lefts.map { abs(it - leftMean) }.average().toFloat()
        val aligned = leftDeviation <= medianHeight * 0.85f

        val text = block.text.trim()
        val charCount = text.count { !it.isWhitespace() }
        if (charCount < 30) return false

        val digitCount = text.count { it.isDigit() }
        val digitRatio = digitCount.toFloat() / charCount.coerceAtLeast(1)
        val terminalCount = lines.count { line ->
            val value = line.text.trimEnd()
            value.endsWith("。") || value.endsWith("！") || value.endsWith("？") ||
                value.endsWith(".") || value.endsWith("!") || value.endsWith("?")
        }

        val prefixes = lines.mapNotNull { line ->
            line.text.trim().takeIf { it.length >= 4 }?.take(6)
        }
        val repeatedPrefix = prefixes.groupingBy { it }.eachCount().values.any { it >= 2 }

        // List/stat rows commonly have high numeric density, repeated row prefixes,
        // or unusually uniform line widths. Prose usually has more varied wrapping.
        val widths = boxes.map { it.width().toFloat() }
        val widthMean = widths.average().toFloat().coerceAtLeast(1f)
        val widthDeviation = widths.map { abs(it - widthMean) }.average().toFloat()
        val veryUniformWidths = widthDeviation / widthMean < 0.10f

        var score = 0
        if (compactSpacing) score++
        if (aligned) score++
        if (charCount >= 45) score++
        if (terminalCount <= lines.size / 2) score++
        if (digitRatio < 0.18f) score++
        if (veryUniformWidths) score--
        if (repeatedPrefix) score -= 2
        if (digitRatio >= 0.28f) score -= 2

        return score >= 3
    }

    fun analyze(bitmap: Bitmap, block: TextBlock): Result? {
        val box = block.boundingBox ?: return null
        if (box.width() <= 0 || box.height() <= 0) return null
        val elementHeights = block.lines.flatMap { line ->
            line.elements.mapNotNull { it.boundingBox?.height()?.takeIf { height -> height > 0 } }
        }
        val glyphHeight = if (elementHeights.isNotEmpty()) {
            elementHeights.sorted()[elementHeights.size / 2].toFloat()
        } else {
            box.height().toFloat() / block.lines.size.coerceAtLeast(1)
        }
        return buildResult(bitmap, box.left, box.top, box.right, box.bottom, glyphHeight)
    }

    fun analyze(bitmap: Bitmap, line: Line): Result? {
        val box = line.boundingBox ?: return null
        if (box.width() <= 0 || box.height() <= 0) return null
        val elementHeights = line.elements.mapNotNull { it.boundingBox?.height()?.takeIf { height -> height > 0 } }
        val glyphHeight = if (elementHeights.isNotEmpty()) elementHeights.sorted()[elementHeights.size / 2].toFloat() else box.height().toFloat()
        return buildResult(bitmap, box.left, box.top, box.right, box.bottom, glyphHeight)
    }

    private fun buildResult(bitmap: Bitmap, rawLeft: Int, rawTop: Int, rawRight: Int, rawBottom: Int, glyphHeight: Float): Result? {
        val sourceTextSizePx = (glyphHeight * 1.15f).coerceIn(8f, 96f)
        val horizontalPad = (glyphHeight * 0.20f).roundToInt().coerceIn(3, 18)
        val verticalPad = (glyphHeight * 0.18f).roundToInt().coerceIn(2, 12)
        val left = (rawLeft - horizontalPad).coerceIn(0, bitmap.width - 1)
        val top = (rawTop - verticalPad).coerceIn(0, bitmap.height - 1)
        val right = (rawRight + horizontalPad).coerceIn(left + 1, bitmap.width)
        val bottom = (rawBottom + verticalPad).coerceIn(top + 1, bitmap.height)
        return Result(left, top, right, bottom, sourceTextSizePx, estimateBackgroundColor(bitmap, left, top, right, bottom), null)
    }

    private fun estimateBackgroundColor(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Int {
        val samples = ArrayList<Int>(48)
        val width = right - left
        val height = bottom - top
        val stepX = (width / 12).coerceAtLeast(1)
        val stepY = (height / 6).coerceAtLeast(1)
        for (x in left until right step stepX) {
            if (top > 1) samples.add(bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), top - 1))
            if (bottom < bitmap.height) samples.add(bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), bottom))
        }
        for (y in top until bottom step stepY) {
            if (left > 1) samples.add(bitmap.getPixel(left - 1, y.coerceIn(0, bitmap.height - 1)))
            if (right < bitmap.width) samples.add(bitmap.getPixel(right, y.coerceIn(0, bitmap.height - 1)))
        }
        if (samples.isEmpty()) return Color.BLACK
        val sortedR = samples.map { Color.red(it) }.sorted()
        val sortedG = samples.map { Color.green(it) }.sorted()
        val sortedB = samples.map { Color.blue(it) }.sorted()
        val middle = samples.size / 2
        return Color.rgb(sortedR[middle], sortedG[middle], sortedB[middle])
    }
}
