package com.kharagedition.tibetankeyboard.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebCheckoutTest {

    private val link = "https://pay.rev.cat/abc/"

    @Test
    fun appendsTheEncodedAppUserId() {
        assertEquals("https://pay.rev.cat/abc/lmEOeGUs2ONp4", WebCheckout.url(link, "lmEOeGUs2ONp4"))
        assertEquals("https://pay.rev.cat/abc/a%20b%2Fc", WebCheckout.url(link, "a b/c"))
    }

    @Test
    fun signedOutOrUnconfigured_givesNoLink() {
        assertNull(WebCheckout.url(link, null))
        assertNull(WebCheckout.url(link, " "))
        assertNull(WebCheckout.url("", "uid"))
    }
}
