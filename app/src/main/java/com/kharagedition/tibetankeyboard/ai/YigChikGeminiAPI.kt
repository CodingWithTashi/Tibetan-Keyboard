package com.kharagedition.tibetankeyboard.ai

import com.kharagedition.tibetankeyboard.model.GrammarCheckRequest
import com.kharagedition.tibetankeyboard.model.GrammarCheckResponse
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface YigChikGeminiAPI {
    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("grammar-check")
    suspend fun checkGrammar(@Body request: GrammarCheckRequest): GrammarCheckResponse


}
