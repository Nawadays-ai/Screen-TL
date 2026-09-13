package com.example.screentranslator

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private lateinit var spinnerManualProvider: Spinner
    private lateinit var spinnerApiProvider: Spinner
    private lateinit var etApiKey: EditText
    private lateinit var etBaseUrl: EditText
    private lateinit var etModel: EditText
    private lateinit var labelBaseUrl: TextView
    private lateinit var labelModel: TextView
    private lateinit var tvApiStatus: TextView
    private lateinit var btnApiCheck: Button
    private lateinit var btnApiUse: Button
    private lateinit var btnApiDisable: Button

    private val apiProviders = arrayOf("Gemini AI", "DeepL API", "OpenRouter")
    private var selectedApiProvider = apiProviders[0]
    private var updatingApiField = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        ApiSettings.initialize(applicationContext)

        spinnerManualProvider = findViewById(R.id.spinnerManualProvider)
        spinnerApiProvider = findViewById(R.id.spinnerApiProvider)
        etApiKey = findViewById(R.id.etApiKey)
        etBaseUrl = findViewById(R.id.etBaseUrl)
        etModel = findViewById(R.id.etModel)
        labelBaseUrl = findViewById(R.id.labelBaseUrl)
        labelModel = findViewById(R.id.labelModel)
        tvApiStatus = findViewById(R.id.tvApiStatus)
        btnApiCheck = findViewById(R.id.btnApiCheck)
        btnApiUse = findViewById(R.id.btnApiUse)
        btnApiDisable = findViewById(R.id.btnApiDisable)
        findViewById<Button>(R.id.btnBackSettings).setOnClickListener { finish() }

        val manualProviders = arrayOf(ApiSettings.PROVIDER_ML_KIT)
        spinnerManualProvider.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, manualProviders
        )
        spinnerManualProvider.setSelection(0)
        spinnerManualProvider.setOnItemSelectedListener(
            SimpleItemSelectedListener { ApiSettings.setManualProvider(ApiSettings.PROVIDER_ML_KIT) }
        )

        spinnerApiProvider.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, apiProviders
        )
        val preferredApi = when {
            ApiSettings.isOpenRouterEnabled() -> "OpenRouter"
            ApiSettings.isDeepLEnabled() -> "DeepL API"
            else -> "Gemini AI"
        }
        selectedApiProvider = preferredApi
        spinnerApiProvider.setSelection(apiProviders.indexOf(preferredApi))
        spinnerApiProvider.setOnItemSelectedListener(SimpleItemSelectedListener { position ->
            if (position in apiProviders.indices) {
                selectedApiProvider = apiProviders[position]
                loadSelectedApiKey()
                renderApi()
            }
        })

        etApiKey.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        etApiKey.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (updatingApiField) return
                val key = s?.toString().orEmpty()
                when (selectedApiProvider) {
                    "Gemini AI" -> ApiSettings.setGeminiKey(key)
                    "DeepL API" -> ApiSettings.setDeepLKey(key)
                    "OpenRouter" -> ApiSettings.setOpenRouterKey(key)
                }
                renderApi()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        etBaseUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (updatingApiField) return
                if (selectedApiProvider == "OpenRouter") {
                    ApiSettings.setOpenRouterBaseUrl(s?.toString().orEmpty())
                    renderApi()
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        etModel.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (updatingApiField) return
                if (selectedApiProvider == "OpenRouter") {
                    ApiSettings.setOpenRouterModel(s?.toString().orEmpty())
                    renderApi()
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        btnApiCheck.setOnClickListener { checkSelectedApi() }
        btnApiUse.setOnClickListener {
            when (selectedApiProvider) {
                "Gemini AI" -> ApiSettings.setGeminiEnabled(true)
                "DeepL API" -> ApiSettings.setDeepLEnabled(true)
                "OpenRouter" -> ApiSettings.setOpenRouterEnabled(true)
            }
            renderApi()
        }
        btnApiDisable.setOnClickListener {
            when (selectedApiProvider) {
                "Gemini AI" -> ApiSettings.setGeminiEnabled(false)
                "DeepL API" -> ApiSettings.setDeepLEnabled(false)
                "OpenRouter" -> ApiSettings.setOpenRouterEnabled(false)
            }
            renderApi()
        }

        loadSelectedApiKey()
        renderApi()
    }

    private fun loadSelectedApiKey() {
        updatingApiField = true
        val key = when (selectedApiProvider) {
            "Gemini AI" -> ApiSettings.getGeminiKey().orEmpty()
            "DeepL API" -> ApiSettings.getDeepLKey().orEmpty()
            "OpenRouter" -> ApiSettings.getOpenRouterKey().orEmpty()
            else -> ""
        }
        etApiKey.setText(key)
        etApiKey.setSelection(etApiKey.text.length)

        if (selectedApiProvider == "OpenRouter") {
            etBaseUrl.setText(ApiSettings.getOpenRouterBaseUrl())
            etModel.setText(ApiSettings.getOpenRouterModel())
        } else {
            etBaseUrl.setText("")
            etModel.setText("")
        }
        updatingApiField = false
    }

    private fun checkSelectedApi() {
        val key = etApiKey.text.toString().trim()
        if (key.isBlank()) return
        tvApiStatus.text = "Memeriksa API..."

        when (selectedApiProvider) {
            "Gemini AI" -> {
                GeminiTranslationProvider(key, "Inggris", "Indonesia").prepare(
                    onReady = {
                        runOnUiThread {
                            ApiSettings.setGeminiKey(key)
                            ApiSettings.setGeminiVerified(true)
                            renderApi("API dapat digunakan")
                        }
                    },
                    onFailure = { error ->
                        runOnUiThread {
                            ApiSettings.setGeminiVerified(false)
                            ApiSettings.setGeminiEnabled(false)
                            renderApi("API tidak dapat digunakan${error.message?.let { ": $it" } ?: ""}")
                        }
                    }
                )
            }
            "DeepL API" -> {
                DeepLTranslationProvider(key, "Inggris", "Indonesia").prepare(
                    onReady = {
                        runOnUiThread {
                            ApiSettings.setDeepLKey(key)
                            ApiSettings.setDeepLVerified(true)
                            renderApi("API dapat digunakan")
                        }
                    },
                    onFailure = { error ->
                        runOnUiThread {
                            ApiSettings.setDeepLVerified(false)
                            ApiSettings.setDeepLEnabled(false)
                            renderApi("API tidak dapat digunakan${error.message?.let { ": $it" } ?: ""}")
                        }
                    }
                )
            }
            "OpenRouter" -> {
                val baseUrl = etBaseUrl.text.toString().trim()
                val model = etModel.text.toString().trim()
                if (baseUrl.isBlank() || model.isBlank()) {
                    tvApiStatus.text = "Base URL dan Model wajib diisi"
                    return
                }
                OpenRouterTranslationProvider(key, baseUrl, model, "Inggris", "Indonesia").prepare(
                    onReady = {
                        runOnUiThread {
                            ApiSettings.setOpenRouterKey(key)
                            ApiSettings.setOpenRouterBaseUrl(baseUrl)
                            ApiSettings.setOpenRouterModel(model)
                            ApiSettings.setOpenRouterVerified(true)
                            renderApi("API dapat digunakan")
                        }
                    },
                    onFailure = { error ->
                        runOnUiThread {
                            ApiSettings.setOpenRouterVerified(false)
                            ApiSettings.setOpenRouterEnabled(false)
                            renderApi("API tidak dapat digunakan${error.message?.let { ": $it" } ?: ""}")
                        }
                    }
                )
            }
        }
    }

    private fun renderApi(message: String? = null) {
        val isOpenRouter = selectedApiProvider == "OpenRouter"
        labelBaseUrl.visibility = if (isOpenRouter) View.VISIBLE else View.GONE
        etBaseUrl.visibility = if (isOpenRouter) View.VISIBLE else View.GONE
        labelModel.visibility = if (isOpenRouter) View.VISIBLE else View.GONE
        etModel.visibility = if (isOpenRouter) View.VISIBLE else View.GONE

        val key = etApiKey.text.toString().trim()
        if (key.isBlank()) {
            tvApiStatus.text = ""
            btnApiCheck.visibility = Button.GONE
            btnApiUse.visibility = Button.GONE
            btnApiDisable.visibility = Button.GONE
            return
        }
        val enabled = when (selectedApiProvider) {
            "Gemini AI" -> ApiSettings.isGeminiEnabled()
            "DeepL API" -> ApiSettings.isDeepLEnabled()
            "OpenRouter" -> ApiSettings.isOpenRouterEnabled()
            else -> false
        }
        val verified = when (selectedApiProvider) {
            "Gemini AI" -> ApiSettings.isGeminiVerified()
            "DeepL API" -> ApiSettings.isDeepLVerified()
            "OpenRouter" -> ApiSettings.isOpenRouterVerified()
            else -> false
        }
        tvApiStatus.text = message ?: when {
            enabled -> "API aktif"
            verified -> "API dapat digunakan"
            else -> "Belum dicek"
        }
        btnApiCheck.visibility = Button.VISIBLE
        btnApiUse.visibility = if (verified && !enabled) Button.VISIBLE else Button.GONE
        btnApiDisable.visibility = if (enabled) Button.VISIBLE else Button.GONE
    }
}

private class SimpleItemSelectedListener(private val action: (Int) -> Unit) :
    android.widget.AdapterView.OnItemSelectedListener {
    override fun onItemSelected(
        parent: android.widget.AdapterView<*>?,
        view: android.view.View?,
        position: Int,
        id: Long
    ) = action(position)

    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
}
5. activity_settings.xml (Modifikasi)
Kamu perlu menambahkan 2 kotak input baru setelah etApiKey. Cari bagian ini di layout XML-mu:

xml
<EditText
    android:id="@+id/etApiKey"
    ... />
Lalu tambahkan setelah etApiKey:

xml
<TextView
    android:id="@+id/labelBaseUrl"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="Base URL"
    android:visibility="gone"
    android:layout_marginTop="12dp" />

<EditText
    android:id="@+id/etBaseUrl"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:hint="https://openrouter.ai/api/v1"
    android:inputType="textUri"
    android:visibility="gone" />

<TextView
    android:id="@+id/labelModel"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="Model"
    android:visibility="gone"
    android:layout_marginTop="12dp" />

<EditText
    android:id="@+id/etModel"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:hint="deepseek/deepseek-chat-v3.1:free"
    android:inputType="text"
    android:visibility="gone" />
Penting: Pastikan etApiKey dan tvApiStatus, btnApiCheck, btnApiUse, btnApiDisable sudah ada di layout XML-mu. Kalau tidak ada, kamu perlu menambahkannya juga.

📌 Setelah Selesai
Copy semua file ke proyekmu di VS Code.

Build APK di GitHub Actions (sesuai rencanamu).

Test di HP: buka Settings, pilih OpenRouter, isi API key + base URL + model, klik Cek API, lalu Gunakan API.

Kalau ada error saat build, kirim pesan error-nya ke sini. Kalau ada bagian kode yang tidak cocok dengan struktur proyekmu (misalnya nama file layout berbeda), beritahu saya juga.

Create provider itu yang ini?
private fun createProvider(manualProvider: String): TranslationProvider {
        if (ApiSettings.isGeminiEnabled()) {
            val key = ApiSettings.getGeminiKey()
            if (!key.isNullOrBlank()) return GeminiTranslationProvider(key, sourceLanguage, targetLanguage)
        }
        if (ApiSettings.isDeepLEnabled()) {
            val key = ApiSettings.getDeepLKey()
            if (!key.isNullOrBlank()) return DeepLTranslationProvider(key, sourceLanguage, targetLanguage)
        }
        return when (manualProvider) {
            ApiSettings.PROVIDER_DEEPL -> {
                val key = ApiSettings.getDeepLKey()
                if (!key.isNullOrBlank() && ApiSettings.isDeepLVerified()) {
                    DeepLTranslationProvider(key, sourceLanguage, targetLanguage)
                } else {
                    MissingApiProvider("DeepL API belum diaktifkan di Settings")
                }
            }
            else -> MlKitTranslationProvider(sourceLanguage, targetLanguage)
        }
    }
Ya, benar. Itu adalah fungsi createProvider() yang ada di dalam TranslationManager.kt. Fungsi itulah yang menentukan provider mana yang dipakai berdasarkan pengaturan yang aktif.

Ganti seluruh fungsi itu dengan versi baru yang sudah saya tuliskan, lalu tambahkan juga case OpenRouterTranslationProvider di getProviderName().

📍 Lokasi di TranslationManager.kt
Di file TranslationManager.kt yang kamu kirim sebelumnya, createProvider() ada di bagian bawah. Strukturnya seperti ini:

kotlin
class TranslationManager(...) {
    // ... fungsi prepare, translate, getProviderName, close ...

    private fun createProvider(manualProvider: String): TranslationProvider {
        // ... kode lama ...
    }

    private class MissingApiProvider(...) : TranslationProvider {
        // ... kode lama ...
    }
}
🎯 Yang Perlu Diganti
1. Ganti seluruh isi createProvider() dengan versi baru:

kotlin
private fun createProvider(manualProvider: String): TranslationProvider {
    if (ApiSettings.isOpenRouterEnabled()) {
        val key = ApiSettings.getOpenRouterKey()
        if (!key.isNullOrBlank()) {
            return OpenRouterTranslationProvider(
                apiKey = key,
                baseUrl = ApiSettings.getOpenRouterBaseUrl(),
                model = ApiSettings.getOpenRouterModel(),
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage
            )
        }
    }
    if (ApiSettings.isGeminiEnabled()) {
        val key = ApiSettings.getGeminiKey()
        if (!key.isNullOrBlank()) return GeminiTranslationProvider(key, sourceLanguage, targetLanguage)
    }
    if (ApiSettings.isDeepLEnabled()) {
        val key = ApiSettings.getDeepLKey()
        if (!key.isNullOrBlank()) return DeepLTranslationProvider(key, sourceLanguage, targetLanguage)
    }
    return when (manualProvider) {
        ApiSettings.PROVIDER_DEEPL -> {
            val key = ApiSettings.getDeepLKey()
            if (!key.isNullOrBlank() && ApiSettings.isDeepLVerified()) {
                DeepLTranslationProvider(key, sourceLanguage, targetLanguage)
            } else {
                MissingApiProvider("DeepL API belum diaktifkan di Settings")
            }
        }
        else -> MlKitTranslationProvider(sourceLanguage, targetLanguage)
    }
}
