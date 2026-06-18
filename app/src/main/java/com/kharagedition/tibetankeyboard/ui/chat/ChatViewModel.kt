package com.kharagedition.tibetankeyboard.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.data.model.ChatMessage
import com.kharagedition.tibetankeyboard.data.repository.ChatRepository
import com.kharagedition.tibetankeyboard.ui.settings.SettingsPrefs
import kotlinx.coroutines.launch

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ChatRepository()

    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /** Currently selected Claude model (persisted in the shared prefs). */
    private val _model = MutableLiveData(SettingsPrefs.readModel(app))
    val model: LiveData<String> = _model

    fun setModel(value: String) {
        SettingsPrefs.putString(getApplication(), SettingsPrefs.KEY_AI_MODEL, value)
        _model.value = value
        AppAnalytics.logChatModelChanged(value)
    }

    fun sendMessage(messageText: String, userId: String) {
        if (messageText.isBlank()) return

        val userMessage = ChatMessage(
            message = messageText,
            isFromUser = true
        )

        // Add user message to the list
        val currentList = _messages.value.orEmpty().toMutableList()
        currentList.add(userMessage)
        _messages.value = currentList

        // Set loading state to true when waiting for response
        _isLoading.value = true

        val model = _model.value ?: SettingsPrefs.DEFAULT_MODEL
        AppAnalytics.logChatMessageSent(model)

        // Start a coroutine to fetch the AI response
        viewModelScope.launch {
            try {
                val response = repository.sendMessage(
                    messageText,
                    userId,
                    model
                )

                // Add assistant message to the list
                val updatedList = _messages.value.orEmpty().toMutableList()
                updatedList.add(
                    ChatMessage(
                        message = response,
                        isFromUser = false
                    )
                )
                _messages.value = updatedList
                AppAnalytics.logChatResponseReceived(model, success = true)

            } catch (e: Exception) {
                AppAnalytics.logChatResponseReceived(model, success = false)
                // In case of an error, add a predefined error message
                val updatedList = _messages.value.orEmpty().toMutableList()
                updatedList.add(
                    ChatMessage(
                        message = "དགོངས་དག། ཕྱི་ཕྱོགས་དང་འབྲེལ་བའི་དཀའ་ངལ་ཞིག་འཕྲད་སོང་། ཡང་བསྐྱར་འབད་བརྩོན་གནང་རོགས།",
                        isFromUser = false
                    )
                )
                _messages.value = updatedList
            } finally {
                // Set loading to false once the response is received
                _isLoading.value = false
            }
        }
    }

    // Add initial welcome message
    fun addWelcomeMessage() {
        if (_messages.value.isNullOrEmpty()) {
            val welcomeMessage = ChatMessage(
                message = "བཀྲ་ཤིས་བདེ་ལེགས། དེ་རིང་ངས་ཁྱེད་རང་ལ་ག་རེ་རོགས་བྱེད་ཐུབ།",
                isFromUser = false
            )
            _messages.value = listOf(welcomeMessage)
        }
    }

    fun clearMessages() {
        viewModelScope.launch {
            repository.resetChat()
            _messages.value = emptyList()
            AppAnalytics.logChatCleared()
        }
    }
}
