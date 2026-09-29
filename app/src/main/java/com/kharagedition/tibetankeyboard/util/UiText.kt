package com.kharagedition.tibetankeyboard.util

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/** Text a ViewModel hands to the UI without holding a Context: a string or a plural resource. */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val quantity: Int, val args: List<Any> = emptyList()) : UiText
}

fun Context.resolve(text: UiText): String = when (text) {
    is UiText.Res -> getString(text.id, *text.args.toTypedArray())
    is UiText.Plural -> resources.getQuantityString(text.id, text.quantity, *text.args.toTypedArray())
}
