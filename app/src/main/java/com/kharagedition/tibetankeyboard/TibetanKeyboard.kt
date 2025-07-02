package com.kharagedition.tibetankeyboard

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.preference.PreferenceManager
import com.kharagedition.tibetankeyboard.ui.KeyboardType
import com.kharagedition.tibetankeyboard.util.AppConstant


class TibetanKeyboard : InputMethodService(), OnKeyboardActionListener {
    private var keyboardView: TibetanKeyboardView? = null
    private var keyboard: Keyboard? = null
    private var isCaps = false
    private var isLanguageTibetan: Boolean = true
    lateinit var prefs: SharedPreferences

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        setInputView(onCreateInputView())
        super.onStartInputView(info, restarting)
    }

    //Press Ctrl+O
    override fun onCreateInputView(): View {
        Log.i("TAG", "onCreateInputView: CALLED")
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        setKeyBoardLanguage()
        setKeyBoardView()
        isLanguageTibetan = getSharedPreferences("com.kharagedition.tibetankeyboard", MODE_PRIVATE).getBoolean(
            AppConstant.IS_TIB,true)
        keyboard = if(isLanguageTibetan){
            Keyboard(this, R.xml.tibetan_uchen_alphabet_1)
        }else{
            Keyboard(this, R.xml.qwerty)
        }
        keyboardView?.keyboard = keyboard
        keyboardView?.setOnKeyboardActionListener(this)

        return keyboardView!!
    }

    private fun setKeyBoardLanguage() {
        val isUme = prefs.getBoolean("isUme", false)
        if(isUme){
            FontsOverride.setDefaultFont(this, "DEFAULT", "fonts/qomolangma-tsutong.ttf");
        }else{
            FontsOverride.setDefaultFont(this, "DEFAULT", null);
        }
    }

    private fun setKeyBoardView() {
        val color = prefs.getString("colors", "#FF704C04")
        keyboardView = when (color) {
            "#FF704C04" -> {
                layoutInflater.inflate(R.layout.keyboard_brown, null) as TibetanKeyboardView

            }
            "#FF000000" -> {
                layoutInflater.inflate(R.layout.keyboard_black, null) as TibetanKeyboardView
            }
            else -> {
                layoutInflater.inflate(R.layout.keyboard_green, null) as TibetanKeyboardView
            }
        }
        keyboardView?.setBackgroundColor(Color.parseColor(color))
        /*android:keyTextSize="20sp"*/


    }

    override fun onPress(i: Int) {}
    override fun onRelease(i: Int) {}
    override fun onKey(i: Int, ints: IntArray) {
        val inputConnection = currentInputConnection
        Log.i("TAG", "onKey: $i")
        val vibrate = prefs.getBoolean("vibrate", false)
        val sound = prefs.getBoolean("sound", true)
        if(vibrate){
            vibratePhone()
        }
        if(sound)
            playClick(i)
        when (i) {
            Keyboard.KEYCODE_DELETE -> inputConnection.deleteSurroundingText(1, 0)
            Keyboard.KEYCODE_SHIFT -> {
                isCaps = !isCaps
                keyboard!!.isShifted = isCaps
                keyboardView!!.invalidateAllKeys()
            }
            Keyboard.KEYCODE_DONE -> {
                sendDefaultEditorAction(true)
                inputConnection.sendKeyEvent(
                        KeyEvent(
                                KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_ENTER

                        )
                )
            }
            //set keyboard type base on when user change the keyboard type on click on language icon
            KeyboardType.TIBETAN_UCHEN_ALPHABET_1 -> {
                val prefs = getSharedPreferences(
                    "com.kharagedition.dictionary", Context.MODE_PRIVATE).edit()
                prefs.putBoolean(AppConstant.IS_TIB,true)
                prefs.apply()
                keyboardView?.keyboard = Keyboard(this, R.xml.tibetan_uchen_alphabet_1)
            }

            KeyboardType.TIBETAN_UCHEN_ALPHABET_2 -> {
                keyboardView?.keyboard = Keyboard(this, R.xml.tibetan_uchen_alphabet_2)
            }
            KeyboardType.SYMBOL_1 -> {
                keyboardView?.keyboard = Keyboard(this, R.xml.tibetan_uchen_symbol_1)
            }
            KeyboardType.QWERTY_SMALL -> {
                val prefs = getSharedPreferences(
                    "com.kharagedition.dictionary", Context.MODE_PRIVATE).edit()
                prefs.putBoolean(AppConstant.IS_TIB,false)
                prefs.apply()
                keyboardView?.keyboard = Keyboard(this, R.xml.qwerty)
            }
            KeyboardType.TIBETAN -> {
                keyboardView?.keyboard = Keyboard(this, R.xml.tibetan_uchen_alphabet_1)
            }
            KeyboardType.QWERTY_CAP -> {
                keyboardView?.keyboard = Keyboard(this, R.xml.qwerty_cap)
            }
            KeyboardType.SYMBOL_EN -> {
                keyboardView?.keyboard = Keyboard(this, R.xml.symbol_en)
            }
            KeyboardType.GEMINI -> {
                // navigate to chatactivity page
                var chatactivity = "com.kharagedition.tibetankeyboard.ui.ChatActivity"
                var intent = packageManager.getLaunchIntentForPackage("com.kharagedition.tibetankeyboard")
                if (intent != null) {
                    intent.component = ComponentName("com.kharagedition.tibetankeyboard", chatactivity)
                    startActivity(intent)
                }
            }

            else -> {
                var code = i.toChar()
                if (Character.isLetter(code) && isCaps) code = Character.toUpperCase(code)
                inputConnection.commitText(code.toString(), 1)
            }
        }
    }

    private fun vibratePhone() {
        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(50, 1))
        } else {
            //deprecated in API 26
            v.vibrate(50)
        }
    }

    private fun playClick(i: Int) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        when (i) {
            32 -> am.playSoundEffect(AudioManager.FX_KEYPRESS_SPACEBAR,1.0f)
            Keyboard.KEYCODE_DONE, 10 -> am.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN,1.0f)
            Keyboard.KEYCODE_DELETE -> am.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE,1.0f)
            else -> am.playSoundEffect(AudioManager.FX_KEY_CLICK,1.0f)
        }
    }

    override fun onText(charSequence: CharSequence) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}