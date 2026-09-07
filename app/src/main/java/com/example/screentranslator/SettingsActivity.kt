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

class SettingsActivity : AppCompatActivity() {
    private lateinit var spinnerManualProvider: Spinner
    private lateinit var etGeminiKey: EditText
    private lateinit var tvGeminiStatus: TextView
    private lateinit var btnGeminiCheck: Button
    private lateinit var btnGeminiUse: Button
    private lateinit var btnGeminiDisable: Button
    private lateinit var etDeepLKey: EditText
    private lateinit var tvDeepLStatus: TextView
    private lateinit var btnDeepLCheck: Button
    private lateinit var btnDeepLUse: Button
    private lateinit var btnDeepLDisable: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        ApiSettings.initialize(applicationContext)
        spinnerManualProvider = findViewById(R.id.spinnerManualProvider)
        etGeminiKey = findViewById(R.id.etGeminiKey); tvGeminiStatus = findViewById(R.id.tvGeminiStatus)
        btnGeminiCheck = findViewById(R.id.btnGeminiCheck); btnGeminiUse = findViewById(R.id.btnGeminiUse); btnGeminiDisable = findViewById(R.id.btnGeminiDisable)
        etDeepLKey = findViewById(R.id.etDeepLKey); tvDeepLStatus = findViewById(R.id.tvDeepLStatus)
        btnDeepLCheck = findViewById(R.id.btnDeepLCheck); btnDeepLUse = findViewById(R.id.btnDeepLUse); btnDeepLDisable = findViewById(R.id.btnDeepLDisable)
        findViewById<Button>(R.id.btnBackSettings).setOnClickListener { finish() }

        val providers = arrayOf(ApiSettings.PROVIDER_ML_KIT, ApiSettings.PROVIDER_DEEPL)
        spinnerManualProvider.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, providers)
        spinnerManualProvider.setSelection(providers.indexOf(ApiSettings.getManualProvider()).coerceAtLeast(0))
        spinnerManualProvider.setOnItemSelectedListener(SimpleItemSelectedListener { position -> ApiSettings.setManualProvider(providers[position]) })

        etGeminiKey.setText(ApiSettings.getGeminiKey().orEmpty())
        etDeepLKey.setText(ApiSettings.getDeepLKey().orEmpty())
        installKeyWatcher(etGeminiKey) { ApiSettings.setGeminiKey(it); renderGemini() }
        installKeyWatcher(etDeepLKey) { ApiSettings.setDeepLKey(it); renderDeepL() }

        btnGeminiCheck.setOnClickListener { checkGemini() }
        btnGeminiUse.setOnClickListener { ApiSettings.setGeminiEnabled(true); renderGemini() }
        btnGeminiDisable.setOnClickListener { ApiSettings.setGeminiEnabled(false); renderGemini() }
        btnDeepLCheck.setOnClickListener { checkDeepL() }
        btnDeepLUse.setOnClickListener { ApiSettings.setDeepLEnabled(true); renderDeepL() }
        btnDeepLDisable.setOnClickListener { ApiSettings.setDeepLEnabled(false); renderDeepL() }
        renderGemini(); renderDeepL()
    }

    private fun installKeyWatcher(field: EditText, onChanged: (String) -> Unit) {
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onChanged(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun checkGemini() {
        val key = etGeminiKey.text.toString().trim(); if (key.isBlank()) return
        tvGeminiStatus.text = "Memeriksa API..."
        GeminiTranslationProvider(key, "Inggris", "Indonesia").prepare(
            onReady = { runOnUiThread { ApiSettings.setGeminiVerified(true); ApiSettings.setGeminiKey(key); renderGemini("API dapat digunakan") } },
            onFailure = { error -> runOnUiThread { ApiSettings.setGeminiVerified(false); ApiSettings.setGeminiEnabled(false); tvGeminiStatus.text = "API tidak dapat digunakan${error.message?.let { ": $it" } ?: ""}"; renderGeminiButtonsOnly() } }
        )
    }

    private fun checkDeepL() {
        val key = etDeepLKey.text.toString().trim(); if (key.isBlank()) return
        tvDeepLStatus.text = "Memeriksa API..."
        DeepLTranslationProvider(key, "Inggris", "Indonesia").prepare(
            onReady = { runOnUiThread { ApiSettings.setDeepLVerified(true); ApiSettings.setDeepLKey(key); renderDeepL("API dapat digunakan") } },
            onFailure = { error -> runOnUiThread { ApiSettings.setDeepLVerified(false); ApiSettings.setDeepLEnabled(false); tvDeepLStatus.text = "API tidak dapat digunakan${error.message?.let { ": $it" } ?: ""}"; renderDeepLButtonsOnly() } }
        )
    }

    private fun renderGemini(successMessage: String? = null) {
        val key = etGeminiKey.text.toString().trim()
        if (key.isBlank()) { tvGeminiStatus.text = ""; btnGeminiCheck.visibility = Button.GONE; btnGeminiUse.visibility = Button.GONE; btnGeminiDisable.visibility = Button.GONE; return }
        if (successMessage != null) tvGeminiStatus.text = successMessage else if (ApiSettings.isGeminiEnabled()) tvGeminiStatus.text = "API aktif" else if (ApiSettings.isGeminiVerified()) tvGeminiStatus.text = "API dapat digunakan" else tvGeminiStatus.text = "Belum dicek"
        btnGeminiCheck.visibility = Button.VISIBLE; btnGeminiUse.visibility = if (ApiSettings.isGeminiVerified() && !ApiSettings.isGeminiEnabled()) Button.VISIBLE else Button.GONE; btnGeminiDisable.visibility = if (ApiSettings.isGeminiEnabled()) Button.VISIBLE else Button.GONE
    }
    private fun renderGeminiButtonsOnly() { btnGeminiCheck.visibility = Button.VISIBLE; btnGeminiUse.visibility = Button.GONE; btnGeminiDisable.visibility = Button.GONE }

    private fun renderDeepL(successMessage: String? = null) {
        val key = etDeepLKey.text.toString().trim()
        if (key.isBlank()) { tvDeepLStatus.text = ""; btnDeepLCheck.visibility = Button.GONE; btnDeepLUse.visibility = Button.GONE; btnDeepLDisable.visibility = Button.GONE; return }
        if (successMessage != null) tvDeepLStatus.text = successMessage else if (ApiSettings.isDeepLEnabled()) tvDeepLStatus.text = "API aktif" else if (ApiSettings.isDeepLVerified()) tvDeepLStatus.text = "API dapat digunakan" else tvDeepLStatus.text = "Belum dicek"
        btnDeepLCheck.visibility = Button.VISIBLE; btnDeepLUse.visibility = if (ApiSettings.isDeepLVerified() && !ApiSettings.isDeepLEnabled()) Button.VISIBLE else Button.GONE; btnDeepLDisable.visibility = if (ApiSettings.isDeepLEnabled()) Button.VISIBLE else Button.GONE
    }
    private fun renderDeepLButtonsOnly() { btnDeepLCheck.visibility = Button.VISIBLE; btnDeepLUse.visibility = Button.GONE; btnDeepLDisable.visibility = Button.GONE }
}

private class SimpleItemSelectedListener(private val action: (Int) -> Unit) : android.widget.AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) = action(position)
    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
}
