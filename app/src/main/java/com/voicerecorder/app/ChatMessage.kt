package com.voicerecorder.app

data class ChatMessage(
    val role: String,   // "user" or "assistant"
    val content: String
)
