package com.voicerecorder.app

data class QAHistory(
    val id: String,
    val imagePath: String,
    val firstQuestion: String,
    val firstAnswer: String,
    val date: String,
    val messageCount: Int
)
