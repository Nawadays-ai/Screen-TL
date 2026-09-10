package com.example.screentranslator

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object LocalModelStore {
    const val QWEN_FILE = "Qwen2.5-0.5B-Instruct-Q4_0_4_4.gguf"
    const val QWEN_SIZE_BYTES = 352L * 1024L * 1024L
    private const val QWEN_URL = "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/Qwen2.5-0.5B-Instruct-Q4_0_4_4.gguf?download=true"
    private const val QWEN_SHA256 = "9f2c0af1a4a2a1f1b4ad51d2aa3fb09913ba8821f3aef71541fef56b3c7da8d0"

    private lateinit var root: File
    private val client = OkHttpClient()

    fun initialize(context: Context) {
        if (!::root.isInitialized) {
            root = File(context.applicationContext.filesDir, "translation_models")
            root.mkdirs()
        }
    }

    fun qwenFile(): File {
        check(::root.isInitialized) { "LocalModelStore.initialize(context) must be called first" }
        return File(root, QWEN_FILE)
    }

    fun isQwenInstalled(): Boolean = qwenFile().let { it.isFile && it.length() > 0L }

    fun deleteQwen(): Boolean {
        val file = qwenFile()
        return !file.exists() || file.delete()
    }

    suspend fun downloadQwen(onProgress: (downloaded: Long, total: Long) -> Unit) = withContext(Dispatchers.IO) {
        val destination = qwenFile()
        val partial = File(destination.parentFile, "${destination.name}.part")
        val request = Request.Builder().url(QWEN_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Download gagal: HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("Download tidak memiliki data")
            val total = body.contentLength().takeIf { it > 0 } ?: QWEN_SIZE_BYTES
            var downloaded = 0L
            body.byteStream().use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, total)
                    }
                    output.fd.sync()
                }
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        partial.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actualSha.equals(QWEN_SHA256, ignoreCase = true)) {
            partial.delete()
            throw IllegalStateException("Checksum model tidak cocok")
        }
        if (destination.exists()) destination.delete()
        if (!partial.renameTo(destination)) throw IllegalStateException("Model selesai diunduh tetapi gagal dipindahkan")
    }
}
