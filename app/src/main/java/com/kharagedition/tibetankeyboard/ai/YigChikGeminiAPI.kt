package com.kharagedition.tibetankeyboard.ai

import com.kharagedition.tibetankeyboard.model.GeminiChatRequest
import com.kharagedition.tibetankeyboard.model.GeminiChatResponse
import com.kharagedition.tibetankeyboard.model.GrammarCheckRequest
import com.kharagedition.tibetankeyboard.model.GrammarCheckResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface YigChikGeminiAPI {
    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("grammar-check")
    suspend fun checkGrammar(@Body request: GrammarCheckRequest): GrammarCheckResponse


    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("chat")
    suspend fun chatWithGemini(@Body request: GeminiChatRequest,  @Header("userid") userId: String): GeminiChatResponse

    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("chat/reset")
    suspend fun resetChatSession(@Body request: Map<String, String>): GeminiChatResponse


}
