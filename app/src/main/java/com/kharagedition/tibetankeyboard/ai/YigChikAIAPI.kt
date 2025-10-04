package com.kharagedition.tibetankeyboard.ai

import com.kharagedition.tibetankeyboard.model.GrammarCheckRequest
import com.kharagedition.tibetankeyboard.model.GrammarCheckResponse
import com.kharagedition.tibetankeyboard.model.TranslationRequest
import com.kharagedition.tibetankeyboard.model.TranslationResponse
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface YigChikAIAPI {
    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("grammar-check")
    suspend fun checkGrammar(@Body request: GrammarCheckRequest): GrammarCheckResponse
}
