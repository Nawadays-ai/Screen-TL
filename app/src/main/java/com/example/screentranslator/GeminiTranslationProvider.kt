package com.example.screentranslator

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiTranslationProvider(
    private val apiKey: String,
    private val sourceLanguage: String,
    private val targetLanguage: String
) : TranslationProvider {

    companion object {
        private const val TAG = "ScreenTL-Gemini"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        // Daftar model untuk rotasi. Index 0 = utama (sticky start).
        val MODEL_QUEUE = listOf(
            "gemini-3.8-flash",
            "gemini-3.7-flash",
            "gemini-3.6-flash",
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-3-flash-preview"
        )

        // Callback untuk toast dari service. Bisa dipasang/dilepas.
        @Volatile
        var onRotationEvent: ((String) -> Unit)? = null

        // Index sticky — dipertahankan antar instance provider.
        @Volatile
        private var stickyIndex: Int = 0

        fun getCurrentModel(): String = MODEL_QUEUE.getOrElse(stickyIndex) { MODEL_QUEUE[0] }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun prepare(onReady: () -> Unit, onFailure: (Exception) -> Unit) {
        if (apiKey.isBlank()) {
            onFailure(IllegalStateException("Gemini API key belum diatur"))
            return
        }
        request(
            "Translate 'Hello' from English to Indonesia. Return only the translation.",
            onSuccess = { onReady() },
            onFailure = onFailure
        )
    }

    override fun translate(
        text: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (apiKey.isBlank()) {
            onFailure(IllegalStateException("Gemini API key belum diatur"))
            return
        }
        if (sourceLanguage == targetLanguage) {
            onSuccess(text)
            return
        }
        val prompt = """
Translate the following text from $sourceLanguage to $targetLanguage.
Return only the translation. Do not explain, summarize, add quotes, or add notes.
Preserve numbers, names, punctuation, and line breaks when appropriate.

TEXT:
$text
""".trimIndent()
        request(prompt, onSuccess, onFailure)
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun endpoint(model: String): String = "$BASE_URL/$model:generateContent"

    private fun request(
        prompt: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val startIndex = stickyIndex
        val totalModels = MODEL_QUEUE.size
        var attempts = 0

        fun attempt(index: Int) {
            if (attempts >= totalModels) {
                val msg = "Semua model Gemini limit. Sudah dicoba ${totalModels} model."
                log(msg)
                onRotationEvent?.invoke(msg)
                onFailure(IllegalStateException(msg))
                return
            }
            val model = MODEL_QUEUE[index]
            log("Mencoba model: $model (attempt ${attempts + 1}/$totalModels)")

            sendRequest(
                model = model,
                prompt = prompt,
                onSuccess = { result ->
                    stickyIndex = index
                    log("Sukses di $model")
                    onSuccess(result)
                },
                onFailure = { ex, httpCode ->
                    if (httpCode == 429) {
                        val msg = "Limit di $model (429), rotasi ke model berikutnya..."
                        log(msg)
                        onRotationEvent?.invoke(msg)
                        attempts++
                        attempt((index + 1) % totalModels)
                    } else {
                        val msg = "Gagal di $model (HTTP $httpCode): ${ex.message}"
                        log(msg)
                        onFailure(IllegalStateException(msg, ex))
                    }
                }
            )
        }

        attempt(startIndex)
    }

    private fun sendRequest(
        model: String,
        prompt: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception, Int) -> Unit
    ) {
        val parts = JSONArray().put(JSONObject().put("text", prompt))
        val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", parts))
        val payload = JSONObject()
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.1).put("maxOutputTokens", 512))
            .toString()

        val request = Request.Builder()
            .url(endpoint(model))
            .header("x-goog-api-key", apiKey)
            .post(payload.toRequestBody(JSON))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onFailure(e, 0)
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        onFailure(
                            IllegalStateException("HTTP ${it.code}: ${body.take(180)}"),
                            it.code
                        )
                        return
                    }
                    try {
                        val candidates = JSONObject(body).getJSONArray("candidates")
                        val partsArray = candidates.getJSONObject(0)
                            .getJSONObject("content").getJSONArray("parts")
                        val result = buildString {
                            for (i in 0 until partsArray.length()) {
                                val part = partsArray.getJSONObject(i)
                                if (part.has("text")) append(part.getString("text"))
                            }
                        }.trim()
                        if (result.isBlank()) {
                            throw IllegalStateException("Gemini tidak mengembalikan teks")
                        }
                        onSuccess(result)
                    } catch (e: Exception) {
                        onFailure(IllegalStateException("Respons Gemini tidak valid", e), it.code)
                    }
                }
            }
        })
    }

    private fun log(message: String) {
        Log.i(TAG, message)
    }
}