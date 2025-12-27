package com.kharagedition.tibetankeyboard.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.ai.AIRepository
import com.kharagedition.tibetankeyboard.ai.PremiumFeatureManager
import com.kharagedition.tibetankeyboard.auth.AuthManager
import com.kharagedition.tibetankeyboard.util.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Activity for Tibetan-English Phonetic Transliteration
 */
class TransliterationActivity : AppCompatActivity() {

    private val repository = AIRepository(application)
    private lateinit var authManager: AuthManager
    private lateinit var premiumFeatureManager: PremiumFeatureManager

    // UI Components
    private lateinit var toolbar: MaterialToolbar
    private lateinit var sourceInput: TextInputEditText
    private lateinit var targetOutput: TextInputEditText
    private lateinit var sourceSystemSpinner: Spinner
    private lateinit var targetSystemSpinner: Spinner
    private lateinit var btnConvert: MaterialButton
    private lateinit var loadingAnimation: LottieAnimationView
    private lateinit var resultStatus: TextView

    private val transliterationScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_transliteration)

        authManager = AuthManager(this)
        premiumFeatureManager = PremiumFeatureManager(this)

        if (!authManager.isUserAuthenticated()) {
            authManager.redirectToLogin()
            return
        }

        initializeViews()
        setupUI()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.transliteration_toolbar)
        sourceInput = findViewById(R.id.source_input)
        targetOutput = findViewById(R.id.target_output)
        sourceSystemSpinner = findViewById(R.id.source_system_spinner)
        targetSystemSpinner = findViewById(R.id.target_system_spinner)
        btnConvert = findViewById(R.id.btn_convert)
        loadingAnimation = findViewById(R.id.transliteration_loading)
        resultStatus = findViewById(R.id.result_status)
    }

    private fun setupUI() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Transliteration"

        toolbar.setNavigationOnClickListener { onBackPressed() }

        // Setup spinners
        val systems = arrayOf("Wylie", "Tibetan", "Phonetic", "DTS")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, systems)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        sourceSystemSpinner.adapter = adapter
        targetSystemSpinner.adapter = adapter

        // Set defaults
        sourceSystemSpinner.setSelection(0) // Wylie
        targetSystemSpinner.setSelection(1) // Tibetan

        // Real-time conversion
        sourceInput.addTextChangedListener { text ->
            if (text?.isNotBlank() == true) {
                performTransliteration()
            }
        }

        // Convert button
        btnConvert.setOnClickListener {
            if (premiumFeatureManager.hasExceededLimit(
                    PremiumFeatureManager.PremiumFeature.TRANSLITERATION
                )
            ) {
                premiumFeatureManager.showPremiumDialog(
                    this,
                    PremiumFeatureManager.PremiumFeature.TRANSLITERATION
                )
                return@setOnClickListener
            }
            performTransliteration()
            premiumFeatureManager.incrementFeatureUsage(
                PremiumFeatureManager.PremiumFeature.TRANSLITERATION
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.transliteration_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun performTransliteration() {
        val text = sourceInput.text.toString().trim()
        if (text.isEmpty()) return

        loadingAnimation.visibility = View.VISIBLE
        btnConvert.isEnabled = false

        transliterationScope.launch {
            try {
                val result = repository.transliterate(
                    text,
                    getSourceSystem(),
                    getTargetSystem(),
                    authManager.getUser()?.uid ?: ""
                )

                targetOutput.setText(result.result)
                resultStatus.text = "Confidence: ${(result.confidence * 100).toInt()}%"
                resultStatus.visibility = View.VISIBLE

                // Animate entry
                targetOutput.alpha = 0f
                targetOutput.animate().alpha(1f).setDuration(300).start()

            } catch (e: Exception) {
                showToast("Transliteration failed: ${e.message}")
            } finally {
                loadingAnimation.visibility = View.GONE
                btnConvert.isEnabled = true
            }
        }
    }

    private fun getSourceSystem(): String {
        return when (sourceSystemSpinner.selectedItemPosition) {
            0 -> "wylie"
            1 -> "tibetan"
            2 -> "phonetic"
            3 -> "dts"
            else -> "wylie"
        }
    }

    private fun getTargetSystem(): String {
        return when (targetSystemSpinner.selectedItemPosition) {
            0 -> "wylie"
            1 -> "tibetan"
            2 -> "phonetic"
            3 -> "dts"
            else -> "tibetan"
        }
    }

    override fun onResume() {
        super.onResume()
        if (!authManager.isUserAuthenticated()) {
            authManager.redirectToLogin()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        transliterationScope.cancel()
    }
}
