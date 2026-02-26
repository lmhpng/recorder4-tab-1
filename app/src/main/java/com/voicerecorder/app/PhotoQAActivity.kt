package com.voicerecorder.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PhotoQAActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var btnHistory: LinearLayout
    private lateinit var layoutSelectImage: LinearLayout
    private lateinit var layoutImagePreview: androidx.cardview.widget.CardView
    private lateinit var ivSelectedImage: ImageView
    private lateinit var btnCamera: LinearLayout
    private lateinit var btnGallery: LinearLayout
    private lateinit var btnChangeImage: LinearLayout
    private lateinit var rvChat: RecyclerView
    private lateinit var etQuestion: EditText
    private lateinit var btnSend: LinearLayout

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter

    private var currentImageBase64: String? = null
    private var currentImageUri: Uri? = null
    private var cameraImageUri: Uri? = null
    private var isFirstQuestion = true

    private val REQUEST_CAMERA = 101
    private val REQUEST_GALLERY = 102
    private val REQUEST_CAMERA_PERMISSION = 103

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_qa)
        initViews()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        btnHistory = findViewById(R.id.btnHistory)
        layoutSelectImage = findViewById(R.id.layoutSelectImage)
        layoutImagePreview = findViewById(R.id.layoutImagePreview)
        ivSelectedImage = findViewById(R.id.ivSelectedImage)
        btnCamera = findViewById(R.id.btnCamera)
        btnGallery = findViewById(R.id.btnGallery)
        btnChangeImage = findViewById(R.id.btnChangeImage)
        rvChat = findViewById(R.id.rvChat)
        etQuestion = findViewById(R.id.etQuestion)
        btnSend = findViewById(R.id.btnSend)

        chatAdapter = ChatAdapter(messages)
        rvChat.layoutManager = LinearLayoutManager(this).also { it.stackFromEnd = true }
        rvChat.adapter = chatAdapter

        btnBack.setOnClickListener { finish() }
        btnCamera.setOnClickListener { requestCameraAndTakePhoto() }
        btnGallery.setOnClickListener { openGallery() }
        btnChangeImage.setOnClickListener { resetConversation() }
        btnHistory.setOnClickListener { showHistoryDialog() }
        btnSend.setOnClickListener { sendQuestion() }
    }

    private fun requestCameraAndTakePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        } else {
            takePhoto()
        }
    }

    private fun takePhoto() {
        val photoFile = File(externalCacheDir, "photo_${System.currentTimeMillis()}.jpg")
        cameraImageUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
        }
        startActivityForResult(intent, REQUEST_CAMERA)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_GALLERY)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            takePhoto()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return

        val uri = when (requestCode) {
            REQUEST_CAMERA -> cameraImageUri
            REQUEST_GALLERY -> data?.data
            else -> null
        } ?: return

        loadImage(uri)
    }

    private fun loadImage(uri: Uri) {
        try {
            currentImageUri = uri
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val bitmap = BitmapFactory.decodeStream(inputStream)
            ivSelectedImage.setImageBitmap(bitmap)

            // 压缩转Base64
            val compressed = Bitmap.createScaledBitmap(bitmap,
                minOf(bitmap.width, 1024),
                (minOf(bitmap.width, 1024).toFloat() / bitmap.width * bitmap.height).toInt(), true)
            val baos = ByteArrayOutputStream()
            compressed.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            currentImageBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

            // 切换显示
            layoutSelectImage.visibility = View.GONE
            layoutImagePreview.visibility = View.VISIBLE
            isFirstQuestion = true
            messages.clear()
            chatAdapter.notifyDataSetChanged()

            Toast.makeText(this, "图片已加载，请输入问题", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendQuestion() {
        val question = etQuestion.text.toString().trim()
        if (question.isEmpty()) {
            Toast.makeText(this, "请输入问题", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentImageBase64 == null) {
            Toast.makeText(this, "请先选择或拍摄图片", Toast.LENGTH_SHORT).show()
            return
        }

        // 添加用户消息
        messages.add(ChatMessage("user", question))
        chatAdapter.notifyItemInserted(messages.size - 1)
        rvChat.scrollToPosition(messages.size - 1)
        etQuestion.setText("")
        btnSend.isEnabled = false

        // 添加AI思考中占位
        val loadingIndex = messages.size
        messages.add(ChatMessage("assistant", "正在分析中..."))
        chatAdapter.notifyItemInserted(loadingIndex)
        rvChat.scrollToPosition(messages.size - 1)

        lifecycleScope.launch {
            try {
                // 构建对话历史（只传user/assistant交替）
                val history = if (isFirstQuestion) emptyList()
                else messages.dropLast(2).map { Pair(it.role, it.content) }

                val answer = IFlytekService.askAboutImage(
                    imageBase64 = currentImageBase64!!,
                    imageMimeType = "image/jpeg",
                    conversationHistory = history,
                    question = question
                )

                // 替换占位消息
                messages[loadingIndex] = ChatMessage("assistant", answer)
                chatAdapter.notifyItemChanged(loadingIndex)
                rvChat.scrollToPosition(messages.size - 1)
                isFirstQuestion = false

                // 保存历史（第一轮问答时保存）
                if (messages.size == 2) {
                    saveHistory(question, answer)
                }
            } catch (e: Exception) {
                messages[loadingIndex] = ChatMessage("assistant", "出错了：${e.message}")
                chatAdapter.notifyItemChanged(loadingIndex)
            } finally {
                btnSend.isEnabled = true
            }
        }
    }

    private fun resetConversation() {
        AlertDialog.Builder(this)
            .setTitle("更换图片")
            .setMessage("更换图片将清空当前对话，确定继续？")
            .setPositiveButton("确定") { _, _ ->
                layoutSelectImage.visibility = View.VISIBLE
                layoutImagePreview.visibility = View.GONE
                currentImageBase64 = null
                currentImageUri = null
                messages.clear()
                chatAdapter.notifyDataSetChanged()
                isFirstQuestion = true
            }
            .setNegativeButton("取消", null).show()
    }

    // 保存历史记录
    private fun saveHistory(question: String, answer: String) {
        val prefs = getSharedPreferences("qa_history", MODE_PRIVATE)
        val historyJson = prefs.getString("history_list", "[]")
        val historyArray = JSONArray(historyJson)

        val item = JSONObject().apply {
            put("id", System.currentTimeMillis().toString())
            put("imagePath", currentImageUri?.toString() ?: "")
            put("firstQuestion", question)
            put("firstAnswer", answer)
            put("date", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
            put("messageCount", messages.size)
        }
        historyArray.put(item)

        // 最多保存50条
        val trimmed = JSONArray()
        val start = maxOf(0, historyArray.length() - 50)
        for (i in start until historyArray.length()) trimmed.put(historyArray.get(i))

        prefs.edit().putString("history_list", trimmed.toString()).apply()
    }

    private fun showHistoryDialog() {
        val prefs = getSharedPreferences("qa_history", MODE_PRIVATE)
        val historyJson = prefs.getString("history_list", "[]")
        val historyArray = JSONArray(historyJson)

        if (historyArray.length() == 0) {
            Toast.makeText(this, "暂无历史记录", Toast.LENGTH_SHORT).show()
            return
        }

        // 构建历史列表
        val items = Array(historyArray.length()) { i ->
            val item = historyArray.getJSONObject(historyArray.length() - 1 - i)
            "${item.optString("date")}  ${item.optString("firstQuestion")}"
        }

        AlertDialog.Builder(this)
            .setTitle("历史问答记录")
            .setItems(items) { _, index ->
                val item = historyArray.getJSONObject(historyArray.length() - 1 - index)
                AlertDialog.Builder(this)
                    .setTitle(item.optString("firstQuestion"))
                    .setMessage("🤖 AI回答：\n\n${item.optString("firstAnswer")}\n\n📅 ${item.optString("date")}")
                    .setPositiveButton("关闭", null)
                    .setNeutralButton("删除") { _, _ ->
                        // 删除这条记录
                        val newArray = JSONArray()
                        for (j in 0 until historyArray.length()) {
                            if (j != historyArray.length() - 1 - index) {
                                newArray.put(historyArray.get(j))
                            }
                        }
                        prefs.edit().putString("history_list", newArray.toString()).apply()
                        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
            .setNegativeButton("关闭", null)
            .setNeutralButton("清空全部") { _, _ ->
                prefs.edit().putString("history_list", "[]").apply()
                Toast.makeText(this, "已清空历史记录", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
