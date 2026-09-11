package com.example.screentranslator

import android.graphics.Bitmap

/**
 * First-stage image preparation for the Firefox Translator pipeline.
 *
 * The initial implementation intentionally keeps the original bitmap intact.
 * More advanced adaptive preprocessing can be added here and compared against
 * the original before OCR is allowed to depend on it.
 */
object TextPreprocessor {
    data class Result(
        val original: Bitmap,
        val ocrBitmap: Bitmap
    )

    fun prepare(source: Bitmap): Result {
        // Baseline/pass-through checkpoint. Keep the source unchanged so this
        // stage cannot regress capture coordinates or existing OCR behavior.
        return Result(
            original = source,
            ocrBitmap = source
        )
    }
}
