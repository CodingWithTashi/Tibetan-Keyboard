package com.kharagedition.tibetankeyboard.data.repository

import com.kharagedition.tibetankeyboard.data.remote.RetrofitClient
import com.kharagedition.tibetankeyboard.data.model.GeminiChatRequest
import com.kharagedition.tibetankeyboard.util.ApiErrorType
import com.kharagedition.tibetankeyboard.util.ApiErrors

class ChatRepository() {
    private var currentSessionId: String? = null

    suspend fun sendMessage(message: String, userId: String, model: String? = null): String {
        return try {
            val request = GeminiChatRequest(
                message = message,
                sessionId = currentSessionId,
                resetChat = false,
                model = model
            )

            val response = RetrofitClient.geminiAPI.chatWithGemini(request,userId)

            if (response.success && response.data != null) {
                // Update session ID for conversation continuity
                currentSessionId = response.data.sessionId
                response.data.response
            } else {
                // 200 with success=false (rare) — prefer the backend's own copy.
                response.message ?: response.error ?: tibetanError(ApiErrorType.UNKNOWN)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Distinguish no-internet / timeout / daily-limit / server errors so
            // the user sees a meaningful Tibetan message, not one generic line.
            tibetanError(ApiErrors.from(e).type)
        }
    }

    /**
     * User-facing Tibetan copy per failure category. Edit freely to refine the
     * wording — these are the messages shown in the chat bubble on a failure.
     */
    private fun tibetanError(type: ApiErrorType): String = when (type) {
        ApiErrorType.NO_INTERNET ->
            "དགོངས་དག། དྲ་རྒྱ་དང་འབྲེལ་མ་ཐུབ། ཁྱེད་ཀྱི་དྲ་ཐག་ལ་བལྟ་ཞིབ་བྱས་ནས་ཡང་བསྐྱར་འབད་བརྩོན་གནང་རོགས།"
        ApiErrorType.TIMEOUT ->
            "དགོངས་དག། ལན་འདེབས་ལ་དུས་ཡུན་འགོར་དྲགས་སོང་། ཡང་བསྐྱར་འབད་བརྩོན་གནང་རོགས།"
        ApiErrorType.RATE_LIMITED ->
            "དེ་རིང་གི་རིན་མེད་སྤྱོད་ཚད་ཟིན་སོང་། ཚད་མེད་སྤྱོད་པར་ Pro ལ་གཞི་སྤོ་གནང་རོགས།"
        ApiErrorType.SERVER ->
            "དགོངས་དག། ཞབས་ཞུ་ལ་གནད་དོན་འཕྲད་སོང་། སྐར་མ་འགའ་རྗེས་ཡང་བསྐྱར་འབད་བརྩོན་གནང་རོགས།"
        ApiErrorType.BAD_REQUEST, ApiErrorType.UNKNOWN ->
            "དགོངས་དག། དཀའ་ངལ་ཞིག་འཕྲད་སོང་། ཡང་བསྐྱར་འབད་བརྩོན་གནང་རོགས།"
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