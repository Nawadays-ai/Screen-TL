package com.example.screentranslator

import android.graphics.Bitmap
import com.google.mlkit.vision.text.Text
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
    val bottom: Int
)

class OcrManager(
    sourceLanguage: String
) {

    private val recognizer: TextRecognizer = when (sourceLanguage) {

        "Jepang" -> {
            TextRecognition.getClient(
                JapaneseTextRecognizerOptions.Builder().build()
            )
        }

        "Mandarin (China)" -> {
            TextRecognition.getClient(
                ChineseTextRecognizerOptions.Builder().build()
            )
        }

        else -> {
            TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
            )
        }
    }

    fun recognize(
        bitmap: Bitmap,
        onSuccess: (List<DetectedText>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {

        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->

                val detectedTexts = mutableListOf<DetectedText>()

                for (block in visionText.textBlocks) {

                    for (line in block.lines) {

                        val box = line.boundingBox

                        if (box != null && line.text.isNotBlank()) {

                            detectedTexts.add(
                                DetectedText(
                                    text = line.text,
                                    left = box.left,
                                    top = box.top,
                                    right = box.right,
                                    bottom = box.bottom
                                )
                            )
                        }
                    }
                }

                onSuccess(detectedTexts)
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
            .addOnCompleteListener {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
    }

    fun close() {
        recognizer.close()
    }
}
