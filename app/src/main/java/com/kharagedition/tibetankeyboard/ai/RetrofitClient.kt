package com.kharagedition.tibetankeyboard.ai

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val geminiAPI: YigChikGeminiAPI by lazy {
        Retrofit.Builder()
            .baseUrl("https://yig-chik-gfg2cdb5a3dycvh8.centralindia-01.azurewebsites.net/")
            .client(httpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(YigChikGeminiAPI::class.java)
    }

    val translateAPI: YigChikTranslateAPI by lazy {
        Retrofit.Builder()
            .baseUrl("https://asia-south1-tibetan-keyboard.cloudfunctions.net/api/")
            .client(httpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(YigChikTranslateAPI::class.java)
    }
}