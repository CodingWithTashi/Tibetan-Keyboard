package com.kharagedition.tibetankeyboard.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.kharagedition.tibetankeyboard.R

data class ChatConversation(
    val conversationId: String,
    val title: String,
    val mode: String = "general",
    val lastMessage: String,
    val timestamp: Long,
    val messageCount: Int,
    val favorite: Boolean = false,
    val archived: Boolean = false
)

/**
 * Bottom Sheet Dialog for Chat History
 */
class ChatHistoryBottomSheet(
    private val onConversationSelected: (ChatConversation) -> Unit
) : DialogFragment() {

    private lateinit var searchView: SearchView
    private lateinit var recyclerView: RecyclerView
    private lateinit var historyAdapter: ChatHistoryAdapter
    private var conversations: List<ChatConversation> = emptyList()
    private var filteredConversations: List<ChatConversation> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_chat_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchView = view.findViewById(R.id.chat_history_search)
        recyclerView = view.findViewById(R.id.chat_history_list)
        val btnNewChat = view.findViewById<MaterialButton>(R.id.btn_new_chat)

        // Setup RecyclerView
        historyAdapter = ChatHistoryAdapter { conversation ->
            onConversationSelected(conversation)
            dismiss()
        }
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = historyAdapter
        }

        // Setup search
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true

            override fun onQueryTextChange(newText: String?): Boolean {
                filterConversations(newText ?: "")
                return true
            }
        })

        // New chat button
        btnNewChat.setOnClickListener {
            dismiss()
        }

        // Load conversations (mock data for now)
        loadConversations()
    }

    private fun loadConversations() {
        // TODO: Load from Firestore
        conversations = listOf(
            ChatConversation(
                "conv_1",
                "དབོད་ཡིག་གི་བསྒྲུབས་པ།",
                "tutoring",
                "ཁྱེད་ཀིས་བོད་ཡིག་ག་ག་སྦེ་བསམ་གྲུབ།",
                System.currentTimeMillis() - 3600000,
                15,
                true
            ),
            ChatConversation(
                "conv_2",
                "རྒྱལ་སྤྱི་བོད་ཡིག།",
                "general",
                "རྒྱལ་སྤྱི་བོད་ཡིག་སྦེ་ལོ་མང་།",
                System.currentTimeMillis() - 7200000,
                8,
                false
            )
        )
        filteredConversations = conversations
        historyAdapter.submitList(conversations)
    }

    private fun filterConversations(query: String) {
        filteredConversations = if (query.isEmpty()) {
            conversations
        } else {
            conversations.filter { conv ->
                conv.title.contains(query, ignoreCase = true) ||
                conv.lastMessage.contains(query, ignoreCase = true)
            }
        }
        historyAdapter.submitList(filteredConversations)
    }

    companion object {
        fun newInstance(onSelected: (ChatConversation) -> Unit): ChatHistoryBottomSheet {
            return ChatHistoryBottomSheet(onSelected)
        }
    }
}
