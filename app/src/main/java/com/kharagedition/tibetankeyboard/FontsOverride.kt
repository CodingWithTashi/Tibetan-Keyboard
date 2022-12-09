package com.kharagedition.tibetankeyboard

import android.content.Context
import android.graphics.Typeface
import java.lang.reflect.Field;




/**
 * Created by kharag on 07,August,2022
 */

object FontsOverride {
    fun setDefaultFont(
        context: Context,
        staticTypefaceFieldName: String, fontAssetName: String?
    ) {
        var regular:Typeface? = null;
        //check if font asset name is null or not
        if(fontAssetName!=null){
            regular = try{
                //get type face from asset
                Typeface.createFromAsset(
                    context.assets,
                    fontAssetName
                )
            }catch (e:Exception){
                null
            }

        }
        //replace font
        replaceFont(staticTypefaceFieldName, regular)


    }

    private fun replaceFont(
        staticTypefaceFieldName: String,
        newTypeface: Typeface?
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