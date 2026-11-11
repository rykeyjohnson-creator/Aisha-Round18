package com.jarvis.assistant.network

import com.google.gson.JsonParser
import com.jarvis.assistant.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Result of a text-generation request. Never fabricate a success: [Error] carries the real
 * reason (network failure, missing key, API error) so the UI can show it honestly.
 */
sealed class GenerationResult {
    data class Success(val text: String) : GenerationResult()
    data class Error(val message: String) : GenerationResult()
}

/**
 * Round 8 (provider selection): a small routing layer so Coding Mode, Game Builder and the
 * Landing Page Builder can call "whichever text AI the user has configured" without caring
 * which one it is. Gemini remains the default and its existing Live/voice flow is untouched —
 * this is a separate, simple one-shot REST call used only by the new tool screens.
 */
object TextGenerationClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // Full HTML pages/games are long completions (no streaming here), so 60s was too short
        // and was timing out honest, in-progress generations — not actually broken, just cut off
        // too early. 150s gives real generations room to finish.
        .readTimeout(150, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generate(prefs: AppPreferences, prompt: String): GenerationResult = withContext(Dispatchers.IO) {
        // Up to 2 automatic retries (3 attempts total), but only for transient failures — a
        // timeout, or the server itself saying it's temporarily overloaded (503/UNAVAILABLE/
        // "high demand", which is Google's own servers, not a bug here). A real error (bad key,
        // bad request, 4xx) is reported as-is immediately rather than silently retried.
        var result = callProvider(prefs, prompt)
        var attempt = 0
        while (attempt < 2 && isTransientError(result)) {
            kotlinx.coroutines.delay(2000L * (attempt + 1)) // 2s, then 4s — growing backoff
            result = callProvider(prefs, prompt)
            attempt++
        }
        return@withContext result
    }

    private fun isTransientError(result: GenerationResult): Boolean =
        result is GenerationResult.Error && (
            result.message.contains("timeout", ignoreCase = true) ||
            result.message.contains("503") ||
            result.message.contains("UNAVAILABLE", ignoreCase = true) ||
            result.message.contains("high demand", ignoreCase = true)
        )

    private fun callProvider(prefs: AppPreferences, prompt: String): GenerationResult = when (prefs.aiProvider) {
        "OPENAI" -> generateWithOpenAi(prefs.openAiApiKey, prompt)
        else -> generateWithGemini(prefs.apiKey, prompt)
    }

    /**
     * Round 10: trend research for Pin Studio, grounded in real Google Search results via
     * Gemini's built-in search tool — not the model guessing from training data. Gemini-only
     * (OpenAI has no equivalent built-in grounding here), so this always uses the Gemini key
     * regardless of the user's selected default provider.
     */
    suspend fun generateGroundedInSearch(geminiApiKey: String, prompt: String): GenerationResult = withContext(Dispatchers.IO) {
        if (geminiApiKey.isBlank()) {
            return@withContext GenerationResult.Error("No Gemini API key is configured in Settings.")
        }
        try {
            val payload = mapOf(
                "contents" to listOf(mapOf("parts" to listOf(mapOf("text" to prompt)))),
                "tools" to listOf(mapOf("google_search" to emptyMap<String, Any>()))
            )
            val body = com.google.gson.Gson().toJson(payload).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=$geminiApiKey")
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext GenerationResult.Error("Gemini search error (${response.code}): ${shorten(raw)}")
                }
                val root = JsonParser.parseString(raw).asJsonObject
                val text = root.getAsJsonArray("candidates")
                    ?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("content")
                    ?.getAsJsonArray("parts")
                    ?.firstOrNull()?.asJsonObject
                    ?.get("text")?.asString
                if (text.isNullOrBlank()) GenerationResult.Error("Gemini search returned no usable text.")
                else GenerationResult.Success(text)
            }
        } catch (e: Exception) {
            GenerationResult.Error("Network/parse error during search: ${e.message}")
        }
    }

    private fun generateWithGemini(apiKey: String, prompt: String): GenerationResult {
        if (apiKey.isBlank()) {
            return GenerationResult.Error("No Gemini API key is configured in Settings.")
        }
        return try {
            val body = """
                {"contents":[{"parts":[{"text":${jsonString(prompt)}}]}]}
            """.trimIndent().toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=$apiKey")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return GenerationResult.Error("Gemini API error (${response.code}): ${shorten(raw)}")
                }
                val root = JsonParser.parseString(raw).asJsonObject
                val text = root.getAsJsonArray("candidates")
                    ?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("content")
                    ?.getAsJsonArray("parts")
                    ?.firstOrNull()?.asJsonObject
                    ?.get("text")?.asString
                if (text.isNullOrBlank()) {
                    GenerationResult.Error("Gemini returned no usable text.")
                } else {
                    GenerationResult.Success(text)
                }
            }
        } catch (e: Exception) {
            GenerationResult.Error("Network/parse error calling Gemini: ${e.message}")
        }
    }

    private fun generateWithOpenAi(apiKey: String, prompt: String): GenerationResult {
        if (apiKey.isBlank()) {
            return GenerationResult.Error("No OpenAI API key is configured. Add one in AI Provider Settings.")
        }
        return try {
            val body = """
                {"model":"gpt-4o-mini","messages":[{"role":"user","content":${jsonString(prompt)}}]}
            """.trimIndent().toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return GenerationResult.Error("OpenAI API error (${response.code}): ${shorten(raw)}")
                }
                val root = JsonParser.parseString(raw).asJsonObject
                val text = root.getAsJsonArray("choices")
                    ?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("message")
                    ?.get("content")?.asString
                if (text.isNullOrBlank()) {
                    GenerationResult.Error("OpenAI returned no usable text.")
                } else {
                    GenerationResult.Success(text)
                }
            }
        } catch (e: Exception) {
            GenerationResult.Error("Network/parse error calling OpenAI: ${e.message}")
        }
    }

    /** Quick connection test used by the provider settings screen — never claims success without a real call. */
    suspend fun testConnection(prefs: AppPreferences): GenerationResult =
        generate(prefs, "Reply with just the word OK.")

    /**
     * Round 9: streaming generation so the UI can show text appearing live, token by token,
     * as the AI actually writes it — proof it's genuinely working rather than frozen. [onChunk]
     * is called on the Main thread with each new piece of text as it arrives; the final
     * [GenerationResult] carries the complete text (or an error, with whatever was streamed so
     * far left on screen rather than erased, so the user can see exactly how far it got).
     */
    suspend fun generateStreaming(
        prefs: AppPreferences,
        prompt: String,
        onChunk: suspend (String) -> Unit
    ): GenerationResult = withContext(Dispatchers.IO) {
        when (prefs.aiProvider) {
            "OPENAI" -> streamOpenAi(prefs.openAiApiKey, prompt, onChunk)
            else -> streamGemini(prefs.apiKey, prompt, onChunk)
        }
    }

    private suspend fun streamGemini(apiKey: String, prompt: String, onChunk: suspend (String) -> Unit): GenerationResult {
        if (apiKey.isBlank()) return GenerationResult.Error("No Gemini API key is configured in Settings.")
        return try {
            val body = """
                {"contents":[{"parts":[{"text":${jsonString(prompt)}}]}]}
            """.trimIndent().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:streamGenerateContent?alt=sse&key=$apiKey")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val raw = response.body?.string().orEmpty()
                    return GenerationResult.Error("Gemini API error (${response.code}): ${shorten(raw)}")
                }
                val full = StringBuilder()
                val source = response.body?.source() ?: return GenerationResult.Error("Gemini returned no response body.")
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data: ")) continue
                    val jsonPart = line.removePrefix("data: ").trim()
                    if (jsonPart.isEmpty() || jsonPart == "[DONE]") continue
                    try {
                        val root = JsonParser.parseString(jsonPart).asJsonObject
                        val delta = root.getAsJsonArray("candidates")
                            ?.firstOrNull()?.asJsonObject
                            ?.getAsJsonObject("content")
                            ?.getAsJsonArray("parts")
                            ?.firstOrNull()?.asJsonObject
                            ?.get("text")?.asString
                        if (!delta.isNullOrEmpty()) {
                            full.append(delta)
                            withContext(Dispatchers.Main) { onChunk(delta) }
                        }
                    } catch (_: Exception) {
                        // one malformed SSE line shouldn't kill an otherwise-working stream
                    }
                }
                if (full.isEmpty()) GenerationResult.Error("Gemini returned no usable text.")
                else GenerationResult.Success(full.toString())
            }
        } catch (e: Exception) {
            GenerationResult.Error("Network/parse error calling Gemini: ${e.message}")
        }
    }

    private suspend fun streamOpenAi(apiKey: String, prompt: String, onChunk: suspend (String) -> Unit): GenerationResult {
        if (apiKey.isBlank()) return GenerationResult.Error("No OpenAI API key configured. Add one in AI Provider Settings.")
        return try {
            val payload = mapOf(
                "model" to "gpt-4o-mini",
                "stream" to true,
                "messages" to listOf(mapOf("role" to "user", "content" to prompt))
            )
            val body = com.google.gson.Gson().toJson(payload).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val raw = response.body?.string().orEmpty()
                    return GenerationResult.Error("OpenAI API error (${response.code}): ${shorten(raw)}")
                }
                val full = StringBuilder()
                val source = response.body?.source() ?: return GenerationResult.Error("OpenAI returned no response body.")
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data: ")) continue
                    val jsonPart = line.removePrefix("data: ").trim()
                    if (jsonPart.isEmpty() || jsonPart == "[DONE]") continue
                    try {
                        val root = JsonParser.parseString(jsonPart).asJsonObject
                        val delta = root.getAsJsonArray("choices")
                            ?.firstOrNull()?.asJsonObject
                            ?.getAsJsonObject("delta")
                            ?.get("content")?.asString
                        if (!delta.isNullOrEmpty()) {
                            full.append(delta)
                            withContext(Dispatchers.Main) { onChunk(delta) }
                        }
                    } catch (_: Exception) {
                        // skip a malformed chunk, keep streaming
                    }
                }
                if (full.isEmpty()) GenerationResult.Error("OpenAI returned no usable text.")
                else GenerationResult.Success(full.toString())
            }
        } catch (e: Exception) {
            GenerationResult.Error("Network/parse error calling OpenAI: ${e.message}")
        }
    }

    private fun jsonString(s: String): String = com.google.gson.Gson().toJson(s)

    private fun shorten(raw: String): String = if (raw.length > 200) raw.take(200) + "…" else raw
}
