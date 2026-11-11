package com.jarvis.assistant.automation.command

sealed class JarvisCommand {
    data class PlayYouTube(val query: String) : JarvisCommand()
    data class SearchYouTube(val query: String) : JarvisCommand()
    data class OpenApp(val packageName: String) : JarvisCommand()
    /** Round 6: "Gemini se image banao <X>" / "generate image of <X>" — opens Image Generation
     *  pre-filled with the prompt and starts generating immediately, no manual typing needed. */
    data class GenerateImage(val prompt: String) : JarvisCommand()
    /** Round 11: "game banao <X>" — opens Game Builder pre-filled and starts generating
     *  immediately, with live code streaming and a "play karna hai?" prompt once it's done. */
    data class GenerateGame(val prompt: String) : JarvisCommand()
    /** Round 12: "landing page banao <X>" — opens Landing Page Builder pre-filled, same pattern
     *  as GenerateGame/GenerateImage, with live code streaming. */
    data class GenerateLandingPage(val prompt: String) : JarvisCommand()
    /** Round 17: "code likho <X>" / "write code for <X>" — opens Coding Mode pre-filled, so
     *  code requests never fall through to typing into other apps via accessibility. */
    data class GenerateCode(val prompt: String) : JarvisCommand()
    data class WhatsAppMessage(val contact: String, val message: String) : JarvisCommand()
    data class WhatsAppCall(val contact: String, val video: Boolean) : JarvisCommand()
    data class WhatsAppDeleteMessage(val contact: String?, val forEveryone: Boolean) : JarvisCommand()
    object SubscribeChannel : JarvisCommand()
    object ToggleFullscreen : JarvisCommand()
    data class Brightness(val level: Int, val isRelative: Boolean = false, val delta: Int = 0) : JarvisCommand()
    data class Volume(val level: Int, val isRelative: Boolean = false, val delta: Int = 0) : JarvisCommand()
    data class Flash(val enabled: Boolean) : JarvisCommand()
    data class Remember(val text: String) : JarvisCommand()
    data class Forget(val query: String) : JarvisCommand()
    object WhatDoYouRemember : JarvisCommand()
    data class SearchWeb(val query: String) : JarvisCommand()
    data class OpenWebUrls(val urls: List<String>) : JarvisCommand()
    data class PhoneCall(val contactOrNumber: String) : JarvisCommand()
    object PauseMedia : JarvisCommand()
    object ResumeMedia : JarvisCommand()
    object NextMedia : JarvisCommand()
    object PreviousMedia : JarvisCommand()
    data class ConfirmAction(val confirmed: Boolean) : JarvisCommand()
    data class DisambiguationChoice(val choice: String) : JarvisCommand()
    object RecallBirthday : JarvisCommand()
    object RecallFavoriteSong : JarvisCommand()
    object RecallRoutine : JarvisCommand()
    object GoToBackground : JarvisCommand()
}
