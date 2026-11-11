package com.jarvis.assistant.ui.coding

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.data.preferences.AppPreferences
import com.jarvis.assistant.databinding.ActivityCodingModeBinding
import com.jarvis.assistant.network.GenerationResult
import com.jarvis.assistant.network.TextGenerationClient
import kotlinx.coroutines.launch
import java.io.File

/**
 * Round 2 — Coding Mode.
 *
 * Scope note (so nothing here is overstated): this generates and displays code, and lets the
 * user copy or save it as a file. It does NOT execute, build, or run anything — a phone app has
 * no safe sandbox for that, and the master spec itself says never to run destructive commands
 * automatically. If the model's answer includes shell commands, they are shown as plain text for
 * the user to read and run manually elsewhere — that is the "safe command review step," not an
 * execution feature.
 */
class CodingModeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROMPT = "extra_prompt"
        const val EXTRA_AUTO_GENERATE = "extra_auto_generate"
    }

    private lateinit var binding: ActivityCodingModeBinding
    private lateinit var prefs: AppPreferences
    private var lastGenerated: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCodingModeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPreferences(this)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnGenerate.setOnClickListener {
            val prompt = binding.etPrompt.text?.toString()?.trim().orEmpty()
            if (prompt.isEmpty()) {
                Toast.makeText(this, "Describe what you want coded first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            runGeneration(prompt)
        }

        binding.btnCopy.setOnClickListener {
            val text = lastGenerated
            if (text.isNullOrBlank()) {
                Toast.makeText(this, "Nothing generated yet.", Toast.LENGTH_SHORT).show()
            } else {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Aisha generated code", text))
                Toast.makeText(this, "Copied.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSave.setOnClickListener { saveToFile() }

        // Round 17: voice-command shortcut ("code likho ek car game ka", "write code for X")
        // arrives here with the prompt already filled in and starts generating immediately —
        // same pattern as Game Builder / Landing Builder / Image Generation. This is the fix
        // for Aisha trying to type code into other apps (Chrome, Claude) via accessibility
        // instead of using this screen: now a code request is caught locally and opens here
        // directly, before it ever reaches that fallback behavior.
        val incomingPrompt = intent.getStringExtra(EXTRA_PROMPT)
        if (!incomingPrompt.isNullOrBlank()) {
            binding.etPrompt.setText(incomingPrompt)
            if (intent.getBooleanExtra(EXTRA_AUTO_GENERATE, false)) {
                runGeneration(incomingPrompt)
            }
        }
    }

    private fun runGeneration(prompt: String) {
        binding.btnGenerate.isEnabled = false
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.tvError.visibility = android.view.View.GONE

        val fullPrompt = """
            You are a coding assistant. The user's request may be in Hindi, Hinglish or English.
            Produce clean, correctly formatted code with a short explanation above it.
            If the task genuinely needs a shell/terminal command, show the command as plain text
            clearly labelled "Command to run manually:" — do not claim you ran it.

            User request: $prompt
        """.trimIndent()

        binding.tvOutput.text = ""
        lifecycleScope.launch {
            val result = TextGenerationClient.generateStreaming(prefs, fullPrompt) { chunk ->
                // Live, token-by-token — this is the actual proof it's working, not a canned wait.
                binding.tvOutput.append(chunk)
            }
            when (result) {
                is GenerationResult.Success -> {
                    lastGenerated = result.text
                }
                is GenerationResult.Error -> {
                    // Whatever already streamed to tvOutput stays visible — the user can see
                    // exactly how far it got before the error, instead of the screen going blank.
                    binding.tvError.text = result.message
                    binding.tvError.visibility = android.view.View.VISIBLE
                }
            }
            binding.btnGenerate.isEnabled = true
            binding.progressBar.visibility = android.view.View.GONE
        }
    }

    private fun saveToFile() {
        val text = lastGenerated
        if (text.isNullOrBlank()) {
            Toast.makeText(this, "Nothing generated yet.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val dir = File(filesDir, "coding_mode_snippets").apply { mkdirs() }
            val fileName = "snippet_${System.currentTimeMillis()}.txt"
            File(dir, fileName).writeText(text)
            Toast.makeText(this, "Saved: ${dir.name}/$fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
