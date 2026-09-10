package com.example.screentranslator

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions

/**
 * Manga pipeline: ML Kit is used only to locate candidate text regions; the
 * actual Japanese recognition is performed by the Manga OCR ONNX model.
 */
class MangaOcrManager(
    context: android.content.Context
) {
    companion object { private const val TAG = "ScreenTL-MangaOCR" }

    private val detector: TextRecognizer = TextRecognition.getClient(
        JapaneseTextRecognizerOptions.Builder().build()
    )
    private val store = MangaOcrModelStore(context)
    private val engine = MangaOcrEngine(store)

    fun recognize(
        bitmap: Bitmap,
        onSuccess: (List<DetectedText>) -> Unit,
        onFailure: (Exception) -> Unit,
        trace: ScreenTLPerformanceTrace? = null
    ) {
        val perfTrace = trace ?: ScreenTLPerformanceTrace.current()
        perfTrace?.mark("ocr_start engine=manga")
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { visionText ->
                Thread {
                    try {
                        engine.prepare()
                        val regions = mergeNearbyBlocks(visionText.textBlocks)
                        val results = regions.mapNotNull { region ->
                            val layout = TextLayoutAnalyzer.analyze(bitmap, region.block) ?: return@mapNotNull null
                            val raw = engine.recognize(bitmap, region.rect)
                            val text = TextPreprocessor.normalize(raw, mangaMode = true)
                            if (text.isBlank()) return@mapNotNull null
                            DetectedText(
                                text = text,
                                left = layout.left,
                                top = layout.top,
                                right = layout.right,
                                bottom = layout.bottom,
                                sourceTextSizePx = layout.sourceTextSizePx,
                                backgroundColor = layout.backgroundColor,
                                blurredPatch = layout.blurredPatch,
                                orientation = if (TextLayoutAnalyzer.isVerticalBlock(region.block)) {
                                    TextLayoutAnalyzer.WritingOrientation.VERTICAL
                                } else {
                                    layout.orientation
                                }
                            )
                        }
                        val ordered = SmartTextLayout.sort(results, mangaMode = true)
                        perfTrace?.mark("manga_ocr_complete regions=${regions.size} detected=${ordered.size}")
                        perfTrace?.mark("ocr_complete")
                        onSuccess(ordered)
                    } catch (exception: Exception) {
                        Log.e(TAG, "Manga OCR failed", exception)
                        perfTrace?.mark("manga_ocr_failed ${exception.javaClass.simpleName}")
                        perfTrace?.mark("ocr_failed")
                        onFailure(exception)
                    }
                }.start()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Manga OCR detector failed", exception)
                perfTrace?.mark("manga_detector_failed")
                perfTrace?.mark("ocr_failed")
                onFailure(exception)
            }
    }

    fun close() {
        detector.close()
        engine.close()
    }

    private data class Region(val block: Text.TextBlock, val rect: Rect)

    private fun mergeNearbyBlocks(blocks: List<Text.TextBlock>): List<Region> {
        val candidates = blocks.mapNotNull { block ->
            val rect = block.boundingBox ?: return@mapNotNull null
            if (rect.width() < 3 || rect.height() < 3 || block.text.isBlank()) null else Region(block, Rect(rect))
        }
        if (candidates.isEmpty()) return emptyList()

        val used = BooleanArray(candidates.size)
        val result = mutableListOf<Region>()
        for (i in candidates.indices) {
            if (used[i]) continue
            used[i] = true
            val members = mutableListOf(candidates[i])
            var changed = true
            while (changed) {
                changed = false
                for (j in candidates.indices) {
                    if (used[j]) continue
                    if (members.any { shouldMerge(it.rect, candidates[j].rect) }) {
                        used[j] = true
                        members += candidates[j]
                        changed = true
                    }
                }
            }
            val merged = Rect(members.first().rect)
            members.drop(1).forEach { merged.union(it.rect) }
            val representative = members.maxByOrNull { it.block.text.length }!!.block
            result += Region(representative, merged)
        }
        return result
    }

    private fun shouldMerge(a: Rect, b: Rect): Boolean {
        val expanded = Rect(a)
        val pad = maxOf(a.width(), a.height()) * 0.45f
        expanded.inset(-pad.toInt(), -pad.toInt())
        return expanded.intersects(b.left, b.top, b.right, b.bottom)
    }
}
