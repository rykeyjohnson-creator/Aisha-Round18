package com.jarvis.assistant.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.data.preferences.AppPreferences
import com.jarvis.assistant.databinding.ActivityProviderSettingsBinding
import com.jarvis.assistant.network.GenerationResult
import com.jarvis.assistant.network.TextGenerationClient
import kotlinx.coroutines.launch

/**
 * Round 4 — AI provider selection.
 *
 * Gemini stays the default and its existing Live/voice functionality is untouched by this screen.
 * OpenAI is added as a second, optional text-provider option for the new tool screens (Coding
 * Mode, Game Builder, Landing Page Builder). "Test connection" only reports success after a real
 * API call actually succeeds — never on faith.
 */
class ProviderSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProviderSettingsBinding
    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProviderSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPreferences(this)

        binding.btnBack.setOnClickListener { finish() }

        // Load current state
        if (prefs.aiProvider == "OPENAI") binding.radioOpenAi.isChecked = true else binding.radioGemini.isChecked = true
        binding.etGeminiKey.setText(prefs.apiKey)
        binding.etOpenAiKey.setText(prefs.openAiApiKey)

        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnTest.setOnClickListener { testConnection() }
    }

    private fun saveSettings() {
        try {
            prefs.aiProvider = if (binding.radioOpenAi.isChecked) "OPENAI" else "GEMINI"
            val geminiKey = binding.etGeminiKey.text?.toString().orEmpty()
            val openAiKey = binding.etOpenAiKey.text?.toString().orEmpty()
            if (prefs.canStoreApiKeySecurely) {
                prefs.apiKey = geminiKey
                prefs.openAiApiKey = openAiKey
            } else {
                Toast.makeText(this, "Encrypted key storage is unavailable on this device — keys were not saved.", Toast.LENGTH_LONG).show()
                return
            }
            Toast.makeText(this, "Saved.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not save: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun testConnection() {
        // Save first so the test exercises exactly what's on screen.
        saveSettings()
        binding.progressBar.visibility = View.VISIBLE
        binding.tvResult.visibility = View.GONE
        binding.btnTest.isEnabled = false

        lifecycleScope.launch {
            when (val result = TextGenerationClient.testConnection(prefs)) {
                is GenerationResult.Success -> {
                    binding.tvResult.text = "Connected — provider responded successfully."
                    binding.tvResult.setTextColor(resources.getColor(com.jarvis.assistant.R.color.status_green, theme))
                }
                is GenerationResult.Error -> {
                    binding.tvResult.text = result.message
                    binding.tvResult.setTextColor(resources.getColor(com.jarvis.assistant.R.color.status_red, theme))
                }
            }
            binding.tvResult.visibility = View.VISIBLE
            binding.progressBar.visibility = View.GONE
            binding.btnTest.isEnabled = true
        }
    }
}
