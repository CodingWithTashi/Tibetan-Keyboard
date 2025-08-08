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
import androidx.core.content.ContextCompat
import com.kharagedition.tibetankeyboard.ai.AIKeyboardInterface
import com.kharagedition.tibetankeyboard.ai.AIService
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
    private lateinit var rephraseBtn: Button
    private lateinit var normalKeyboardContainer: FrameLayout
    private lateinit var aiInterfaceContainer: LinearLayout

    private lateinit var aiBackBtn: ImageView
    private lateinit var aiTitleText: TextView
    private lateinit var originalTextView: TextView
    private lateinit var suggestedTextView: TextView
    private lateinit var aiSummaryText: TextView
    private lateinit var aiReplaceBtn: Button
    private lateinit var aiCancelBtn: Button
    private lateinit var loadingProgressBar: ProgressBar

    private var aiKeyboardInterface: AIKeyboardInterface? = null
    private var themeColor: String = "#FF704C04"
    private var isOptionsExpanded = false
    private var currentOriginalText = ""
    private var currentSuggestedText = ""
    private val aiService = AIService()

    init {
        orientation = VERTICAL
        setupView()
    }

    private fun setupView() {
        LayoutInflater.from(context).inflate(R.layout.ai_keyboard_layout, this, true)
        initializeViews()
        setupClickListeners()
    }

    private fun initializeViews() {
        aiToolbar = findViewById(R.id.ai_toolbar)
        aiOptionsIcon = findViewById(R.id.ai_options_icon)
        aiOptionsContainer = findViewById(R.id.ai_options_container)
        grammarBtn = findViewById(R.id.grammar_btn)
        rephraseBtn = findViewById(R.id.rephrase_btn)
        normalKeyboardContainer = findViewById(R.id.normal_keyboard_container)
        aiInterfaceContainer = findViewById(R.id.ai_interface_container)
        aiBackBtn = findViewById(R.id.ai_back_btn)
        aiTitleText = findViewById(R.id.ai_title_text)
        originalTextView = findViewById(R.id.original_text_view)
        suggestedTextView = findViewById(R.id.suggested_text_view)
        aiSummaryText = findViewById(R.id.ai_summary_text)
        aiReplaceBtn = findViewById(R.id.ai_replace_btn)
        aiCancelBtn = findViewById(R.id.ai_cancel_btn)
        loadingProgressBar = findViewById(R.id.loading_progress_bar)
    }

    private fun setupClickListeners() {
        aiOptionsIcon.setOnClickListener { toggleAIOptions() }

        grammarBtn.setOnClickListener {
            hideAIOptions()
            val currentText = getCurrentInputText()
            showGrammarInterface(currentText)
        }

        rephraseBtn.setOnClickListener {
            hideAIOptions()
            val currentText = getCurrentInputText()
            showRephraseInterface(currentText)
        }

        aiBackBtn.setOnClickListener {
            showNormalKeyboard()
            aiKeyboardInterface?.onAICancel()
        }

        aiReplaceBtn.setOnClickListener {
            if (currentSuggestedText.isNotEmpty()) {
                when (aiTitleText.text.toString()) {
                    "Grammar Check" -> aiKeyboardInterface?.onGrammarReplace(currentOriginalText, currentSuggestedText)
                    "Rephrase Text" -> aiKeyboardInterface?.onRephraseReplace(currentOriginalText, currentSuggestedText)
                }
            }
        }

        aiCancelBtn.setOnClickListener {
            showNormalKeyboard()
            aiKeyboardInterface?.onAICancel()
        }
    }

    private fun getCurrentInputText(): String {
        val text = aiKeyboardInterface?.getCurrentText() ?: ""
        return if (text.isBlank()) {
            "This is sample text for testing the AI features."
        } else {
            text
        }
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
        rephraseBtn.setBackgroundColor(lighterColor)
        aiReplaceBtn.setBackgroundColor(lighterColor)
        val textColor = if (isColorDark(colorInt)) Color.WHITE else Color.BLACK
        grammarBtn.setTextColor(textColor)
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
        rotateAnimator.duration = 300

        aiOptionsContainer.visibility = View.VISIBLE
        val expandAnimator = ValueAnimator.ofInt(0, 120)
        expandAnimator.duration = 300
        expandAnimator.addUpdateListener { animator ->
            val value = animator.animatedValue as Int
            val layoutParams = aiOptionsContainer.layoutParams
            layoutParams.height = (value * context.resources.displayMetrics.density).toInt()
            aiOptionsContainer.layoutParams = layoutParams
        }

        grammarBtn.alpha = 0f
        rephraseBtn.alpha = 0f
        val fadeInAnimator1 = ObjectAnimator.ofFloat(grammarBtn, "alpha", 0f, 1f)
        val fadeInAnimator2 = ObjectAnimator.ofFloat(rephraseBtn, "alpha", 0f, 1f)
        fadeInAnimator1.startDelay = 150
        fadeInAnimator2.startDelay = 200
        fadeInAnimator1.duration = 200
        fadeInAnimator2.duration = 200

        AnimatorSet().apply {
            playTogether(rotateAnimator, expandAnimator, fadeInAnimator1, fadeInAnimator2)
            start()
        }
    }

    private fun hideAIOptions() {
        isOptionsExpanded = false
        val rotateAnimator = ObjectAnimator.ofFloat(aiOptionsIcon, "rotation", 180f, 0f)
        rotateAnimator.duration = 300

        val collapseAnimator = ValueAnimator.ofInt(120, 0)
        collapseAnimator.duration = 300
        collapseAnimator.addUpdateListener { animator ->
            val value = animator.animatedValue as Int
            val layoutParams = aiOptionsContainer.layoutParams
            layoutParams.height = (value * context.resources.displayMetrics.density).toInt()
            aiOptionsContainer.layoutParams = layoutParams
        }

        val fadeOutAnimator1 = ObjectAnimator.ofFloat(grammarBtn, "alpha", 1f, 0f)
        val fadeOutAnimator2 = ObjectAnimator.ofFloat(rephraseBtn, "alpha", 1f, 0f)
        fadeOutAnimator1.duration = 150
        fadeOutAnimator2.duration = 150

        AnimatorSet().apply {
            playTogether(rotateAnimator, collapseAnimator, fadeOutAnimator1, fadeOutAnimator2)
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
        showLoadingState()
        showAIInterface()
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

    fun showRephraseInterface(text: String) {
        Log.d("AIKeyboardView", "showRephraseInterface called with text: $text")
        currentOriginalText = text
        aiTitleText.text = "Rephrase Text"
        originalTextView.text = text
        showLoadingState()
        showAIInterface()
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

    private fun showLoadingState() {
        loadingProgressBar.visibility = View.VISIBLE
        suggestedTextView.text = "Analyzing text..."
        aiSummaryText.text = "Please wait while we process your request."
        aiReplaceBtn.isEnabled = false
        aiReplaceBtn.alpha = 0.5f
    }

    private fun showGrammarResult(result: AIService.GrammarResult) {
        loadingProgressBar.visibility = View.GONE
        currentSuggestedText = result.correctedText
        suggestedTextView.text = result.correctedText
        aiSummaryText.text = "${result.corrections.size} corrections found: ${result.corrections.joinToString(", ")}"
        aiReplaceBtn.isEnabled = true
        aiReplaceBtn.alpha = 1f
    }

    private fun showRephraseResult(result: AIService.RephraseResult) {
        loadingProgressBar.visibility = View.GONE
        currentSuggestedText = result.rephrasedText
        suggestedTextView.text = result.rephrasedText
        aiSummaryText.text = "Style: ${result.style} - ${result.improvements.joinToString(", ")}"
        aiReplaceBtn.isEnabled = true
        aiReplaceBtn.alpha = 1f
    }

    private fun showError(message: String) {
        loadingProgressBar.visibility = View.GONE
        suggestedTextView.text = "Error occurred"
        aiSummaryText.text = message
        aiReplaceBtn.isEnabled = false
        aiReplaceBtn.alpha = 0.5f
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
