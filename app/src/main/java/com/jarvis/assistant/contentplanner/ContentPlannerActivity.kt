package com.jarvis.assistant.contentplanner

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.data.preferences.AppPreferences
import com.jarvis.assistant.databinding.ActivityContentPlannerBinding
import com.jarvis.assistant.databinding.ItemContentPlanCardBinding
import com.jarvis.assistant.network.GenerationResult
import com.jarvis.assistant.network.TextGenerationClient
import kotlinx.coroutines.launch

/**
 * Round 18 — Content Planner.
 *
 * Scope, stated plainly: this generates ORIGINAL content prompts, hooks, SEO text, and per-
 * channel branding notes for the user's own channels — one base idea becomes several distinct,
 * AI-written variations, one per channel, so no two channels post identical content. It does
 * NOT watch, copy, or reference any specific other creator's video. It does NOT post anything —
 * it produces a plan the user (or their editing app) acts on. Everything is saved to one
 * dated file (see ContentPlanStore) so each channel's plan stays separate and nothing gets
 * confused with another channel's.
 */
class ContentPlannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContentPlannerBinding
    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContentPlannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPreferences(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnGeneratePlan.setOnClickListener { runGeneratePlan() }
        binding.btnLoadToday.setOnClickListener { loadSavedPlan() }
    }

    private fun runGeneratePlan() {
        val basePrompt = binding.etBasePrompt.text?.toString()?.trim().orEmpty()
        val channelsRaw = binding.etChannels.text?.toString()?.trim().orEmpty()
        val channels = channelsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }

        if (basePrompt.isEmpty()) {
            Toast.makeText(this, "Enter a base idea/theme first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (channels.isEmpty()) {
            Toast.makeText(this, "Enter at least one channel name.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnGeneratePlan.isEnabled = false
        binding.planContainer.removeAllViews()
        binding.tvError.visibility = View.GONE
        binding.tvStatus.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val results = mutableListOf<ContentPlanItem>()
            val errors = mutableListOf<String>()

            channels.forEachIndexed { index, channelName ->
                binding.tvStatus.text = "Planning ${index + 1} of ${channels.size}: $channelName…"

                val prompt = """
                    You are planning ORIGINAL content for one specific channel, part of a content
                    calendar built from one shared base idea. Base idea: "$basePrompt".
                    This entry is for channel "$channelName". Give THIS channel a distinct angle
                    or variation on the base idea so it does not duplicate other channels on the
                    same calendar — do not reference or copy any other creator's specific video.

                    Reply in EXACTLY this format, one field per line, nothing else:
                    PROMPT: <the unique content idea for this channel today, 1-2 sentences>
                    HOOK: <a strong, specific opening line/hook for the first few seconds>
                    SEO_TITLE: <short catchy title, under 100 chars>
                    SEO_DESCRIPTION: <2 sentences>
                    SEO_TAGS: <8-10 comma-separated keywords, no # symbol>
                    LOGO_NOTE: <one line: where/how to place "$channelName" branding when editing>
                    UPLOAD_TIME: <one suggested time today, e.g. "6:00 PM">
                """.trimIndent()

                when (val result = TextGenerationClient.generate(prefs, prompt)) {
                    is GenerationResult.Success -> {
                        val item = parsePlanItem(channelName, result.text)
                        results.add(item)
                        addPlanCard(item)
                    }
                    is GenerationResult.Error -> {
                        errors.add("$channelName: ${result.message}")
                    }
                }
            }

            if (results.isNotEmpty()) {
                ContentPlanStore.saveTodayPlan(this@ContentPlannerActivity, results)
            }
            if (errors.isNotEmpty()) {
                binding.tvError.text = errors.joinToString("\n")
                binding.tvError.visibility = View.VISIBLE
            }
            binding.tvStatus.text = "Saved ${results.size} of ${channels.size} channel plans for ${ContentPlanStore.todayLabel()}."
            binding.btnGeneratePlan.isEnabled = true
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun loadSavedPlan() {
        val items = ContentPlanStore.loadTodayPlan(this)
        binding.planContainer.removeAllViews()
        if (items.isEmpty()) {
            Toast.makeText(this, "No saved plan for today yet.", Toast.LENGTH_SHORT).show()
            return
        }
        items.forEach { addPlanCard(it) }
        binding.tvStatus.text = "Loaded ${items.size} channel plans for ${ContentPlanStore.todayLabel()}."
        binding.tvStatus.visibility = View.VISIBLE
    }

    private fun addPlanCard(item: ContentPlanItem) {
        val cardBinding = ItemContentPlanCardBinding.inflate(layoutInflater, binding.planContainer, false)
        cardBinding.tvChannelName.text = item.channelName
        cardBinding.tvUploadTime.text = "Suggested upload time: ${item.uploadTime}"
        cardBinding.tvPrompt.text = item.prompt
        cardBinding.tvHook.text = item.hookNote
        cardBinding.tvSeo.text = "Title: ${item.seoTitle}\n${item.seoDescription}\nTags: ${item.seoTags}"
        cardBinding.tvLogoNote.text = item.logoInstruction
        binding.planContainer.addView(cardBinding.root)
    }

    /** Parses the model's labeled-line reply into a structured item; missing fields fall back
     *  to a plain note rather than silently dropping the channel's entry. */
    private fun parsePlanItem(channelName: String, raw: String): ContentPlanItem {
        fun field(label: String): String {
            val line = raw.lineSequence().firstOrNull { it.trim().startsWith("$label:", ignoreCase = true) }
            return line?.substringAfter(":")?.trim().orEmpty()
        }
        return ContentPlanItem(
            channelName = channelName,
            dateLabel = ContentPlanStore.todayLabel(),
            prompt = field("PROMPT").ifBlank { raw.trim() },
            hookNote = field("HOOK").ifBlank { "(not provided)" },
            uploadTime = field("UPLOAD_TIME").ifBlank { "(not provided)" },
            seoTitle = field("SEO_TITLE").ifBlank { "(not provided)" },
            seoDescription = field("SEO_DESCRIPTION").ifBlank { "(not provided)" },
            seoTags = field("SEO_TAGS").ifBlank { "(not provided)" },
            logoInstruction = field("LOGO_NOTE").ifBlank { "(not provided)" }
        )
    }
}
