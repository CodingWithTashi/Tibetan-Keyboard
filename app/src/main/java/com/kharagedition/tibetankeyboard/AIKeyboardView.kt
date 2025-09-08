package com.kharagedition.tibetankeyboard

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
import androidx.lifecycle.LifecycleOwner
import com.kharagedition.tibetankeyboard.ai.AIKeyboardInterface
import com.kharagedition.tibetankeyboard.ai.AIService
import com.kharagedition.tibetankeyboard.auth.AuthManager
import com.kharagedition.tibetankeyboard.model.GrammarResult
import com.kharagedition.tibetankeyboard.model.RephraseResult
import com.kharagedition.tibetankeyboard.model.TranslationResult
import com.kharagedition.tibetankeyboard.subscription.RevenueCatManager
import com.kharagedition.tibetankeyboard.subscription.SubscriptionUIComponent
import kotlinx.coroutines.*

class AIKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var aiToolbar: LinearLayout
    private lateinit var aiOptionsIcon: ImageView
    private lateinit var aiOptionsContainer: LinearLayout
    private lateinit var grammarBtn: Button
    private lateinit var translateBtn: Button
    private lateinit var rephraseBtn: Button
    private lateinit var normalKeyboardContainer: FrameLayout
    private lateinit var aiInterfaceContainer: LinearLayout
    private lateinit var authManager: AuthManager
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
    private var isOptionsExpanded = false
    private var currentOriginalText = ""
    private var currentSuggestedText = ""
    private var currentSourceLang = "en" // English
    private var currentTargetLang = "bo" // Tibetan
    private val aiService = AIService()
    private var isPremiumUser = false;
    init {
        orientation = VERTICAL
        setupView()
        RevenueCatManager.getInstance().isPremiumUser.observeForever{ isPremium ->
            isPremiumUser = isPremium;
        }
    }

    private fun setupView() {
        LayoutInflater.from(context).inflate(R.layout.ai_keyboard_layout, this, true)
        authManager = AuthManager(context)

        initializeViews()
        setupClickListeners()
        updateLanguageLabels()
    }

    private fun initializeViews() {
        aiToolbar = findViewById(R.id.ai_toolbar)
        aiOptionsIcon = findViewById(R.id.ai_options_icon)
        aiOptionsContainer = findViewById(R.id.ai_options_container)
        grammarBtn = findViewById(R.id.grammar_btn)
        translateBtn = findViewById(R.id.translate_btn)
        rephraseBtn = findViewById(R.id.rephrase_btn)
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
    }

    private fun setupClickListeners() {
        aiOptionsIcon.setOnClickListener {

            // Check authentication first
            if (!authManager.isUserAuthenticated() || !isPremiumUser) {
                authManager.redirectToLogin()
            }else{
                toggleAIOptions()
            }

        }

        grammarBtn.setOnClickListener {
            Toast.makeText(context, "Coming in next release....", Toast.LENGTH_SHORT).show()
            return@setOnClickListener;
            val currentText = getCurrentInputText()
            if (currentText.isEmpty()) {
                Toast.makeText(context, "Input text is empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            handleGrammarClick(currentText)
            showAIInterface()
        }

        translateBtn.setOnClickListener {
            val currentText = getCurrentInputText()
            if(currentText.isEmpty()) {
                Toast.makeText(context, "Input text is empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
           handleTranslateClick(currentText)
            showAIInterface()
        }

        rephraseBtn.setOnClickListener {
            val currentText = getCurrentInputText()
            if(currentText.isEmpty()) {
                Toast.makeText(context, "Input text is empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
          handleRephraseClick(currentText)
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
        hideAIOptions()
    }

    private fun handleTranslateClick(currentText: String) {

        showTranslateInterface(currentText)
        hideAIOptions()
    }

    private fun handleGrammarClick(currentText: String) {

        showGrammarInterface(currentText)
        hideAIOptions()
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
        val colorInt = Color.parseColor(themeColor)
        val lighterColor = adjustColorBrightness(colorInt, 0.2f)
        aiToolbar.setBackgroundColor(colorInt)
        grammarBtn.setBackgroundColor(lighterColor)
        translateBtn.setBackgroundColor(lighterColor)
        rephraseBtn.setBackgroundColor(lighterColor)
        //aiReplaceBtn.setBackgroundColor(lighterColor)
        val textColor = if (isColorDark(colorInt)) Color.WHITE else Color.BLACK
        grammarBtn.setTextColor(textColor)
        translateBtn.setTextColor(textColor)
        rephraseBtn.setTextColor(textColor)
        aiReplaceBtn.setTextColor(textColor)
    }

    private fun adjustColorBrightness(color: Int, factor: Float): Int {
        val red = ((Color.red(color) * (1 + factor)).coerceAtMost(255f)).toInt()
        val green = ((Color.green(color) * (1 + factor)).coerceAtMost(255f)).toInt()
        val blue = ((Color.blue(color) * (1 + factor)).coerceAtMost(255f)).toInt()
        return Color.rgb(red, green, blue)
    }

    private fun isColorDark(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.5
    }

    private fun toggleAIOptions() {
        if (isOptionsExpanded) hideAIOptions() else showAIOptions()
    }

    private fun showAIOptions() {
        isOptionsExpanded = true
        val rotateAnimator = ObjectAnimator.ofFloat(aiOptionsIcon, "rotation", 0f, 180f)
        rotateAnimator.duration = 30

        aiOptionsContainer.visibility = View.VISIBLE
        val expandAnimator = ValueAnimator.ofInt(0, 60)
        expandAnimator.duration = 30
        expandAnimator.addUpdateListener { animator ->
            val value = animator.animatedValue as Int
            val layoutParams = aiOptionsContainer.layoutParams
            layoutParams.height = (value * context.resources.displayMetrics.density).toInt()
            aiOptionsContainer.layoutParams = layoutParams
        }

        grammarBtn.alpha = 0f
        translateBtn.alpha = 0f
        rephraseBtn.alpha = 0f
        val fadeInAnimator1 = ObjectAnimator.ofFloat(grammarBtn, "alpha", 0f, 1f)
        val fadeInAnimator2 = ObjectAnimator.ofFloat(translateBtn, "alpha", 0f, 1f)
        val fadeInAnimator3 = ObjectAnimator.ofFloat(rephraseBtn, "alpha", 0f, 1f)
        fadeInAnimator1.startDelay = 150
        fadeInAnimator2.startDelay = 200
        fadeInAnimator3.startDelay = 250
        fadeInAnimator1.duration = 200
        fadeInAnimator2.duration = 200
        fadeInAnimator3.duration = 200

        AnimatorSet().apply {
            playTogether(rotateAnimator, expandAnimator, fadeInAnimator1, fadeInAnimator2, fadeInAnimator3)
            start()
        }
    }

    private fun hideAIOptions() {
        isOptionsExpanded = false
        val rotateAnimator = ObjectAnimator.ofFloat(aiOptionsIcon, "rotation", 180f, 0f)
        rotateAnimator.duration = 30

        val collapseAnimator = ValueAnimator.ofInt(60, 0)
        collapseAnimator.duration = 30
        collapseAnimator.addUpdateListener { animator ->
            val value = animator.animatedValue as Int
            val layoutParams = aiOptionsContainer.layoutParams
            layoutParams.height = (value * context.resources.displayMetrics.density).toInt()
            aiOptionsContainer.layoutParams = layoutParams
        }

        val fadeOutAnimator1 = ObjectAnimator.ofFloat(grammarBtn, "alpha", 1f, 0f)
        val fadeOutAnimator2 = ObjectAnimator.ofFloat(translateBtn, "alpha", 1f, 0f)
        val fadeOutAnimator3 = ObjectAnimator.ofFloat(rephraseBtn, "alpha", 1f, 0f)
        fadeOutAnimator1.duration = 150
        fadeOutAnimator2.duration = 150
        fadeOutAnimator3.duration = 150

        AnimatorSet().apply {
            playTogether(rotateAnimator, collapseAnimator, fadeOutAnimator1, fadeOutAnimator2, fadeOutAnimator3)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    aiOptionsContainer.visibility = View.GONE
                }
            })
            start()
        }
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
                    if (isOptionsExpanded) hideAIOptions()
                }
                .start()
        }
    }
}