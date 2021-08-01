package com.kharagedition.tibetankeyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import android.view.View
import com.kharagedition.tibetankeyboard.ui.KeyboardType
import com.kharagedition.tibetankeyboard.util.AppConstant


class TibetanKeyboard : InputMethodService(), OnKeyboardActionListener {
    private var kv: KeyboardView? = null
    private var keyboard: Keyboard? = null
    private var isCaps = false
    private var isLanguageTibetan: Boolean = true
    //Press Ctrl+O
    override fun onCreateInputView(): View {
        Log.i("TAG", "onCreateInputView: CALLED")
        kv = layoutInflater.inflate(R.layout.keyboard, null) as KeyboardView
        isLanguageTibetan = getSharedPreferences("com.kharagedition.tibetankeyboard", MODE_PRIVATE).getBoolean(
            AppConstant.IS_TIB,true)
        keyboard = if(isLanguageTibetan){
            Keyboard(this, R.xml.tibetan_alphabet_1)
        }else{
            Keyboard(this, R.xml.qwerty)
        }
        kv!!.keyboard = keyboard
        kv!!.setOnKeyboardActionListener(this)

        return kv!!
    }

    override fun onPress(i: Int) {}
    override fun onRelease(i: Int) {}
    override fun onKey(i: Int, ints: IntArray) {
        val ic = currentInputConnection
        Log.i("TAG", "onKey: $i")
        playClick(i)
        when (i) {
            Keyboard.KEYCODE_DELETE -> ic.deleteSurroundingText(1, 0)
            Keyboard.KEYCODE_SHIFT -> {
                isCaps = !isCaps
                keyboard!!.isShifted = isCaps
                kv!!.invalidateAllKeys()
            }
            Keyboard.KEYCODE_DONE -> {
                sendDefaultEditorAction(true);
                ic.sendKeyEvent(
                        KeyEvent(
                                KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_ENTER

                        )
                )
            }
            KeyboardType.TIBETAN_ALPHABET_1 -> {
                val prefs = getSharedPreferences(
                    "com.kharagedition.dictionary", Context.MODE_PRIVATE).edit()
                prefs.putBoolean(AppConstant.IS_TIB,true)
                prefs.apply();
                kv?.keyboard = Keyboard(this, R.xml.tibetan_alphabet_1)
            }

            KeyboardType.TIBETAN_ALPHABET_2 -> {
                kv?.keyboard = Keyboard(this, R.xml.tibetan_alphabet_2)
            }
            KeyboardType.SYMBOL_1 -> {
                kv?.keyboard = Keyboard(this, R.xml.tibetan_symbol_1)
            }
            KeyboardType.QWERTY_SMALL -> {
                val prefs = getSharedPreferences(
                    "com.kharagedition.dictionary", Context.MODE_PRIVATE).edit()
                prefs.putBoolean(AppConstant.IS_TIB,false)
                prefs.apply();
                kv?.keyboard = Keyboard(this, R.xml.qwerty)
            }
            KeyboardType.TIBETAN -> {
                kv?.keyboard = Keyboard(this, R.xml.tibetan_alphabet_1)
            }
            KeyboardType.QWERTY_CAP -> {
                kv?.keyboard = Keyboard(this, R.xml.qwerty_cap)
            }
            KeyboardType.SYMBOL_EN -> {
                kv?.keyboard = Keyboard(this, R.xml.symbol_en)
            }

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