package com.example.screentranslator

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
        private const val MODEL = "gemini-3.5-flash"
        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
        private val JSON = "application/json; charset=utf-8".toMediaType()
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
        request("Translate 'Hello' from English to Indonesia. Return only the translation.", onSuccess = { onReady() }, onFailure = onFailure)
    }

    override fun translate(text: String, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        if (apiKey.isBlank()) {
            onFailure(IllegalStateException("Gemini API key belum diatur"))
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

    private fun request(prompt: String, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val parts = JSONArray().put(JSONObject().put("text", prompt))
        val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", parts))
        val payload = JSONObject().put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.1).put("maxOutputTokens", 512))
            .toString()
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("x-goog-api-key", apiKey)
            .post(payload.toRequestBody(JSON))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onFailure(e)
            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        onFailure(IllegalStateException("Gemini gagal (HTTP ${it.code}): ${body.take(180)}"))
                        return
                    }
                    try {
                        val candidates = JSONObject(body).getJSONArray("candidates")
                        val partsArray = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                        val result = buildString {
                            for (index in 0 until partsArray.length()) {
                                val part = partsArray.getJSONObject(index)
                                if (part.has("text")) append(part.getString("text"))
                            }
                        }.trim()
                        if (result.isBlank()) throw IllegalStateException("Gemini tidak mengembalikan teks")
                        onSuccess(result)
                    } catch (e: Exception) {
                        onFailure(IllegalStateException("Respons Gemini tidak valid", e))
                    }
                }
            }
        })
    }
}
