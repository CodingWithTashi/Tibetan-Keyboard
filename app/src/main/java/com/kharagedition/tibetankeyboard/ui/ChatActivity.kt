package com.kharagedition.tibetankeyboard.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.ConsumeParams
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.common.collect.ImmutableList
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.kharagedition.tibetankeyboard.LoginActivity
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.UserPreferences
import com.kharagedition.tibetankeyboard.chat.ChatAdapter
import com.kharagedition.tibetankeyboard.chat.ChatViewModel

class ChatActivity : AppCompatActivity(), PurchasesUpdatedListener {

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
    private lateinit var mainLayout: ConstraintLayout
    private lateinit var auth: FirebaseAuth
    private lateinit var userPreferences: UserPreferences
    private lateinit var buttonBack: ImageButton
    private lateinit var buyPremium: ShapeableImageView
    private lateinit var billingClient: BillingClient
    private var productDetails: ProductDetails? = null

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
        setupWindowInsets()
        setupBillingClient()
        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeViewModel()

        // Show welcome message for first-time users
        if (userPreferences.isFirstTimeUser()) {
            showWelcomeMessage()
        }

        // Add welcome message
        viewModel.addWelcomeMessage()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Apply the maximum of system bars and IME insets to the bottom padding
            val bottomPadding = if (imeInsets.bottom > systemBars.bottom) imeInsets.bottom else systemBars.bottom

            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding)
            insets
        }
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(this)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

        connectToBillingService()
    }

    private fun connectToBillingService() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    println("Billing Client Setup Finished")
                    queryProductDetails()
                    checkExistingPurchases()
                } else {
                    println("Billing Client Setup Failed: ${billingResult.debugMessage}")
                    handleBillingError("Failed to connect to billing service")
                }
            }

            override fun onBillingServiceDisconnected() {
                println("Billing Client Disconnected")
                // Try to reconnect
                connectToBillingService()
            }
        })
    }

    private fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ImmutableList.of(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId("monthly_premium_subscription")
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            ).build()

        billingClient.queryProductDetailsAsync(params) { queryResponse, productDetailsList ->
            if (queryResponse.responseCode == BillingClient.BillingResponseCode.OK) {
                if (productDetailsList.productDetailsList.isNotEmpty()) {
                    productDetails = productDetailsList.productDetailsList[0]
                    println("Product loaded: ${productDetails?.name}")

                    // Log available subscription offers
                    productDetails?.subscriptionOfferDetails?.forEach { offer ->
                        println("Offer: ${offer.basePlanId}, Token: ${offer.offerToken}")
                        offer.pricingPhases.pricingPhaseList.forEach { phase ->
                            println("Price: ${phase.formattedPrice}, Period: ${phase.billingPeriod}")
                        }
                    }
                } else {
                    println("No products found")
                    handleBillingError("Premium subscription not available")
                }
            } else {
                println("Failed to query product details: ${queryResponse.debugMessage}")
                handleBillingError("Failed to load premium subscription details")
            }
        }
    }

    private fun checkExistingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        // User already has premium subscription
                        if (purchase.products.contains("monthly_premium_subscription")) {
                            //userPreferences.setPremiumUser(true)
                            updatePremiumUI(true)
                        }

                        // Acknowledge the purchase if not acknowledged
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        }
                    }
                }
            }
        }
    }


    private fun isUserAuthenticated(): Boolean {
        return auth.currentUser != null && userPreferences.isUserLoggedIn()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
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
        buyPremium = findViewById(R.id.buyPremium)
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

        editTextMessage.addTextChangedListener {
            buttonSend.isEnabled = !it.isNullOrBlank()
        }

        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { v, insets ->
            if (chatAdapter.itemCount > 0) {
                recyclerView.scrollToPosition(chatAdapter.itemCount - 1)
            }
            insets
        }

        buyPremium.setOnClickListener {
            launchPremiumPurchase()
        }
    }

    private fun launchPremiumPurchase() {
        if (!::billingClient.isInitialized || !billingClient.isReady) {
            handleBillingError("Billing service not ready. Please try again.")
            return
        }

        val currentProductDetails = productDetails
        if (currentProductDetails == null) {
            handleBillingError("Premium subscription not available")
            return
        }

        // Get the subscription offer details
        val subscriptionOfferDetails = currentProductDetails.subscriptionOfferDetails
        if (subscriptionOfferDetails.isNullOrEmpty()) {
            handleBillingError("No subscription offers available")
            return
        }

        // Use the first available offer (you can implement logic to choose specific offers)
        val selectedOffer = subscriptionOfferDetails[0]

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(currentProductDetails)
            .setOfferToken(selectedOffer.offerToken)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val billingResult = billingClient.launchBillingFlow(this, billingFlowParams)

        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            handleBillingError("Failed to launch purchase flow: ${billingResult.debugMessage}")
        }
    }

    private fun observeViewModel() {
        viewModel.messages.observe(this) { messages ->
            chatAdapter.submitList(messages) {
                if (messages.isNotEmpty()) {
                    recyclerView.smoothScrollToPosition(messages.size - 1)
                }
                layoutEmptyChat.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
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
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out?")
            .setPositiveButton("Sign Out") { _, _ ->
                signOut()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showClearChatDialog() {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
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
        auth.signOut()
        userPreferences.clearUserData()
        redirectToLogin()
        Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        if (!isUserAuthenticated()) {
            redirectToLogin()
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.let { handlePurchases(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Toast.makeText(this, "Purchase cancelled", Toast.LENGTH_SHORT).show()
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Toast.makeText(this, "You already own this subscription", Toast.LENGTH_SHORT).show()
                //userPreferences.setPremiumUser(true)
                updatePremiumUI(true)
            }
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> {
                handleBillingError("Subscriptions not supported on this device")
            }
            else -> {
                handleBillingError("Purchase failed: ${billingResult.debugMessage}")
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    if (purchase.products.contains("monthly_premium_subscription")) {
                        // Grant premium features
                        //userPreferences.setPremiumUser(true)
                        updatePremiumUI(true)
                        Toast.makeText(this, "Premium subscription activated!", Toast.LENGTH_LONG).show()

                        // Acknowledge the purchase if not already acknowledged
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        }
                    }
                }
                Purchase.PurchaseState.PENDING -> {
                    Toast.makeText(this, "Purchase pending. Please wait for confirmation.", Toast.LENGTH_LONG).show()
                }
                Purchase.PurchaseState.UNSPECIFIED_STATE -> {
                    handleBillingError("Purchase in unknown state")
                }
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                println("Purchase acknowledged successfully")
            } else {
                println("Failed to acknowledge purchase: ${billingResult.debugMessage}")
            }
        }
    }

    private fun updatePremiumUI(isPremium: Boolean) {
        // Update UI to reflect premium status
        if (isPremium) {
            buyPremium.visibility = View.GONE
            // You can add other UI updates here for premium users
        } else {
            buyPremium.visibility = View.VISIBLE
        }
    }

    private fun handleBillingError(message: String) {
        println("Billing Error: $message")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::billingClient.isInitialized) {
            billingClient.endConnection()
        }
    }
}