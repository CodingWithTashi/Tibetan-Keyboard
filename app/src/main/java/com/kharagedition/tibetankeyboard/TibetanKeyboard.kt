package com.kharagedition.tibetankeyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import android.view.View


class TibetanKeyboard : InputMethodService(), OnKeyboardActionListener {
    private var kv: KeyboardView? = null
    private var keyboard: Keyboard? = null
    private var isCaps = false

    //Press Ctrl+O
    override fun onCreateInputView(): View {
        Log.i("TAG", "onCreateInputView: CALLED")
        kv = layoutInflater.inflate(R.layout.keyboard, null) as KeyboardView
        keyboard = Keyboard(this, R.xml.qwerty)
        kv!!.keyboard = keyboard
        kv!!.setOnKeyboardActionListener(this)
        return kv!!
    }

    override fun onPress(i: Int) {}
    override fun onRelease(i: Int) {}
    override fun onKey(i: Int, ints: IntArray) {
        val ic = currentInputConnection
        playClick(i)
        when (i) {
            Keyboard.KEYCODE_DELETE -> ic.deleteSurroundingText(1, 0)
            Keyboard.KEYCODE_SHIFT -> {
                isCaps = !isCaps
                keyboard!!.isShifted = isCaps
                kv!!.invalidateAllKeys()
            }
            Keyboard.KEYCODE_DONE -> ic.sendKeyEvent(
                KeyEvent(
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_ENTER
                )
            )
            else -> {
                var code = i.toChar()
                if (Character.isLetter(code) && isCaps) code = Character.toUpperCase(code)
                ic.commitText(code.toString(), 1)
            }
        }
    }

    private fun playClick(i: Int) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        when (i) {
            32 -> am.playSoundEffect(AudioManager.FX_KEYPRESS_SPACEBAR)
            Keyboard.KEYCODE_DONE, 10 -> am.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN)
            Keyboard.KEYCODE_DELETE -> am.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE)
            else -> am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
        }
    }

    override fun onText(charSequence: CharSequence) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}