package com.kharagedition.tibetankeyboard.subscription

import java.net.URLEncoder

/** The RevenueCat Web Purchase Link (card checkout where Play can't sell). */
object WebCheckout {

    fun isConfigured(link: String): Boolean = link.isNotBlank()

    /**
     * The link for [appUserId], or null if the link isn't configured or nobody is signed in. It
     * must carry a real App User ID (the Firebase UID, which RevenueCat also uses) so the purchase
     * lands on this account; RevenueCat doesn't accept anonymous IDs here.
     */
    fun url(link: String, appUserId: String?): String? {
        val base = link.trim().trimEnd('/').takeIf { it.isNotEmpty() } ?: return null
        val id = appUserId?.takeIf { it.isNotBlank() } ?: return null
        return "$base/${URLEncoder.encode(id, "UTF-8").replace("+", "%20")}"
    }
}
