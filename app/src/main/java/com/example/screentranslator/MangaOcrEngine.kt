package com.example.screentranslator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.microsoft.onnxruntime.OnnxTensor
import com.microsoft.onnxruntime.OrtEnvironment
import com.microsoft.onnxruntime.OrtSession
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Android implementation of the 2025 Manga OCR ONNX export.
 * The model is Japanese-focused and accepts one 224x224 crop at a time.
 */
class MangaOcrEngine(
    private val modelStore: MangaOcrModelStore
) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val sessionOptions = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
        setInterOpNumThreads(1)
    }
    private var encoder: OrtSession? = null
    private var decoder: OrtSession? = null
    private var vocabulary: Array<String> = emptyArray()

    fun prepare() {
        if (!modelStore.isInstalled()) {
            throw IllegalStateException("Manga OCR model belum diunduh")
        }
        if (encoder != null && decoder != null && vocabulary.isNotEmpty()) return
        encoder = environment.createSession(modelStore.encoderFile().absolutePath, sessionOptions)
        decoder = environment.createSession(modelStore.decoderFile().absolutePath, sessionOptions)
        vocabulary = loadVocabulary(modelStore.tokenizerFile())
    }

    fun recognize(bitmap: Bitmap, box: android.graphics.Rect): String {
        prepare()
        val input = cropToModelInput(bitmap, box)
        try {
            val hidden = encode(input)
            return decode(hidden)
        } finally {
            input.recycle()
        }
    }

    private fun encode(bitmap: Bitmap): FloatArray3 {
        val pixels = IntArray(224 * 224)
        bitmap.getPixels(pixels, 0, 224, 0, 0, 224, 224)
        val plane = 224 * 224
        val data = FloatArray(plane * 3)
        for (index in pixels.indices) {
            val color = pixels[index]
            data[index] = (Color.red(color) / 255f) * 2f - 1f
            data[plane + index] = (Color.green(color) / 255f) * 2f - 1f
            data[plane * 2 + index] = (Color.blue(color) / 255f) * 2f - 1f
        }
        val tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(data), longArrayOf(1, 3, 224, 224))
        try {
            val result = encoder!!.run(mapOf("pixel_values" to tensor))
            try {
                val value = result["last_hidden_state"].get().value as Array<Array<FloatArray>>
                val seq = value[0].size
                val hidden = value[0].firstOrNull()?.size ?: 0
                val flat = FloatArray(seq * hidden)
                for (i in 0 until seq) {
                    System.arraycopy(value[0][i], 0, flat, i * hidden, hidden)
                }
                return FloatArray3(flat, seq, hidden)
            } finally {
                result.close()
            }
        } finally {
            tensor.close()
        }
    }

    private fun decode(hidden: FloatArray3): String {
        val ids = ArrayList<Long>(50)
        ids.add(2L) // BOS
        repeat(50) {
            val idsArray = LongArray(ids.size) { ids[it] }
            val inputIds = OnnxTensor.createTensor(environment, LongBuffer.wrap(idsArray), longArrayOf(1, idsArray.size.toLong()))
            val hiddenTensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(hidden.data), longArrayOf(1, hidden.sequence.toLong(), hidden.hidden.toLong()))
            try {
                val result = decoder!!.run(
                    mapOf(
                        "input_ids" to inputIds,
                        "encoder_hidden_states" to hiddenTensor
                    )
                )
                try {
                    val logits = result["logits"].get().value as Array<Array<FloatArray>>
                    val last = logits[0][logits[0].lastIndex]
                    var bestIndex = 0
                    var bestScore = Float.NEGATIVE_INFINITY
                    for (index in last.indices) {
                        if (last[index] > bestScore) {
                            bestScore = last[index]
                            bestIndex = index
                        }
                    }
                    if (bestIndex == 3) return idsToText(ids.drop(1))
                    ids.add(bestIndex.toLong())
                } finally {
                    result.close()
                }
            } finally {
                inputIds.close()
                hiddenTensor.close()
            }
        }
        return idsToText(ids.drop(1))
    }

    private fun idsToText(ids: List<Long>): String = buildString {
        ids.forEach { id ->
            val index = id.toInt()
            if (index <= 14 || index >= vocabulary.size) return@forEach
            append(vocabulary[index])
        }
    }.replace("▁", "").trim()

    private fun loadVocabulary(file: File): Array<String> {
        val json = JSONObject(file.readText())
        val vocab = json.getJSONObject("model").getJSONObject("vocab")
        val maxId = vocab.keys().asSequence().map { vocab.getInt(it) }.maxOrNull() ?: 0
        val result = Array(maxId + 1) { "" }
        val keys = vocab.keys()
        while (keys.hasNext()) {
            val token = keys.next()
            result[vocab.getInt(token)] = token
        }
        return result
    }

    private fun cropToModelInput(source: Bitmap, box: android.graphics.Rect): Bitmap {
        val padding = 16
        val left = (box.left - padding).coerceAtLeast(0)
        val top = (box.top - padding).coerceAtLeast(0)
        val right = (box.right + padding).coerceAtMost(source.width)
        val bottom = (box.bottom + padding).coerceAtMost(source.height)
        val canvasBitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        Canvas(canvasBitmap).drawBitmap(
            source,
            android.graphics.Rect(left, top, right, bottom),
            android.graphics.Rect(0, 0, 256, 256),
            null
        )
        return Bitmap.createBitmap(canvasBitmap, 16, 16, 224, 224).also { canvasBitmap.recycle() }
    }

    override fun close() {
        decoder?.close()
        encoder?.close()
        decoder = null
        encoder = null
        vocabulary = emptyArray()
    }

    private data class FloatArray3(val data: FloatArray, val sequence: Int, val hidden: Int)
}
