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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var spinnerLocalEngine: Spinner
    private lateinit var tvLocalEngineStatus: TextView
    private lateinit var btnLocalDownload: Button
    private lateinit var btnLocalUse: Button
    private lateinit var btnLocalDisable: Button
    private lateinit var btnLocalDelete: Button
    private lateinit var spinnerManualProvider: Spinner
    private lateinit var spinnerApiProvider: Spinner
    private lateinit var etApiKey: EditText
    private lateinit var tvApiStatus: TextView
    private lateinit var btnApiCheck: Button
    private lateinit var btnApiUse: Button
    private lateinit var btnApiDisable: Button

    private val localEngines = arrayOf(ApiSettings.PROVIDER_ML_KIT, ApiSettings.PROVIDER_QWEN)
    private val apiProviders = arrayOf("Gemini AI", "DeepL API")
    private var selectedLocalEngine = ApiSettings.PROVIDER_ML_KIT
    private var selectedApiProvider = apiProviders[0]
    private var updatingApiField = false
    private var localDownloadRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        ApiSettings.initialize(applicationContext)
        LocalModelStore.initialize(applicationContext)

        spinnerLocalEngine = findViewById(R.id.spinnerLocalEngine)
        tvLocalEngineStatus = findViewById(R.id.tvLocalEngineStatus)
        btnLocalDownload = findViewById(R.id.btnLocalDownload)
        btnLocalUse = findViewById(R.id.btnLocalUse)
        btnLocalDisable = findViewById(R.id.btnLocalDisable)
        btnLocalDelete = findViewById(R.id.btnLocalDelete)
        spinnerManualProvider = findViewById(R.id.spinnerManualProvider)
        spinnerApiProvider = findViewById(R.id.spinnerApiProvider)
        etApiKey = findViewById(R.id.etApiKey)
        tvApiStatus = findViewById(R.id.tvApiStatus)
        btnApiCheck = findViewById(R.id.btnApiCheck)
        btnApiUse = findViewById(R.id.btnApiUse)
        btnApiDisable = findViewById(R.id.btnApiDisable)
        findViewById<Button>(R.id.btnBackSettings).setOnClickListener { finish() }

        spinnerLocalEngine.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, localEngines)
        selectedLocalEngine = ApiSettings.getLocalEngine()
        spinnerLocalEngine.setSelection(localEngines.indexOf(selectedLocalEngine).coerceAtLeast(0))
        spinnerLocalEngine.setOnItemSelectedListener(SimpleItemSelectedListener { position ->
            if (position in localEngines.indices) {
                selectedLocalEngine = localEngines[position]
                renderLocalEngine()
            }
        })
        btnLocalDownload.setOnClickListener { downloadSelectedLocalModel() }
        btnLocalUse.setOnClickListener {
            if (selectedLocalEngine == ApiSettings.PROVIDER_QWEN && LocalModelStore.isQwenInstalled()) {
                ApiSettings.setLocalEngine(ApiSettings.PROVIDER_QWEN)
                renderLocalEngine()
            }
        }
        btnLocalDisable.setOnClickListener {
            ApiSettings.setLocalEngine(ApiSettings.PROVIDER_ML_KIT)
            selectedLocalEngine = ApiSettings.PROVIDER_ML_KIT
            spinnerLocalEngine.setSelection(0)
            renderLocalEngine()
        }
        btnLocalDelete.setOnClickListener {
            if (selectedLocalEngine == ApiSettings.PROVIDER_QWEN && ApiSettings.getLocalEngine() != ApiSettings.PROVIDER_QWEN) {
                LocalModelStore.deleteQwen()
                renderLocalEngine()
            }
        }

        val manualProviders = arrayOf(ApiSettings.PROVIDER_ML_KIT)
        spinnerManualProvider.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, manualProviders)
        spinnerManualProvider.setSelection(0)
        spinnerManualProvider.setOnItemSelectedListener(SimpleItemSelectedListener { ApiSettings.setManualProvider(ApiSettings.PROVIDER_ML_KIT) })

        spinnerApiProvider.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, apiProviders)
        val preferredApi = if (ApiSettings.isDeepLEnabled()) "DeepL API" else "Gemini AI"
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
                if (selectedApiProvider == "Gemini AI") ApiSettings.setGeminiKey(key) else ApiSettings.setDeepLKey(key)
                renderApi()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        btnApiCheck.setOnClickListener { checkSelectedApi() }
        btnApiUse.setOnClickListener {
            if (selectedApiProvider == "Gemini AI") ApiSettings.setGeminiEnabled(true) else ApiSettings.setDeepLEnabled(true)
            renderApi()
        }
        btnApiDisable.setOnClickListener {
            if (selectedApiProvider == "Gemini AI") ApiSettings.setGeminiEnabled(false) else ApiSettings.setDeepLEnabled(false)
            renderApi()
        }

        loadSelectedApiKey()
        renderLocalEngine()
        renderApi()
    }

    private fun renderLocalEngine(message: String? = null) {
        val active = ApiSettings.getLocalEngine()
        val qwenInstalled = LocalModelStore.isQwenInstalled()
        val isQwen = selectedLocalEngine == ApiSettings.PROVIDER_QWEN
        val activeQwen = active == ApiSettings.PROVIDER_QWEN

        spinnerLocalEngine.isEnabled = !activeQwen && !localDownloadRunning
        if (localDownloadRunning) {
            tvLocalEngineStatus.text = message ?: "Mengunduh model..."
            btnLocalDownload.visibility = View.VISIBLE
            btnLocalDownload.isEnabled = false
            btnLocalUse.visibility = View.GONE
            btnLocalDisable.visibility = View.GONE
            btnLocalDelete.visibility = View.GONE
            return
        }

        if (selectedLocalEngine == ApiSettings.PROVIDER_ML_KIT) {
            tvLocalEngineStatus.text = "Google ML Kit — engine default"
            btnLocalDownload.visibility = View.GONE
            btnLocalUse.visibility = View.GONE
            btnLocalDisable.visibility = if (activeQwen) View.VISIBLE else View.GONE
            btnLocalDelete.visibility = View.GONE
            return
        }

        tvLocalEngineStatus.text = message ?: when {
            activeQwen -> "Sedang aktif — model hanya di-load saat service menerjemahkan"
            qwenInstalled -> "Model terpasang"
            else -> "Belum diunduh · sekitar 350 MB"
        }
        btnLocalDownload.visibility = if (!qwenInstalled) View.VISIBLE else View.GONE
        btnLocalUse.visibility = if (qwenInstalled && !activeQwen) View.VISIBLE else View.GONE
        btnLocalDisable.visibility = if (activeQwen) View.VISIBLE else View.GONE
        btnLocalDelete.visibility = if (qwenInstalled && !activeQwen) View.VISIBLE else View.GONE
        btnLocalDownload.isEnabled = isQwen
    }

    private fun downloadSelectedLocalModel() {
        if (selectedLocalEngine != ApiSettings.PROVIDER_QWEN || localDownloadRunning) return
        localDownloadRunning = true
        renderLocalEngine("Menyiapkan download Qwen...")
        lifecycleScope.launch {
            try {
                LocalModelStore.downloadQwen { downloaded, total ->
                    runOnUiThread {
                        val percent = if (total > 0) ((downloaded * 100L) / total).toInt() else 0
                        renderLocalEngine("Mengunduh model Qwen... $percent%")
                    }
                }
                localDownloadRunning = false
                renderLocalEngine("Model Qwen berhasil diunduh")
            } catch (error: Exception) {
                localDownloadRunning = false
                renderLocalEngine("Download gagal: ${error.message ?: "error tidak diketahui"}")
            }
        }
    }

    private fun loadSelectedApiKey() {
        val key = if (selectedApiProvider == "Gemini AI") ApiSettings.getGeminiKey().orEmpty() else ApiSettings.getDeepLKey().orEmpty()
        updatingApiField = true
        etApiKey.setText(key)
        etApiKey.setSelection(etApiKey.text.length)
        updatingApiField = false
    }

    private fun checkSelectedApi() {
        val key = etApiKey.text.toString().trim()
        if (key.isBlank()) return
        tvApiStatus.text = "Memeriksa API..."
        if (selectedApiProvider == "Gemini AI") {
            GeminiTranslationProvider(key, "Inggris", "Indonesia").prepare(
                onReady = { runOnUiThread { ApiSettings.setGeminiKey(key); ApiSettings.setGeminiVerified(true); renderApi("API dapat digunakan") } },
                onFailure = { error -> runOnUiThread { ApiSettings.setGeminiVerified(false); ApiSettings.setGeminiEnabled(false); renderApi("API tidak dapat digunakan${error.message?.let { ": $it" } ?: ""}") } }
            )
        } else {
            DeepLTranslationProvider(key, "Inggris", "Indonesia").prepare(
                onReady = { runOnUiThread { ApiSettings.setDeepLKey(key); ApiSettings.setDeepLVerified(true); renderApi("API dapat digunakan") } },
                onFailure = { error -> runOnUiThread { ApiSettings.setDeepLVerified(false); ApiSettings.setDeepLEnabled(false); renderApi("API tidak dapat digunakan${error.message?.let { ": $it" } ?: ""}") } }
            )
        }
    }

    private fun renderApi(message: String? = null) {
        val key = etApiKey.text.toString().trim()
        if (key.isBlank()) {
            tvApiStatus.text = ""
            btnApiCheck.visibility = Button.GONE
            btnApiUse.visibility = Button.GONE
            btnApiDisable.visibility = Button.GONE
            return
        }
        val enabled = if (selectedApiProvider == "Gemini AI") ApiSettings.isGeminiEnabled() else ApiSettings.isDeepLEnabled()
        val verified = if (selectedApiProvider == "Gemini AI") ApiSettings.isGeminiVerified() else ApiSettings.isDeepLVerified()
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

private class SimpleItemSelectedListener(private val action: (Int) -> Unit) : android.widget.AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) = action(position)
    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
}
