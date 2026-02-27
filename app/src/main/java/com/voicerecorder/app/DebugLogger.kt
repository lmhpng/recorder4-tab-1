package com.aiassistant

object DebugLogger {
    private val logs = mutableListOf<String>()

    fun log(tag: String, msg: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        logs.add("[$time][$tag] $msg")
        if (logs.size > 100) logs.removeAt(0)
        android.util.Log.d(tag, msg)
    }

    fun getLogs(): String = logs.joinToString("\n")
}
