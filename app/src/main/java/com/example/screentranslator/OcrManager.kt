package com.example.screentranslator

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

data class DetectedText(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val sourceTextSizePx: Float = 0f,
    val backgroundColor: Int = android.graphics.Color.BLACK,
    var blurredPatch: Bitmap? = null
)

class OcrManager(
    sourceLanguage: String
) {

    companion object {
        private const val TAG = "ScreenTL-OCR"
    }

    private val recognizer: TextRecognizer = when (sourceLanguage) {
        "Jepang" -> TextRecognition.getClient(
            JapaneseTextRecognizerOptions.Builder().build()
        )
        "Mandarin (China)" -> TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )
        else -> TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )
    }

    fun recognize(
        bitmap: Bitmap,
        trace: ScreenTLPerformanceTrace? = null,
        onSuccess: (List<DetectedText>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        trace?.mark("ocr_start")
        Log.i(TAG, "OCR started for bitmap ${bitmap.width}x${bitmap.height}")

        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                trace?.mark("mlkit_result blocks=${visionText.textBlocks.size} lines=${visionText.textBlocks.sumOf { it.lines.size }}")
                val detectedTexts = mutableListOf<DetectedText>()
                var paragraphCount = 0
                var lineCount = 0

                for (block in visionText.textBlocks) {
                    val blockText = block.text.trim()
                    if (blockText.isBlank()) continue

                    val treatAsParagraph = TextLayoutAnalyzer.shouldTreatAsParagraph(block)
                    if (treatAsParagraph) {
                        val layout = TextLayoutAnalyzer.analyze(bitmap, block)
                        if (layout != null) {
                            detectedTexts.add(
                                DetectedText(
                                    text = blockText,
                                    left = layout.left,
                                    top = layout.top,
                                    right = layout.right,
                                    bottom = layout.bottom,
                                    sourceTextSizePx = layout.sourceTextSizePx,
                                    backgroundColor = layout.backgroundColor,
                                    blurredPatch = layout.blurredPatch
                                )
                            )
                            paragraphCount++
                        }
                    } else {
                        for (line in block.lines) {
                            val lineText = line.text.trim()
                            if (lineText.isBlank()) continue
                            val layout = TextLayoutAnalyzer.analyze(bitmap, line) ?: continue
                            detectedTexts.add(
                                DetectedText(
                                    text = lineText,
                                    left = layout.left,
                                    top = layout.top,
                                    right = layout.right,
                                    bottom = layout.bottom,
                                    sourceTextSizePx = layout.sourceTextSizePx,
                                    backgroundColor = layout.backgroundColor,
                                    blurredPatch = layout.blurredPatch
                                )
                            )
                            lineCount++
                        }
                    }
                }

                trace?.mark("ocr_geometry_complete paragraphs=$paragraphCount lines=$lineCount detected=${detectedTexts.size}")
                Log.i(TAG, "OCR completed: ${detectedTexts.size} regions detected ($paragraphCount paragraphs, $lineCount lines)")
                detectedTexts.forEachIndexed { index, detected ->
                    Log.d(
                        TAG,
                        "[$index] region chars=${detected.text.length} " +
                            "box=${detected.left},${detected.top},${detected.right},${detected.bottom} " +
                            "font=${detected.sourceTextSizePx}"
                    )
                }

                trace?.mark("ocr_complete")
                onSuccess(detectedTexts)
            }
            .addOnFailureListener { exception ->
                trace?.mark("ocr_failed")
                Log.e(TAG, "OCR failed", exception)
                onFailure(exception)
            }
    }

    fun close() {
        recognizer.close()
    }
}
