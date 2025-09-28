package com.kharagedition.tibetankeyboard

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import java.util.*

class EmojiKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var searchEditText: EditText
    private lateinit var emojiRecyclerView: RecyclerView
    private lateinit var categoryRecyclerView: RecyclerView
    private lateinit var backButton: ImageButton
    private lateinit var searchKeyboardContainer: FrameLayout

    private lateinit var emojiAdapter: EmojiAdapter
    private lateinit var categoryAdapter: EmojiCategoryAdapter
    private var emojiClickListener: ((String) -> Unit)? = null
    private var backClickListener: (() -> Unit)? = null
    private var showSearchKeyboardListener: (() -> Unit)? = null
    private var hideSearchKeyboardListener: (() -> Unit)? = null

    private val allEmojis = mutableListOf<EmojiItem>()
    private val filteredEmojis = mutableListOf<EmojiItem>()
    private var isSearchMode = false

    init {
        orientation = VERTICAL
        setupView()
        initializeEmojis()
        setupRecyclerViews()
        setupSearch()
        setupBackButton()
        setupSearchKeyboard()
    }

    private fun setupView() {
        LayoutInflater.from(context).inflate(R.layout.emoji_keyboard_layout, this, true)

        searchEditText = findViewById(R.id.emoji_search)
        emojiRecyclerView = findViewById(R.id.emoji_recycler_view)
        categoryRecyclerView = findViewById(R.id.category_recycler_view)
        backButton = findViewById(R.id.emoji_back_button)
        searchKeyboardContainer = findViewById(R.id.search_keyboard_container)
    }

    private fun setupRecyclerViews() {
        // Setup emoji grid
        emojiAdapter = EmojiAdapter(filteredEmojis) { emoji ->
            emojiClickListener?.invoke(emoji)
        }
        emojiRecyclerView.layoutManager = GridLayoutManager(context, 8)
        emojiRecyclerView.adapter = emojiAdapter

        // Setup category horizontal list
        val categories = EmojiCategory.values().toList()
        categoryAdapter = EmojiCategoryAdapter(categories) { category ->
            // Clear search when switching categories
            if (searchEditText.text.isNotEmpty()) {
                searchEditText.setText("")
            }
            // Filter emojis by selected category
            filterByCategory(category)
        }
        categoryRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        categoryRecyclerView.adapter = categoryAdapter

        // Show smileys initially
        filterByCategory(EmojiCategory.SMILEYS)
        categoryAdapter.setSelectedCategory(EmojiCategory.SMILEYS)
    }

    private fun setupSearch() {
        searchEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                enterSearchMode()
            } else {
                exitSearchMode()
            }
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.isEmpty()) {
                    filterByCategory(EmojiCategory.SMILEYS)
                } else {
                    searchEmojis(query)
                }
            }
        })
    }

    private fun setupBackButton() {
        backButton.setOnClickListener {
            if (isSearchMode) {
                exitSearchMode()
                searchEditText.clearFocus()
                searchEditText.setText("")
            } else {
                backClickListener?.invoke()
            }
        }
    }

    private fun setupSearchKeyboard() {
        val searchKeyboard = SimpleSearchKeyboard(context)
        searchKeyboard.setOnKeyClickListener { key ->
            insertTextInSearch(key)
        }
        searchKeyboard.setOnDeleteClickListener {
            deleteFromSearch()
        }
        searchKeyboardContainer.addView(searchKeyboard)
    }

    private fun initializeEmojis() {
        allEmojis.clear()
        allEmojis.addAll(EmojiItemList.allEmojis.toMutableList())
       }

    private fun filterByCategory(category: EmojiCategory) {
        filteredEmojis.clear()
        if (category == EmojiCategory.ALL) {
            filteredEmojis.addAll(allEmojis)
        } else {
            filteredEmojis.addAll(allEmojis.filter { it.category == category })
        }

        // Debug logging (you can remove this later)
        android.util.Log.d("EmojiKeyboard", "Filtering by ${category.displayName}, found ${filteredEmojis.size} emojis")

        emojiAdapter.notifyDataSetChanged()
        // Note: We don't call setSelectedCategory here to avoid circular calls
    }

    private fun searchEmojis(query: String) {
        filteredEmojis.clear()
        val lowercaseQuery = query.lowercase()
        filteredEmojis.addAll(allEmojis.filter { emojiItem ->
            emojiItem.searchTags.any { tag ->
                tag.lowercase().contains(lowercaseQuery)
            }
        })
        emojiAdapter.notifyDataSetChanged()
    }

    private fun enterSearchMode() {
        isSearchMode = true
        // Reduce emoji grid height to make space for search keyboard
        val layoutParams = emojiRecyclerView.layoutParams
        layoutParams.height = (120 * context.resources.displayMetrics.density).toInt() // 120dp
        emojiRecyclerView.layoutParams = layoutParams

        // Show search keyboard
        searchKeyboardContainer.visibility = View.VISIBLE
        showSearchKeyboardListener?.invoke()
    }

    private fun exitSearchMode() {
        isSearchMode = false
        // Restore emoji grid height
        val layoutParams = emojiRecyclerView.layoutParams
        layoutParams.height = (200 * context.resources.displayMetrics.density).toInt() // 200dp
        emojiRecyclerView.layoutParams = layoutParams

        // Hide search keyboard
        searchKeyboardContainer.visibility = View.GONE
        hideSearchKeyboardListener?.invoke()
    }

    private fun insertTextInSearch(text: String) {
        val currentText = searchEditText.text.toString()
        val start = searchEditText.selectionStart
        val newText = currentText.substring(0, start) + text + currentText.substring(start)
        searchEditText.setText(newText)
        searchEditText.setSelection(start + text.length)
    }

    private fun deleteFromSearch() {
        val currentText = searchEditText.text.toString()
        val start = searchEditText.selectionStart
        if (start > 0) {
            val newText = currentText.substring(0, start - 1) + currentText.substring(start)
            searchEditText.setText(newText)
            searchEditText.setSelection(start - 1)
        }
    }

    // Public methods
    fun setOnEmojiClickListener(listener: (String) -> Unit) {
        emojiClickListener = listener
    }

    fun setOnBackClickListener(listener: () -> Unit) {
        backClickListener = listener
    }

    fun setThemeColor(color: String) {
        // Apply theme color to the view if needed
        // You can customize the appearance based on the theme
    }

    fun setOnShowSearchKeyboardListener(listener: () -> Unit) {
        showSearchKeyboardListener = listener
    }

    fun setOnHideSearchKeyboardListener(listener: () -> Unit) {
        hideSearchKeyboardListener = listener
    }
}

// Data classes and enums
data class EmojiItem(
    val emoji: String,
    val category: EmojiCategory,
    val searchTags: List<String>
)

enum class EmojiCategory(val displayName: String, val icon: String) {
    ALL("All", "🔍"),
    SMILEYS("Smileys", "😀"),
    ANIMALS("Animals", "🐶"),
    FOOD("Food", "🍎"),
    ACTIVITIES("Activities", "⚽"),
    TRAVEL("Travel", "🚗"),
    OBJECTS("Objects", "💡")
}

// Emoji Adapter
class EmojiAdapter(
    private val emojis: List<EmojiItem>,
    private val onEmojiClick: (String) -> Unit
) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

    class EmojiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val emojiText: TextView = itemView.findViewById(R.id.emoji_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.emoji_item, parent, false)
        return EmojiViewHolder(view)
    }

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        val emoji = emojis[position].emoji
        holder.emojiText.text = emoji
        holder.itemView.setOnClickListener {
            onEmojiClick(emoji)
        }
    }

    override fun getItemCount(): Int = emojis.size
}

// Category Adapter
class EmojiCategoryAdapter(
    private val categories: List<EmojiCategory>,
    private val onCategoryClick: (EmojiCategory) -> Unit
) : RecyclerView.Adapter<EmojiCategoryAdapter.CategoryViewHolder>() {

    private var selectedCategory = EmojiCategory.SMILEYS

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val categoryIcon: TextView = itemView.findViewById(R.id.category_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.emoji_category_item, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.categoryIcon.text = category.icon

        // Highlight selected category
        holder.itemView.isSelected = category == selectedCategory
        holder.itemView.alpha = if (category == selectedCategory) 1.0f else 0.6f

        holder.itemView.setOnClickListener {
            if (selectedCategory != category) {
                val oldSelectedIndex = categories.indexOf(selectedCategory)
                selectedCategory = category

                // Notify the callback BEFORE updating UI
                onCategoryClick(category)

                // Update UI - notify old and new selections
                if (oldSelectedIndex != -1) {
                    notifyItemChanged(oldSelectedIndex)
                }
                notifyItemChanged(position)
            }
        }
    }

    override fun getItemCount(): Int = categories.size

    fun setSelectedCategory(category: EmojiCategory) {
        if (selectedCategory != category) {
            val oldSelectedIndex = categories.indexOf(selectedCategory)
            selectedCategory = category

            // Update UI - notify old and new selections
            if (oldSelectedIndex != -1) {
                notifyItemChanged(oldSelectedIndex)
            }
            val newSelectedIndex = categories.indexOf(category)
            if (newSelectedIndex != -1) {
                notifyItemChanged(newSelectedIndex)
            }
        }
    }
}