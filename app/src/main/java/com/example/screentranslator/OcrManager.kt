package com.example.screentranslator

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.Text.TextBlock
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
        onSuccess: (List<DetectedText>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        Log.i(TAG, "OCR started for bitmap ${bitmap.width}x${bitmap.height}")

        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val detectedTexts = mutableListOf<DetectedText>()

                // ML Kit text blocks are the most useful paragraph-level unit for
                // manual translation. Previously we translated every OCR line
                // independently, which could change the meaning across line breaks.
                for (block in visionText.textBlocks) {
                    val blockText = block.text.trim()
                    if (blockText.isBlank()) continue

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
                    }
                }

                Log.i(TAG, "OCR completed: ${detectedTexts.size} paragraphs detected")
                detectedTexts.forEachIndexed { index, detected ->
                    Log.d(
                        TAG,
                        "[$index] paragraph chars=${detected.text.length} " +
                            "box=${detected.left},${detected.top},${detected.right},${detected.bottom} " +
                            "font=${detected.sourceTextSizePx}"
                    )
                }

                onSuccess(detectedTexts)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "OCR failed", exception)
                onFailure(exception)
            }
    }

    fun close() {
        recognizer.close()
    }
}
