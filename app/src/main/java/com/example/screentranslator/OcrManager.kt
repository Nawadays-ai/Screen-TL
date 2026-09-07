package com.example.screentranslator

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.common.InputImage

data class DetectedText(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val sourceTextSizePx: Float = 0f,
    val backgroundColor: Int = android.graphics.Color.BLACK,
    val blurredPatch: Bitmap? = null
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

                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        if (line.text.isBlank()) continue

                        val layout = TextLayoutAnalyzer.analyze(bitmap, line)
                        if (layout != null) {
                            detectedTexts.add(
                                DetectedText(
                                    text = line.text,
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
                }

                Log.i(TAG, "OCR completed: ${detectedTexts.size} lines detected")
                detectedTexts.forEachIndexed { index, detected ->
                    Log.i(
                        TAG,
                        "[$index] '${detected.text}' " +
                                "mask=${detected.left},${detected.top},${detected.right},${detected.bottom} " +
                                "font=${detected.sourceTextSizePx} " +
                                "bg=#${detected.backgroundColor.toUInt().toString(16)} " +
                                "blur=${detected.blurredPatch != null}"
                    )
                }

                onSuccess(detectedTexts)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "OCR failed", exception)
                onFailure(exception)
            }
            .addOnCompleteListener {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
                Log.i(TAG, "OCR bitmap released")
            }
    }

    fun close() {
        recognizer.close()
    }
}
