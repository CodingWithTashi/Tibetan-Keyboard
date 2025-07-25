package com.kharagedition.tibetankeyboard.chat
import android.content.ClipData
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kharagedition.tibetankeyboard.R
import java.text.SimpleDateFormat
import java.util.Locale
import android.content.ClipboardManager
class ChatAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_ASSISTANT = 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_user, parent, false)
                UserMessageViewHolder(view)
            }
            VIEW_TYPE_ASSISTANT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_assistant, parent, false)
                AssistantMessageViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)

        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is AssistantMessageViewHolder -> holder.bind(message)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isFromUser) VIEW_TYPE_USER else VIEW_TYPE_ASSISTANT
    }

    class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewMessage: TextView = itemView.findViewById(R.id.textViewUserMessage)
        private val textViewTimestamp: TextView = itemView.findViewById(R.id.textViewTimestamp)
        private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        fun bind(message: ChatMessage) {
            textViewMessage.setTextIsSelectable(true)
            textViewMessage.text = message.message
            textViewTimestamp.text = timeFormat.format(message.timestamp)
        }
    }

    class AssistantMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewMessage: TextView = itemView.findViewById(R.id.textViewAssistantMessage)
        private val textViewTimestamp: TextView = itemView.findViewById(R.id.textViewTimestamp)
        private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        private  val clipboardImg : ImageView = itemView.findViewById(R.id.clipboardIcon)
        fun bind(message: ChatMessage) {
            textViewMessage.setTextIsSelectable(true)
            textViewMessage.text = message.message
            textViewTimestamp.text = timeFormat.format(message.timestamp)
            clipboardImg.setOnClickListener {
                val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("message", message.message)
                clipboard.setPrimaryClip(clip)


            }
        }
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}