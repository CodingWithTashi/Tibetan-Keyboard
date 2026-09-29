package com.kharagedition.tibetankeyboard.subscription

import java.net.URLEncoder

/** The RevenueCat Web Purchase Link (card checkout where Play can't sell). */
object WebCheckout {

    fun isConfigured(link: String): Boolean = link.isNotBlank()

    /**
     * The link for [appUserId], or null if the link isn't configured or nobody is signed in. It
     * must carry a real App User ID (the Firebase UID, which RevenueCat also uses) so the purchase
     * lands on this account; RevenueCat doesn't accept anonymous IDs here.
     *
     * With [packageId] the link opens that plan's checkout directly: the plan was picked in the
     * app, so the hosted plan page would only ask again.
     */
    fun url(link: String, appUserId: String?, packageId: String? = null): String? {
        val base = link.trim().trimEnd('/').takeIf { it.isNotEmpty() } ?: return null
        val id = appUserId?.takeIf { it.isNotBlank() } ?: return null
        val plan = packageId?.takeIf { it.isNotBlank() }?.let { "?package_id=${encode(it)}" }.orEmpty()
        return "$base/${encode(id)}$plan"
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
