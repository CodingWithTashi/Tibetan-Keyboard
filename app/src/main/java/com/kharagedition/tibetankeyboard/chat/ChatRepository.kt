package com.kharagedition.tibetankeyboard.chat

import com.kharagedition.tibetankeyboard.ai.RetrofitClient
import com.kharagedition.tibetankeyboard.model.GeminiChatRequest

class ChatRepository() {
    private var currentSessionId: String? = null

    suspend fun sendMessage(message: String, userId: String): String {
        return try {
            val request = GeminiChatRequest(
                message = message,
                sessionId = currentSessionId,
                resetChat = false
            )

            val response = RetrofitClient.geminiAPI.chatWithGemini(request,userId)

            if (response.success && response.data != null) {
                // Update session ID for conversation continuity
                currentSessionId = response.data.sessionId
                response.data.response
            } else {
                response.error ?: "དགོངས་དག། ལན་འདེབས་དཀའ་ངལ་འཕྲད་སོང་།"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "དགོངས་དག། ཕྱི་ཕྱོགས་དང་འབྲེལ་བའི་དཀའ་ངལ་ཞིག་འཕྲད་སོང་།"
        }
    }

    suspend fun resetChat(): Boolean {
        return try {
            currentSessionId?.let { sessionId ->
                val request = mapOf("sessionId" to sessionId)
                val response = RetrofitClient.geminiAPI.resetChatSession(request)
                if (response.success) {
                    currentSessionId = null
                    true
                } else {
                    false
                }
            } ?: true // If no session, consider it reset
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun clearSession() {
        currentSessionId = null
    }
}