package com.jarvis.assistant.ui.imagegen

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.data.preferences.AppPreferences
import com.jarvis.assistant.databinding.ActivityImageGenBinding
import com.jarvis.assistant.network.ImageGenResult
import com.jarvis.assistant.network.ImageGenerationClient
import kotlinx.coroutines.launch

/**
 * Round 4 — Image generation.
 *
 * Connects only to OpenAI's documented image-generation endpoint (requires an OpenAI key,
 * configured in AI Provider Settings — there is no bundled/default key). Shows real loading,
 * success and error states; never displays a placeholder or claims an image was generated when
 * the call failed. Saving is an explicit, separate action using MediaStore (no storage permission
 * needed on this device's Android version).
 */
class ImageGenActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROMPT = "extra_prompt"
        const val EXTRA_AUTO_GENERATE = "extra_auto_generate"
    }

    private lateinit var binding: ActivityImageGenBinding
    private lateinit var prefs: AppPreferences
    private var lastBitmap: Bitmap? = null
    private var isGenerating = false
    private var lastCompletedPrompt: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageGenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPreferences(this)

        binding.btnBack.setOnClickListener { finish() }

        // Round 6: voice-command shortcut — arrives with the prompt already filled in and
        // starts generating immediately, so the user doesn't have to type/tap anything.
        val incomingPrompt = intent.getStringExtra(EXTRA_PROMPT)
        if (!incomingPrompt.isNullOrBlank()) {
            binding.etPrompt.setText(incomingPrompt)
            if (intent.getBooleanExtra(EXTRA_AUTO_GENERATE, false)) {
                runGeneration(incomingPrompt)
            }
        }

        binding.btnGenerate.setOnClickListener {
            val prompt = binding.etPrompt.text?.toString()?.trim().orEmpty()
            if (prompt.isEmpty()) {
                Toast.makeText(this, "Describe the image first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            runGeneration(prompt)
        }

        binding.btnSaveImage.setOnClickListener { saveImage() }
    }

    private fun runGeneration(prompt: String) {
        // Round 16: don't regenerate a duplicate — covers both "already generating this exact
        // prompt right now" (e.g. a voice command and a button tap both firing) and "this exact
        // prompt just finished" (e.g. a slow UI update made it look like nothing happened, so
        // the same request got sent again) — reuse what's already there instead of a fresh,
        // wasteful API call.
        if (isGenerating) {
            Toast.makeText(this, "Already generating — please wait.", Toast.LENGTH_SHORT).show()
            return
        }
        if (prompt == lastCompletedPrompt && lastBitmap != null) {
            Toast.makeText(this, "Already generated this — shown above.", Toast.LENGTH_SHORT).show()
            return
        }
        isGenerating = true
        binding.btnGenerate.isEnabled = false
        binding.btnSaveImage.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE

        lifecycleScope.launch {
            when (val result = ImageGenerationClient.generate(prefs.openAiApiKey, prompt)) {
                is ImageGenResult.Success -> {
                    try {
                        val bytes = Base64.decode(result.base64Png, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            lastBitmap = bmp
                            binding.ivResult.setImageBitmap(bmp)
                            binding.btnSaveImage.isEnabled = true
                        } else {
                            showError("Image data could not be decoded.")
                        }
                    } catch (e: Exception) {
                        showError("Image decode failed: ${e.message}")
                    }
                }
                is ImageGenResult.Error -> showError(result.message)
            }
            if (result is ImageGenResult.Success) lastCompletedPrompt = prompt
            isGenerating = false
            binding.btnGenerate.isEnabled = true
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun saveImage() {
        val bmp = lastBitmap ?: run {
            Toast.makeText(this, "Nothing to save.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "aisha_${System.currentTimeMillis()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Aisha")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri == null) {
                    Toast.makeText(this, "Save failed: could not create gallery entry.", Toast.LENGTH_LONG).show()
                    return
                }
                contentResolver.openOutputStream(uri)?.use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                Toast.makeText(this, "Saved to Pictures/Aisha.", Toast.LENGTH_SHORT).show()
            } else {
                // Pre-Android 10 MediaStore writes need WRITE_EXTERNAL_STORAGE, which this app
                // does not currently request. Rather than silently fail, say so plainly.
                Toast.makeText(
                    this,
                    "Saving images needs Android 10 or newer on this build. Not saved.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
