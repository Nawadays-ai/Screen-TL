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

        // Avoid a needless API round-trip when the selected languages are identical.
        if (sourceLanguage == targetLanguage) {
            onSuccess(text)
            return
        }

        val targetCode = deepLTargetCode(targetLanguage)
        if (targetCode == null) {
            onFailure(IllegalArgumentException("Bahasa target tidak didukung DeepL: $targetLanguage"))
            return
        }

        val json = JSONObject()
            .put("text", org.json.JSONArray().put(text))
            .put("target_lang", targetCode)
            .apply {
                // DeepL uses EN as a source language; EN-US/EN-GB are target variants.
                // Unknown source values are intentionally omitted so DeepL can auto-detect.
                deepLSourceCode(sourceLanguage)?.let { put("source_lang", it) }
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

    /**
     * Sends every text as one element of DeepL's `text` array.
     *
     * DeepL documents that each array element is translated independently and that results come
     * back in request order, so batching here cannot merge two units or let one unit influence
     * another. Length is therefore asserted against the request rather than trusted blindly.
     */
    override fun translateBatch(
        texts: List<String>,
        onSuccess: (BatchTranslationResult) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (texts.isEmpty()) {
            onSuccess(BatchTranslationResult(emptyMap(), emptyMap()))
            return
        }
        // A blank text is not worth a round trip, but it must not cost the other texts their
        // translation either. Drop the blanks from the request and report them by original index.
        val blankIndexes = texts.indices.filter { texts[it].isBlank() }
        val sendableIndexes = texts.indices.filter { texts[it].isNotBlank() }
        if (sendableIndexes.isEmpty()) {
            onSuccess(BatchTranslationResult(emptyMap(), blankIndexes.associateWith { "Teks kosong" }))
            return
        }
        val sendableTexts = sendableIndexes.map { texts[it] }
        if (sourceLanguage == targetLanguage) {
            val resolved = LinkedHashMap<Int, String>()
            sendableIndexes.forEach { resolved[it] = texts[it] }
            onSuccess(BatchTranslationResult(resolved, blankIndexes.associateWith { "Teks kosong" }))
            return
        }

        val targetCode = deepLTargetCode(targetLanguage)
        if (targetCode == null) {
            onFailure(IllegalArgumentException("Bahasa target tidak didukung DeepL: $targetLanguage"))
            return
        }

        val textArray = JSONArray()
        sendableTexts.forEach { textArray.put(it) }
        val json = JSONObject()
            .put("text", textArray)
            .put("target_lang", targetCode)
            .apply {
                deepLSourceCode(sourceLanguage)?.let { put("source_lang", it) }
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
                        if (translations.length() != sendableTexts.size) {
                            onFailure(
                                IllegalStateException(
                                    "DeepL mengembalikan ${translations.length()} terjemahan untuk ${sendableTexts.size} teks"
                                )
                            )
                            return
                        }
                        val resolved = LinkedHashMap<Int, String>()
                        val failed = LinkedHashMap<Int, String>(blankIndexes.associateWith { "Teks kosong" })
                        for (position in sendableTexts.indices) {
                            // Map back through sendableIndexes so the caller always receives the
                            // index of the text it originally asked about, blanks included.
                            val originalIndex = sendableIndexes[position]
                            val value = translations.getJSONObject(position).optString("text", "")
                            if (value.isBlank()) {
                                failed[originalIndex] = "DeepL mengembalikan teks kosong"
                            } else {
                                resolved[originalIndex] = value
                            }
                        }
                        onSuccess(BatchTranslationResult(resolved, failed))
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

    private fun deepLSourceCode(language: String): String? = when (language) {
        "Jepang" -> "JA"
        "Mandarin (China)" -> "ZH"
        "Inggris" -> "EN"
        "Indonesia" -> "ID"
        else -> null
    }

    private fun deepLTargetCode(language: String): String? = when (language) {
        "Jepang" -> "JA"
        "Mandarin (China)" -> "ZH"
        "Inggris" -> "EN-US"
        "Indonesia" -> "ID"
        else -> null
    }
}
