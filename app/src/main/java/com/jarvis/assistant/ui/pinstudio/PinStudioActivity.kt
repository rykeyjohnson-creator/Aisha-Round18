package com.jarvis.assistant.ui.pinstudio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
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
import com.jarvis.assistant.databinding.ActivityPinStudioBinding
import com.jarvis.assistant.network.GenerationResult
import com.jarvis.assistant.network.ImageGenResult
import com.jarvis.assistant.network.ImageGenerationClient
import com.jarvis.assistant.network.TextGenerationClient
import kotlinx.coroutines.launch

/**
 * Round 10 — Pin Studio.
 *
 * Scope, stated plainly: this takes trend IDEAS (topics/styles/colors that are currently
 * popular, found via real Google Search grounding) and uses them as inspiration to create an
 * ORIGINAL image and original SEO text. It never copies, downloads, or reposts anyone else's
 * actual pin/image — that would be copyright infringement regardless of how many accounts it's
 * spread across, and isn't something this screen does.
 *
 * It also does not post to Pinterest or Facebook directly — that requires the user's own
 * Pinterest/Meta developer app credentials (OAuth), which this environment doesn't have. Once
 * provided, a posting step can be added on top of this screen's output.
 */
class PinStudioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinStudioBinding
    private lateinit var prefs: AppPreferences
    private var lastBitmap: Bitmap? = null
    private var lastSeoText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPreferences(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnResearch.setOnClickListener { runResearch() }
        binding.btnGeneratePin.setOnClickListener { runGeneratePin() }
        binding.btnSaveImage.setOnClickListener { saveImage() }
        binding.btnCopySeo.setOnClickListener { copySeo() }
    }

    override fun onResume() {
        super.onResume()
        // Make a missing OpenAI key visible up front, not just as small red text after the
        // user already tapped Generate and wonders why "nothing happened" to the image.
        if (prefs.openAiApiKey.isBlank()) {
            binding.tvError.text = "Image generation needs an OpenAI key — add one in AI Provider Settings (SEO text will still work without it)."
            binding.tvError.setTextColor(resources.getColor(com.jarvis.assistant.R.color.status_red, theme))
            binding.tvError.visibility = View.VISIBLE
        }
    }

    private fun runResearch() {
        val niche = binding.etNiche.text?.toString()?.trim().orEmpty()
        if (niche.isEmpty()) {
            Toast.makeText(this, "Enter a niche/topic first.", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnResearch.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE

        val prompt = """
            Search for what's currently trending on Pinterest and social media right now in the
            "$niche" niche: popular visual styles, color palettes, formats (e.g. infographic,
            before/after, checklist), and topics. Summarize in 5-8 short bullet points, in plain
            language, no fluff. This is for inspiration to create an ORIGINAL pin — do not
            describe or reference any single specific creator's image.
        """.trimIndent()

        lifecycleScope.launch {
            when (val result = TextGenerationClient.generateGroundedInSearch(prefs.apiKey, prompt)) {
                is GenerationResult.Success -> {
                    binding.tvTrends.text = result.text
                    binding.tvTrends.visibility = View.VISIBLE
                }
                is GenerationResult.Error -> {
                    binding.tvError.text = result.message
                    binding.tvError.visibility = View.VISIBLE
                }
            }
            binding.btnResearch.isEnabled = true
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun runGeneratePin() {
        val niche = binding.etNiche.text?.toString()?.trim().orEmpty()
        val idea = binding.etPinIdea.text?.toString()?.trim().orEmpty()
        if (idea.isEmpty()) {
            Toast.makeText(this, "Describe your pin idea first.", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnGeneratePin.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE
        binding.ivPin.visibility = View.GONE
        binding.btnSaveImage.visibility = View.GONE
        binding.tvSeoLabel.visibility = View.GONE
        binding.tvSeo.visibility = View.GONE
        binding.btnCopySeo.visibility = View.GONE

        val imagePrompt = "Pinterest-style vertical pin graphic, $niche niche. $idea. Clean, high-contrast, readable at thumbnail size."
        val seoPrompt = """
            Write an SEO package for a Pinterest pin about: $idea (niche: $niche).
            Format exactly as:
            TITLE: <max 100 chars, keyword-first>
            DESCRIPTION: <2-3 sentences, natural language, include 2-3 relevant keywords>
            TAGS: <8-10 comma-separated relevant keywords, no # symbol>
            ALT TEXT: <one plain sentence describing the image for accessibility/SEO>
        """.trimIndent()

        lifecycleScope.launch {
            // Run image + SEO generation; image needs a configured OpenAI key (per Provider Settings).
            val imageResult = ImageGenerationClient.generate(prefs.openAiApiKey, imagePrompt)
            val seoResult = TextGenerationClient.generate(prefs, seoPrompt)

            when (imageResult) {
                is ImageGenResult.Success -> {
                    try {
                        val bytes = Base64.decode(imageResult.base64Png, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            lastBitmap = bmp
                            binding.ivPin.setImageBitmap(bmp)
                            binding.ivPin.visibility = View.VISIBLE
                            binding.btnSaveImage.visibility = View.VISIBLE
                        }
                    } catch (_: Exception) { /* fall through to error text below if also failed */ }
                }
                is ImageGenResult.Error -> {
                    binding.tvError.text = imageResult.message
                    binding.tvError.visibility = View.VISIBLE
                }
            }

            when (seoResult) {
                is GenerationResult.Success -> {
                    lastSeoText = seoResult.text
                    binding.tvSeo.text = seoResult.text
                    binding.tvSeoLabel.visibility = View.VISIBLE
                    binding.tvSeo.visibility = View.VISIBLE
                    binding.btnCopySeo.visibility = View.VISIBLE
                }
                is GenerationResult.Error -> {
                    val existing = binding.tvError.text?.toString().orEmpty()
                    binding.tvError.text = if (existing.isBlank()) seoResult.message else "$existing\n${seoResult.message}"
                    binding.tvError.visibility = View.VISIBLE
                }
            }

            binding.btnGeneratePin.isEnabled = true
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun saveImage() {
        val bmp = lastBitmap ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "pin_${System.currentTimeMillis()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Aisha/Pins")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
                    Toast.makeText(this, "Saved to Pictures/Aisha/Pins.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Save failed: could not create gallery entry.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Saving needs Android 10 or newer on this build.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun copySeo() {
        val text = lastSeoText ?: return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Pin SEO", text))
        Toast.makeText(this, "Copied.", Toast.LENGTH_SHORT).show()
    }
}
