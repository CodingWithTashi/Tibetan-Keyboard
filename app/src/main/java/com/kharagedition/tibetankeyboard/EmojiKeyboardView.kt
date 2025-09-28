package com.kharagedition.tibetankeyboard

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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

    private lateinit var emojiAdapter: EmojiAdapter
    private lateinit var categoryAdapter: EmojiCategoryAdapter
    private var emojiClickListener: ((String) -> Unit)? = null
    private var backClickListener: (() -> Unit)? = null

    private val allEmojis = mutableListOf<EmojiItem>()
    private val filteredEmojis = mutableListOf<EmojiItem>()

    init {
        orientation = VERTICAL
        setupView()
        initializeEmojis()
        setupRecyclerViews()
        setupSearch()
        setupBackButton()
    }

    private fun setupView() {
        LayoutInflater.from(context).inflate(R.layout.emoji_keyboard_layout, this, true)

        searchEditText = findViewById(R.id.emoji_search)
        emojiRecyclerView = findViewById(R.id.emoji_recycler_view)
        categoryRecyclerView = findViewById(R.id.category_recycler_view)
        backButton = findViewById(R.id.emoji_back_button)
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
            filterByCategory(category)
        }
        categoryRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        categoryRecyclerView.adapter = categoryAdapter

        // Show all emojis initially
        filterByCategory(EmojiCategory.SMILEYS)
    }

    private fun setupSearch() {
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
            backClickListener?.invoke()
        }
    }

    private fun initializeEmojis() {
        allEmojis.clear()

        // Smileys & People
        val smileys = listOf(
            "😀", "😃", "😄", "😁", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘",
            "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏", "😒",
            "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡",
            "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗", "🤔", "🤭", "🤫", "🤥", "😶",
            "😐", "😑", "😬", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴",
            "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈", "👿", "👹", "👺", "🤡", "💩", "👻", "💀"
        )

        // Animals & Nature
        val animals = listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐽", "🐸", "🐵",
            "🙈", "🙉", "🙊", "🐒", "🐔", "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗",
            "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞", "🐜", "🦟", "🦗", "🕷️", "🦂", "🐢", "🐍", "🦎", "🦖",
            "🦕", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋", "🦈", "🐊", "🐅", "🐆",
            "🦓", "🦍", "🦧", "🐘", "🦛", "🦏", "🐪", "🐫", "🦒", "🦘", "🐃", "🐂", "🐄", "🐎", "🐖", "🐏"
        )

        // Food & Drink
        val food = listOf(
            "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝",
            "🍅", "🍆", "🥑", "🥦", "🥬", "🥒", "🌶️", "🫑", "🌽", "🥕", "🫒", "🧄", "🧅", "🥔", "🍠", "🥐",
            "🥯", "🍞", "🥖", "🥨", "🧀", "🥚", "🍳", "🧈", "🥞", "🧇", "🥓", "🥩", "🍗", "🍖", "🦴", "🌭",
            "🍔", "🍟", "🍕", "🫓", "🥪", "🥙", "🧆", "🌮", "🌯", "🫔", "🥗", "🥘", "🫕", "🍝", "🍜", "🍲",
            "🍛", "🍣", "🍱", "🥟", "🦪", "🍤", "🍙", "🍚", "🍘", "🍥", "🥠", "🥮", "🍢", "🍡", "🍧", "🍨"
        )

        // Activity & Sports
        val activities = listOf(
            "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱", "🪀", "🏓", "🏸", "🏒", "🏑", "🥍",
            "🏏", "🪃", "🥅", "⛳", "🪁", "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹", "🛷", "⛸️", "🥌", "🎿",
            "⛷️", "🏂", "🪂", "🏋️", "🤼", "🤸", "⛹️", "🤺", "🤾", "🏌️", "🏇", "🧘", "🏄", "🏊", "🤽", "🚣",
            "🧗", "🚵", "🚴", "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️", "🏵️", "🎗️", "🎫", "🎟️", "🎪", "🤹", "🎭"
        )

        // Travel & Places
        val travel = listOf(
            "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🏍️", "🛵",
            "🚲", "🛴", "🛺", "🚨", "🚔", "🚍", "🚘", "🚖", "🚡", "🚠", "🚟", "🚃", "🚋", "🚞", "🚝", "🚄",
            "🚅", "🚈", "🚂", "🚆", "🚇", "🚊", "🚉", "✈️", "🛫", "🛬", "🛩️", "💺", "🛰️", "🚀", "🛸", "🚁",
            "🛶", "⛵", "🚤", "🛥️", "🛳️", "⛴️", "🚢", "⚓", "🪝", "⛽", "🚧", "🚦", "🚥", "🗺️", "🗿", "🗽"
        )

        // Objects & Symbols
        val objects = listOf(
            "⌚", "📱", "📲", "💻", "⌨️", "🖥️", "🖨️", "🖱️", "🖲️", "🕹️", "🗜️", "💽", "💾", "💿", "📀", "📼",
            "📷", "📸", "📹", "🎥", "📽️", "🎞️", "📞", "☎️", "📟", "📠", "📺", "📻", "🎙️", "🎚️", "🎛️", "🧭",
            "⏱️", "⏲️", "⏰", "🕰️", "⌛", "⏳", "📡", "🔋", "🔌", "💡", "🔦", "🕯️", "🪔", "🧯", "🛢️", "💸",
            "💵", "💴", "💶", "💷", "🪙", "💰", "💳", "💎", "⚖️", "🪜", "🧰", "🔧", "🔨", "⚒️", "🛠️", "⛏️"
        )

        // Add all emojis with their categories and search tags
        addEmojiItems(smileys, EmojiCategory.SMILEYS, listOf("smile", "happy", "sad", "face", "emotion"))
        addEmojiItems(animals, EmojiCategory.ANIMALS, listOf("animal", "pet", "zoo", "wild", "nature"))
        addEmojiItems(food, EmojiCategory.FOOD, listOf("food", "eat", "drink", "fruit", "vegetable", "meal"))
        addEmojiItems(activities, EmojiCategory.ACTIVITIES, listOf("sport", "game", "play", "activity", "ball"))
        addEmojiItems(travel, EmojiCategory.TRAVEL, listOf("car", "travel", "transport", "plane", "vehicle"))
        addEmojiItems(objects, EmojiCategory.OBJECTS, listOf("object", "tool", "tech", "phone", "computer"))
    }

    private fun addEmojiItems(emojis: List<String>, category: EmojiCategory, tags: List<String>) {
        emojis.forEach { emoji ->
            allEmojis.add(EmojiItem(emoji, category, tags))
        }
    }

    private fun filterByCategory(category: EmojiCategory) {
        filteredEmojis.clear()
        if (category == EmojiCategory.ALL) {
            filteredEmojis.addAll(allEmojis)
        } else {
            filteredEmojis.addAll(allEmojis.filter { it.category == category })
        }
        emojiAdapter.notifyDataSetChanged()
        categoryAdapter.setSelectedCategory(category)
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
}

// Data classes
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
            val oldSelected = selectedCategory
            selectedCategory = category
            onCategoryClick(category)
            // Notify changes for old and new selection
            notifyItemChanged(categories.indexOf(oldSelected))
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = categories.size

    fun setSelectedCategory(category: EmojiCategory) {
        val oldSelected = selectedCategory
        selectedCategory = category
        notifyItemChanged(categories.indexOf(oldSelected))
        notifyItemChanged(categories.indexOf(category))
    }
}