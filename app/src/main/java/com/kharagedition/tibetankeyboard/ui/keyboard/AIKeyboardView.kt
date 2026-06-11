package com.kharagedition.tibetankeyboard.ui.keyboard

import com.kharagedition.tibetankeyboard.R

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.kharagedition.tibetankeyboard.ui.keyboard.AIKeyboardInterface
import com.kharagedition.tibetankeyboard.data.repository.AIService
import com.kharagedition.tibetankeyboard.data.model.GrammarResult
import com.kharagedition.tibetankeyboard.data.model.RephraseResult
import com.kharagedition.tibetankeyboard.data.model.TranslationResult
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager
import kotlinx.coroutines.*
import com.kharagedition.botok.autocomplete.SuggestionEngine

class AIKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var aiToolbar: LinearLayout
    private lateinit var proPill: View
    private lateinit var proChatBtn: ImageView
    private lateinit var proAutoBtn: ImageView
    private lateinit var proTranslateBtn: ImageView
    private lateinit var normalKeyboardContainer: FrameLayout
    private lateinit var aiInterfaceContainer: LinearLayout
    private lateinit var aiBackBtn: ImageView
    private lateinit var aiTitleText: TextView
    private lateinit var originalTextView: TextView
    private lateinit var suggestedTextView: TextView
    private lateinit var aiSummaryText: TextView
    private lateinit var aiReplaceBtn: Button
    private lateinit var aiCancelBtn: Button
    private lateinit var aiInrBtn: Button
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var translateSwapBtn: ImageView
    private lateinit var sourceLanguageText: TextView
    private lateinit var targetLanguageText: TextView

    private var aiKeyboardInterface: AIKeyboardInterface? = null
    private var themeColor: String = "#FF704C04"
    private lateinit var suggestionStrip: SuggestionStripView
    private var suggestionEngine: SuggestionEngine? = null
    private var currentOriginalText = ""
    private var currentSuggestedText = ""
    private var currentSourceLang = "en" // English
    private var currentTargetLang = "bo" // Tibetan
    private val aiService = AIService()
    private var isPremiumUser = false

    // Kept as a field so we can removeObserver() on detach — the IME rebuilds this view on every
    // onStartInputView, and the LiveData lives on a process-wide singleton, so an un-removed
    // observeForever would leak every previous view (and run applyPremiumState on detached views).
    private val premiumObserver = Observer<Boolean> { isPremium ->
        isPremiumUser = isPremium
        applyPremiumState()
    }

    init {
        orientation = VERTICAL
        setupView()
        RevenueCatManager.getInstance().isPremiumUser.observeForever(premiumObserver)
    }

    override fun onDetachedFromWindow() {
        RevenueCatManager.getInstance().isPremiumUser.removeObserver(premiumObserver)
        super.onDetachedFromWindow()
    }

    private fun setupView() {
        LayoutInflater.from(context).inflate(R.layout.ai_keyboard_layout, this, true)

        initializeViews()
        setupClickListeners()
        updateLanguageLabels()
        loadSuggestionEngine()
        applyBottomInsetPadding()
        applyPremiumState()
    }

    /**
     * Show/hide and dim the PRO strip controls based on entitlement.
     *  - Free users: gold "PRO" pill + all three feature icons visible but dimmed (locked);
     *    tapping any of them routes to the unlock flow.
     *  - PRO users: the upsell pill and the Autocomplete icon are hidden (autocomplete just
     *    works while typing); Chat + Translate are full-opacity and functional.
     */
    private fun applyPremiumState() {
        val pro = isPremiumUser
        proPill.visibility = if (pro) View.GONE else View.VISIBLE
        proAutoBtn.visibility = if (pro) View.GONE else View.VISIBLE
        val activeAlpha = 1f
        val lockedAlpha = 0.5f
        proChatBtn.alpha = if (pro) activeAlpha else lockedAlpha
        proTranslateBtn.alpha = if (pro) activeAlpha else lockedAlpha
        proAutoBtn.alpha = lockedAlpha
    }

    /**
     * Lift the whole keyboard above the system's IME navigation bar (the hide-keyboard
     * arrow on the left and the switch-input globe on the right). Without this the root
     * view draws to the screen's bottom edge and those system buttons overlap our bottom
     * key row.
     *
     * We prefer the REAL navigation-bar inset — on gesture-nav devices that's just the slim
     * pill height, so we don't over-pad the way the platform navigation_bar_height resource
     * (≈48dp, the old 3-button height) would. The IME rebuilds this view on every open
     * (onStartInputView) and the inset only reports non-zero on the first settle, so we cache
     * the last good value statically and reuse it on later opens — that avoids both the
     * reopen-collapse and the over-padding from the resource fallback.
     */
    private fun applyBottomInsetPadding() {
        val base = if (cachedNavInset >= 0) cachedNavInset else systemNavBarHeight()
        updatePadding(bottom = base + EXTRA_BOTTOM_GAP_PX)
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            if (navBars.bottom > 0) cachedNavInset = navBars.bottom
            val resolved = if (cachedNavInset >= 0) cachedNavInset else navBars.bottom
            v.updatePadding(bottom = resolved + EXTRA_BOTTOM_GAP_PX)
            insets
        }
        ViewCompat.requestApplyInsets(this)
    }

    /** Fallback only, used before any real inset arrives; over-reports on gesture nav. */
    private fun systemNavBarHeight(): Int {
        val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId)
        else (16 * resources.displayMetrics.density).toInt()
    }

    /** The breathing room above the system IME bar — tune this single value to taste. */
    private val EXTRA_BOTTOM_GAP_PX: Int
        get() = (8 * resources.displayMetrics.density).toInt()

    private fun initializeViews() {
        aiToolbar = findViewById(R.id.ai_toolbar)
        proPill = findViewById(R.id.pro_pill)
        proChatBtn = findViewById(R.id.pro_chat_btn)
        proAutoBtn = findViewById(R.id.pro_auto_btn)
        proTranslateBtn = findViewById(R.id.pro_translate_btn)
        normalKeyboardContainer = findViewById(R.id.normal_keyboard_container)
        aiInterfaceContainer = findViewById(R.id.ai_interface_container)
        aiBackBtn = findViewById(R.id.ai_back_btn)
        aiTitleText = findViewById(R.id.ai_title_text)
        aiInrBtn = findViewById(R.id.ai_inner_btn)
        originalTextView = findViewById(R.id.original_text_view)
        suggestedTextView = findViewById(R.id.suggested_text_view)
        aiSummaryText = findViewById(R.id.ai_summary_text)
        aiReplaceBtn = findViewById(R.id.ai_replace_btn)
        aiCancelBtn = findViewById(R.id.ai_cancel_btn)
        loadingProgressBar = findViewById(R.id.loading_progress_bar)
        translateSwapBtn = findViewById(R.id.translate_swap_btn)
        sourceLanguageText = findViewById(R.id.source_language_text)
        targetLanguageText = findViewById(R.id.target_language_text)
        suggestionStrip = findViewById(R.id.suggestion_strip)
        suggestionStrip.onSuggestionClick = { word ->
            aiKeyboardInterface?.onSuggestionSelected(word)
        }
    }

    private fun setupClickListeners() {
        // Gold upsell pill — free users only (hidden for PRO).
        proPill.setOnClickListener { aiKeyboardInterface?.onUnlockPro() }

        // AI Chat — PRO opens the chat screen; free routes to unlock.
        proChatBtn.setOnClickListener {
            if (isPremiumUser) aiKeyboardInterface?.onOpenChat() else aiKeyboardInterface?.onUnlockPro()
        }

        // Autocomplete — shown to free users only as an upsell.
        proAutoBtn.setOnClickListener { aiKeyboardInterface?.onUnlockPro() }

        // Translate — PRO opens the translate panel; free routes to unlock.
        proTranslateBtn.setOnClickListener {
            if (!isPremiumUser) {
                aiKeyboardInterface?.onUnlockPro()
                return@setOnClickListener
            }
            val currentText = getCurrentInputText()
            if (currentText.isEmpty()) {
                Toast.makeText(context, "Input text is empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            handleTranslateClick(currentText)
            showAIInterface()
        }

        aiInrBtn.setOnClickListener() {
            val currentText = getCurrentInputText()
            if(currentText.isEmpty()) {
                Toast.makeText(context, "Input text is empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            when (aiInrBtn.text.toString()) {
                context.resources.getString(R.string.fix) -> handleGrammarClick(currentText)
                context.resources.getString(R.string.translate) ->  handleTranslateClick(currentText)
                context.resources.getString(R.string.rephrase) -> handleRephraseClick(currentText)
            }
        }

        aiBackBtn.setOnClickListener {
            showNormalKeyboard()
            aiKeyboardInterface?.onAICancel()
        }

        aiReplaceBtn.setOnClickListener {
            if (currentSuggestedText.isNotEmpty()) {
                when (aiReplaceBtn.text.toString()) {
                    context.resources.getString(R.string.apply_corrections) -> aiKeyboardInterface?.onGrammarReplace(currentOriginalText, currentSuggestedText)
                    context.resources.getString(R.string.use_translation) -> aiKeyboardInterface?.onTranslateReplace(currentOriginalText, currentSuggestedText)
                    context.resources.getString(R.string.use_rephrase) -> aiKeyboardInterface?.onRephraseReplace(currentOriginalText, currentSuggestedText)
                }
            }
        }

        aiCancelBtn.setOnClickListener {
            showNormalKeyboard()
            aiKeyboardInterface?.onAICancel()
        }

        translateSwapBtn.setOnClickListener {
            swapTranslationLanguages()
        }
    }

    private fun handleRephraseClick(currentText: String) {
        showRephraseInterface(currentText)
    }

    private fun handleTranslateClick(currentText: String) {
        showTranslateInterface(currentText)
    }

    private fun handleGrammarClick(currentText: String) {
        showGrammarInterface(currentText)
    }

    private fun getCurrentInputText(): String {
        val text = aiKeyboardInterface?.getCurrentText() ?: ""
        return text.trim()
    }

    fun setAIKeyboardInterface(aiKeyboardInterface: AIKeyboardInterface) {
        this.aiKeyboardInterface = aiKeyboardInterface
    }

    fun setThemeColor(color: String) {
        this.themeColor = color
        applyTheme()
    }

    private fun applyTheme() {
        // Darken the chosen theme colour into a deep espresso surface so the keyboard
        // matches the premium dark-warm design and the brown key caps pop above it.
        val surface = darken(Color.parseColor(themeColor), 0.42f)
        // Tint the root too so the bottom inset padding (the gap above the system IME bar)
        // matches the keyboard surface rather than showing the lighter root brown.
        setBackgroundColor(surface)
        aiToolbar.setBackgroundColor(surface)
        suggestionStrip.setThemeColor(surface)
        val textColor = if (isColorDark(surface)) Color.WHITE else Color.BLACK
        aiReplaceBtn.setTextColor(textColor)
    }

    private fun darken(color: Int, factor: Float): Int {
        val red = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val green = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val blue = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(red, green, blue)
    }

    private fun isColorDark(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.5
    }

    fun showGrammarInterface(text: String) {
        Log.d("AIKeyboardView", "showGrammarInterface called with text: $text")
        currentOriginalText = text
        aiTitleText.text = "Grammar Check"
        originalTextView.text = text
        hideTranslationControls()
        showLoadingState()


        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = aiService.checkGrammar(text)
                showGrammarResult(result)
            } catch (e: Exception) {
                Log.e("AIKeyboardView", "Grammar check failed", e)
                showError("Failed to check grammar")
            }
        }
    }

    fun showTranslateInterface(text: String) {
        Log.d("AIKeyboardView", "showTranslateInterface called with text: $text")
        currentOriginalText = text
        aiTitleText.text = "Translate Text"
        originalTextView.text = text
        showTranslationControls()
        showLoadingState()


        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = aiService.translateText(text, currentSourceLang, currentTargetLang)
                showTranslationResult(result)
            } catch (e: Exception) {
                Log.e("AIKeyboardView", "Translation failed", e)
                showError("Failed to translate text")
            }
        }
    }

    fun showRephraseInterface(text: String) {
        Log.d("AIKeyboardView", "showRephraseInterface called with text: $text")

        currentOriginalText = text

        aiTitleText.text = "Rephrase Text"

        originalTextView.text = text

        hideTranslationControls()

        showLoadingState()

        CoroutineScope(Dispatchers.Main).launch {
            try {

                val result = aiService.rephraseText(text)

                showRephraseResult(result)

            } catch (e: Exception) {
                Log.e("AIKeyboardView", "Rephrase failed", e)
                showError("Failed to rephrase text")
            }
        }
    }

    private fun showTranslationControls() {
        translateSwapBtn.visibility = View.VISIBLE
        sourceLanguageText.visibility = View.VISIBLE
        targetLanguageText.visibility = View.VISIBLE
        updateLanguageLabels()
    }

    private fun hideTranslationControls() {
        translateSwapBtn.visibility = View.GONE
        sourceLanguageText.visibility = View.GONE
        targetLanguageText.visibility = View.GONE
    }

    private fun swapTranslationLanguages() {
        val tempLang = currentSourceLang

        currentSourceLang = currentTargetLang

        currentTargetLang = tempLang

        // Animate the swap
        val rotateAnimator = ObjectAnimator.ofFloat(translateSwapBtn, "rotation", 0f, 180f)

        rotateAnimator.duration = 30

        rotateAnimator.start()

        updateLanguageLabels()

        // Re-translate with swapped languages if we have text
        if (currentOriginalText.isNotEmpty()) {
            showLoadingState()
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val result = aiService.translateText(currentOriginalText, currentSourceLang, currentTargetLang)
                    showTranslationResult(result)
                } catch (e: Exception) {
                    Log.e("AIKeyboardView", "Translation swap failed", e)
                    showError("Failed to translate text")
                }
            }
        }
    }

    private fun updateLanguageLabels() {
        sourceLanguageText.text = getLanguageDisplayName(currentSourceLang)
        targetLanguageText.text = getLanguageDisplayName(currentTargetLang)
    }

    private fun getLanguageDisplayName(langCode: String): String {
        return when (langCode) {
            "bo" -> "བོད་སྐད།" // Tibetan
            "en" -> "English"
            else -> langCode
        }
    }

    private fun showLoadingState() {
        aiInrBtn.visibility = View.GONE
        loadingProgressBar.visibility = View.VISIBLE
        suggestedTextView.text = "Analyzing text..."
        aiSummaryText.text = "Please wait while we process your request."
        aiReplaceBtn.isEnabled = false
        aiReplaceBtn.alpha = 0.5f
    }

    private fun showGrammarResult(result: GrammarResult) {

        loadingProgressBar.visibility = View.GONE
        currentSuggestedText = result.correctedText
        suggestedTextView.text = result.correctedText
        aiSummaryText.text = "${result.corrections.size} corrections found: ${result.corrections.joinToString(", ")}"
        aiReplaceBtn.isEnabled = true
        aiReplaceBtn.alpha = 1f
        aiReplaceBtn.text = context.getString(R.string.apply_corrections)
        aiInrBtn.visibility = View.VISIBLE
        aiInrBtn.text = context.getString(R.string.fix);
    }

    private fun showTranslationResult(result: TranslationResult) {
        loadingProgressBar.visibility = View.GONE
        currentSuggestedText = result.translatedText
        suggestedTextView.text = result.translatedText
        aiSummaryText.text = "Translated from ${result.sourceLanguage} to ${result.targetLanguage}"
        aiReplaceBtn.isEnabled = true
        aiReplaceBtn.alpha = 1f
        aiReplaceBtn.text = context.getString(R.string.use_translation)
        aiInrBtn.visibility = View.VISIBLE
        aiInrBtn.text = context.getString(R.string.translate);
        if(result.error!=null){
            showError("Translation error: ${result.error}")
        }
    }

    private fun showRephraseResult(result: RephraseResult) {
        loadingProgressBar.visibility = View.GONE
        currentSuggestedText = result.rephrasedText
        suggestedTextView.text = result.rephrasedText
        aiSummaryText.text = "Style: ${result.style} - ${result.improvements.joinToString(", ")}"
        aiReplaceBtn.isEnabled = true
        aiReplaceBtn.alpha = 1f
        aiReplaceBtn.text = context.getString(R.string.use_rephrase)
        aiInrBtn.visibility = View.VISIBLE
        aiInrBtn.text = context.getString(R.string.rephrase);
    }

    private fun showError(message: String) {
        loadingProgressBar.visibility = View.GONE
        suggestedTextView.text = "Error occurred"
        aiSummaryText.text = message
        aiReplaceBtn.isEnabled = false
        aiReplaceBtn.alpha = 0.5f
        aiInrBtn.visibility = View.VISIBLE
        aiInrBtn.text = context.getString(R.string.retry);
    }

    private fun showAIInterface() {
        normalKeyboardContainer.visibility = View.GONE
        suggestionStrip.visibility = View.GONE
        aiInterfaceContainer.visibility = View.VISIBLE
        aiInterfaceContainer.translationY = aiInterfaceContainer.height.toFloat()
        aiInterfaceContainer.animate().translationY(0f).setDuration(300).start()
    }

    fun showNormalKeyboard() {
        if (aiInterfaceContainer.visibility == View.VISIBLE) {
            aiInterfaceContainer.animate()
                .translationY(aiInterfaceContainer.height.toFloat())
                .setDuration(300)
                .withEndAction {
                    aiInterfaceContainer.visibility = View.GONE
                    normalKeyboardContainer.visibility = View.VISIBLE
                    currentOriginalText = ""
                    currentSuggestedText = ""
                }
                .start()
        }
    }

    fun updateSuggestions(prefix: String) {
        if (!isPremiumUser) {
            suggestionStrip.visibility = View.GONE
            return
        }
        val engine = suggestionEngine
        if (engine == null) {
            Log.d(TAG, "updateSuggestions: engine not loaded yet, prefix='$prefix'")
            return
        }
        if (!engine.isReady) {
            Log.d(TAG, "updateSuggestions: engine not ready yet, prefix='$prefix'")
            return
        }
        val suggestions = if (prefix.isNotEmpty()) engine.getSuggestions(prefix, 4) else emptyList()
        Log.d(TAG, "updateSuggestions: prefix='$prefix' → ${suggestions.size} results: $suggestions")
        if (suggestions.isEmpty()) {
            suggestionStrip.visibility = View.GONE
        } else {
            suggestionStrip.setSuggestions(suggestions)
            suggestionStrip.visibility = View.VISIBLE
        }
    }

    private fun loadSuggestionEngine() {
        val cached = sharedEngine
        if (cached != null && cached.isReady) {
            Log.d(TAG, "loadSuggestionEngine: using cached engine")
            suggestionEngine = cached
            return
        }
        Log.d(TAG, "loadSuggestionEngine: starting background load")
        CoroutineScope(Dispatchers.IO).launch {
            val engine = SuggestionEngine()
            val tsvPaths = listOf(
                "botok/general/dictionary/words/tsikchen.tsv",
                "botok/general/dictionary/words/uncompound_lexicon.tsv",
                "botok/general/dictionary/words_non_inflected/particles.tsv"
            )
            for (path in tsvPaths) {
                try {
                    context.assets.open(path).bufferedReader(Charsets.UTF_8).useLines { lines ->
                        engine.addLines(lines)
                    }
                    Log.d(TAG, "loadSuggestionEngine: loaded $path")
                } catch (e: Exception) {
                    Log.w(TAG, "loadSuggestionEngine: skipping $path — ${e.message}")
                }
            }
            engine.ready()
            Log.d(TAG, "loadSuggestionEngine: ready, wordCount=${engine.wordCount}")
            withContext(Dispatchers.Main) {
                sharedEngine = engine
                suggestionEngine = engine
                Log.d(TAG, "loadSuggestionEngine: engine assigned on main thread")
            }
        }
    }

    companion object {
        private const val TAG = "AIKeyboardView"
        @Volatile
        private var sharedEngine: SuggestionEngine? = null
        /** Last real navigation-bar inset, remembered across IME view rebuilds. -1 = unknown. */
        @Volatile
        private var cachedNavInset = -1
    }
}