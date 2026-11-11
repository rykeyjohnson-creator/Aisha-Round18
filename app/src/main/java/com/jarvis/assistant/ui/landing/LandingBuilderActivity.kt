package com.jarvis.assistant.ui.landing

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.data.preferences.AppPreferences
import com.jarvis.assistant.databinding.ActivityLandingBuilderBinding
import com.jarvis.assistant.network.GenerationResult
import com.jarvis.assistant.network.TextGenerationClient
import com.jarvis.assistant.util.GeneratedCodeUtil
import kotlinx.coroutines.launch

/**
 * Round 3 — Landing Page / Website Builder.
 *
 * Generates a single-file, responsive HTML+CSS+JS page from a plain-language description,
 * previews it live in a WebView, shows the source, and exports via the system file picker
 * (Storage Access Framework — no storage permission needed). This never claims a live deployment
 * happened; it only produces a file the user can host wherever they choose.
 */
class LandingBuilderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROMPT = "extra_prompt"
        const val EXTRA_AUTO_GENERATE = "extra_auto_generate"
    }

    private lateinit var binding: ActivityLandingBuilderBinding
    private lateinit var prefs: AppPreferences
    private var lastHtml: String? = null
    private var showingCode = false

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        val html = lastHtml
        if (uri == null || html == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(html.toByteArray()) }
            Toast.makeText(this, "Exported.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLandingBuilderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPreferences(this)

        binding.webPreview.settings.javaScriptEnabled = true

        binding.btnBack.setOnClickListener { finish() }

        binding.btnGenerate.setOnClickListener {
            val prompt = binding.etPrompt.text?.toString()?.trim().orEmpty()
            if (prompt.isEmpty()) {
                Toast.makeText(this, "Describe the page first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            runGeneration(prompt)
        }

        binding.btnToggleCode.setOnClickListener {
            showingCode = !showingCode
            binding.codeScroll.visibility = if (showingCode) View.VISIBLE else View.GONE
            binding.webPreview.visibility = if (showingCode) View.GONE else View.VISIBLE
            binding.btnToggleCode.text = if (showingCode) "View preview" else "View code"
        }

        // Round 12: voice-command shortcut ("landing page banao dog ka") arrives with the
        // prompt already filled in and starts generating immediately.
        val incomingPrompt = intent.getStringExtra(EXTRA_PROMPT)
        if (!incomingPrompt.isNullOrBlank()) {
            binding.etPrompt.setText(incomingPrompt)
            if (intent.getBooleanExtra(EXTRA_AUTO_GENERATE, false)) {
                runGeneration(incomingPrompt)
            }
        }

        binding.btnExport.setOnClickListener {
            if (lastHtml.isNullOrBlank()) {
                Toast.makeText(this, "Nothing generated yet.", Toast.LENGTH_SHORT).show()
            } else {
                exportLauncher.launch("landing_page_${System.currentTimeMillis()}.html")
            }
        }
    }

    private fun runGeneration(prompt: String) {
        binding.btnGenerate.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.tvError.text = "Generating… ek pura page banane mein 1-2 minute tak lag sakte hain."
        binding.tvError.setTextColor(resources.getColor(com.jarvis.assistant.R.color.text_muted, theme))
        binding.tvError.visibility = View.VISIBLE

        val fullPrompt = """
            Generate a complete, single-file responsive landing page using plain HTML, CSS and
            JavaScript only (no external frameworks or network requests), mobile-friendly with a
            proper viewport meta tag. Page description: $prompt
            Return ONLY the full HTML file contents inside one ```html code fence, nothing else.
        """.trimIndent()

        showingCode = true
        binding.codeScroll.visibility = View.VISIBLE
        binding.webPreview.visibility = View.GONE
        binding.btnToggleCode.text = "View preview"
        binding.tvCode.text = ""

        lifecycleScope.launch {
            val result = TextGenerationClient.generateStreaming(prefs, fullPrompt) { chunk ->
                binding.tvCode.append(chunk)
            }
            when (result) {
                is GenerationResult.Success -> {
                    val html = GeneratedCodeUtil.extractHtml(result.text)
                    lastHtml = html
                    binding.tvCode.text = html
                    binding.webPreview.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                    binding.tvError.visibility = View.GONE
                }
                is GenerationResult.Error -> {
                    binding.tvError.text = result.message
                    binding.tvError.setTextColor(resources.getColor(com.jarvis.assistant.R.color.status_red, theme))
                    binding.tvError.visibility = View.VISIBLE
                }
            }
            binding.btnGenerate.isEnabled = true
            binding.progressBar.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        (binding.webPreview as? WebView)?.destroy()
        super.onDestroy()
    }
}
