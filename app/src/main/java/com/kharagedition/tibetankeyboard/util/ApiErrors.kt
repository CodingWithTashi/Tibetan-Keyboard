package com.kharagedition.tibetankeyboard.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Turns any network/API failure into a single, user-friendly [ApiError] so the
 * UI never shows a raw exception string (e.g. "HTTP 429", "Unable to resolve
 * host…"). The [ApiError.type] lets a screen react (e.g. show an upgrade prompt
 * on [ApiErrorType.RATE_LIMITED]); the [ApiError.message] is a clean English
 * sentence — for non-2xx responses we prefer the backend's own `message` field
 * (which is already written to be user-facing, e.g. the daily-limit copy).
 */
enum class ApiErrorType {
    NO_INTERNET,
    TIMEOUT,
    RATE_LIMITED,
    BAD_REQUEST,
    SERVER,
    UNKNOWN,
}

data class ApiError(
    val type: ApiErrorType,
    val message: String,
)

@Serializable
private data class ApiErrorBody(
    val error: String? = null,
    val message: String? = null,
)

object ApiErrors {

    // Default English copy (used when the server sends no usable message).
    const val NO_INTERNET = "No internet connection. Please check your network and try again."
    const val TIMEOUT = "The request timed out. Please try again."
    const val RATE_LIMITED = "You've reached the limit for now. Please try again later."
    const val BAD_REQUEST = "We couldn't process that request. Please try again."
    const val SERVER = "Our service is temporarily unavailable. Please try again shortly."
    const val UNKNOWN = "Something went wrong. Please try again."

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun from(t: Throwable): ApiError = when (t) {
        is HttpException -> fromHttp(t)
        is SocketTimeoutException -> ApiError(ApiErrorType.TIMEOUT, TIMEOUT)
        is UnknownHostException -> ApiError(ApiErrorType.NO_INTERNET, NO_INTERNET)
        is IOException -> ApiError(ApiErrorType.NO_INTERNET, NO_INTERNET)
        else -> ApiError(ApiErrorType.UNKNOWN, UNKNOWN)
    }

    private fun fromHttp(e: HttpException): ApiError {
        val serverMessage = runCatching {
            e.response()?.errorBody()?.string()
                ?.takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString<ApiErrorBody>(it).message }
        }.getOrNull()?.takeIf { it.isNotBlank() }

        return when (e.code()) {
            429 -> ApiError(ApiErrorType.RATE_LIMITED, serverMessage ?: RATE_LIMITED)
            in 400..499 -> ApiError(ApiErrorType.BAD_REQUEST, serverMessage ?: BAD_REQUEST)
            in 500..599 -> ApiError(ApiErrorType.SERVER, serverMessage ?: SERVER)
            else -> ApiError(ApiErrorType.UNKNOWN, serverMessage ?: UNKNOWN)
        }
    }
}
