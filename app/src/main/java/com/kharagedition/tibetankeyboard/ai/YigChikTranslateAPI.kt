package com.kharagedition.tibetankeyboard.ai

import com.kharagedition.tibetankeyboard.model.TranslationRequest
import com.kharagedition.tibetankeyboard.model.TranslationResponse
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface YigChikTranslateAPI {
    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("translate")
    suspend fun translateText(@Body request: TranslationRequest): TranslationResponse
}
