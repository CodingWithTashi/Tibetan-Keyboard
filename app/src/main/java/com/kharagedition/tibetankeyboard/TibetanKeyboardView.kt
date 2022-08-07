package com.kharagedition.tibetankeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet

/**
 * Created by kharag on 07,August,2022
 */
//added for custom font purpose
class TibetanKeyboardView(context: Context?, attrs: AttributeSet?) : KeyboardView(context, attrs) {
 /*   @SuppressLint("DrawAllocation")
    @Deprecated("Deprecated in Java")
    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        val paint = Paint()
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 40.0f
        val font = Typeface.createFromAsset(context.assets,
        "fonts/qomolangma-tsutong.ttf"); //Insert your font here.
        paint.typeface = font;

        val keys = keyboard.keys;
        for(key in keys) {
            if(key.label != null)
                canvas?.drawText(key.label.toString(), key.x.toFloat(), key.y.toFloat(), paint);
        }
    }*/

}