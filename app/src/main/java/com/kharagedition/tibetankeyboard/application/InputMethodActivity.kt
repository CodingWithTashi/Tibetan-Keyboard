package com.kharagedition.tibetankeyboard.application

import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity


/**
 * Created by kharag on 31,July,2021
 */
abstract class InputMethodActivity : AppCompatActivity() {
    protected abstract fun onInputMethodPicked()
    override fun onCreate(savedInstanceState: Bundle?) {
        mState = NONE
        super.onCreate(savedInstanceState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (mState == PICKING) {
            mState = CHOSEN
        } else if (mState == CHOSEN) {
            onInputMethodPicked()
        }
    }

    private var mState = 0
    protected fun pickInput() {
        val imm: InputMethodManager =
            applicationContext.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
        mState = PICKING
    }

    companion object {
        private const val NONE = 0
        private const val PICKING = 1
        private const val CHOSEN = 2
    }
}