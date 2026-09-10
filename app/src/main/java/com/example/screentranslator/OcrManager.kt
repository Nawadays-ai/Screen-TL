package com.example.screentl

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.max

 data class DetectedText(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val sourceTextSizePx: Float = 0f,
    val backgroundColor: Int = android.graphics.Color.BLACK,
    var blurredPatch: Bitmap? = null,
    val orientation: TextLayoutAnalyzer.WritingOrientation = TextLayoutAnalyzer.WritingOrientation.HORIZONTAL
)

class OcrManager(
    sourceLanguage: String
) {
    companion object {
        private const val TAG = "ScreenTL-OCR"
    }

    private val recognizer: TextRecognizer = when (sourceLanguage) {
        "Jepang" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        "Mandarin (China)" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun recognize(bitmap: Bitmap, onSuccess: (List<DetectedText>) -> Unit, onFailure: (Exception) -> Unit) {
        recognize(bitmap, onSuccess, onFailure, null)
    }

    fun recognize(
        bitmap: Bitmap,
        onSuccess: (List<DetectedText>) -> Unit,
        onFailure: (Exception) -> Unit,
        trace: ScreenTLPerformanceTrace? = null
    ) {
        val perfTrace = trace ?: ScreenTLPerformanceTrace.current()
        perfTrace?.mark("ocr_start")
        Log.i(TAG, "OCR started for bitmap ${bitmap.width}x${bitmap.height}")
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                perfTrace?.mark("mlkit_result blocks=${visionText.textBlocks.size} lines=${visionText.textBlocks.sumOf { it.lines.size }}")
                val detectedTexts = mutableListOf<DetectedText>()
                val verticalCandidates = mutableListOf<VerticalCandidate>()
                var paragraphCount = 0
                var lineCount = 0

                for (block in visionText.textBlocks) {
                    val blockText = block.text.trim()
                    if (blockText.isBlank()) continue

                    if (TextLayoutAnalyzer.isVerticalBlock(block)) {
                        val layout = TextLayoutAnalyzer.analyze(bitmap, block)
                        if (layout != null) {
                            detectedTexts.add(
                                DetectedText(
                                    TextLayoutAnalyzer.verticalBlockText(block),
                                    layout.left,
                                    layout.top,
                                    layout.right,
                                    layout.bottom,
                                    layout.sourceTextSizePx,
                                    layout.backgroundColor,
                                    layout.blurredPatch,
                                    TextLayoutAnalyzer.WritingOrientation.VERTICAL
                                )
                            )
                        }
                        continue
                    }

                    if (TextLayoutAnalyzer.shouldTreatAsParagraph(block)) {
                        val layout = TextLayoutAnalyzer.analyze(bitmap, block)
                        if (layout != null) {
                            detectedTexts.add(DetectedText(blockText, layout.left, layout.top, layout.right, layout.bottom, layout.sourceTextSizePx, layout.backgroundColor, layout.blurredPatch, layout.orientation))
                            paragraphCount++
                        }
                    } else {
                        for (line in block.lines) {
                            val lineText = line.text.trim()
                            if (lineText.isBlank()) continue
                            val layout = TextLayoutAnalyzer.analyze(bitmap, line) ?: continue
                            if (layout.orientation == TextLayoutAnalyzer.WritingOrientation.VERTICAL) {
                                verticalCandidates.add(VerticalCandidate(lineText, line.boundingBox ?: continue, layout))
                            } else {
                                detectedTexts.add(DetectedText(lineText, layout.left, layout.top, layout.right, layout.bottom, layout.sourceTextSizePx, layout.backgroundColor, layout.blurredPatch, layout.orientation))
                                lineCount++
                            }
                        }
                    }
                }

                val verticalGroups = groupVerticalCandidates(verticalCandidates)
                for (group in verticalGroups) {
                    val layout = mergeVerticalLayouts(group)
                    if (layout != null) {
                        val text = group
                            .sortedByDescending { it.box.centerX() }
                            .joinToString(separator = "") { it.text }
                            .trim()
                        if (text.isNotBlank()) {
                            detectedTexts.add(
                                DetectedText(
                                    text,
                                    layout.left,
                                    layout.top,
                                    layout.right,
                                    layout.bottom,
                                    layout.sourceTextSizePx,
                                    layout.backgroundColor,
                                    layout.blurredPatch,
                                    TextLayoutAnalyzer.WritingOrientation.VERTICAL
                                )
                            )
                        }
                    }
                }

                perfTrace?.mark("ocr_geometry_complete paragraphs=$paragraphCount lines=$lineCount verticalGroups=${verticalGroups.size} detected=${detectedTexts.size}")
                Log.i(TAG, "OCR completed: ${detectedTexts.size} regions detected ($paragraphCount paragraphs, $lineCount lines, ${verticalGroups.size} vertical groups)")
                detectedTexts.forEachIndexed { index, detected ->
                    Log.d(TAG, "[$index] ${detected.orientation} chars=${detected.text.length} box=${detected.left},${detected.top},${detected.right},${detected.bottom} font=${detected.sourceTextSizePx}")
                }
                perfTrace?.mark("ocr_complete")
                onSuccess(detectedTexts)
            }
            .addOnFailureListener { exception ->
                perfTrace?.mark("ocr_failed")
                Log.e(TAG, "OCR failed", exception)
                onFailure(exception)
            }
    }

    private data class VerticalCandidate(
        val text: String,
        val box: android.graphics.Rect,
        val layout: TextLayoutAnalyzer.Result
    )

    private fun groupVerticalCandidates(candidates: List<VerticalCandidate>): List<List<VerticalCandidate>> {
        if (candidates.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<VerticalCandidate>>()
        for (candidate in candidates.sortedBy { it.box.left }) {
            val match = groups.firstOrNull { group -> group.any { areSameBubbleColumn(it, candidate) } }
            if (match != null) match.add(candidate) else groups.add(mutableListOf(candidate))
        }
        return groups
    }

    /**
     * Columns in one manga bubble normally overlap strongly on Y and are separated
     * by roughly one glyph width on X. Requiring both conditions prevents distant
     * vertical bubbles from being merged into one sentence.
     */
    private fun areSameBubbleColumn(a: VerticalCandidate, b: VerticalCandidate): Boolean {
        val overlapTop = max(a.box.top, b.box.top)
        val overlapBottom = minOf(a.box.bottom, b.box.bottom)
        val overlap = (overlapBottom - overlapTop).coerceAtLeast(0)
        val minHeight = minOf(a.box.height(), b.box.height()).coerceAtLeast(1)
        val verticalOverlapRatio = overlap.toFloat() / minHeight
        val horizontalGap = when {
            a.box.right < b.box.left -> b.box.left - a.box.right
            b.box.right < a.box.left -> a.box.left - b.box.right
            else -> 0
        }
        val glyphWidth = minOf(a.box.width(), b.box.width()).coerceAtLeast(1)
        return verticalOverlapRatio >= 0.35f && horizontalGap <= glyphWidth * 2.25f
    }

    private fun mergeVerticalLayouts(group: List<VerticalCandidate>): TextLayoutAnalyzer.Result? {
        if (group.isEmpty()) return null
        val left = group.minOf { it.layout.left }
        val top = group.minOf { it.layout.top }
        val right = group.maxOf { it.layout.right }
        val bottom = group.maxOf { it.layout.bottom }
        val sourceTextSize = group.map { it.layout.sourceTextSizePx }.average().toFloat()
        val background = group.map { it.layout.backgroundColor }.first()
        return TextLayoutAnalyzer.Result(
            left,
            top,
            right,
            bottom,
            sourceTextSize,
            background,
            TextLayoutAnalyzer.WritingOrientation.VERTICAL
        )
    }

    fun close() {
        recognizer.close()
    }
}
