package com.kharagedition.tibetankeyboard.ui.login

/** Screen to open after a successful login. */
enum class PostLoginDestination { PREMIUM, CHAT }

/**
 * Pure decision for where to go after login. When the login was launched from a PRO upsell
 * (the keyboard's PRO strip sets [LoginActivity.EXTRA_OPEN_PREMIUM_AFTER_LOGIN]) we forward to the
 * paywall; otherwise the chat screen. Separated out so the routing is unit-testable.
 */
fun postLoginDestination(openPremiumAfterLogin: Boolean): PostLoginDestination =
    if (openPremiumAfterLogin) PostLoginDestination.PREMIUM else PostLoginDestination.CHAT
