package com.kharagedition.tibetankeyboard.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
class ChatViewModel : ViewModel() {

    private val repository = ChatRepository()

    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun sendMessage(messageText: String) {
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

        // Start a coroutine to simulate an AI response
        viewModelScope.launch {
            try {
                // Simulate an AI response
                val response = repository.getResponse(messageText)

                // Add assistant message to the list
                val updatedList = _messages.value.orEmpty().toMutableList()
                updatedList.add(
                    ChatMessage(
                        message = response,
                        isFromUser = false
                    )
                )
                _messages.value = updatedList

            } catch (e: Exception) {
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
        _messages.value = emptyList()
    }
}
