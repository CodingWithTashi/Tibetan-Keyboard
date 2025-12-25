package com.kharagedition.tibetankeyboard.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.ai.AIGrammarViewModel
import com.kharagedition.tibetankeyboard.ai.GrammarCorrectionAdapter
import com.kharagedition.tibetankeyboard.ai.PremiumFeatureManager
import com.kharagedition.tibetankeyboard.auth.AuthManager
import com.kharagedition.tibetankeyboard.util.showToast
import androidx.recyclerview.widget.RecyclerView

/**
 * Activity for Advanced Tibetan Grammar Analysis
 */
class GrammarActivity : AppCompatActivity() {

    private val viewModel: AIGrammarViewModel by viewModels()
    private lateinit var authManager: AuthManager
    private lateinit var premiumFeatureManager: PremiumFeatureManager

    // UI Components
    private lateinit var toolbar: MaterialToolbar
    private lateinit var inputText: TextInputEditText
    private lateinit var inputLayout: TextInputLayout
    private lateinit var btnAnalyze: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingAnimation: LottieAnimationView
    private lateinit var emptyStateView: MaterialCardView
    private lateinit var grammarAdapter: GrammarCorrectionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_grammar)

        authManager = AuthManager(this)
        premiumFeatureManager = PremiumFeatureManager(this)

        // Check authentication
        if (!authManager.isUserAuthenticated()) {
            authManager.redirectToLogin()
            return
        }

        initializeViews()
        setupUI()
        setupObservers()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.grammar_toolbar)
        inputText = findViewById(R.id.grammar_input_text)
        inputLayout = findViewById(R.id.grammar_input_layout)
        btnAnalyze = findViewById(R.id.btn_analyze_grammar)
        recyclerView = findViewById(R.id.grammar_corrections_list)
        loadingAnimation = findViewById(R.id.grammar_loading)
        emptyStateView = findViewById(R.id.grammar_empty_state)
    }

    private fun setupUI() {
        // Setup toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Grammar Analyzer"

        toolbar.setNavigationOnClickListener { onBackPressed() }

        // Setup RecyclerView
        grammarAdapter = GrammarCorrectionAdapter { correction ->
            // Handle correction click
            showCorrectionDetails(correction)
        }
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@GrammarActivity)
            adapter = grammarAdapter
            visibility = View.GONE
        }

        // Setup button click
        btnAnalyze.setOnClickListener {
            analyzeGrammar()
        }

        // Setup window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.grammar_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            loadingAnimation.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnAnalyze.isEnabled = !isLoading
            inputText.isEnabled = !isLoading
        }

        viewModel.grammarResult.observe(this) { result ->
            if (result != null) {
                displayResults(result)
            }
        }

        viewModel.error.observe(this) { error ->
            if (error != null) {
                showToast(error)
            }
        }
    }

    private fun analyzeGrammar() {
        val text = inputText.text.toString().trim()

        if (text.isEmpty()) {
            inputLayout.error = "Please enter text to analyze"
            return
        }

        // Check premium feature limit
        if (premiumFeatureManager.hasExceededLimit(
                PremiumFeatureManager.PremiumFeature.GRAMMAR_CHECK
            )
        ) {
            premiumFeatureManager.showPremiumDialog(
                this,
                PremiumFeatureManager.PremiumFeature.GRAMMAR_CHECK
            )
            return
        }

        inputLayout.error = null
        premiumFeatureManager.incrementFeatureUsage(
            PremiumFeatureManager.PremiumFeature.GRAMMAR_CHECK
        )

        viewModel.analyzeGrammar(
            text,
            authManager.getUser()?.uid ?: ""
        )
    }

    private fun displayResults(result: Any) {
        // Parse result and display corrections
        emptyStateView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE

        // Animate RecyclerView entry
        recyclerView.alpha = 0f
        recyclerView.animate()
            .alpha(1f)
            .setDuration(300)
            .start()

        // Update adapter with corrections
        // This would require proper data mapping
        showToast("Analysis complete")
    }

    private fun showCorrectionDetails(correction: Any) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Correction Details")
            .setMessage("Grammar correction explanation")
            .setPositiveButton("Accept") { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton("Dismiss") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (!authManager.isUserAuthenticated()) {
            authManager.redirectToLogin()
        }
    }
}
