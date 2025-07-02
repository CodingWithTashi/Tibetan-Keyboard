package com.kharagedition.tibetankeyboard.chat

import java.util.Date

data class ChatMessage(
    val id: String = System.currentTimeMillis().toString(),
    val message: String,
    val isFromUser: Boolean,
    val timestamp: Date = Date()
)