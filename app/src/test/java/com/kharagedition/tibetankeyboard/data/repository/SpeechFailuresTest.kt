package com.kharagedition.tibetankeyboard.data.repository

import com.kharagedition.tibetankeyboard.ui.keyboard.voice.FailureKind
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Decides whether the panel offers Retry (the recording is kept) or a fresh take. */
class SpeechFailuresTest {

    private fun http(code: Int) =
        HttpException(Response.error<Any>(code, "{}".toResponseBody("application/json".toMediaType())))

    @Test
    fun noConnectionAndTimeoutsAreOfflineSoRetryKeepsTheRecording() {
        assertEquals(FailureKind.OFFLINE, SpeechFailures.kindOf(UnknownHostException()))
        assertEquals(FailureKind.OFFLINE, SpeechFailures.kindOf(SocketTimeoutException()))
        assertEquals(FailureKind.OFFLINE, SpeechFailures.kindOf(IOException("connection reset")))
    }

    @Test
    fun theServerBeingBusyOrDownIsRetryable() {
        for (code in listOf(429, 500, 502, 503, 504)) {
            assertEquals("HTTP $code", FailureKind.SERVER, SpeechFailures.kindOf(http(code)))
        }
    }

    @Test
    fun aRecordingTheServerRefusedIsNotRetriedAsIs() {
        // 400 (bad audio), 413 (too long), 415, 422: sending the same bytes again can only fail again.
        for (code in listOf(400, 413, 415, 422)) {
            assertEquals("HTTP $code", FailureKind.BAD_AUDIO, SpeechFailures.kindOf(http(code)))
        }
    }

    @Test
    fun aMissingRouteOrARefusedKeyIsOursToFixNotTheRecordings() {
        // Found on a phone before the route was deployed: a 404 read "We couldn't read that
        // recording" and blamed the user's audio. Only the codes above say the audio is at fault.
        for (code in listOf(401, 403, 404, 405, 408)) {
            assertEquals("HTTP $code", FailureKind.SERVER, SpeechFailures.kindOf(http(code)))
        }
    }

    @Test
    fun anythingUnexpectedIsAServerProblemNotTheUsersFault() {
        assertEquals(FailureKind.SERVER, SpeechFailures.kindOf(IllegalStateException("bad json")))
    }
}
