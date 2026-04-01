package com.kharagedition.tibetankeyboard.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.kharagedition.tibetankeyboard.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter for displaying chat conversation history
 */
class ChatHistoryAdapter(
    private val onConversationClick: (ChatConversation) -> Unit
) : ListAdapter<ChatConversation, ChatHistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_history, parent, false)
        return ViewHolder(view as MaterialCardView, onConversationClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    class ViewHolder(
        private val cardView: MaterialCardView,
        private val onConversationClick: (ChatConversation) -> Unit
    ) : RecyclerView.ViewHolder(cardView) {

        private val titleText: TextView = cardView.findViewById(R.id.conversation_title)
        private val previewText: TextView = cardView.findViewById(R.id.conversation_preview)
        private val timeText: TextView = cardView.findViewById(R.id.conversation_time)
        private val modeChip: com.google.android.material.chip.Chip = cardView.findViewById(R.id.mode_chip)
        private val messageCount: TextView = cardView.findViewById(R.id.message_count)
        private val btnMore: ImageButton = cardView.findViewById(R.id.btn_conversation_more)
        private val starIcon: ImageButton = cardView.findViewById(R.id.btn_favorite)

        fun bind(conversation: ChatConversation, position: Int) {
            titleText.text = conversation.title
            previewText.text = conversation.lastMessage.take(80)
            messageCount.text = "${conversation.messageCount} messages"

            // Set time
            val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            timeText.text = dateFormat.format(Date(conversation.timestamp))

            // Set mode chip
            modeChip.text = when (conversation.mode) {
                "tutoring" -> "🎓 Tutoring"
                "translation" -> "🔄 Translation"
                else -> "💬 Chat"
            }

            modeChip.setChipBackgroundColorResource(
                when (conversation.mode) {
                    "tutoring" -> R.color.warning_card_bg
                    "translation" -> R.color.success_card_bg
                    else -> R.color.error_card_bg
                }
            )

            // Favorite button
            updateFavoriteButton()
            starIcon.setOnClickListener {
                // Toggle favorite
            }

            // Click listener
            cardView.setOnClickListener {
                onConversationClick(conversation)
            }

            // Animate entry
            cardView.alpha = 0f
            cardView.translationX = 50f
            cardView.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(300)
                .setStartDelay(position * 50L)
                .start()

            // More options
            btnMore.setOnClickListener {
                showConversationOptions(conversation)
            }
        }

        private fun updateFavoriteButton() {
            // Update star icon based on favorite status
        }

        private fun showConversationOptions(conversation: ChatConversation) {
            // Show popup menu with options: Edit, Delete, Export, Archive
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ChatConversation>() {
        override fun areItemsTheSame(oldItem: ChatConversation, newItem: ChatConversation): Boolean =
            oldItem.conversationId == newItem.conversationId

        override fun areContentsTheSame(oldItem: ChatConversation, newItem: ChatConversation): Boolean =
            oldItem == newItem
    }
}
