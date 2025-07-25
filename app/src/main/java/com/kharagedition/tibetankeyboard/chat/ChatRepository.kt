package com.kharagedition.tibetankeyboard.chat

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatRepository {
    private val apiKey = "AIzaSyCxUMaoBVH5SIII7Wa0uQYvjrjI9IjV9cg" // enjoy me expose api key

    private val generationConfig = generationConfig {
        temperature = 0.7f
        topK = 40
        topP = 0.95f
        maxOutputTokens = 800
    }

    private val model = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = apiKey,
        generationConfig = generationConfig
    )

    // Chat object to maintain conversation history
    private var chat: Chat? = null

    // Initialize the chat session
    suspend fun initializeChat() {
        withContext(Dispatchers.IO) {
            try {
                // Start a new chat session
                chat = model.startChat()

                // Send system instructions
                val systemInstructions = """
                Instructions:
                - You are master in Tibetan script and language.
                - The user will may ask questions in english or tibetan.
                - You must respond ONLY in Tibetan script (བོད་ཡིག་).
                - Do not respond in any language other than Tibetan script.
                """

                // Send the system instructions and get acknowledgment (we'll discard this response)
                chat?.sendMessage(systemInstructions)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getResponse(query: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Initialize chat if it doesn't exist
                if (chat == null) {
                    initializeChat()
                }

                // Send message and get response with context awareness
                val response = chat?.sendMessage(query)
                val tibetanResponse = response?.text?.trim() ?: ""

                // Fallback in case of empty response
                if (tibetanResponse.isBlank()) {
                    return@withContext "དགོངས་དག། ལན་འདེབས་དཀའ་ངལ་འཕྲད་སོང་། ཡང་བསྐྱར་འབད་བརྩོན་གནང་རོགས།"
                }

                tibetanResponse
            } catch (e: Exception) {
                // Log the exception if possible
                e.printStackTrace()
                "དགོངས་དག། ཕྱི་ཕྱོགས་དང་འབྲེལ་བའི་དཀའ་ངལ་ཞིག་འཕྲད་སོང་། ཡང་བསྐྱར་འབད་བརྩོན་གནང་རོགས།"
            }
        }
    }

    // Method to reset the chat session
    fun resetChat() {
        chat = null
    }
}