package com.kharagedition.tibetankeyboard.ai

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

import retrofit2.http.*

interface YigChikAPI {
    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("grammar-check")
    suspend fun checkGrammar(@Body request: AIService.GrammarCheckRequest): AIService.GrammarCheckResponse

    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("translate")
    suspend fun translateText(@Body request: AIService.TranslationRequest): AIService.TranslationResponse
}