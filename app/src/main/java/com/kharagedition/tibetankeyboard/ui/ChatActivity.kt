package com.kharagedition.tibetankeyboard.ui

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.kharagedition.tibetankeyboard.R
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.appbar.MaterialToolbar
import com.kharagedition.tibetankeyboard.chat.ChatAdapter
import com.kharagedition.tibetankeyboard.chat.ChatViewModel
class ChatActivity : AppCompatActivity() {

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var editTextMessage: TextInputEditText
    private lateinit var buttonSend: FloatingActionButton
    private lateinit var animationViewLoading: LottieAnimationView
    private  lateinit var materialToolbar: MaterialToolbar
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set window soft input mode to resize
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Apply the maximum of system bars and IME insets to the bottom padding
            val bottomPadding = if (imeInsets.bottom > systemBars.bottom) imeInsets.bottom else systemBars.bottom

            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding)
            insets
        }


        initializeViews()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
        materialToolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        // Add welcome message
        viewModel.addWelcomeMessage()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.recyclerViewChat)
        editTextMessage = findViewById(R.id.editTextMessage)
        buttonSend = findViewById(R.id.buttonSend)
        animationViewLoading = findViewById(R.id.animationViewLoading)
        materialToolbar = findViewById(R.id.chat_toolbar)
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
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            animationViewLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (isLoading) {
                animationViewLoading.visibility = View.VISIBLE
            } else {
                animationViewLoading.visibility = View.GONE
            }
            editTextMessage.isEnabled = !isLoading
            buttonSend.isEnabled = !isLoading
        }
    }

    private fun sendMessage() {
        val messageText = editTextMessage.text.toString().trim()
        if (messageText.isNotEmpty() && messageText.length<=300) {
            viewModel.sendMessage(messageText)
            editTextMessage.setText("")
        }else{
            Toast.makeText(this, "Message too long or short", Toast.LENGTH_SHORT).show()
        }
    }
}