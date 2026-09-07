package com.example.screentranslator

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.text.Text.Line
import kotlin.math.roundToInt

/**
 * Converts ML Kit line geometry into rendering geometry for the Manual overlay.
 * The renderer uses this geometry to cover the source line without making the
 * translated text oversized or vertically distorted.
 */
object TextLayoutAnalyzer {

    data class Result(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val sourceTextSizePx: Float,
        val backgroundColor: Int
    )

    fun analyze(bitmap: Bitmap, line: Line): Result? {
        val box = line.boundingBox ?: return null
        if (box.width() <= 0 || box.height() <= 0) return null

        val elementHeights = line.elements
            .mapNotNull { it.boundingBox?.height()?.takeIf { height -> height > 0 } }

        val glyphHeight = if (elementHeights.isNotEmpty()) {
            elementHeights.sorted()[elementHeights.size / 2].toFloat()
        } else {
            box.height().toFloat()
        }

        // OCR glyph height is the visible height of the source characters,
        // while Paint.textSize is the font's full em size. A smaller calibration
        // than the old 1.45x keeps the translation close to the source instead
        // of producing the oversized/tall text seen on the device.
        val sourceTextSizePx = (glyphHeight * 1.10f).coerceIn(8f, 96f)

        // Give the mask a little breathing room around the source glyphs, but
        // keep it tight enough that adjacent lines do not get covered.
        val horizontalPad = (glyphHeight * 0.20f).roundToInt().coerceIn(3, 18)
        val verticalPad = (glyphHeight * 0.18f).roundToInt().coerceIn(2, 12)

        val left = (box.left - horizontalPad).coerceIn(0, bitmap.width - 1)
        val top = (box.top - verticalPad).coerceIn(0, bitmap.height - 1)
        val right = (box.right + horizontalPad).coerceIn(left + 1, bitmap.width)
        val bottom = (box.bottom + verticalPad).coerceIn(top + 1, bitmap.height)

        return Result(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            sourceTextSizePx = sourceTextSizePx,
            backgroundColor = estimateBackgroundColor(bitmap, left, top, right, bottom)
        )
    }

    private fun estimateBackgroundColor(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Int {
        val samples = ArrayList<Int>(32)
        val width = right - left
        val height = bottom - top
        val stepX = (width / 8).coerceAtLeast(1)
        val stepY = (height / 4).coerceAtLeast(1)

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
