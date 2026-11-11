package com.jarvis.assistant.network

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

sealed class ImageGenResult {
    /** Base64-encoded PNG bytes, as returned by the API. */
    data class Success(val base64Png: String) : ImageGenResult()
    data class Error(val message: String) : ImageGenResult()
}

/**
 * Round 9: real image generation, wired to one documented API only (OpenAI's image endpoint).
 * Requires the user to configure an OpenAI key in AI Provider Settings — there is no fallback,
 * no placeholder image, and no "pretend it worked" path. A failed or unconfigured call always
 * surfaces as [ImageGenResult.Error].
 */
object ImageGenerationClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generate(openAiApiKey: String, prompt: String, size: String = "1024x1024"): ImageGenResult =
        withContext(Dispatchers.IO) {
            if (openAiApiKey.isBlank()) {
                return@withContext ImageGenResult.Error(
                    "No OpenAI API key configured. Image generation needs one — add it in AI Provider Settings."
                )
            }
            try {
                val payload = mapOf(
                    "model" to "gpt-image-1",
                    "prompt" to prompt,
                    "size" to size,
                    "n" to 1
                )
                val body = Gson().toJson(payload).toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url("https://api.openai.com/v1/images/generations")
                    .addHeader("Authorization", "Bearer $openAiApiKey")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext ImageGenResult.Error(
                            "Image API error (${response.code}): ${if (raw.length > 200) raw.take(200) + "…" else raw}"
                        )
                    }
                    val root = JsonParser.parseString(raw).asJsonObject
                    val b64 = root.getAsJsonArray("data")
                        ?.firstOrNull()?.asJsonObject
                        ?.get("b64_json")?.asString
                    if (b64.isNullOrBlank()) {
                        ImageGenResult.Error("Image API returned no image data.")
                    } else {
                        ImageGenResult.Success(b64)
                    }
                }
            } catch (e: Exception) {
                ImageGenResult.Error("Network/parse error calling image API: ${e.message}")
            }
        }
}
