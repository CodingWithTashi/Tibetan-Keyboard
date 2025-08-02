package com.kharagedition.tibetankeyboard.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.kharagedition.tibetankeyboard.R
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.kharagedition.tibetankeyboard.LoginActivity
import com.kharagedition.tibetankeyboard.chat.ChatAdapter
import com.kharagedition.tibetankeyboard.chat.ChatViewModel
import com.kharagedition.tibetankeyboard.UserPreferences

class ChatActivity : AppCompatActivity() {

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutEmptyChat: LinearLayout
    private lateinit var editTextMessage: TextInputEditText
    private lateinit var buttonSend: FloatingActionButton
    private lateinit var animationViewLoading: LottieAnimationView
    private lateinit var materialToolbar: MaterialToolbar
    private lateinit var userProfileImage: ShapeableImageView
    private lateinit var userNameText: TextView
    private  lateinit var mainLayout: ConstraintLayout;
    private lateinit var auth: FirebaseAuth
    private lateinit var userPreferences: UserPreferences
    private lateinit var buttonBack: ImageButton;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase and preferences
        auth = Firebase.auth
        userPreferences = UserPreferences(this)

        // Check if user is authenticated
        if (!isUserAuthenticated()) {
            redirectToLogin()
            return
        }

        // Set window soft input mode to resize
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)

        initializeViews()

        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Apply the maximum of system bars and IME insets to the bottom padding
            val bottomPadding = if (imeInsets.bottom > systemBars.bottom) imeInsets.bottom else systemBars.bottom

            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding)
            insets
        }


        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
        //loadUserProfile()

        // Show welcome message for first-time users
        if (userPreferences.isFirstTimeUser()) {
            showWelcomeMessage()
        }

        // Add welcome message
        viewModel.addWelcomeMessage()
    }

    private fun isUserAuthenticated(): Boolean {
        return auth.currentUser != null && userPreferences.isUserLoggedIn()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        //intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK aor Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
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
    }

    private fun setupToolbar() {
        setSupportActionBar(materialToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        materialToolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun loadUserProfile() {
        val userName = userPreferences.getUserName()
        val userPhotoUrl = userPreferences.getUserPhotoUrl()

        userNameText.text = if (userName.isNotEmpty()) userName else "User"

        if (userPhotoUrl.isNotEmpty()) {
            Glide.with(this)
                .load(userPhotoUrl)
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar)
                .into(userProfileImage)
        } else {
            userProfileImage.setImageResource(R.drawable.ic_default_avatar)
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
            // Add extra padding at the bottom of the RecyclerView to ensure messages aren't hidden
            setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom + 16)
            clipToPadding = false
        }
    }

    private fun setupListeners() {
        buttonSend.setOnClickListener {
            sendMessage()
        }
        buttonBack.setOnClickListener {
            onBackPressed()
        }

        editTextMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                return@setOnEditorActionListener true
            }
            false
        }

        // Enable/disable send button based on text input
        editTextMessage.addTextChangedListener {
            buttonSend.isEnabled = !it.isNullOrBlank()
        }

        // Scroll RecyclerView when keyboard appears
        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { v, insets ->
            if (chatAdapter.itemCount > 0) {
                recyclerView.scrollToPosition(chatAdapter.itemCount - 1)
            }
            insets
        }
    }

    private fun observeViewModel() {
        viewModel.messages.observe(this) { messages ->
            chatAdapter.submitList(messages) {
                // Scroll to the bottom when a new message is added
                if (messages.isNotEmpty()) {
                    recyclerView.smoothScrollToPosition(messages.size - 1)
                }
                if(messages.isEmpty()) {
                    layoutEmptyChat.visibility = View.VISIBLE
                }else{
                    layoutEmptyChat.visibility = View.GONE
                }
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            animationViewLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
            editTextMessage.isEnabled = !isLoading
            buttonSend.isEnabled = !isLoading && !editTextMessage.text.isNullOrBlank()
        }
    }

    private fun sendMessage() {
        val messageText = editTextMessage.text.toString().trim()
        if (messageText.isNotEmpty() && messageText.length <= 300) {
            viewModel.sendMessage(messageText)
            editTextMessage.setText("")
        } else {
            Toast.makeText(this, "Message too long or short", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showWelcomeMessage() {
        val userName = userPreferences.getUserName()
        val welcomeMessage = if (userName.isNotEmpty()) {
            "Welcome to the chat, $userName! 🎉"
        } else {
            "Welcome to the chat! 🎉"
        }

        Snackbar.make(mainLayout, welcomeMessage, Snackbar.LENGTH_LONG)
            .setAction("Got it!") { }
            .show()
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
        AlertDialog.Builder(this,R.style.CustomAlertDialog)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out?")
            .setPositiveButton("Sign Out") { _, _ ->
                signOut()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showClearChatDialog() {
        AlertDialog.Builder(this,R.style.CustomAlertDialog)
            .setTitle("Clear Chat")
            .setMessage("Are you sure you want to clear all messages?")
            .setPositiveButton("Clear") { _, _ ->
                viewModel.clearMessages()
                Toast.makeText(this, "Chat cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun signOut() {
        // Sign out from Firebase
        auth.signOut()

        // Clear user preferences
        userPreferences.clearUserData()

        // Navigate to login
        redirectToLogin()

        Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        // Check authentication status when app resumes
        if (!isUserAuthenticated()) {
            redirectToLogin()
        }
    }
}