package com.example.screentranslator

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class MangaOcrModelStore(context: Context) {
    companion object {
        private const val BASE_URL = "https://huggingface.co/l0wgear/manga-ocr-2025-onnx/resolve/main/"
        private const val ENCODER = "encoder_model.onnx"
        private const val DECODER = "decoder_model.onnx"
        private const val TOKENIZER = "tokenizer.json"
    }

    private val root = File(context.applicationContext.filesDir, "manga_ocr_2025")
    private val client = OkHttpClient()

    fun isInstalled(): Boolean = File(root, ENCODER).length() > 20L * 1024L * 1024L &&
        File(root, DECODER).length() > 100L * 1024L * 1024L &&
        File(root, TOKENIZER).length() > 10_000L

    fun encoderFile() = File(root, ENCODER)
    fun decoderFile() = File(root, DECODER)
    fun tokenizerFile() = File(root, TOKENIZER)

    suspend fun download(onProgress: (name: String, percent: Int) -> Unit) = withContext(Dispatchers.IO) {
        root.mkdirs()
        downloadOne(ENCODER, onProgress)
        downloadOne(DECODER, onProgress)
        downloadOne(TOKENIZER, onProgress)
    }

    fun delete() {
        root.deleteRecursively()
    }

    private fun downloadOne(name: String, onProgress: (String, Int) -> Unit) {
        val target = File(root, name)
        val part = File(root, "$name.part")
        val request = Request.Builder().url(BASE_URL + name + "?download=true").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Manga OCR download gagal: HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("Manga OCR response kosong: $name")
            val total = body.contentLength()
            var written = 0L
            body.byteStream().use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                        written += count
                        val percent = if (total > 0) ((written * 100L) / total).toInt().coerceIn(0, 100) else -1
                        onProgress(name, percent)
                    }
                    output.fd.sync()
                }
            }
            if (part.length() <= 0L) throw IllegalStateException("Manga OCR file kosong: $name")
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) throw IllegalStateException("Gagal memasang model Manga OCR: $name")
        }
    }
}
