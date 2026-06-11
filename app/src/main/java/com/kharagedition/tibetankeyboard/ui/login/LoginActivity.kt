package com.kharagedition.tibetankeyboard.ui.login

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.data.local.UserPreferences
import com.kharagedition.tibetankeyboard.data.repository.UserRepository
import com.kharagedition.tibetankeyboard.ui.chat.ChatActivity
import com.kharagedition.tibetankeyboard.ui.subscription.PremiumActivity
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanKeyboardTheme
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var userPreferences: UserPreferences
    private lateinit var userRepository: UserRepository
    private lateinit var revenueCatManager: RevenueCatManager

    private val viewModel: LoginViewModel by viewModels()

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

        // Initialize Firebase Auth
        auth = Firebase.auth
        userRepository = UserRepository()
        userPreferences = UserPreferences(this)
        revenueCatManager = RevenueCatManager.getInstance()

        // Check if user is already signed in
        if (auth.currentUser != null && userPreferences.isUserLoggedIn()) {
            Log.d("LoginActivity", "User already logged in, initializing RevenueCat...")
            initializeRevenueCatAndNavigate()
            return
        }

        configureGoogleSignIn()

        setContent {
            TibetanKeyboardTheme {
                val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
                LoginScreen(
                    isLoading = isLoading,
                    onGoogleSignIn = { signInWithGoogle() },
                    onTerms = { openTermsOfService() },
                    onPrivacy = { openPrivacyPolicy() },
                )
            }
        }
    }

    private fun openTermsOfService() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://kharagedition.github.io/term-and-condition/tibetan-keyboard.html"))
        startActivity(intent)
    }

    private fun openPrivacyPolicy() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://kharagedition.github.io/privacy-policy/tibetan-keyboard"))
        startActivity(intent)
    }

    private fun configureGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .requestProfile()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
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
        // Persist login state — this alone is enough to treat the user as signed in.
        userPreferences.saveUserLoginState(
            isLoggedIn = true,
            userId = firebaseUser.uid,
            userName = firebaseUser.displayName ?: "User",
            userEmail = firebaseUser.email ?: "",
            userPhotoUrl = firebaseUser.photoUrl?.toString() ?: ""
        )

        // Kick off RevenueCat init in the background (idempotent). Destination screens read the
        // premium entitlement from its LiveData, so the redirect must NOT wait on this callback —
        // previously a callback that never fired left users stuck on the login screen until they
        // force-restarted the app.
        revenueCatManager.initialize(applicationContext, auth, null)

        // Best-effort Firestore profile sync + analytics, off the redirect path. Uses a standalone
        // scope so finishing LoginActivity below doesn't cancel these writes.
        val uid = firebaseUser.uid
        val name = firebaseUser.displayName ?: "User"
        val email = firebaseUser.email ?: ""
        val photo = firebaseUser.photoUrl?.toString() ?: ""
        val hasName = firebaseUser.displayName != null
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                userRepository.createOrUpdateUser(
                    uid = uid,
                    displayName = name,
                    email = email,
                    photoUrl = photo,
                    context = applicationContext,
                    isNewUser = isNewUser
                )
            }.onFailure { Log.w("LoginActivity", "createOrUpdateUser failed (non-blocking)", it) }
            runCatching {
                userRepository.trackUserEvent(
                    uid,
                    if (isNewUser) "user_registration_completed" else "user_login_success",
                    mapOf(
                        "login_method" to "google",
                        "user_email" to email,
                        "has_display_name" to hasName
                    )
                )
            }
        }

        // Redirect immediately — never gated on the network calls above.
        hideLoading()
        Toast.makeText(
            this,
            if (isNewUser) "Welcome, $name!" else "Welcome back, $name!",
            Toast.LENGTH_SHORT
        ).show()
        navigateToChatActivity()
    }

    /**
     * Initialize RevenueCat after login and navigate to next screen
     */
    private fun initializeRevenueCatAndNavigate() {
        Log.d("LoginActivity", "Initializing RevenueCat after login...")
        revenueCatManager.initialize(this, auth, object : RevenueCatManager.SubscriptionCallback {
            override fun onSuccess(message: String) {
                Log.d("LoginActivity", "✅ RevenueCat initialized: $message")
                navigateToChatActivity()
            }

            override fun onError(error: String) {
                Log.e("LoginActivity", "❌ RevenueCat init failed: $error")
                navigateToChatActivity()
            }

            override fun onUserCancelled() {}
        })
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

    private fun showLoading() = viewModel.setLoading(true)

    private fun hideLoading() = viewModel.setLoading(false)

    private fun navigateToChatActivity() {
        // When the login was launched from a PRO upsell (e.g. the keyboard's PRO strip),
        // forward to the premium paywall after a successful sign-in instead of the chat.
        val openPremium = intent?.getBooleanExtra(EXTRA_OPEN_PREMIUM_AFTER_LOGIN, false) == true
        val next = if (openPremium) Intent(this, PremiumActivity::class.java)
                   else Intent(this, ChatActivity::class.java)
        startActivity(next)
        finish()
    }

    companion object {
        /** Set true to route to the premium paywall after a successful login. */
        const val EXTRA_OPEN_PREMIUM_AFTER_LOGIN = "open_premium_after_login"
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