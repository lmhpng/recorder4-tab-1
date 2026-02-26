package com.voicerecorder.app

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object IFlytekService {

    private const val API_KEY = "sk-avlzbdvmsqmkcyezwhtvygllpkzbnjpcswbttswiishevoto"
    private const val TRANSCRIPTION_URL = "https://api.siliconflow.cn/v1/audio/transcriptions"
    private const val CHAT_URL = "https://api.siliconflow.cn/v1/chat/completions"
    private const val BOUNDARY = "----FormBoundary7MA4YWxkTrZu0gW"

    // ── 语音转文字 ──
    suspend fun transcribeAudio(audioFile: File): String = withContext(Dispatchers.IO) {
        val conn = URL(TRANSCRIPTION_URL).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $API_KEY")
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$BOUNDARY")
        conn.connectTimeout = 60000
        conn.readTimeout = 60000

        DataOutputStream(conn.outputStream).use { out ->
            out.writeBytes("--$BOUNDARY\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${audioFile.name}\"\r\n")
            out.writeBytes("Content-Type: audio/m4a\r\n\r\n")
            out.write(audioFile.readBytes())
            out.writeBytes("\r\n")
            out.writeBytes("--$BOUNDARY\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
            out.writeBytes("FunAudioLLM/SenseVoiceSmall\r\n")
            out.writeBytes("--$BOUNDARY--\r\n")
        }

        val code = conn.responseCode
        val response = if (code == 200) conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                       else conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText() ?: "HTTP $code"
        if (code != 200) throw Exception("识别失败：$response")
        JSONObject(response).optString("text", "").ifEmpty { "（识别结果为空）" }
    }

    // ── AI总结 ──
    suspend fun generateSummary(transcript: String): String = withContext(Dispatchers.IO) {
        callChatSimple(
            system = "你是专业的会议记录助手，对录音文字进行智能总结，包括：核心内容、关键要点、重要结论，用简洁清晰的中文输出。",
            user = "请对以下录音内容进行AI总结：\n\n$transcript",
            maxTokens = 2000
        )
    }

    // ── 智能重命名 ──
    suspend fun generateSmartName(transcript: String): String = withContext(Dispatchers.IO) {
        callChatSimple(
            system = "你是录音命名助手。根据录音内容生成一个简洁准确的名称，要求：5-15个汉字，不含标点，直接输出名称本身，不要任何解释。",
            user = "根据以下录音内容生成名称：\n\n$transcript",
            maxTokens = 30
        ).trim().replace(Regex("[「」《》【】\\[\\]""''。，！？、]"), "").take(20)
    }

    // ── 提取待办事项 ──
    suspend fun extractTodos(transcript: String): String = withContext(Dispatchers.IO) {
        callChatSimple(
            system = "你是待办事项提取助手。从录音内容中提取所有需要完成的任务、行动项、待办事项。每条待办用「• 」开头，一行一条，简洁清晰。如果没有待办事项，输出「暂未发现待办事项」。",
            user = "请从以下录音内容中提取待办事项：\n\n$transcript",
            maxTokens = 500
        )
    }

    // ── 纯文字AI对话（多轮） ──
    suspend fun chat(history: List<Pair<String, String>>, question: String): String = withContext(Dispatchers.IO) {
        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", "你是一个智能AI助手，能够回答各种问题，提供帮助和建议。用简洁友好的中文回复。")
        })
        history.forEach { (role, content) ->
            messages.put(JSONObject().apply { put("role", role); put("content", content) })
        }
        messages.put(JSONObject().apply { put("role", "user"); put("content", question) })

        val body = JSONObject().apply {
            put("model", "Qwen/Qwen3-8B")
            put("max_tokens", 1500)
            put("messages", messages)
        }
        callChat(body)
    }

    // ── 文档总结 ──
    suspend fun summarizeDocument(text: String): String = withContext(Dispatchers.IO) {
        callChatSimple(
            system = "你是文档分析专家。对用户粘贴的文字进行深度分析和总结，包括：核心主题、主要观点、关键信息、结论建议。输出结构清晰，重点突出。",
            user = "请对以下内容进行总结分析：\n\n$text",
            maxTokens = 2000
        )
    }

    // ── 图片问答（多轮对话） ──
    suspend fun askAboutImage(
        imageBase64: String,
        imageMimeType: String,
        conversationHistory: List<Pair<String, String>>,
        question: String
    ): String = withContext(Dispatchers.IO) {
        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", "你是一个智能图片分析助手，请根据用户提供的图片认真回答问题，回答要准确、详细、友好。")
        })

        val firstQuestion = if (conversationHistory.isNotEmpty()) conversationHistory.first().second else question
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply { put("url", "data:$imageMimeType;base64,$imageBase64") })
                })
                put(JSONObject().apply { put("type", "text"); put("text", firstQuestion) })
            })
        })

        if (conversationHistory.size > 1) {
            messages.put(JSONObject().apply { put("role", "assistant"); put("content", conversationHistory[1].second) })
            var i = 2
            while (i < conversationHistory.size) {
                val (role, content) = conversationHistory[i]
                messages.put(JSONObject().apply { put("role", role); put("content", content) })
                i++
            }
        }
        if (conversationHistory.isNotEmpty()) {
            messages.put(JSONObject().apply { put("role", "user"); put("content", question) })
        }

        val body = JSONObject().apply {
            put("model", "Qwen/Qwen2.5-VL-7B-Instruct")
            put("max_tokens", 1500)
            put("messages", messages)
        }
        callChat(body)
    }

    // ── 通用单轮对话 ──
    private fun callChatSimple(system: String, user: String, maxTokens: Int = 1000): String {
        val body = JSONObject().apply {
            put("model", "deepseek-ai/DeepSeek-V3")
            put("max_tokens", maxTokens)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", system) })
                put(JSONObject().apply { put("role", "user"); put("content", user) })
            })
        }
        return callChat(body)
    }

    private fun callChat(body: JSONObject): String {
        val conn = URL(CHAT_URL).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $API_KEY")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val response = if (code == 200) conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                       else conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText() ?: "HTTP $code"
        if (code != 200) throw Exception("请求失败：$response")

        return JSONObject(response)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content", "")
            ?.ifEmpty { "（AI没有返回内容）" }
            ?: "（解析失败）"
    }
}
