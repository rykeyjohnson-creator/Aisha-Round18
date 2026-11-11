package com.jarvis.assistant.util

/** Round 3 helper: AI text responses often wrap code in ```html fences — strip them for preview/export. */
object GeneratedCodeUtil {
    fun extractHtml(raw: String): String {
        val fenceRegex = Regex("```(?:html|HTML)?\\s*([\\s\\S]*?)```")
        val match = fenceRegex.find(raw)
        return (match?.groupValues?.get(1) ?: raw).trim()
    }
}
