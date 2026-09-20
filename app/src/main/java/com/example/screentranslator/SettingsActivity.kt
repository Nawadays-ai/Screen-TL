package com.example.screentranslator

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SettingsActivity : AppCompatActivity() {
    private lateinit var spinnerManualProvider: Spinner
    private lateinit var spinnerApiProvider: Spinner
    private lateinit var tilApiKey: TextInputLayout
    private lateinit var etApiKey: TextInputEditText
    private lateinit var tilBaseUrl: TextInputLayout
    private lateinit var etBaseUrl: TextInputEditText
    private lateinit var tilModel: TextInputLayout
    private lateinit var etModel: TextInputEditText
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
        tilApiKey = findViewById(R.id.tilApiKey)
        etApiKey = findViewById(R.id.etApiKey)
        tilBaseUrl = findViewById(R.id.tilBaseUrl)
        etBaseUrl = findViewById(R.id.etBaseUrl)
        tilModel = findViewById(R.id.tilModel)
        etModel = findViewById(R.id.etModel)
        tvApiStatus = findViewById(R.id.tvApiStatus)
        btnApiCheck = findViewById(R.id.btnApiCheck)
        btnApiUse = findViewById(R.id.btnApiUse)
        btnApiDisable = findViewById(R.id.btnApiDisable)
        findViewById<ImageButton>(R.id.btnBackSettings).setOnClickListener { finish() }

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
        tilApiKey.setHint("API Key")
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
        tilBaseUrl.visibility = if (isOpenRouter) View.VISIBLE else View.GONE
        tilModel.visibility = if (isOpenRouter) View.VISIBLE else View.GONE

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
