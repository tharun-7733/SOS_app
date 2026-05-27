package com.example.sos

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sos.BuildConfig
import com.example.sos.ai.GeminiContent
import com.example.sos.ai.GeminiPart
import com.example.sos.ai.GeminiRequest
import com.example.sos.ai.GeminiRetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Message(val text: String, val isUser: Boolean)

/**
 * ViewModel that drives the TARS AI assistant chat screen.
 *
 * Security model
 * ──────────────
 * • The Gemini API key is loaded from BuildConfig.GEMINI_API_KEY, which Gradle
 *   reads from local.properties at build time.
 * • local.properties is listed in .gitignore — the key is NEVER committed.
 * • The key is never stored as a field in this class; it is passed inline to
 *   each API call so there is no single long-lived string holding it.
 *
 * Concurrency model
 * ─────────────────
 * • All network work runs on Dispatchers.IO via viewModelScope.launch to keep
 *   the main thread free.
 * • State (chatMessages) is a SnapshotStateList updated only on the main
 *   dispatcher.
 */
class ChatViewModel : ViewModel() {

    val chatMessages = mutableStateListOf(
        Message("Hi! I'm **TARS**, your AI emergency road assistant. How can I help you today?\n\n**I can help with:**\n• Emergency SOS guidance\n• Road hazard information\n• Vehicle troubleshooting\n• Route assistance & navigation tips", false)
    )

    /** True while the AI is generating a response — drives the typing indicator. */
    val isTyping = mutableStateOf(false)

    /** Quick suggestion chips shown above the input bar. */
    val suggestions = listOf(
        "🚨 SOS Emergency",
        "🔧 Car breakdown help",
        "🏥 Nearest hospital",
        "🚧 Report road hazard"
    )

    private val systemInstruction = """
        You are TARS, an AI emergency road assistant inside the RescueLink app.
        Your responses MUST follow this structured format:

        1. Use **bold** (double asterisks) for key terms, action items, and section headings.
        2. Use bullet points (•) for lists of steps or options.
        3. Keep responses concise and action-oriented — max 5 bullet points per section.
        4. For emergencies, always start with a ⚠️ or 🚨 emoji header.
        5. For helpful tips, use 💡 before the tip.
        6. For locations or distances, use 📍.
        7. End every response with a short follow-up question or offer for more help.
        8. Never use markdown headers like # or ##. Use bold text instead.
        9. Use line breaks between sections for readability.
    """.trimIndent()

    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) return

        chatMessages.add(Message(userMessage, true))
        isTyping.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val reply = runCatching {
                val request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(parts = listOf(GeminiPart(systemInstruction + "\n\nUser: " + userMessage)))
                    )
                )

                // API key injected from BuildConfig — read from local.properties
                val response = GeminiRetrofitClient.service.generateContent(
                    apiKey = BuildConfig.GEMINI_API_KEY,
                    body = request
                )

                response.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text
                    ?: "I'm sorry, I couldn't generate a response."

            }.getOrElse { e ->
                "⚠️ **Connection Error**\n\nI couldn't reach my servers right now.\n\n• Check your internet connection\n• Try again in a moment\n\nError: ${e.localizedMessage}"
            }

            withContext(Dispatchers.Main) {
                isTyping.value = false
                chatMessages.add(Message(reply, false))
            }
        }
    }
}