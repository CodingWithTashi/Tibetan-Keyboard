package com.kharagedition.tibetankeyboard

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.UserPreferences
import com.kharagedition.tibetankeyboard.repo.UserRepository
import com.kharagedition.tibetankeyboard.ui.ChatActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var buttonGoogleSignIn: MaterialButton
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var userPreferences: UserPreferences
    private lateinit var textViewPrivacyPolicy: TextView
    private lateinit var userRepository: UserRepository

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            hideLoading()
            Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize Firebase Auth
        auth = Firebase.auth
        userRepository = UserRepository()
        userPreferences = UserPreferences(this)

        // Check if user is already signed in
        if (auth.currentUser != null && userPreferences.isUserLoggedIn()) {
            navigateToChatActivity()
            return
        }

        //setupEdgeToEdge()
        initializeViews()
        setupPrivacyTextView()
        configureGoogleSignIn()
        setupClickListeners()
    }

    private fun setupPrivacyTextView() {
        val fullText = "By signing in, you agree to our Terms of Service and Privacy Policy"
        val spannableString = SpannableString(fullText)

        // Find the positions of the links
        val termsStart = fullText.indexOf("Terms of Service")
        val termsEnd = termsStart + "Terms of Service".length
        val privacyStart = fullText.indexOf("Privacy Policy")
        val privacyEnd = privacyStart + "Privacy Policy".length
        val termsClickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                openTermsOfService()
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = ContextCompat.getColor(this@LoginActivity, R.color.brown_700)
                ds.isUnderlineText = true
            }
        }

        val privacyClickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                openPrivacyPolicy()
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = ContextCompat.getColor(this@LoginActivity, R.color.brown_700)
                ds.isUnderlineText = true
            }
        }

        // Apply the spans
        spannableString.setSpan(termsClickableSpan, termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannableString.setSpan(privacyClickableSpan, privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Set the text and make it clickable
        textViewPrivacyPolicy.text = spannableString
        textViewPrivacyPolicy.movementMethod = LinkMovementMethod.getInstance()

        // Optional: Remove the default link color highlighting
        textViewPrivacyPolicy.highlightColor = Color.TRANSPARENT


    }
    private fun openTermsOfService() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://kharagedition.github.io/term-and-condition/tibetan-keyboard.html"))
        startActivity(intent)
    }

    private fun openPrivacyPolicy() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://kharagedition.github.io/privacy-policy/tibetan-keyboard"))
        startActivity(intent)
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_container)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initializeViews() {
        buttonGoogleSignIn = findViewById(R.id.buttonGoogleSignIn)
        progressIndicator = findViewById(R.id.progressIndicator)
        textViewPrivacyPolicy = findViewById(R.id.textViewPrivacy)
    }

    private fun configureGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .requestProfile()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun setupClickListeners() {
        buttonGoogleSignIn.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun signInWithGoogle() {
        showLoading()
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                hideLoading()
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let { firebaseUser ->
                        handleSuccessfulLogin(firebaseUser, task.result?.additionalUserInfo?.isNewUser ?: false)
                    }
                } else {
                    handleLoginError(task.exception)
                }
            }
    }

    private fun handleSuccessfulLogin(firebaseUser: FirebaseUser, isNewUser: Boolean) {
        // Save user login state locally
        userPreferences.saveUserLoginState(
            isLoggedIn = true,
            userId = firebaseUser.uid,
            userName = firebaseUser.displayName ?: "User",
            userEmail = firebaseUser.email ?: "",
            userPhotoUrl = firebaseUser.photoUrl?.toString() ?: ""
        )

        // Create or update user in Firestore
        lifecycleScope.launch {
            try {
                showLoading()

                val result = userRepository.createOrUpdateUser(
                    uid = firebaseUser.uid,
                    displayName = firebaseUser.displayName ?: "User",
                    email = firebaseUser.email ?: "",
                    photoUrl = firebaseUser.photoUrl?.toString() ?: "",
                    context = this@LoginActivity,
                    isNewUser = isNewUser
                )

                hideLoading()

                if (result.isSuccess) {
                    val user = result.getOrNull()
                    val welcomeMessage = if (isNewUser) {
                        "Welcome to our app, ${firebaseUser.displayName}!"
                    } else {
                        "Welcome back, ${firebaseUser.displayName}!"
                    }

                    Toast.makeText(this@LoginActivity, welcomeMessage, Toast.LENGTH_SHORT).show()

                    // Track successful login
                    userRepository.trackUserEvent(
                        firebaseUser.uid,
                        if (isNewUser) "user_registration_completed" else "user_login_success",
                        mapOf(
                            "login_method" to "google",
                            "user_email" to (firebaseUser.email ?: ""),
                            "has_display_name" to (firebaseUser.displayName != null)
                        )
                    )

                    navigateToChatActivity()
                } else {
                    // Even if Firestore fails, continue with login but show warning
                    Toast.makeText(
                        this@LoginActivity,
                        "Welcome ${firebaseUser.displayName}! (Profile sync pending)",
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToChatActivity()
                }
            } catch (e: Exception) {
                hideLoading()
                // Don't block login for data collection failures
                Toast.makeText(
                    this@LoginActivity,
                    "Welcome ${firebaseUser.displayName}!",
                    Toast.LENGTH_SHORT
                ).show()
                navigateToChatActivity()
            }
        }
    }
    private fun handleLoginError(exception: Exception?) {
        val errorMessage = when (exception) {
            is FirebaseAuthUserCollisionException -> "An account already exists with this email"
            is FirebaseNetworkException -> "Network error. Please check your connection"
            else -> "Authentication failed: ${exception?.message}"
        }

        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()

        // Track failed login attempt
        lifecycleScope.launch {
            userRepository.trackUserEvent(
                "anonymous",
                "login_failed",
                mapOf(
                    "error_message" to (exception?.message ?: "Unknown error"),
                    "login_method" to "google"
                )
            )
        }
    }

    private fun showLoading() {
        buttonGoogleSignIn.text = ""
        buttonGoogleSignIn.isEnabled = false
        progressIndicator.visibility = android.view.View.VISIBLE
    }

    private fun hideLoading() {
        buttonGoogleSignIn.text = getString(R.string.sign_in_with_google)
        buttonGoogleSignIn.isEnabled = true
        progressIndicator.visibility = android.view.View.GONE
    }

    private fun navigateToChatActivity() {
        val intent = Intent(this, ChatActivity::class.java)
        //intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in and update UI accordingly
        val currentUser = auth.currentUser
        if (currentUser != null && userPreferences.isUserLoggedIn()) {
            navigateToChatActivity()
        }
    }
}