package com.kharagedition.tibetankeyboard

import android.content.Context
import android.graphics.Typeface
import java.lang.reflect.Field




/**
 * Created by kharag on 07,August,2022
 *
 * Reflectively swaps one of [Typeface]'s static faces for a bundled font. Never writes null —
 * that poisons the field process-wide (the IME shares its process with every Compose Activity).
 */
object FontsOverride {

    /** True when [typeface] is safe to write into a static field. Pure so it is unit-testable. */
    fun shouldReplace(typeface: Typeface?): Boolean = typeface != null

    fun setDefaultFont(
        context: Context,
        staticTypefaceFieldName: String, fontAssetName: String?
    ) {
        if (fontAssetName == null) return

        val regular = try {
            Typeface.createFromAsset(context.assets, fontAssetName)
        } catch (e: Exception) {
            null
        }?.takeIf { shouldReplace(it) } ?: return

        replaceFont(staticTypefaceFieldName, regular)
    }

    private fun replaceFont(
        staticTypefaceFieldName: String,
        newTypeface: Typeface
    ) {
        try {
            val staticField: Field = Typeface::class.java
                .getDeclaredField(staticTypefaceFieldName)
            staticField.isAccessible = true
            staticField.set(null, newTypeface)
        } catch (e: NoSuchFieldException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        }
    }
}
