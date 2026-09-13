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

class OpenRouterTranslationProvider(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String,
    private val sourceLanguage: String,
    private val targetLanguage: String
) : TranslationProvider {

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun prepare(onReady: () -> Unit, onFailure: (Exception) -> Unit) {
        if (apiKey.isBlank()) {
            onFailure(IllegalStateException("OpenRouter API key belum diatur"))
            return
        }
        if (model.isBlank()) {
            onFailure(IllegalStateException("Model OpenRouter belum diatur"))
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
            onFailure(IllegalStateException("OpenRouter API key belum diatur"))
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

    private fun endpoint(): String {
        val base = baseUrl.trimEnd('/')
        return "$base/chat/completions"
    }

    private fun request(
        prompt: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val messages = JSONArray()
            .put(JSONObject().put("role", "user").put("content", prompt))

        val payload = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", 0.1)
            .put("max_tokens", 512)
            .toString()

        val request = Request.Builder()
            .url(endpoint())
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", "https://screentranslator.app")
            .header("X-Title", "Screen Translator")
            .post(payload.toRequestBody(JSON))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onFailure(e)

            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        onFailure(
                            IllegalStateException(
                                "OpenRouter gagal (HTTP ${it.code}): ${body.take(180)}"
                            )
                        )
                        return
                    }
                    try {
                        val choices = JSONObject(body).getJSONArray("choices")
                        val message = choices.getJSONObject(0).getJSONObject("message")
                        val result = message.getString("content").trim()
                        if (result.isBlank()) {
                            throw IllegalStateException("OpenRouter tidak mengembalikan teks")
                        }
                        onSuccess(result)
                    } catch (e: Exception) {
                        onFailure(IllegalStateException("Respons OpenRouter tidak valid", e))
                    }
                }
            }
        })
    }
}
