package com.kharagedition.tibetankeyboard.ui.chat

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.kharagedition.tibetankeyboard.R

/**
 * Custom panel for configuring and displaying tutoring mode
 */
class TutoringModePanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    private lateinit var switchTutoring: SwitchMaterial
    private lateinit var levelGroup: RadioGroup
    private lateinit var progressBar: ProgressBar
    private lateinit var currentLessonText: TextView
    private lateinit var progressText: TextView
    private var onModeChange: ((enabled: Boolean, level: String) -> Unit)? = null

    init {
        setupView(context)
    }

    private fun setupView(context: Context) {
        val view = LayoutInflater.from(context).inflate(R.layout.panel_tutoring_mode, this, true)

        switchTutoring = view.findViewById(R.id.switch_tutoring)
        levelGroup = view.findViewById(R.id.level_group)
        progressBar = view.findViewById(R.id.progress_bar)
        currentLessonText = view.findViewById(R.id.current_lesson)
        progressText = view.findViewById(R.id.progress_percentage)

        // Setup switch
        switchTutoring.setOnCheckedChangeListener { _, isChecked ->
            levelGroup.isEnabled = isChecked
            if (isChecked) {
                val level = when (levelGroup.checkedRadioButtonId) {
                    R.id.level_beginner -> "beginner"
                    R.id.level_intermediate -> "intermediate"
                    R.id.level_advanced -> "advanced"
                    else -> "intermediate"
                }
                onModeChange?.invoke(true, level)
            } else {
                onModeChange?.invoke(false, "")
            }
        }

        // Setup level selection
        levelGroup.setOnCheckedChangeListener { _, checkedId ->
            val level = when (checkedId) {
                R.id.level_beginner -> "beginner"
                R.id.level_intermediate -> "intermediate"
                R.id.level_advanced -> "advanced"
                else -> "intermediate"
            }
            if (switchTutoring.isChecked) {
                onModeChange?.invoke(true, level)
            }
        }

        // Set default
        view.findViewById<RadioButton>(R.id.level_intermediate).isChecked = true
    }

    fun setTutoringMode(enabled: Boolean, level: String = "intermediate") {
        switchTutoring.isChecked = enabled

        val radioId = when (level) {
            "beginner" -> R.id.level_beginner
            "advanced" -> R.id.level_advanced
            else -> R.id.level_intermediate
        }
        levelGroup.check(radioId)
    }

    fun updateProgress(percentage: Int, currentLesson: String, nextLesson: String) {
        progressBar.progress = percentage
        progressText.text = "$percentage% Complete"
        currentLessonText.text = "Current: $currentLesson\nNext: $nextLesson"
    }

    fun setOnModeChangeListener(listener: (enabled: Boolean, level: String) -> Unit) {
        this.onModeChange = listener
    }
}
