package com.kharagedition.tibetankeyboard.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.auth.AuthManager
import com.kharagedition.tibetankeyboard.chat.ChatAdapter
import com.kharagedition.tibetankeyboard.chat.ChatViewModel
import com.kharagedition.tibetankeyboard.subscription.RevenueCatManager
import com.kharagedition.tibetankeyboard.subscription.SubscriptionUIComponent
import com.kharagedition.tibetankeyboard.util.*

class ChatActivity : AppCompatActivity(), RevenueCatManager.SubscriptionCallback {

    // ViewModels and Managers
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var authManager: AuthManager
    private lateinit var subscriptionUIComponent: SubscriptionUIComponent

    // UI Components
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutEmptyChat: LinearLayout
    private lateinit var editTextMessage: TextInputEditText
    private lateinit var buttonSend: FloatingActionButton
    private lateinit var animationViewLoading: LottieAnimationView
    private lateinit var materialToolbar: MaterialToolbar
    private lateinit var userProfileImage: ShapeableImageView
    private lateinit var userNameText: TextView
    private lateinit var mainLayout: ConstraintLayout
    private lateinit var buttonBack: ImageButton
    private lateinit var buyPremium: ShapeableImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize managers
        authManager = AuthManager(this)

        // Check authentication first
        if (!authManager.isUserAuthenticated()) {
            authManager.redirectToLogin()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)

        initializeViews()
        setupUI()
        setupObservers()
        initializeServices()

        // Show welcome message for first-time users
        if (authManager.isFirstTimeUser()) {
            showWelcomeMessage()
        }

        // Add welcome message to chat
        viewModel.addWelcomeMessage()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.recyclerViewChat)
        editTextMessage = findViewById(R.id.editTextMessage)
        buttonSend = findViewById(R.id.buttonSend)
        animationViewLoading = findViewById(R.id.animationViewLoading)
        materialToolbar = findViewById(R.id.chat_toolbar)
        userProfileImage = findViewById(R.id.userProfileImage)
        userNameText = findViewById(R.id.userNameText)
        mainLayout = findViewById(R.id.main_container)
        layoutEmptyChat = findViewById(R.id.layoutEmptyChat)
        buttonBack = findViewById(R.id.buttonBack)
        buyPremium = findViewById(R.id.buyPremium)
    }

    private fun setupUI() {
        // Setup edge-to-edge with keyboard handling
        setupEdgeToEdgeWithKeyboard(mainLayout)

        // Setup toolbar
        setupToolbar()

        // Setup RecyclerView
        setupRecyclerView()

        // Setup listeners
        setupListeners()

        // Load user profile
        loadUserProfile()

        // Initialize subscription UI component
        subscriptionUIComponent = SubscriptionUIComponent(this, buyPremium)
    }

    private fun setupToolbar() {
        setSupportActionBar(materialToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        materialToolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun loadUserProfile() {
        val userName = authManager.getCurrentUserName()
        val userPhotoUrl = authManager.getCurrentUserPhotoUrl()

        userNameText.text = userName
        userProfileImage.loadProfileImage(userPhotoUrl)
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
            setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom + 16)
            clipToPadding = false
        }
    }

    private fun setupListeners() {
        buttonSend.setOnClickListener { sendMessage() }
        buttonBack.setOnClickListener { onBackPressed() }

        editTextMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                return@setOnEditorActionListener true
            }
            false
        }

        editTextMessage.addTextChangedListener {
            buttonSend.isEnabled = !it.isNullOrBlank()
        }

        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { _, insets ->
            if (chatAdapter.itemCount > 0) {
                recyclerView.scrollToPosition(chatAdapter.itemCount - 1)
            }
            insets
        }
    }

    private fun setupObservers() {
        // Observe chat messages
        viewModel.messages.observe(this) { messages ->
            chatAdapter.submitList(messages) {
                if (messages.isNotEmpty()) {
                    recyclerView.smoothScrollToPosition(messages.size - 1)
                }
                layoutEmptyChat.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        // Observe loading state
        viewModel.isLoading.observe(this) { isLoading ->
            animationViewLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
            editTextMessage.isEnabled = !isLoading
            buttonSend.isEnabled = !isLoading && !editTextMessage.text.isNullOrBlank()
        }
    }

    private fun initializeServices() {
        // Initialize user session and RevenueCat
        authManager.initializeUserSession(this)
    }

    private fun sendMessage() {
        val messageText = editTextMessage.text.toString().trim()
        if (messageText.isValidMessage()) {
            viewModel.sendMessage(messageText)
            editTextMessage.setText("")
        } else {
            showToast("Message too long or short")
        }
    }

    private fun showWelcomeMessage() {
        val userName = authManager.getCurrentUserName()
        mainLayout.showWelcomeSnackbar(userName)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                showLogoutDialog()
                true
            }
            R.id.action_clear_chat -> {
                showClearChatDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showLogoutDialog() {
        showConfirmationDialog(
            title = "Sign Out",
            message = "Are you sure you want to sign out?",
            positiveText = "Sign Out",
            onPositive = { signOut() }
        )
    }

    private fun showClearChatDialog() {
        showConfirmationDialog(
            title = "Clear Chat",
            message = "Are you sure you want to clear all messages?",
            positiveText = "Clear",
            onPositive = {
                viewModel.clearMessages()
                showToast("Chat cleared")
            }
        )
    }

    private fun signOut() {
        authManager.signOut {
            authManager.redirectToLogin()
            showToast("Signed out successfully")
        }
    }

    override fun onResume() {
        super.onResume()
        if (!authManager.isUserAuthenticated()) {
            authManager.redirectToLogin()
        } else {
            // Refresh subscription status when resuming
            subscriptionUIComponent.refreshSubscriptionStatus()
        }
    }

    // RevenueCatManager.SubscriptionCallback implementations
    override fun onSuccess(message: String) {
        // Handle successful initialization
        println("RevenueCat initialized: $message")
    }

    override fun onError(error: String) {
        println("RevenueCat error: $error")
        showToast("Premium services temporarily unavailable")
    }

    override fun onUserCancelled() {
        // Not applicable for initialization
    }
}