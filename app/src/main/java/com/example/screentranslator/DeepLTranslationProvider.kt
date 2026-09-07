package com.example.screentranslator

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class DeepLTranslationProvider(
    private val apiKey: String,
    private val sourceLanguage: String,
    private val targetLanguage: String
) : TranslationProvider {

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val FREE_ENDPOINT = "https://api-free.deepl.com/v2/translate"
        private const val PRO_ENDPOINT = "https://api.deepl.com/v2/translate"
        private const val FREE_USAGE_ENDPOINT = "https://api-free.deepl.com/v2/usage"
        private const val PRO_USAGE_ENDPOINT = "https://api.deepl.com/v2/usage"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun prepare(onReady: () -> Unit, onFailure: (Exception) -> Unit) {
        if (apiKey.isBlank()) {
            onFailure(IllegalStateException("DeepL API key belum diatur"))
            return
        }

        // Validate the key without sending a translation request. Using /usage
        // here avoids consuming characters every time a translation session starts.
        val request = Request.Builder()
            .url(usageEndpointForKey())
            .header("Authorization", "DeepL-Auth-Key $apiKey")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onFailure(e)

            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use {
                    val responseBody = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        onFailure(
                            IllegalStateException(
                                "DeepL menolak API key (HTTP ${it.code}): ${responseBody.take(160)}"
                            )
                        )
                        return
                    }
                    try {
                        JSONObject(responseBody)
                        onReady()
                    } catch (e: Exception) {
                        onFailure(IllegalStateException("Respons DeepL usage tidak valid", e))
                    }
                }
            }
        })
    }

    override fun translate(
        text: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (apiKey.isBlank()) {
            onFailure(IllegalStateException("DeepL API key belum diatur"))
            return
        }

        val json = JSONObject()
            .put("text", org.json.JSONArray().put(text))
            .put("target_lang", deepLCode(targetLanguage))
            .apply {
                val source = deepLCode(sourceLanguage)
                if (source != null) put("source_lang", source)
            }
            .toString()

        val request = Request.Builder()
            .url(endpointForKey())
            .header("Authorization", "DeepL-Auth-Key $apiKey")
            .post(json.toRequestBody(JSON))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onFailure(e)

            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use {
                    val responseBody = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        onFailure(
                            IllegalStateException(
                                "DeepL gagal (HTTP ${it.code}): ${responseBody.take(160)}"
                            )
                        )
                        return
                    }
                    try {
                        val translations = JSONObject(responseBody).getJSONArray("translations")
                        val translated = translations.getJSONObject(0).getString("text")
                        onSuccess(translated)
                    } catch (e: Exception) {
                        onFailure(IllegalStateException("Respons DeepL tidak valid", e))
                    }
                }
            }
        })
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun endpointForKey(): String =
        if (apiKey.endsWith(":fx", ignoreCase = true)) FREE_ENDPOINT else PRO_ENDPOINT

    private fun usageEndpointForKey(): String =
        if (apiKey.endsWith(":fx", ignoreCase = true)) FREE_USAGE_ENDPOINT else PRO_USAGE_ENDPOINT

    private fun deepLCode(language: String): String? = when (language) {
        "Jepang" -> "JA"
        "Mandarin (China)" -> "ZH"
        "Inggris" -> "EN-US"
        "Indonesia" -> "ID"
        else -> null
    }
}
