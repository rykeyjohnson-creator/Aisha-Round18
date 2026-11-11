package com.jarvis.assistant.contentplanner

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One channel's plan entry for one day — kept separate per channel so nothing gets mixed up. */
data class ContentPlanItem(
    val channelName: String,
    val dateLabel: String,        // e.g. "2026-10-28"
    val prompt: String,           // the unique, original prompt for this channel today
    val hookNote: String,         // what makes the hook strong for this one
    val uploadTime: String,       // suggested upload time slot
    val seoTitle: String,
    val seoDescription: String,
    val seoTags: String,
    val logoInstruction: String,  // which logo/branding to apply when editing, for this channel
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * Round 18 — Content Planner storage. Plain JSON file in app-private storage, one file per day,
 * so "today's plan" is always exactly findable and channels never get confused with each other —
 * each item carries its own channelName, and the UI groups strictly by that field.
 */
object ContentPlanStore {

    private fun dirFor(context: Context): File =
        File(context.filesDir, "content_plans").apply { mkdirs() }

    private fun fileForToday(context: Context): File {
        val dateLabel = todayLabel()
        return File(dirFor(context), "plan_$dateLabel.json")
    }

    fun todayLabel(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun saveTodayPlan(context: Context, items: List<ContentPlanItem>) {
        val json = Gson().toJson(items)
        fileForToday(context).writeText(json)
    }

    fun loadTodayPlan(context: Context): List<ContentPlanItem> {
        val file = fileForToday(context)
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<ContentPlanItem>>() {}.type
            Gson().fromJson(file.readText(), type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun listSavedDates(context: Context): List<String> =
        dirFor(context).listFiles { f -> f.name.startsWith("plan_") && f.name.endsWith(".json") }
            ?.map { it.name.removePrefix("plan_").removeSuffix(".json") }
            ?.sortedDescending()
            ?: emptyList()
}
