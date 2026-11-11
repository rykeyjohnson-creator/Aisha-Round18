package com.jarvis.assistant.ui.gamebuilder

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.data.preferences.AppPreferences
import com.jarvis.assistant.databinding.ActivityGameBuilderBinding
import com.jarvis.assistant.network.GenerationResult
import com.jarvis.assistant.network.TextGenerationClient
import com.jarvis.assistant.util.GeneratedCodeUtil
import kotlinx.coroutines.launch

/**
 * Round 3 — Game Builder.
 *
 * Generates a single-file HTML5 Canvas game (plain JS, no external framework needed — this keeps
 * the whole game self-contained in one .html file, which is what "lightweight 2D framework" means
 * here) from a plain-language description, previews it live in a WebView, shows the source, and
 * exports it via the system file picker (Storage Access Framework — no storage permission needed).
 * Generated game projects are kept separate from the Aisha app's own source, as files the user owns.
 */
class GameBuilderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROMPT = "extra_prompt"
        const val EXTRA_AUTO_GENERATE = "extra_auto_generate"
    }

    private lateinit var binding: ActivityGameBuilderBinding
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
        binding = ActivityGameBuilderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPreferences(this)

        binding.webPreview.settings.javaScriptEnabled = true

        binding.btnBack.setOnClickListener { finish() }

        binding.btnGenerate.setOnClickListener {
            val prompt = binding.etPrompt.text?.toString()?.trim().orEmpty()
            if (prompt.isEmpty()) {
                Toast.makeText(this, "Describe the game first.", Toast.LENGTH_SHORT).show()
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

        // Round 11: voice-command shortcut ("game banao ek car race wala") arrives here with
        // the prompt already filled in and starts generating immediately.
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
                exportLauncher.launch("game_${System.currentTimeMillis()}.html")
            }
        }
    }

    private fun runGeneration(prompt: String) {
        binding.btnGenerate.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        // A full game is a long AI response (no streaming) — can genuinely take over a minute.
        // Say so up front instead of looking frozen.
        binding.tvError.text = "Generating… ek pura game banane mein 1-2 minute tak lag sakte hain."
        binding.tvError.setTextColor(resources.getColor(com.jarvis.assistant.R.color.text_muted, theme))
        binding.tvError.visibility = View.VISIBLE

        val fullPrompt = """
            Generate a complete, single-file HTML5 game using plain HTML, CSS and JavaScript with
            the <canvas> element only (no external libraries, no network requests). The game: $prompt
            Return ONLY the full HTML file contents inside one ```html code fence, nothing else.
        """.trimIndent()

        // Switch to code view so the user watches it being written live, token by token —
        // real proof of work instead of a silent wait, which was the actual complaint.
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

                    androidx.appcompat.app.AlertDialog.Builder(this@GameBuilderActivity)
                        .setTitle("Game ban gaya!")
                        .setMessage("Khelna chahte ho?")
                        .setPositiveButton("Khelo") { _, _ ->
                            showingCode = false
                            binding.codeScroll.visibility = View.GONE
                            binding.webPreview.visibility = View.VISIBLE
                            binding.btnToggleCode.text = "View code"
                        }
                        .setNegativeButton("Baad mein", null)
                        .show()
                }
                is GenerationResult.Error -> {
                    // Partial code already streamed stays visible on screen as proof of progress.
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
