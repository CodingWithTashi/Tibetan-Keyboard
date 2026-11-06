package com.kharagedition.tibetankeyboard.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ImageSpan
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.firebase.auth.FirebaseAuth
import com.kharagedition.tibetankeyboard.BuildConfig
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.analytics.UserActivityTracker
import com.kharagedition.tibetankeyboard.data.local.TypingStatsStore
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager
import com.kharagedition.tibetankeyboard.data.repository.subscriptionCallback
import com.kharagedition.tibetankeyboard.ui.keyboard.KeyboardType
import com.kharagedition.tibetankeyboard.ui.settings.SettingsPrefs
import com.kharagedition.tibetankeyboard.util.AppConstant
import com.kharagedition.tibetankeyboard.util.openPremiumUpgrade
import com.kharagedition.tibetankeyboard.ui.chat.ChatActivity
import com.kharagedition.tibetankeyboard.ui.journey.WordSegmenter
import com.kharagedition.tibetankeyboard.ui.keyboard.AIKeyboardInterface
import com.kharagedition.tibetankeyboard.ui.keyboard.ClearRule
import com.kharagedition.tibetankeyboard.ui.keyboard.DeleteRule
import com.kharagedition.tibetankeyboard.ui.keyboard.KeyboardLayoutHint
import com.kharagedition.tibetankeyboard.ui.keyboard.SizedKeyboard
import com.kharagedition.tibetankeyboard.ui.keyboard.keyLabelFontScaleFor
import com.kharagedition.tibetankeyboard.ui.keyboard.TibetanKeyboardView
import com.kharagedition.tibetankeyboard.ui.keyboard.WordComposer
import com.kharagedition.tibetankeyboard.ui.keyboard.EditorReadGuard
import com.kharagedition.tibetankeyboard.ui.keyboard.AIKeyboardView
import com.kharagedition.tibetankeyboard.ui.keyboard.EmojiKeyboardView
import com.kharagedition.tibetankeyboard.ui.keyboard.AIKeyboardCodes
import com.kharagedition.tibetankeyboard.ui.keyboard.EmojiItemList
import com.kharagedition.tibetankeyboard.ui.keyboard.StickerItem
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.data.repository.SpeechRepository
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.AppAnalyticsVoiceEvents
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.AudioRecordRecorder
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceAvailability
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceController
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceEvents
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceField
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceInsert
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoicePanelMemory
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoicePermissionActivity
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoicePermissionResult
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoicePolicy
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceResume


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

    /** What the keyboard's views are built with; see [keyboardContext]. Set per view build. */
    private var viewContext: Context = this

    // Per-field layout override requested by the focused field via
    // EditorInfo.privateImeOptions (e.g. the AI Translate source field forces
    // QWERTY for an English source). true → Tibetan, false → QWERTY, null →
    // honour the user's own saved language choice. Set on every onStartInputView.
    private var forcedTibetan: Boolean? = null

    // The word being typed (the suggestion prefix) and the rules for accepting a suggestion:
    // what a tap replaces, the tsheg it writes, and how the next key treats that tsheg. Pure and
    // unit-tested; this service only feeds it editor text and applies the edits it returns.
    private val composer = WordComposer()

    // The editor reads below block this thread on the app being typed in. A hung app would stall
    // every key (a held Delete repeats it) into an ANR, so after one slow read they answer null
    // for a few seconds, which the composer already handles as "the editor cannot say".
    private val editorReads = EditorReadGuard(now = SystemClock::elapsedRealtime)

    // Journey streak/stats. PRIVACY: the store only ever receives counts and one-way word
    // hashes — no typed text is persisted or transmitted (see TypingStatsStore's contract).
    private val typingStats by lazy { TypingStatsStore.getInstance(this) }

    enum class KeyboardMode {
        NORMAL,
        AI_GRAMMAR,
        AI_REPHRASE
    }

    companion object {
        /** Shared logcat tag for the client subscription flow (`adb logcat -s SubsFlow:*`). */
        private const val SUBS_TAG = "SubsFlow"
    }

    override fun onCreate() {
        super.onCreate()
        // The IME can start in a FRESH process with no app Activity having run first
        // (the user just types in another app), and Application.onCreate does NOT
        // configure RevenueCat. Without this, RevenueCatManager.isPremiumUser stays
        // false inside the keyboard, so a PAYING user sees Chat/Translate/Autocomplete
        // locked and gets routed to the paywall. Firebase Auth restores currentUser
        // synchronously in any process, and initialize() is idempotent (a cheap logIn
        // when already configured), so this is safe on every IME create.
        initializeRevenueCatForKeyboard()
        VoicePermissionResult.listener = ::onVoicePermissionAnswer
    }

    /**
     * Configure RevenueCat for this process so the keyboard knows the user's PRO status.
     * No-ops for signed-out users (the keyboard simply runs free-tier). All lines are
     * prefixed `subs-flow:` so the whole subscription path is greppable in logcat:
     *   adb logcat | grep subs-flow
     */
    private fun initializeRevenueCatForKeyboard() {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            Log.d(SUBS_TAG, "subs-flow: IME onCreate — no Firebase user; keyboard runs free-tier")
            return
        }
        Log.i(SUBS_TAG, "subs-flow: IME onCreate — initializing RevenueCat for uid=${user.uid}")
        RevenueCatManager.getInstance().initialize(applicationContext, auth, subscriptionCallback(
            onSuccess = {
                Log.i(
                    SUBS_TAG,
                    "subs-flow: IME RevenueCat ready — premium=${RevenueCatManager.getInstance().isPremiumUserCached()}"
                )
            },
            onError = { Log.w(SUBS_TAG, "subs-flow: IME RevenueCat init failed: $it") },
        ))
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        composer.reset()
        editorReads.reset()
        // Read the focused field's requested layout before (re)building the view,
        // so onCreateInputView can open on the matching language.
        forcedTibetan = KeyboardLayoutHint.forcedTibetan(info?.privateImeOptions)
        // The voice panel survives the view being rebuilt (an app restarting input, the system's
        // permission dialog) on the same field, in whatever state it is in; never a move to
        // another field, where its text would land in the wrong place.
        val field = VoiceField.of(info?.packageName, info?.fieldId ?: 0)
        val voiceAllowed = VoiceAvailability.allowed(info?.inputType ?: 0, info?.privateImeOptions)
        val keepVoice = voicePanel.restoreOn(field, voiceAllowed)
        if (voiceStarted && !keepVoice) voice.cancelListening(VoiceEvents.EndReason.KEYBOARD_HIDDEN)
        if (!restarting) {
            AppAnalytics.logKeyboardShown()
            // Count keyboard usage toward the user's daily activity (throttled to one write/day).
            UserActivityTracker.recordActive(this, UserActivityTracker.Source.KEYBOARD)
        }
        setInputView(onCreateInputView())
        aiKeyboardView?.applyVoiceAvailability(voiceAllowed)
        if (keepVoice) {
            aiKeyboardView?.setVoiceController(voice)
            aiKeyboardView?.showVoicePanel(animated = false)
        }
        // Whether or not the panel came back by itself: right after "Allow microphone" it is the
        // permission's answer that decides what it shows next (listening, asking again, or off).
        resumeVoiceAfterPermission(field)
        super.onStartInputView(info, restarting)
    }

    override fun onWindowHidden() {
        // The keyboard is going away (Back, another app): a recording must not go on unseen.
        // A write-down already under way finishes; its text is typed only if the field is the same.
        // Coming back, the user gets the keys, not a stale panel.
        voicePanel.closed()
        if (voiceStarted) voice.cancelListening(VoiceEvents.EndReason.KEYBOARD_HIDDEN)
        super.onWindowHidden()
    }

    override fun onDestroy() {
        VoicePermissionResult.listener = null
        if (voiceStarted) voice.release()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        Log.i("TAG", "onCreateInputView: CALLED")
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        viewContext = keyboardContext()

        // Create the main container with AI toolbar
        val mainContainer = createMainKeyboardView()

        // Create and set up the normal keyboard view
        setKeyBoardView()

        // Add the normal keyboard to the AI container
        val normalContainer = aiKeyboardView?.findViewById<FrameLayout>(R.id.normal_keyboard_container)
        normalContainer?.addView(keyboardView)

        isLanguageTibetan = getSharedPreferences("com.kharagedition.tibetankeyboard", MODE_PRIVATE).getBoolean(
            AppConstant.IS_TIB,true)
        // A focused field can override the saved choice for itself only (e.g. the
        // Translate source field). Doesn't touch the persisted preference, so the
        // user's language in every other app is unaffected.
        forcedTibetan?.let { isLanguageTibetan = it }
        keyboard = if(isLanguageTibetan){
            SizedKeyboard(this, R.xml.tibetan_uchen_alphabet_1)
        }else{
            SizedKeyboard(this, R.xml.qwerty)
        }
        keyboardView?.keyboard = keyboard
        keyboardView?.setOnKeyboardActionListener(this)

        return mainContainer
    }

    /**
     * The context the keyboard's views are built with: this service, or, at a system font size
     * larger than a key has room for, this service with the font scale capped (see
     * [KeyboardSizing.labelFontScale]). `KeyboardView` takes its label size from its context
     * and offers no other handle on it; at 200% the letters of neighbouring keys ran into each
     * other and the bar's text outgrew its fixed height.
     */
    private fun keyboardContext(): Context {
        val scale = keyLabelFontScaleFor(this)
        if (scale >= resources.configuration.fontScale) return this
        // The theme InputMethodService gives itself, so the views resolve the same attributes.
        return ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_InputMethod).apply {
            applyOverrideConfiguration(Configuration().apply { fontScale = scale })
        }
    }

    /** Colour the user is entitled to; cached entitlement keeps this synchronous on the hot path. */
    private fun entitledColor(): String = SettingsPrefs.effectiveColor(
        prefs.getString(SettingsPrefs.KEY_COLOR, SettingsPrefs.DEFAULT_COLOR) ?: SettingsPrefs.DEFAULT_COLOR,
        RevenueCatManager.getInstance().isPremiumUserCached(),
    )

    /** Layout the user is entitled to. See [entitledColor]. */
    private fun entitledStyle(): String = SettingsPrefs.effectiveStyle(
        prefs.getString(SettingsPrefs.KEY_STYLE, SettingsPrefs.DEFAULT_STYLE) ?: SettingsPrefs.DEFAULT_STYLE,
        RevenueCatManager.getInstance().isPremiumUserCached(),
    )

    private fun createMainKeyboardView(): View {
        val color = entitledColor()

        // Create AIKeyboardView directly
        aiKeyboardView = AIKeyboardView(viewContext)
        aiKeyboardView?.setAIKeyboardInterface(this)
        aiKeyboardView?.setThemeColor(color ?: "#FF704C04")
        isEmojiMode = false
        return aiKeyboardView!!
    }

    private fun setKeyBoardView() {
        val color = entitledColor()
        val keyboardStyle = entitledStyle()
        val inflater = if (viewContext === this) layoutInflater else layoutInflater.cloneInContext(viewContext)

        if (keyboardStyle == "borderless") {
            // Borderless is colour-agnostic: one transparent-key layout, surface tint
            // applied below. Glyphs only, no key boxes.
            keyboardView = inflater.inflate(R.layout.keyboard_borderless, null) as TibetanKeyboardView
            keyboardView?.setBackgroundColor(darkenColor(Color.parseColor(color ?: "#FF704C04"), 0.42f))
            return
        }

        keyboardView = when (color) {
            "#FF704C04" -> {
                if (keyboardStyle == "modern") {
                    inflater.inflate(R.layout.keyboard_brown_modern, null) as TibetanKeyboardView
                } else {
                    inflater.inflate(R.layout.keyboard_brown, null) as TibetanKeyboardView
                }
            }
            "#FF000000" -> {
                if (keyboardStyle == "modern") {
                    inflater.inflate(R.layout.keyboard_black_modern, null) as TibetanKeyboardView
                } else {
                    inflater.inflate(R.layout.keyboard_black, null) as TibetanKeyboardView
                }
            }
            else -> {
                if (keyboardStyle == "modern") {
                    inflater.inflate(R.layout.keyboard_green_modern, null) as TibetanKeyboardView
                } else {
                    inflater.inflate(R.layout.keyboard_green, null) as TibetanKeyboardView
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
        // currentInputConnection is a platform type: it is null whenever no editor is bound
        // (the target app went away, or a key lands between onFinishInput and onStartInput),
        // and Kotlin will not null-check it for us. Every branch below types into it, so
        // bail out rather than NPE inside the IME.
        val inputConnection = currentInputConnection ?: return
        Log.i("TAG", "onKey: $i")
        val vibrate = prefs.getBoolean("vibrate", false)
        val sound = prefs.getBoolean("sound", true)

        if(vibrate){
            vibratePhone()
        }
        if(sound)
            playClick(i)

        when (i) {
            Keyboard.KEYCODE_DELETE -> deleteBackward(inputConnection)
            Keyboard.KEYCODE_SHIFT -> {
                isCaps = !isCaps
                keyboard!!.isShifted = isCaps
                keyboardView!!.invalidateAllKeys()
            }
            Keyboard.KEYCODE_DONE -> {
                // Enter ends the run like a shad does (word stats, suggestion state); the editor
                // itself gets the action / key event below, not the composer's text.
                val edit = composer.onChar('\n', textBeforeCursor(inputConnection))
                if (edit.finishedChunk.isNotEmpty()) {
                    recordChunkAsWords(edit.finishedChunk)
                }
                sendDefaultEditorAction(true)
                inputConnection.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_ENTER
                    )
                )
                aiKeyboardView?.updateSuggestions("")
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
                AppAnalytics.logKeyboardLanguageSwitched(AppAnalytics.KeyboardLanguage.TIBETAN)
                keyboardView?.keyboard = SizedKeyboard(this, R.xml.tibetan_uchen_alphabet_1)
            }

            KeyboardType.TIBETAN_UCHEN_ALPHABET_2 -> {
                keyboardView?.keyboard = SizedKeyboard(this, R.xml.tibetan_uchen_alphabet_2)
            }
            KeyboardType.SYMBOL_1 -> {
                keyboardView?.keyboard = SizedKeyboard(this, R.xml.tibetan_uchen_symbol_1)
            }
            KeyboardType.QWERTY_SMALL -> {
                val prefs = getSharedPreferences(
                    "com.kharagedition.dictionary", Context.MODE_PRIVATE).edit()
                prefs.putBoolean(AppConstant.IS_TIB,false)
                prefs.apply()
                AppAnalytics.logKeyboardLanguageSwitched(AppAnalytics.KeyboardLanguage.ENGLISH)
                keyboardView?.keyboard = SizedKeyboard(this, R.xml.qwerty)
            }
            KeyboardType.TIBETAN -> {
                keyboardView?.keyboard = SizedKeyboard(this, R.xml.tibetan_uchen_alphabet_1)
            }
            KeyboardType.QWERTY_CAP -> {
                keyboardView?.keyboard = SizedKeyboard(this, R.xml.qwerty_cap)
            }
            KeyboardType.SYMBOL_EN -> {
                keyboardView?.keyboard = SizedKeyboard(this, R.xml.symbol_en)
            }
            KeyboardType.EMOJI_KEYBOARD -> {
                toggleEmojiKeyboard()
            }

            else -> {
                var code = i.toChar()
                if (Character.isLetter(code) && isCaps) code = Character.toUpperCase(code)
                val edit = composer.onChar(code, textBeforeCursor(inputConnection))
                // A shad (།), space or newline just finished a chunk: segment it into real
                // dictionary words (Tibetan has no spaces between words, so the whole chunk can
                // be a full clause) and fold each into the Journey stats. Only one-way hashes
                // survive (vocabulary size), never the text.
                if (edit.finishedChunk.isNotEmpty()) {
                    recordChunkAsWords(edit.finishedChunk)
                }
                applyEdit(inputConnection, edit)
                // Journey streak: count Tibetan code points typed (a number, nothing else). A
                // tsheg typed onto the one an accepted suggestion already wrote inserts nothing.
                if (edit.text.isNotEmpty() && TypingStatsStore.isTibetanCodePoint(code.code)) {
                    typingStats.recordTibetanChars(1)?.let { milestone ->
                        AppAnalytics.logStreakMilestone(milestone)
                    }
                }
                aiKeyboardView?.updateSuggestions(composer.composing)
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
        AppAnalytics.logKeyboardEmojiOpened()
        if (emojiKeyboardView == null) {
            emojiKeyboardView = EmojiKeyboardView(this)
            val themeColor = entitledColor()
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
        if (keyCode == KeyEvent.KEYCODE_BACK && aiKeyboardView?.isVoicePanelOpen == true) {
            // Back from the voice panel returns to the keys; the recording is thrown away.
            aiKeyboardView?.hideVoicePanel()
            onVoiceClosed()
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
        AppAnalytics.logKeyboardAiApplied(AppAnalytics.KeyboardFeature.GRAMMAR)
        val inputConnection = currentInputConnection
        if (inputConnection != null) {
            // Clear current text and insert corrected text
            val textLength = originalText.length
            inputConnection.deleteSurroundingText(textLength, 0)
            inputConnection.commitText(correctedText, 1)
        }
        composer.reset()
        currentMode = KeyboardMode.NORMAL
        aiKeyboardView?.showNormalKeyboard()
    }

    override fun onTranslateReplace(originalText: String, translatedText: String) {
        AppAnalytics.logKeyboardAiApplied(AppAnalytics.KeyboardFeature.TRANSLATE)
        val inputConnection = currentInputConnection
        if (inputConnection != null) {
            // Clear current text and insert rephrased text
            val textLength = originalText.length
            inputConnection.deleteSurroundingText(textLength, 0)
            inputConnection.commitText(translatedText, 1)
        }
        composer.reset()
        currentMode = KeyboardMode.NORMAL
        aiKeyboardView?.showNormalKeyboard()
    }

    override fun onRephraseReplace(originalText: String, rephrasedText: String) {
        AppAnalytics.logKeyboardAiApplied(AppAnalytics.KeyboardFeature.REPHRASE)
        val inputConnection = currentInputConnection
        if (inputConnection != null) {
            // Clear current text and insert rephrased text
            val textLength = originalText.length
            inputConnection.deleteSurroundingText(textLength, 0)
            inputConnection.commitText(rephrasedText, 1)
        }
        composer.reset()
        currentMode = KeyboardMode.NORMAL
        aiKeyboardView?.showNormalKeyboard()
    }

    override fun onAICancel() {
        currentMode = KeyboardMode.NORMAL
        aiKeyboardView?.showNormalKeyboard()
    }

    override fun onSuggestionSelected(word: String, matched: String) {
        val ic = currentInputConnection ?: return
        AppAnalytics.logKeyboardSuggestionSelected()
        val edit = composer.onAccept(
            word,
            matched,
            textBeforeCursor(ic, extra = matched.length),
            editorReads.read { ic.getTextAfterCursor(1, 0) },
        )
        if (BuildConfig.DEBUG) {
            Log.d(
                "TibetanKeyboard",
                "onSuggestionSelected: '$word' for '$matched' → delete ${edit.deleteBefore}, insert '${edit.text}'"
            )
        }
        applyEdit(ic, edit)
        // Journey stats (hash-only, see store contract): the accepted word completes a word, and
        // the syllables it leaves in place before it are typed words too.
        if (edit.finishedChunk.isNotEmpty()) {
            recordChunkAsWords(edit.finishedChunk)
        }
        typingStats.recordWordTyped(word)?.let { milestone ->
            AppAnalytics.logStreakMilestone(milestone)
        }
        aiKeyboardView?.updateSuggestions("")
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // Our own edits echo back here too. The composer keeps state the editor still confirms
        // and drops the rest (the user tapped elsewhere, the app changed the text), so the strip
        // never offers to replace characters that are no longer before the cursor.
        if (!composer.hasState) return
        val ic = currentInputConnection ?: return
        val before = if (newSelStart == newSelEnd) textBeforeCursor(ic) else null
        if (composer.sync(before)) aiKeyboardView?.updateSuggestions("")
    }

    override fun onOpenChat() {
        startActivity(
            Intent(this, ChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun onUnlockPro(source: String) {
        // Single source of truth for the upgrade flow.
        openPremiumUpgrade(source)
    }

    // --- Voice typing -----------------------------------------------------------------------
    // The session lives here, not in a view: the keyboard rebuilds its views every time it opens,
    // and a recording or an upload must survive that. See VoiceController.

    private val voiceLazy = lazy {
        VoiceController(
            recorder = AudioRecordRecorder(this),
            transcriber = SpeechRepository(),
            micGranted = ::hasMicPermission,
            events = AppAnalyticsVoiceEvents,
        ).also { it.host = voiceHost }
    }
    private val voice: VoiceController get() = voiceLazy.value
    private val voiceStarted: Boolean get() = voiceLazy.isInitialized()

    /** Armed at "Allow microphone"; listening starts by itself once the user is back on that field. */
    private val voiceResume = VoiceResume()

    /** Which field the voice panel is open on, so it survives the keyboard rebuilding its views. */
    private val voicePanel = VoicePanelMemory()

    private val voiceHost = object : VoiceController.Host {
        override fun insert(text: String, field: VoiceField?): String? {
            val ic = currentInputConnection ?: return null
            if (field == null || !field.matches(currentVoiceField()) || !voiceAllowedHere()) return null
            composer.reset()
            val inserted = VoiceInsert.join(textBeforeCursor(ic), text)
            ic.commitText(inserted, 1)
            aiKeyboardView?.updateSuggestions("")
            creditStreak(text)
            return inserted
        }

        override fun undo(inserted: String): Boolean {
            val ic = currentInputConnection ?: return false
            val before = editorReads.read { ic.getTextBeforeCursor(inserted.length, 0) }
            if (!VoiceInsert.canUndo(before, inserted)) return false
            ic.deleteSurroundingText(inserted.length, 0)
            composer.reset()
            aiKeyboardView?.updateSuggestions("")
            return true
        }

        override fun selectAll() {
            val ic = onVoiceField() ?: return
            selectEverything(ic) { getCurrentInputText().length }
        }

        override fun clearAll(): String? {
            val ic = onVoiceField() ?: return null
            val everything = getCurrentInputText()
            // Undo can only put back what was read, and the read is capped: past the cap, do nothing
            // rather than clear text that could not all be restored.
            if (!ClearRule.restorable(everything)) return null
            selectEverything(ic) { everything.length }
            // Delete only what was read and is really selected (see ClearRule): an editor that ignored
            // "select all", or that gave a window of a longer field, is left untouched.
            if (!ClearRule.canClear(everything, editorReads.read { ic.getSelectedText(0) })) return null
            ic.commitText("", 1)
            composer.reset()
            aiKeyboardView?.updateSuggestions("")
            return everything
        }

        override fun restore(text: String): Boolean {
            val ic = onVoiceField() ?: return false
            ic.commitText(text, 1)
            composer.reset()
            aiKeyboardView?.updateSuggestions("")
            return true
        }

        override fun backspace(): String? {
            val ic = onVoiceField() ?: return null
            return deleteBackward(ic)
        }
    }

    /**
     * The input connection, but only while the keyboard is still on the field the panel opened on
     * and voice may be used there. A field id is no proof of the same field (many apps never set
     * one), so a password field of the same app must be refused by what it is, not by its id.
     */
    private fun onVoiceField(): InputConnection? {
        val ic = currentInputConnection ?: return null
        if (!voiceAllowedHere()) return null
        return if (voice.field?.matches(currentVoiceField()) == true) ic else null
    }

    /** Whether the focused field may be dictated into at all (never a password or a field that opted out). */
    private fun voiceAllowedHere(): Boolean {
        val info = currentInputEditorInfo ?: return false
        return VoiceAvailability.allowed(info.inputType, info.privateImeOptions)
    }

    /** The editor's own "select all"; a few apps ignore it, so fall back to selecting by range. */
    private fun selectEverything(ic: InputConnection, length: () -> Int) {
        if (!ic.performContextMenuAction(android.R.id.selectAll)) ic.setSelection(0, length())
    }

    override fun onVoiceRequested() {
        val view = aiKeyboardView ?: return
        if (isEmojiMode) hideEmojiKeyboard()
        view.setVoiceController(voice)
        view.showVoicePanel()
        voicePanel.opened(currentVoiceField())
        openVoiceSession(currentVoiceField())
    }

    /**
     * Starts a fresh session on [field], unless one is already running there. The panel opens
     * ready (or asking for the microphone), never recording: the user holds the mic to talk.
     */
    private fun openVoiceSession(field: VoiceField?) {
        if (voice.isBusy && field?.matches(voice.field) == true) return
        voice.open(field)
    }

    override fun onVoiceClosed() {
        voicePanel.closed()
        if (voiceStarted) voice.cancel()
    }

    override fun onVoiceAllowMicrophone() {
        currentVoiceField()?.let { voiceResume.arm(it, SystemClock.elapsedRealtime()) }
        startActivity(
            Intent(this, VoicePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun onVoiceOpenSettings() {
        voiceResume.disarm()
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** The panel's delete button. It goes through the controller so the result follows the edit. */
    override fun onVoiceBackspace() {
        if (voiceStarted) voice.backspace()
    }

    /** Back on the field where "Allow microphone" was tapped: carry on, as if nothing happened. */
    private fun resumeVoiceAfterPermission(field: VoiceField?) {
        if (!voiceResume.check(field, SystemClock.elapsedRealtime())) return
        if (!voiceAllowedHere()) return // a field that must not be dictated into gets no panel
        val view = aiKeyboardView ?: return

        view.setVoiceController(voice)
        view.showVoicePanel(animated = false)
        voicePanel.opened(field)
        voice.open(field)
        // Allowed: the panel is ready and the user holds the mic. Refused: it says so.
        if (!hasMicPermission()) {
            voice.onPermissionResult(granted = false, blocked = VoicePermissionResult.blocked)
        }
    }

    /**
     * The microphone request was answered. A refusal updates the panel at once: with the permission
     * already refused for good there is no dialog, so the keyboard never restarts and would not
     * notice. A grant starts listening only once the keyboard is back on the field: right away if
     * it never left (no dialog was needed), otherwise on the restart that follows the dialog.
     */
    private fun onVoicePermissionAnswer(granted: Boolean, blocked: Boolean) {
        if (granted) {
            if (isInputViewShown) resumeVoiceAfterPermission(currentVoiceField())
        } else {
            // Keyboard still up: no dialog was shown (already refused for good), so there will be
            // no restart to resume on. Keyboard hidden: a dialog was shown, and the restart that
            // follows still needs the armed resume to bring the panel back with its answer.
            if (isInputViewShown) voiceResume.disarm()
            if (voiceStarted) voice.onPermissionResult(granted = false, blocked = blocked)
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun currentVoiceField(): VoiceField? =
        VoiceField.of(currentInputEditorInfo?.packageName, currentInputEditorInfo?.fieldId ?: 0)

    /** Dictated text is typing too: it keeps the streak alive and feeds the word stats. */
    private fun creditStreak(text: String) {
        val chars = VoiceInsert.tibetanChars(text)
        if (chars > 0) {
            typingStats.recordTibetanChars(chars)?.let { AppAnalytics.logStreakMilestone(it) }
        }
        recordChunkAsWords(text)
    }

    /**
     * The delete key's work, shared with the voice panel's delete button. Returns the selected
     * text it removed, or null when it deleted one character (the panel offers Undo for the former).
     */
    private fun deleteBackward(inputConnection: InputConnection): String? {
        val selected = editorReads.read { inputConnection.getSelectedText(0) }
        var removed: String? = null
        when (DeleteRule.plan(selected)) {
            DeleteRule.Plan.SELECTION -> {
                removed = selected.toString()
                inputConnection.commitText("", 1) // replaces the selection with nothing
                composer.reset()
            }
            DeleteRule.Plan.ONE_CHAR -> {
                composer.onDelete(textBeforeCursor(inputConnection))
                inputConnection.deleteSurroundingText(1, 0)
            }
        }
        aiKeyboardView?.updateSuggestions(composer.composing)
        return removed
    }

    /**
     * Segments a completed chunk into real dictionary words (see [WordSegmenter]) and folds
     * each into Journey stats, rather than counting the whole space/shad-delimited chunk as a
     * single "word".
     */
    private fun recordChunkAsWords(chunk: String) {
        WordSegmenter.segment(chunk, AIKeyboardView.dictionaryOrNull()).forEach { word ->
            typingStats.recordWordTyped(word)?.let { milestone ->
                AppAnalytics.logStreakMilestone(milestone)
            }
        }
    }

    /**
     * The text before the cursor, as much as the composer needs to check its state (plus
     * [extra]); null when the editor cannot say, which the composer treats as "trust nothing".
     */
    private fun textBeforeCursor(ic: InputConnection, extra: Int = 0): CharSequence? =
        editorReads.read { ic.getTextBeforeCursor(composer.lookback + extra, 0) }

    /** Applies one composer decision as a single editor change. */
    private fun applyEdit(ic: InputConnection, edit: WordComposer.Edit) {
        if (edit.deleteBefore == 0 && edit.text.isEmpty()) return
        ic.beginBatchEdit()
        if (edit.deleteBefore > 0) ic.deleteSurroundingText(edit.deleteBefore, 0)
        if (edit.text.isNotEmpty()) ic.commitText(edit.text, 1)
        ic.endBatchEdit()
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