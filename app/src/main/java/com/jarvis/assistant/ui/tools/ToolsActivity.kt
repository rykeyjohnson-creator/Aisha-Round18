package com.jarvis.assistant.ui.tools

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.R
import com.jarvis.assistant.databinding.ActivityToolsBinding
import com.jarvis.assistant.databinding.RowToolItemBinding

/**
 * Round 12 — a full Tools page, replacing the small popup chooser, styled to match the app's
 * existing Settings & Intelligence page (card rows with icon, title, subtitle, chevron).
 */
class ToolsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityToolsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        setupRow(binding.rowCodingMode, R.drawable.ic_code_chip, "Coding Mode", "Describe it, get working code") {
            com.jarvis.assistant.ui.coding.CodingModeActivity::class.java
        }
        setupRow(binding.rowGameBuilder, R.drawable.ic_settings_apps, "Game Builder", "Describe a game, play it instantly") {
            com.jarvis.assistant.ui.gamebuilder.GameBuilderActivity::class.java
        }
        setupRow(binding.rowLandingBuilder, R.drawable.ic_settings_language, "Landing Page Builder", "Describe a page, get a live preview") {
            com.jarvis.assistant.ui.landing.LandingBuilderActivity::class.java
        }
        setupRow(binding.rowImageGen, R.drawable.ic_sparkle, "Image Generation", "Describe an image, generate it") {
            com.jarvis.assistant.ui.imagegen.ImageGenActivity::class.java
        }
        setupRow(binding.rowPinStudio, R.drawable.ic_social_pinterest, "Pin Studio", "Trend research + original pin + SEO") {
            com.jarvis.assistant.ui.pinstudio.PinStudioActivity::class.java
        }
        setupRow(binding.rowContentPlanner, R.drawable.ic_checklist_tasks, "Content Planner", "Per-channel prompts, hooks, SEO, upload times") {
            com.jarvis.assistant.contentplanner.ContentPlannerActivity::class.java
        }
        setupRow(binding.rowProviderSettings, R.drawable.ic_settings_plug, "AI Provider Settings", "Gemini / OpenAI keys & connection") {
            com.jarvis.assistant.ui.settings.ProviderSettingsActivity::class.java
        }
    }

    private fun setupRow(
        row: RowToolItemBinding,
        iconRes: Int,
        title: String,
        subtitle: String,
        target: () -> Class<out AppCompatActivity>
    ) {
        row.toolIcon.setImageResource(iconRes)
        row.toolTitle.text = title
        row.toolSubtitle.text = subtitle
        row.root.setOnClickListener { startActivity(Intent(this, target())) }
    }
}
