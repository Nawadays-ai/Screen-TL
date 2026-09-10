package com.example.screentranslator

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var spinnerTranslationMode: Spinner
    private lateinit var tvMangaOcrStatus: TextView
    private lateinit var btnMangaOcrDownload: Button
    private lateinit var btnMangaOcrDelete: Button
    private lateinit var spinnerManualProvider: Spinner
    private lateinit var spinnerApiProvider: Spinner
    private lateinit var etApiKey: EditText
    private lateinit var tvApiStatus: TextView
    private lateinit var btnApiCheck: Button
    private lateinit var btnApiUse: Button
    private lateinit var btnApiDisable: Button

    private val apiProviders = arrayOf("Gemini AI", "DeepL API")
    private var selectedApiProvider = apiProviders[0]
    private var updatingApiField = false
    private lateinit var mangaOcrStore: MangaOcrModelStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        ApiSettings.initialize(applicationContext)
        mangaOcrStore = MangaOcrModelStore(applicationContext)

        spinnerTranslationMode = findViewById(R.id.spinnerTranslationMode)
        tvMangaOcrStatus = findViewById(R.id.tvMangaOcrStatus)
        btnMangaOcrDownload = findViewById(R.id.btnMangaOcrDownload)
        btnMangaOcrDelete = findViewById(R.id.btnMangaOcrDelete)
        spinnerManualProvider = findViewById(R.id.spinnerManualProvider)
        spinnerApiProvider = findViewById(R.id.spinnerApiProvider)
        etApiKey = findViewById(R.id.etApiKey)
        tvApiStatus = findViewById(R.id.tvApiStatus)
        btnApiCheck = findViewById(R.id.btnApiCheck)
        btnApiUse = findViewById(R.id.btnApiUse)
        btnApiDisable = findViewById(R.id.btnApiDisable)
        findViewById<Button>(R.id.btnBackSettings).setOnClickListener { finish() }

        val modes = arrayOf(TranslationModeSettings.NORMAL, TranslationModeSettings.MANGA)
        spinnerTranslationMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modes)
        spinnerTranslationMode.setSelection(modes.indexOf(TranslationModeSettings.getMode()).coerceAtLeast(0))
        spinnerTranslationMode.setOnItemSelectedListener(SimpleItemSelectedListener { position ->
            val mode = modes.getOrNull(position) ?: TranslationModeSettings.NORMAL
            TranslationModeSettings.setMode(mode)
            renderMangaOcr()
        })
        btnMangaOcrDownload.setOnClickListener { downloadMangaOcr() }
        btnMangaOcrDelete.setOnClickListener {
            if (TranslationModeSettings.isMangaMode()) TranslationModeSettings.setMode(TranslationModeSettings.NORMAL)
            mangaOcrStore.delete()
            renderMangaOcr()
        }
        renderMangaOcr()

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
        renderApi()
    }

    private fun renderMangaOcr() {
        val installed = mangaOcrStore.isInstalled()
        val active = TranslationModeSettings.isMangaMode()
        tvMangaOcrStatus.text = when {
            installed && active -> "Manga OCR aktif dan siap. Model berada di storage lokal; dimuat saat OCR dipakai."
            installed -> "Manga OCR siap digunakan. Model sudah diunduh."
            active -> "Manga OCR belum diunduh. Download model sebelum Manual TL mode Manga."
            else -> "Mode Normal aktif."
        }
        btnMangaOcrDownload.visibility = if (installed) Button.GONE else Button.VISIBLE
        btnMangaOcrDelete.visibility = if (installed) Button.VISIBLE else Button.GONE
        btnMangaOcrDownload.isEnabled = !installed
    }

    private fun downloadMangaOcr() {
        btnMangaOcrDownload.isEnabled = false
        btnMangaOcrDelete.isEnabled = false
        tvMangaOcrStatus.text = "Menyiapkan download Manga OCR..."
        lifecycleScope.launch {
            try {
                mangaOcrStore.download { name, percent ->
                    runOnUiThread { tvMangaOcrStatus.text = "Mengunduh $name — ${if (percent >= 0) "$percent%" else "..."}" }
                }
                runOnUiThread { renderMangaOcr() }
            } catch (exception: Exception) {
                runOnUiThread {
                    tvMangaOcrStatus.text = "Download gagal: ${exception.message ?: exception.javaClass.simpleName}"
                    btnMangaOcrDownload.isEnabled = true
                    btnMangaOcrDelete.isEnabled = true
                }
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
