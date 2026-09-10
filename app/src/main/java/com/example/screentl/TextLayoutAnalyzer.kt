package com.example.screentl

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.text.Text.Line
import com.google.mlkit.vision.text.Text.TextBlock
import kotlin.math.abs
import kotlin.math.roundToInt

/** Converts ML Kit paragraph/line geometry into rendering geometry for the Manual overlay. */
object TextLayoutAnalyzer {
    enum class WritingOrientation {
        HORIZONTAL,
        VERTICAL
    }

    data class Result(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val sourceTextSizePx: Float,
        val backgroundColor: Int,
        val orientation: WritingOrientation = WritingOrientation.HORIZONTAL,
        val blurredPatch: Bitmap? = null
    )

    fun isVerticalLine(line: Line): Boolean {
        val box = line.boundingBox ?: return false
        if (box.width() <= 0 || box.height() <= 0) return false
        val aspectRatio = box.height().toFloat() / box.width().toFloat()
        if (aspectRatio < 1.30f) return false
        val elementBoxes = line.elements.mapNotNull { it.boundingBox }
            .filter { it.width() > 0 && it.height() > 0 }
        if (elementBoxes.size < 2) return aspectRatio >= 1.55f
        val verticalSteps = elementBoxes.zipWithNext().count { (a, b) ->
            abs(b.centerX() - a.centerX()) <= box.width() * 0.75f &&
                b.centerY() >= a.centerY() - box.height() * 0.08f
        }
        val horizontalSteps = elementBoxes.zipWithNext().count { (a, b) ->
            abs(b.centerY() - a.centerY()) <= box.height() * 0.12f &&
                b.centerX() >= a.centerX() - box.width() * 0.08f
        }
        return verticalSteps >= horizontalSteps
    }

    fun isVerticalBlock(block: TextBlock): Boolean {
        val lines = block.lines
        if (lines.isEmpty()) return false
        val verticalLines = lines.count(::isVerticalLine)
        if (verticalLines == 0) return false
        if (lines.size == 1) return true
        return verticalLines.toFloat() / lines.size >= 0.5f
    }

    fun verticalBlockText(block: TextBlock): String {
        return block.lines
            .filter { it.text.isNotBlank() }
            .sortedByDescending { it.boundingBox?.centerX() ?: 0 }
            .joinToString(separator = "") { it.text.trim() }
            .trim()
    }

    fun shouldTreatAsParagraph(block: TextBlock): Boolean {
        if (isVerticalBlock(block)) return false
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
            value.endsWith("。") || value.endsWith("！") || value.endsWith("？") || value.endsWith(".") || value.endsWith("!") || value.endsWith("?")
        }
        val prefixes = lines.mapNotNull { line -> line.text.trim().takeIf { it.length >= 4 }?.take(6) }
        val repeatedPrefix = prefixes.groupingBy { it }.eachCount().values.any { it >= 2 }
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
        val elementHeights = block.lines.flatMap { line -> line.elements.mapNotNull { it.boundingBox?.height()?.takeIf { height -> height > 0 } } }
        val glyphHeight = if (elementHeights.isNotEmpty()) elementHeights.sorted()[elementHeights.size / 2].toFloat() else box.height().toFloat() / block.lines.size.coerceAtLeast(1)
        val orientation = if (isVerticalBlock(block)) WritingOrientation.VERTICAL else WritingOrientation.HORIZONTAL
        return buildResult(bitmap, box.left, box.top, box.right, box.bottom, glyphHeight, orientation)
    }

    fun analyze(bitmap: Bitmap, line: Line): Result? {
        val box = line.boundingBox ?: return null
        if (box.width() <= 0 || box.height() <= 0) return null
        val elementHeights = line.elements.mapNotNull { it.boundingBox?.height()?.takeIf { height -> height > 0 } }
        val glyphHeight = if (elementHeights.isNotEmpty()) elementHeights.sorted()[elementHeights.size / 2].toFloat() else box.height().toFloat()
        val orientation = if (isVerticalLine(line)) WritingOrientation.VERTICAL else WritingOrientation.HORIZONTAL
        return buildResult(bitmap, box.left, box.top, box.right, box.bottom, glyphHeight, orientation)
    }

    private fun buildResult(bitmap: Bitmap, rawLeft: Int, rawTop: Int, rawRight: Int, rawBottom: Int, glyphHeight: Float, orientation: WritingOrientation): Result? {
        val sourceTextSizePx = (glyphHeight * 1.15f).coerceIn(8f, 96f)
        val horizontalPad = (glyphHeight * 0.20f).roundToInt().coerceIn(3, 18)
        val verticalPad = (glyphHeight * 0.18f).roundToInt().coerceIn(2, 12)
        val left = (rawLeft - horizontalPad).coerceIn(0, bitmap.width - 1)
        val top = (rawTop - verticalPad).coerceIn(0, bitmap.height - 1)
        val right = (rawRight + horizontalPad).coerceIn(left + 1, bitmap.width)
        val bottom = (rawBottom + verticalPad).coerceIn(top + 1, bitmap.height)
        return Result(left, top, right, bottom, sourceTextSizePx, estimateBackgroundColor(bitmap, left, top, right, bottom), orientation)
    }

    private fun estimateBackgroundColor(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Int {
        val samples = ArrayList<Int>(64)
        val width = right - left
        val height = bottom - top
        val stepX = (width / 12).coerceAtLeast(1)
        val stepY = (height / 6).coerceAtLeast(1)
        for (x in left until right step stepX) {
            if (top > 0) samples.add(bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), (top - 1).coerceIn(0, bitmap.height - 1)))
            if (bottom < bitmap.height) samples.add(bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), bottom.coerceIn(0, bitmap.height - 1)))
        }
        for (y in top until bottom step stepY) {
            if (left > 0) samples.add(bitmap.getPixel((left - 1).coerceIn(0, bitmap.width - 1), y.coerceIn(0, bitmap.height - 1)))
            if (right < bitmap.width) samples.add(bitmap.getPixel(right.coerceIn(0, bitmap.width - 1), y.coerceIn(0, bitmap.height - 1)))
        }
        if (samples.isEmpty()) return Color.BLACK
        val sortedR = samples.map { Color.red(it) }.sorted()
        val sortedG = samples.map { Color.green(it) }.sorted()
        val sortedB = samples.map { Color.blue(it) }.sorted()
        val middle = samples.size / 2
        return Color.rgb(sortedR[middle], sortedG[middle], sortedB[middle])
    }
}
