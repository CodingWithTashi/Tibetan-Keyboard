package com.kharagedition.tibetankeyboard.service

import android.content.Context
import android.content.Intent
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
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import com.kharagedition.tibetankeyboard.ui.keyboard.KeyboardType
import com.kharagedition.tibetankeyboard.util.AppConstant
import com.kharagedition.tibetankeyboard.auth.AuthManager
import com.kharagedition.tibetankeyboard.ui.chat.ChatActivity
import com.kharagedition.tibetankeyboard.ui.keyboard.AIKeyboardInterface
import com.kharagedition.tibetankeyboard.ui.keyboard.TibetanKeyboardView
import com.kharagedition.tibetankeyboard.ui.keyboard.AIKeyboardView
import com.kharagedition.tibetankeyboard.ui.keyboard.EmojiKeyboardView
import com.kharagedition.tibetankeyboard.ui.keyboard.AIKeyboardCodes
import com.kharagedition.tibetankeyboard.ui.keyboard.EmojiItemList
import com.kharagedition.tibetankeyboard.ui.keyboard.StickerItem
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.FontsOverride


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

    // Tracks how many Unicode code points the user has typed since the last word boundary.
    // Used to know exactly what to delete when a suggestion is selected.
    // Resets on: shad (།), space, newline, or suggestion commit.
    // Tshek (་) is NOT a boundary — it is part of the Tibetan word.
    private var currentWordLength = 0

    enum class KeyboardMode {
        NORMAL,
        AI_GRAMMAR,
        AI_REPHRASE
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        currentWordLength = 0
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
        // Uchen only — never the cursive Ume/Tsutong face. Use the system Tibetan
        // font (renders Uchen) so Latin UI text stays clean too.
        FontsOverride.setDefaultFont(this, "DEFAULT", null)
    }

    private fun setKeyBoardView() {
        val color = prefs.getString("colors", "#FF704C04")
        val keyboardStyle = prefs.getString("keyboard_style", "classic")

        if (keyboardStyle == "borderless") {
            // Borderless is colour-agnostic: one transparent-key layout, surface tint
            // applied below. Glyphs only, no key boxes.
            keyboardView = layoutInflater.inflate(R.layout.keyboard_borderless, null) as TibetanKeyboardView
            keyboardView?.setBackgroundColor(darkenColor(Color.parseColor(color ?: "#FF704C04"), 0.42f))
            return
        }

        keyboardView = when (color) {
            "#FF704C04" -> {
                if (keyboardStyle == "modern") {
                    layoutInflater.inflate(R.layout.keyboard_brown_modern, null) as TibetanKeyboardView
                } else {
                    layoutInflater.inflate(R.layout.keyboard_brown, null) as TibetanKeyboardView
                }
            }
            "#FF000000" -> {
                if (keyboardStyle == "modern") {
                    layoutInflater.inflate(R.layout.keyboard_black_modern, null) as TibetanKeyboardView
                } else {
                    layoutInflater.inflate(R.layout.keyboard_black, null) as TibetanKeyboardView
                }
            }
            else -> {
                if (keyboardStyle == "modern") {
                    layoutInflater.inflate(R.layout.keyboard_green_modern, null) as TibetanKeyboardView
                } else {
                    layoutInflater.inflate(R.layout.keyboard_green, null) as TibetanKeyboardView
                }
            }
        }
        // Match the dark espresso surface used by the AI toolbar (see AIKeyboardView.applyTheme).
        keyboardView?.setBackgroundColor(darkenColor(Color.parseColor(color), 0.42f))
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
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
            Keyboard.KEYCODE_DELETE -> {
                inputConnection.deleteSurroundingText(1, 0)
                if (currentWordLength > 0) currentWordLength--
                aiKeyboardView?.updateSuggestions(currentPrefix(inputConnection))
            }
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
            KeyboardType.EMOJI_KEYBOARD -> {
                toggleEmojiKeyboard()
            }

            else -> {
                var code = i.toChar()
                if (Character.isLetter(code) && isCaps) code = Character.toUpperCase(code)
                // Shad (།) and space are sentence/word boundaries — reset the word tracker.
                // Tshek (་) is a syllable separator WITHIN a word, so it increments the counter.
                if (code == '།' || code == '༎' || code == ' ' || code == '\n') {
                    currentWordLength = 0
                } else {
                    currentWordLength++
                }
                inputConnection.commitText(code.toString(), 1)
                aiKeyboardView?.updateSuggestions(currentPrefix(inputConnection))
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

    override fun onSuggestionSelected(word: String) {
        val ic = currentInputConnection ?: return
        Log.d("TibetanKeyboard", "onSuggestionSelected: '$word', deleting $currentWordLength chars")
        if (currentWordLength > 0) ic.deleteSurroundingText(currentWordLength, 0)
        ic.commitText(word, 1)
        currentWordLength = 0
        aiKeyboardView?.updateSuggestions("")
    }

    override fun onOpenChat() {
        startActivity(
            Intent(this, ChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun onUnlockPro() {
        // Not signed in → login (which forwards to the paywall); signed in but free → paywall.
        val authManager = AuthManager(this)
        if (!authManager.isUserAuthenticated()) {
            authManager.redirectToLogin(openPremiumAfter = true)
        } else {
            authManager.openPremium()
        }
    }

    // Returns the Unicode code points the user has typed since the last word boundary,
    // verified against actual text before the cursor.
    private fun currentPrefix(ic: InputConnection): String {
        if (currentWordLength == 0) return ""
        val textBefore = ic.getTextBeforeCursor(currentWordLength + 5, 0)?.toString() ?: ""
        val prefix = textBefore.takeLast(currentWordLength)
        Log.d("TibetanKeyboard", "prefix='$prefix' (wordLen=$currentWordLength)")
        return prefix
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