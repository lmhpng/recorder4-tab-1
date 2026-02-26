package com.voicerecorder.app

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var fabRecord: LinearLayout
    private lateinit var ivRecordIcon: ImageView
    private lateinit var ringOuter: View
    private lateinit var ringMid: View
    private lateinit var tvStatus: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvRecordingCount: TextView
    private lateinit var rvRecordings: RecyclerView
    private lateinit var tvEmpty: LinearLayout
    private lateinit var waveformView: WaveformView

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording = false
    private var currentFile: File? = null
    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerSeconds = 0
    private val recordingsList = mutableListOf<Recording>()
    private lateinit var adapter: RecordingAdapter
    private var pulseAnimator: AnimatorSet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        checkPermissions()
        loadRecordings()
    }

    private fun initViews() {
        fabRecord = findViewById(R.id.fabRecord)
        ivRecordIcon = findViewById(R.id.ivRecordIcon)
        ringOuter = findViewById(R.id.ringOuter)
        ringMid = findViewById(R.id.ringMid)
        tvStatus = findViewById(R.id.tvStatus)
        tvTimer = findViewById(R.id.tvTimer)
        tvRecordingCount = findViewById(R.id.tvRecordingCount)
        rvRecordings = findViewById(R.id.rvRecordings)
        tvEmpty = findViewById(R.id.tvEmpty)
        waveformView = findViewById(R.id.waveformView)

        adapter = RecordingAdapter(
            recordingsList,
            onPlay = { playRecording(it) },
            onDelete = { deleteRecording(it) },
            onTranscribe = { transcribeRecording(it) },
            onSummarize = { showSummary(it) },
            onLongPress = { showLongPressMenu(it) }
        )
        rvRecordings.layoutManager = LinearLayoutManager(this)
        rvRecordings.adapter = adapter

        fabRecord.setOnClickListener { if (isRecording) stopRecording() else startRecording() }

        findViewById<LinearLayout>(R.id.tabPhotoQA).setOnClickListener {
            startActivity(Intent(this, PhotoQAActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.tabAiAssistant).setOnClickListener {
            startActivity(Intent(this, AiAssistantActivity::class.java))
        }
    }

    // ── 长按菜单：重命名 + 提取待办 ──
    private fun showLongPressMenu(recording: Recording) {
        val options = arrayOf("✏️  智能重命名", "📋  提取待办事项", "📄  查看AI总结", "🔤  查看转写文字")
        AlertDialog.Builder(this)
            .setTitle(recording.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> smartRename(recording)
                    1 -> extractTodos(recording)
                    2 -> viewSummary(recording)
                    3 -> viewTranscript(recording)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun smartRename(recording: Recording) {
        if (recording.transcript.isNullOrEmpty()) {
            AlertDialog.Builder(this).setTitle("提示")
                .setMessage("智能重命名需要先转文字，是否现在转文字？")
                .setPositiveButton("去转文字") { _, _ -> transcribeRecording(recording) }
                .setNegativeButton("取消", null).show()
            return
        }
        val dlg = AlertDialog.Builder(this).setTitle("智能重命名").setMessage("AI正在生成名称...").setCancelable(false).create()
        dlg.show()
        lifecycleScope.launch {
            try {
                val newName = IFlytekService.generateSmartName(recording.transcript)
                dlg.dismiss()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("建议名称")
                    .setMessage("「$newName」\n\n是否使用此名称？")
                    .setPositiveButton("使用") { _, _ ->
                        val oldFile = File(recording.filePath)
                        val newFile = File(oldFile.parent, "$newName.m4a")
                        if (oldFile.renameTo(newFile)) {
                            // 迁移SharedPreferences
                            val prefs = getPrefs()
                            val transcript = prefs.getString("transcript_${recording.id}", null)
                            val summary = prefs.getString("summary_${recording.id}", null)
                            val todos = prefs.getString("todos_${recording.id}", null)
                            prefs.edit()
                                .remove("transcript_${recording.id}")
                                .remove("summary_${recording.id}")
                                .remove("todos_${recording.id}")
                                .putString("transcript_${newFile.name}", transcript)
                                .putString("summary_${newFile.name}", summary)
                                .putString("todos_${newFile.name}", todos)
                                .apply()
                            loadRecordings()
                            Toast.makeText(this@MainActivity, "已重命名为「$newName」", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "重命名失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("取消", null).show()
            } catch (e: Exception) {
                dlg.dismiss()
                Toast.makeText(this@MainActivity, "生成失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun extractTodos(recording: Recording) {
        if (recording.transcript.isNullOrEmpty()) {
            AlertDialog.Builder(this).setTitle("提示")
                .setMessage("提取待办需要先转文字，是否现在转文字？")
                .setPositiveButton("去转文字") { _, _ -> transcribeRecording(recording) }
                .setNegativeButton("取消", null).show()
            return
        }
        val dlg = AlertDialog.Builder(this).setTitle("提取待办事项").setMessage("AI正在提取...").setCancelable(false).create()
        dlg.show()
        lifecycleScope.launch {
            try {
                val todos = IFlytekService.extractTodos(recording.transcript)
                getPrefs().edit().putString("todos_${recording.id}", todos).apply()
                dlg.dismiss()
                loadRecordings()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("📋 待办事项")
                    .setMessage(todos)
                    .setPositiveButton("关闭", null)
                    .setNeutralButton("复制") { _, _ -> copyToClipboard(todos) }
                    .show()
            } catch (e: Exception) {
                dlg.dismiss()
                Toast.makeText(this@MainActivity, "提取失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun viewSummary(recording: Recording) {
        if (recording.summary.isNullOrEmpty()) {
            Toast.makeText(this, "暂无AI总结，请先点「AI总结」按钮", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this).setTitle("AI总结").setMessage(recording.summary)
            .setPositiveButton("关闭", null)
            .setNeutralButton("复制") { _, _ -> copyToClipboard(recording.summary) }.show()
    }

    private fun viewTranscript(recording: Recording) {
        if (recording.transcript.isNullOrEmpty()) {
            Toast.makeText(this, "暂无转写文字，请先点「转文字」按钮", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this).setTitle("转写文字").setMessage(recording.transcript)
            .setPositiveButton("关闭", null)
            .setNeutralButton("复制") { _, _ -> copyToClipboard(recording.transcript) }.show()
    }

    private fun startPulseAnimation() {
        val sXO = ObjectAnimator.ofFloat(ringOuter, "scaleX", 1f, 1.3f, 1f)
        val sYO = ObjectAnimator.ofFloat(ringOuter, "scaleY", 1f, 1.3f, 1f)
        val aO  = ObjectAnimator.ofFloat(ringOuter, "alpha", 0.7f, 0f, 0.7f)
        val sXM = ObjectAnimator.ofFloat(ringMid, "scaleX", 1f, 1.15f, 1f)
        val sYM = ObjectAnimator.ofFloat(ringMid, "scaleY", 1f, 1.15f, 1f)
        val aM  = ObjectAnimator.ofFloat(ringMid, "alpha", 0.9f, 0.4f, 0.9f)
        pulseAnimator = AnimatorSet().apply {
            playTogether(sXO, sYO, aO, sXM, sYM, aM)
            duration = 1200
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { if (isRecording) start() }
            })
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel(); pulseAnimator = null
        ringOuter.scaleX = 1f; ringOuter.scaleY = 1f; ringOuter.alpha = 1f
        ringMid.scaleX = 1f; ringMid.scaleY = 1f; ringMid.alpha = 1f
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
    }

    private fun getPrefs() = getSharedPreferences("recordings_meta", MODE_PRIVATE)
    private fun getRecordingsDir() = File(filesDir, "recordings").also { if (!it.exists()) it.mkdirs() }

    private fun loadRecordings() {
        recordingsList.clear()
        val prefs = getPrefs()
        getRecordingsDir().listFiles()
            ?.filter { it.extension == "m4a" }
            ?.sortedByDescending { it.lastModified() }
            ?.forEach { file ->
                recordingsList.add(Recording(
                    id = file.name, name = file.nameWithoutExtension,
                    filePath = file.absolutePath, duration = getDuration(file),
                    date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified())),
                    transcript = prefs.getString("transcript_${file.name}", null),
                    summary = prefs.getString("summary_${file.name}", null),
                    todos = prefs.getString("todos_${file.name}", null)
                ))
            }
        adapter.notifyDataSetChanged()
        tvEmpty.visibility = if (recordingsList.isEmpty()) View.VISIBLE else View.GONE
        tvRecordingCount.text = "${recordingsList.size} 条录音"
    }

    private fun getDuration(file: File): String {
        return try {
            val mp = MediaPlayer().apply { setDataSource(file.absolutePath); prepare() }
            val dur = mp.duration / 1000; mp.release()
            "%02d:%02d".format(dur / 60, dur % 60)
        } catch (e: Exception) { "00:00" }
    }

    private fun startRecording() {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        currentFile = File(getRecordingsDir(), "录音_$dateStr.m4a")
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setAudioEncodingBitRate(64000)
            setOutputFile(currentFile!!.absolutePath)
            prepare(); start()
        }
        isRecording = true
        ivRecordIcon.setImageResource(R.drawable.ic_stop)
        fabRecord.background = getDrawable(R.drawable.record_btn_recording)
        tvStatus.text = "● 正在录音..."
        waveformView.startAnimation()
        startPulseAnimation()
        timerSeconds = 0
        startTimer()
    }

    private fun stopRecording() {
        try { mediaRecorder?.apply { stop(); release() } } catch (e: Exception) {}
        mediaRecorder = null; isRecording = false
        timerHandler.removeCallbacksAndMessages(null)
        waveformView.stopAnimation(); stopPulseAnimation()
        ivRecordIcon.setImageResource(R.drawable.ic_mic)
        fabRecord.background = getDrawable(R.drawable.record_btn_main)
        tvStatus.text = "轻触下方按钮开始录音"
        tvTimer.text = "00:00"
        currentFile?.takeIf { it.exists() && it.length() > 0 }?.let {
            Toast.makeText(this, "录音已保存", Toast.LENGTH_SHORT).show()
            loadRecordings()
        }
    }

    private fun startTimer() {
        timerHandler.postDelayed(object : Runnable {
            override fun run() {
                if (!isRecording) return
                timerSeconds++
                tvTimer.text = "%02d:%02d".format(timerSeconds / 60, timerSeconds % 60)
                try { mediaRecorder?.let { waveformView.updateAmplitude(it.maxAmplitude) } } catch (e: Exception) {}
                timerHandler.postDelayed(this, 100)
            }
        }, 100)
    }

    private fun playRecording(recording: Recording) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(recording.filePath); prepare(); start()
            setOnCompletionListener { adapter.setPlaying(null) }
        }
        adapter.setPlaying(recording.id)
    }

    private fun deleteRecording(recording: Recording) {
        AlertDialog.Builder(this).setTitle("删除录音").setMessage("确定删除「${recording.name}」？")
            .setPositiveButton("删除") { _, _ ->
                mediaPlayer?.let { if (it.isPlaying) it.stop(); it.release() }; mediaPlayer = null
                File(recording.filePath).delete()
                getPrefs().edit()
                    .remove("transcript_${recording.id}")
                    .remove("summary_${recording.id}")
                    .remove("todos_${recording.id}")
                    .apply()
                loadRecordings()
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("取消", null).show()
    }

    private fun saveTranscript(recordingId: String, text: String) {
        getPrefs().edit().putString("transcript_$recordingId", text)
            .remove("summary_$recordingId").remove("todos_$recordingId").apply()
    }

    private fun transcribeRecording(recording: Recording) {
        val file = File(recording.filePath)
        if (!file.exists()) { Toast.makeText(this, "录音文件不存在", Toast.LENGTH_SHORT).show(); return }
        val dlg = AlertDialog.Builder(this).setTitle("语音转文字").setMessage("正在识别中，请稍候...").setCancelable(false).create()
        dlg.show()
        lifecycleScope.launch {
            try {
                val t = IFlytekService.transcribeAudio(file); dlg.dismiss()
                saveTranscript(recording.id, t); loadRecordings()
                AlertDialog.Builder(this@MainActivity).setTitle("转文字完成").setMessage(t)
                    .setPositiveButton("立即AI总结") { _, _ ->
                        recordingsList.find { it.id == recording.id }?.let { generateAiSummary(it) }
                    }
                    .setNeutralButton("复制") { _, _ -> copyToClipboard(t) }
                    .setNegativeButton("关闭", null).show()
            } catch (e: Exception) {
                dlg.dismiss()
                AlertDialog.Builder(this@MainActivity).setTitle("识别失败").setMessage("原因：${e.message}")
                    .setPositiveButton("手动输入") { _, _ -> showManualInputDialog(recording) }
                    .setNegativeButton("关闭", null).show()
            }
        }
    }

    private fun showManualInputDialog(recording: Recording) {
        val et = EditText(this).apply { hint = "输入文字内容..."; minLines = 4; maxLines = 10; setPadding(40,20,40,20); setText(recording.transcript ?: "") }
        AlertDialog.Builder(this).setTitle("手动输入文字").setView(et)
            .setPositiveButton("保存") { _, _ ->
                val t = et.text.toString().trim()
                if (t.isNotEmpty()) { saveTranscript(recording.id, t); loadRecordings(); Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show() }
            }.setNegativeButton("取消", null).show()
    }

    private fun showSummary(recording: Recording) {
        val cur = recordingsList.find { it.id == recording.id } ?: recording
        if (cur.transcript.isNullOrEmpty()) {
            AlertDialog.Builder(this).setTitle("提示").setMessage("请先转文字，再生成AI总结。")
                .setPositiveButton("去转文字") { _, _ -> transcribeRecording(recording) }
                .setNegativeButton("取消", null).show(); return
        }
        generateAiSummary(cur)
    }

    private fun generateAiSummary(recording: Recording) {
        val transcript = recording.transcript ?: return
        val dlg = AlertDialog.Builder(this).setTitle("AI总结生成中").setMessage("正在分析内容，请稍候...").setCancelable(false).create()
        dlg.show()
        lifecycleScope.launch {
            try {
                val summary = IFlytekService.generateSummary(transcript)
                getPrefs().edit().putString("summary_${recording.id}", summary).apply()
                dlg.dismiss(); loadRecordings()
                AlertDialog.Builder(this@MainActivity).setTitle("AI总结").setMessage(summary)
                    .setPositiveButton("关闭", null)
                    .setNeutralButton("复制") { _, _ -> copyToClipboard(summary) }.show()
            } catch (e: Exception) {
                dlg.dismiss(); Toast.makeText(this@MainActivity, "总结失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("text", text))
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        if (isRecording) stopRecording()
    }
}
