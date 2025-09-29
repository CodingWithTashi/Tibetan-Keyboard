package com.kharagedition.tibetankeyboard

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ImageSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager

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

    private lateinit var unifiedAdapter: UnifiedAdapter
    private lateinit var categoryAdapter: EmojiCategoryAdapter

    // Updated callbacks
    private var emojiClickListener: ((String) -> Unit)? = null
    private var stickerClickListener: ((StickerItem) -> Unit)? = null
    private var backClickListener: (() -> Unit)? = null
    private var showSearchKeyboardListener: (() -> Unit)? = null
    private var hideSearchKeyboardListener: (() -> Unit)? = null

    private val allItems = mutableListOf<DisplayItem>()
    private val filteredItems = mutableListOf<DisplayItem>()
    private var isSearchMode = false

    init {
        orientation = VERTICAL
        setupView()
        initializeItems()
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
        // Setup unified grid for emojis and stickers
        unifiedAdapter = UnifiedAdapter(
            items = filteredItems,
            onEmojiClick = { emoji -> emojiClickListener?.invoke(emoji) },
            onStickerClick = { sticker -> stickerClickListener?.invoke(sticker) }
        )
        emojiRecyclerView.layoutManager = GridLayoutManager(context, 8)
        emojiRecyclerView.adapter = unifiedAdapter

        // Setup category horizontal list
        val categories = EmojiCategory.values().toList()
        categoryAdapter = EmojiCategoryAdapter(categories) { category ->
            // Clear search when switching categories
            if (searchEditText.text.isNotEmpty()) {
                searchEditText.setText("")
            }
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
                    searchItems(query)
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

    private fun initializeItems() {
        allItems.clear()

        // Add all emojis
        allItems.addAll(EmojiItemList.allEmojis.map { DisplayItem.EmojiDisplay(it) })

        // Add all stickers
        //allItems.addAll(StickerItemList.allStickers.map { DisplayItem.StickerDisplay(it) })
    }

    private fun filterByCategory(category: EmojiCategory) {
        filteredItems.clear()
        if (category == EmojiCategory.ALL) {
            filteredItems.addAll(allItems)
        } else {
            filteredItems.addAll(allItems.filter {
                when (it) {
                    is DisplayItem.EmojiDisplay -> it.emoji.category == category
                    is DisplayItem.StickerDisplay -> it.sticker.category == category
                }
            })
        }

        android.util.Log.d("EmojiKeyboard", "Filtering by ${category.displayName}, found ${filteredItems.size} items")
        unifiedAdapter.notifyDataSetChanged()
    }

    private fun searchItems(query: String) {
        filteredItems.clear()
        val lowercaseQuery = query.lowercase()
        filteredItems.addAll(allItems.filter { item ->
            when (item) {
                is DisplayItem.EmojiDisplay -> {
                    item.emoji.searchTags.any { tag ->
                        tag.lowercase().contains(lowercaseQuery)
                    }
                }
                is DisplayItem.StickerDisplay -> {
                    item.sticker.searchTags.any { tag ->
                        tag.lowercase().contains(lowercaseQuery)
                    }
                }
            }
        })
        unifiedAdapter.notifyDataSetChanged()
    }

    private fun enterSearchMode() {
        isSearchMode = true
        val layoutParams = emojiRecyclerView.layoutParams
        layoutParams.height = (120 * context.resources.displayMetrics.density).toInt()
        emojiRecyclerView.layoutParams = layoutParams
        searchKeyboardContainer.visibility = View.VISIBLE
        showSearchKeyboardListener?.invoke()
    }

    private fun exitSearchMode() {
        isSearchMode = false
        val layoutParams = emojiRecyclerView.layoutParams
        layoutParams.height = (200 * context.resources.displayMetrics.density).toInt()
        emojiRecyclerView.layoutParams = layoutParams
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

    fun setOnStickerClickListener(listener: (StickerItem) -> Unit) {
        stickerClickListener = listener
    }

    fun setOnBackClickListener(listener: () -> Unit) {
        backClickListener = listener
    }

    fun setThemeColor(color: String) {
        // Apply theme color if needed
    }

    fun setOnShowSearchKeyboardListener(listener: () -> Unit) {
        showSearchKeyboardListener = listener
    }

    fun setOnHideSearchKeyboardListener(listener: () -> Unit) {
        hideSearchKeyboardListener = listener
    }
}

// Sealed class for unified display
sealed class DisplayItem {
    data class EmojiDisplay(val emoji: EmojiItem) : DisplayItem()
    data class StickerDisplay(val sticker: StickerItem) : DisplayItem()
}

// Data classes
data class EmojiItem(
    val emoji: String,
    val category: EmojiCategory,
    val searchTags: List<String>
)

data class StickerItem(
    val resourceId: Int,
    val fileName: String,
    val category: EmojiCategory,
    val searchTags: List<String>
)

// Updated enum with Tibetan category
enum class EmojiCategory(val displayName: String, val icon: String) {
    ALL("All", "🔍"),
    SMILEYS("Smileys", "😀"),
    ANIMALS("Animals", "🐶"),
    FOOD("Food", "🍎"),
    ACTIVITIES("Activities", "⚽"),
    TRAVEL("Travel", "🚗"),
    OBJECTS("Objects", "💡"),
    //TIBETAN("Tibetan", "ཨ")  // New Tibetan sticker category
}

// Unified Adapter for both emojis and stickers
class UnifiedAdapter(
    private val items: List<DisplayItem>,
    private val onEmojiClick: (String) -> Unit,
    private val onStickerClick: (StickerItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_EMOJI = 0
        private const val TYPE_STICKER = 1
    }

    class EmojiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val emojiText: TextView = itemView.findViewById(R.id.emoji_text)
    }

    class StickerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val stickerImage: ImageView = itemView.findViewById(R.id.sticker_image)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is DisplayItem.EmojiDisplay -> TYPE_EMOJI
            is DisplayItem.StickerDisplay -> TYPE_STICKER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_EMOJI -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.emoji_item, parent, false)
                EmojiViewHolder(view)
            }
            TYPE_STICKER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.sticker_item, parent, false)
                StickerViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is DisplayItem.EmojiDisplay -> {
                val emojiHolder = holder as EmojiViewHolder
                emojiHolder.emojiText.text = item.emoji.emoji
                holder.itemView.setOnClickListener {
                    onEmojiClick(item.emoji.emoji)
                }
            }
            is DisplayItem.StickerDisplay -> {
                val stickerHolder = holder as StickerViewHolder
                stickerHolder.stickerImage.setImageResource(item.sticker.resourceId)
                holder.itemView.setOnClickListener {
                    onStickerClick(item.sticker)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size
}

// Category Adapter (unchanged)
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

        holder.itemView.isSelected = category == selectedCategory
        holder.itemView.alpha = if (category == selectedCategory) 1.0f else 0.6f

        holder.itemView.setOnClickListener {
            if (selectedCategory != category) {
                val oldSelectedIndex = categories.indexOf(selectedCategory)
                selectedCategory = category
                onCategoryClick(category)
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

// Helper class for inserting stickers into EditText
object StickerHelper {

    fun insertStickerIntoEditText(editText: EditText, sticker: StickerItem, context: Context) {
        val drawable = context.resources.getDrawable(sticker.resourceId, null)

        // Scale the drawable to match text size
        val textSize = editText.textSize.toInt()
        val stickerSize = (textSize * 1.5).toInt() // 1.5x text size
        drawable.setBounds(0, 0, stickerSize, stickerSize)

        // Create ImageSpan with placeholder text (sticker filename)
        val span = ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM)
        val placeholder = "[${sticker.fileName}]"
        val spannable = SpannableString(placeholder)
        spannable.setSpan(span, 0, placeholder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Insert at cursor position
        val start = editText.selectionStart
        val end = editText.selectionEnd
        editText.text.replace(Math.min(start, end), Math.max(start, end), spannable)
    }

    fun loadStickerFromAssets(context: Context, fileName: String): Drawable {
        val inputStream = context.assets.open("stickers/$fileName")
        val bitmap = BitmapFactory.decodeStream(inputStream)
        return BitmapDrawable(context.resources, bitmap)
    }
}

// Sticker list - YOU NEED TO ADD YOUR STICKERS HERE
/*
object StickerItemList {
    val allStickers = mutableListOf(
        // Example Tibetan stickers - replace with your actual drawable resources
        StickerItem(R.drawable.butter_lamp, "tibetan_1.png", EmojiCategory.TIBETAN, listOf("tibetan", "བོད", "Butter"," lamp")),
        StickerItem(R.drawable.mandala, "tibetan_2.png", EmojiCategory.TIBETAN, listOf("tibetan", "བོད", "Mandala")),
        StickerItem(R.drawable.meditation_cushion, "tibetan_3.png", EmojiCategory.TIBETAN, listOf("tibetan", "བོད", "Meditation"," cushion")),
        // Add more stickers here...
    )
}*/
