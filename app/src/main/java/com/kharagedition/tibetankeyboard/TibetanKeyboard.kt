package com.kharagedition.tibetankeyboard

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ImageSpan
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import com.kharagedition.tibetankeyboard.ui.KeyboardType
import com.kharagedition.tibetankeyboard.util.AppConstant
import com.kharagedition.tibetankeyboard.ai.AIKeyboardInterface


class TibetanKeyboard : InputMethodService(), OnKeyboardActionListener, AIKeyboardInterface {
    private var keyboardView: TibetanKeyboardView? = null
    private var keyboard: Keyboard? = null
    private var isCaps = false
    private var isLanguageTibetan: Boolean = true
    lateinit var prefs: SharedPreferences
    private var aiKeyboardView: AIKeyboardView? = null
    private var currentMode = KeyboardMode.NORMAL
    private var emojiKeyboardView: EmojiKeyboardView? = null
    private var isEmojiMode = false

    enum class KeyboardMode {
        NORMAL,
        AI_GRAMMAR,
        AI_REPHRASE
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        setInputView(onCreateInputView())
        super.onStartInputView(info, restarting)
    }

    override fun onCreateInputView(): View {
        Log.i("TAG", "onCreateInputView: CALLED")
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        setKeyBoardLanguage()

        // Create the main container with AI toolbar
        val mainContainer = createMainKeyboardView()

        // Create and set up the normal keyboard view
        setKeyBoardView()

        // Add the normal keyboard to the AI container
        val normalContainer = aiKeyboardView?.findViewById<FrameLayout>(R.id.normal_keyboard_container)
        normalContainer?.addView(keyboardView)

        isLanguageTibetan = getSharedPreferences("com.kharagedition.tibetankeyboard", MODE_PRIVATE).getBoolean(
            AppConstant.IS_TIB,true)
        keyboard = if(isLanguageTibetan){
            Keyboard(this, R.xml.tibetan_uchen_alphabet_1)
        }else{
            Keyboard(this, R.xml.qwerty)
        }
        keyboardView?.keyboard = keyboard
        keyboardView?.setOnKeyboardActionListener(this)

        return mainContainer
    }

    private fun createMainKeyboardView(): View {
        val color = prefs.getString("colors", "#FF704C04")

        // Create AIKeyboardView directly
        aiKeyboardView = AIKeyboardView(this)
        aiKeyboardView?.setAIKeyboardInterface(this)
        aiKeyboardView?.setThemeColor(color ?: "#FF704C04")
        isEmojiMode = false
        return aiKeyboardView!!
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

            // AI Feature Codes - These are handled by AIKeyboardView now
            AIKeyboardCodes.AI_BACK -> {
                currentMode = KeyboardMode.NORMAL
                aiKeyboardView?.showNormalKeyboard()
            }

            // Existing keyboard type handling
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
                var chatactivity = "com.kharagedition.tibetankeyboard.ui.ChatActivity"
                var intent = packageManager.getLaunchIntentForPackage("com.kharagedition.tibetankeyboard")
                if (intent != null) {
                    intent.component = ComponentName("com.kharagedition.tibetankeyboard", chatactivity)
                    startActivity(intent)
                }
            }
            KeyboardType.EMOJI_KEYBOARD -> {
                toggleEmojiKeyboard()
            }

            else -> {
                var code = i.toChar()
                if (Character.isLetter(code) && isCaps) code = Character.toUpperCase(code)
                inputConnection.commitText(code.toString(), 1)
            }
        }
    }

    private fun toggleEmojiKeyboard() {
        if (!isEmojiMode) {
            showEmojiKeyboard()
        } else {
            hideEmojiKeyboard()
        }
    }

    private fun showEmojiKeyboard() {
        if (emojiKeyboardView == null) {
            emojiKeyboardView = EmojiKeyboardView(this)
            val themeColor = prefs.getString("colors", "#FF704C04") ?: "#FF704C04"
            emojiKeyboardView?.setThemeColor(themeColor)


            emojiKeyboardView?.setOnEmojiClickListener { content->
                currentInputConnection?.commitText(content, 1)
            }
            emojiKeyboardView?.setOnStickerClickListener { stickerInfo->
                insertStickerIntoInput(stickerInfo)
            }

            }
            emojiKeyboardView?.setOnBackClickListener {
                hideEmojiKeyboard()
            }


        // Hide the normal keyboard container
        val normalContainer = aiKeyboardView?.findViewById<FrameLayout>(R.id.normal_keyboard_container)
        normalContainer?.visibility = View.GONE

        // Show the emoji keyboard container
        val emojiContainer = aiKeyboardView?.findViewById<FrameLayout>(R.id.emoji_keyboard_container)
        emojiContainer?.let { container ->
            emojiKeyboardView?.parent?.let { parent ->
                (parent as? ViewGroup)?.removeView(emojiKeyboardView)
            }
            container.removeAllViews()
            container.addView(emojiKeyboardView)
            container.visibility = View.VISIBLE
        }

        isEmojiMode = true
    }

    /**
     * Insert sticker as ImageSpan into the input field
     * Format: "STICKER:resourceId:fileName"
     */
    private fun insertStickerIntoInput(stickerItem: StickerItem) {
        val inputConnection = currentInputConnection ?: return


        val resourceId = stickerItem.resourceId
        val fileName = stickerItem.fileName

        try {
            // Load the drawable
            val drawable = resources.getDrawable(resourceId, null)

            // Get the current text size from input (approximate)
            val textSize = 48 // Default text size in pixels, you can adjust this
            val stickerSize = (textSize * 1.5).toInt()
            drawable.setBounds(0, 0, stickerSize, stickerSize)

            // Create ImageSpan with placeholder text
            val span = ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM)
            val placeholder = "[$fileName]"
            val spannable = SpannableString(placeholder)
            spannable.setSpan(span, 0, placeholder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Commit the spannable text
            inputConnection.commitText(spannable, 1)

            Log.d("TAG", "Sticker inserted: $fileName")
        } catch (e: Exception) {
            Log.e("TAG", "Error inserting sticker", e)
            // Fallback: insert as text placeholder
            inputConnection.commitText("[$fileName]", 1)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && isEmojiMode) {
            hideEmojiKeyboard()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun hideEmojiKeyboard() {
        // Show the normal keyboard container
        val normalContainer = aiKeyboardView?.findViewById<FrameLayout>(R.id.normal_keyboard_container)
        normalContainer?.visibility = View.VISIBLE

        // Hide the emoji keyboard container
        val emojiContainer = aiKeyboardView?.findViewById<FrameLayout>(R.id.emoji_keyboard_container)
        emojiContainer?.let { container ->
            container.visibility = View.GONE
            container.removeAllViews()
        }

        isEmojiMode = false
    }

    private fun getCurrentInputText(): String {
        val inputConnection = currentInputConnection ?: return ""

        // Try to get all text from the input field
        val extractedTextRequest = ExtractedTextRequest()
        extractedTextRequest.flags = 0
        extractedTextRequest.hintMaxChars = 10000
        extractedTextRequest.hintMaxLines = 100

        val extractedText: ExtractedText? = inputConnection.getExtractedText(extractedTextRequest, 0)

        return extractedText?.text?.toString() ?: run {
            // Fallback: get text before and after cursor
            val textBefore = inputConnection.getTextBeforeCursor(1000, 0)?.toString() ?: ""
            val textAfter = inputConnection.getTextAfterCursor(1000, 0)?.toString() ?: ""
            textBefore + textAfter
        }
    }

    // AIKeyboardInterface implementation
    override fun getCurrentText(): String {
        return getCurrentInputText()
    }

    override fun onGrammarReplace(originalText: String, correctedText: String) {
        val inputConnection = currentInputConnection
        if (inputConnection != null) {
            // Clear current text and insert corrected text
            val textLength = originalText.length
            inputConnection.deleteSurroundingText(textLength, 0)
            inputConnection.commitText(correctedText, 1)
        }
        currentMode = KeyboardMode.NORMAL
        aiKeyboardView?.showNormalKeyboard()
    }

    override fun onTranslateReplace(originalText: String, translatedText: String) {
        val inputConnection = currentInputConnection
        if (inputConnection != null) {
            // Clear current text and insert rephrased text
            val textLength = originalText.length
            inputConnection.deleteSurroundingText(textLength, 0)
            inputConnection.commitText(translatedText, 1)
        }
        currentMode = KeyboardMode.NORMAL
        aiKeyboardView?.showNormalKeyboard()
    }

    override fun onRephraseReplace(originalText: String, rephrasedText: String) {
        val inputConnection = currentInputConnection
        if (inputConnection != null) {
            // Clear current text and insert rephrased text
            val textLength = originalText.length
            inputConnection.deleteSurroundingText(textLength, 0)
            inputConnection.commitText(rephrasedText, 1)
        }
        currentMode = KeyboardMode.NORMAL
        aiKeyboardView?.showNormalKeyboard()
    }

    override fun onAICancel() {
        currentMode = KeyboardMode.NORMAL
        aiKeyboardView?.showNormalKeyboard()
    }

    private fun vibratePhone() {
        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(50, 1))
        } else {
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