package com.voicerecorder.app

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class AiAssistantActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var btnClearChat: LinearLayout
    private lateinit var tabChat: TextView
    private lateinit var tabDocSummary: TextView
    private lateinit var pageChat: LinearLayout
    private lateinit var pageDocSummary: LinearLayout

    // 对话
    private lateinit var rvChat: RecyclerView
    private lateinit var etChatInput: EditText
    private lateinit var btnChatSend: LinearLayout
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter
    private val chatHistory = mutableListOf<Pair<String, String>>()

    // 文档总结
    private lateinit var etDocInput: EditText
    private lateinit var tvCharCount: TextView
    private lateinit var scrollResult: NestedScrollView
    private lateinit var tvDocResult: TextView
    private lateinit var btnSummarizeDoc: LinearLayout
    private lateinit var btnCopyResult: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_assistant)
        initViews()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        btnClearChat = findViewById(R.id.btnClearChat)
        tabChat = findViewById(R.id.tabChat)
        tabDocSummary = findViewById(R.id.tabDocSummary)
        pageChat = findViewById(R.id.pageChat)
        pageDocSummary = findViewById(R.id.pageDocSummary)
        rvChat = findViewById(R.id.rvChat)
        etChatInput = findViewById(R.id.etChatInput)
        btnChatSend = findViewById(R.id.btnChatSend)
        etDocInput = findViewById(R.id.etDocInput)
        tvCharCount = findViewById(R.id.tvCharCount)
        scrollResult = findViewById(R.id.scrollResult)
        tvDocResult = findViewById(R.id.tvDocResult)
        btnSummarizeDoc = findViewById(R.id.btnSummarizeDoc)
        btnCopyResult = findViewById(R.id.btnCopyResult)

        chatAdapter = ChatAdapter(chatMessages)
        rvChat.layoutManager = LinearLayoutManager(this).also { it.stackFromEnd = true }
        rvChat.adapter = chatAdapter

        // 默认欢迎消息
        chatMessages.add(ChatMessage("assistant", "你好！我是AI助手，可以回答你的各种问题，随时提问吧 😊"))
        chatAdapter.notifyItemInserted(0)

        btnBack.setOnClickListener { finish() }

        btnClearChat.setOnClickListener {
            chatMessages.clear()
            chatHistory.clear()
            chatMessages.add(ChatMessage("assistant", "对话已清空，随时开始新的问题 😊"))
            chatAdapter.notifyDataSetChanged()
        }

        // Tab切换
        tabChat.setOnClickListener { switchTab(true) }
        tabDocSummary.setOnClickListener { switchTab(false) }

        // 发送消息
        btnChatSend.setOnClickListener { sendChatMessage() }

        // 字数统计
        etDocInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                tvCharCount.text = "${s?.length ?: 0} 字"
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // 文档总结
        btnSummarizeDoc.setOnClickListener { summarizeDocument() }
        btnCopyResult.setOnClickListener {
            val text = tvDocResult.text.toString()
            val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("result", text))
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchTab(isChatTab: Boolean) {
        if (isChatTab) {
            pageChat.visibility = View.VISIBLE
            pageDocSummary.visibility = View.GONE
            tabChat.setBackgroundResource(R.drawable.btn_func_bg)
            tabChat.setTextColor(0xFFFFFFFF.toInt())
            tabDocSummary.setBackgroundResource(0)
            tabDocSummary.setTextColor(0xCCFFFFFF.toInt())
        } else {
            pageChat.visibility = View.GONE
            pageDocSummary.visibility = View.VISIBLE
            tabDocSummary.setBackgroundResource(R.drawable.btn_func_bg)
            tabDocSummary.setTextColor(0xFFFFFFFF.toInt())
            tabChat.setBackgroundResource(0)
            tabChat.setTextColor(0xCCFFFFFF.toInt())
        }
    }

    private fun sendChatMessage() {
        val question = etChatInput.text.toString().trim()
        if (question.isEmpty()) return

        chatMessages.add(ChatMessage("user", question))
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        rvChat.scrollToPosition(chatMessages.size - 1)
        etChatInput.setText("")
        btnChatSend.isEnabled = false

        val loadingIndex = chatMessages.size
        chatMessages.add(ChatMessage("assistant", "思考中..."))
        chatAdapter.notifyItemInserted(loadingIndex)
        rvChat.scrollToPosition(chatMessages.size - 1)

        lifecycleScope.launch {
            try {
                val answer = IFlytekService.chat(chatHistory.toList(), question)
                chatMessages[loadingIndex] = ChatMessage("assistant", answer)
                chatAdapter.notifyItemChanged(loadingIndex)
                rvChat.scrollToPosition(chatMessages.size - 1)
                // 保存历史
                chatHistory.add(Pair("user", question))
                chatHistory.add(Pair("assistant", answer))
                // 最多保留20轮
                if (chatHistory.size > 40) {
                    chatHistory.removeAt(0); chatHistory.removeAt(0)
                }
            } catch (e: Exception) {
                chatMessages[loadingIndex] = ChatMessage("assistant", "出错了：${e.message}")
                chatAdapter.notifyItemChanged(loadingIndex)
            } finally {
                btnChatSend.isEnabled = true
            }
        }
    }

    private fun summarizeDocument() {
        val text = etDocInput.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "请先粘贴需要总结的文字", Toast.LENGTH_SHORT).show()
            return
        }
        if (text.length < 50) {
            Toast.makeText(this, "文字内容太短，请粘贴更多内容", Toast.LENGTH_SHORT).show()
            return
        }

        btnSummarizeDoc.isEnabled = false
        scrollResult.visibility = View.GONE
        btnCopyResult.visibility = View.GONE
        tvDocResult.text = "正在分析中..."
        scrollResult.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val result = IFlytekService.summarizeDocument(text)
                tvDocResult.text = result
                btnCopyResult.visibility = View.VISIBLE
            } catch (e: Exception) {
                tvDocResult.text = "总结失败：${e.message}"
            } finally {
                btnSummarizeDoc.isEnabled = true
            }
        }
    }
}
