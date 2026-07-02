package com.kharagedition.tibetankeyboard.data.remote

import com.kharagedition.tibetankeyboard.data.model.JourneyWeeklyRequest
import com.kharagedition.tibetankeyboard.data.model.JourneyWeeklyResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface JourneyAPI {
    /** Numbers-only weekly sync for the opt-in community comparison (see JourneyModels). */
    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("journey/weekly")
    suspend fun submitWeekly(
        @Body request: JourneyWeeklyRequest,
        @Header("userid") userId: String,
    ): JourneyWeeklyResponse
}
