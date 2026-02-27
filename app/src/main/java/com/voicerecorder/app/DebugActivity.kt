package com.aiassistant

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.aiassistant.databinding.ActivityTestBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DebugActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTestBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "调试日志"

        binding.btnSend.text = "手动测试转发"
        binding.etInput.hint = "输入主手机IP测试连接"

        val prefs = getSharedPreferences("sms_forward", Context.MODE_PRIVATE)
        val ip = prefs.getString("main_phone_ip", "") ?: ""
        binding.etInput.setText(ip)

        // 显示当前配置
        binding.tvResult.text = """
当前配置：
主手机IP：$ip
转发开关：${prefs.getBoolean("forward_enabled", false)}
是主手机：${prefs.getBoolean("is_main_phone", false)}
通知监听权限：${SmsNotificationListener.isEnabled(this)}

点击「手动测试转发」测试网络是否通
        """.trimIndent()

        binding.btnTest1.text = "测试连接"
        binding.btnTest2.text = "模拟验证码"
        binding.btnTest3.text = "刷新状态"
        binding.btnTest4.text = "查看日志"

        binding.btnTest1.setOnClickListener {
            val testIp = binding.etInput.text.toString().trim()
            binding.tvResult.append("\n\n正在测试连接 $testIp:8888 ...")
            CoroutineScope(Dispatchers.IO).launch {
                val success = SmsForwarder.forward(testIp, "测试", "这是一条测试消息，验证码：123456")
                runOnUiThread {
                    binding.tvResult.append(if (success) "\n连接成功！主手机应该收到通知了" else "\n连接失败！检查IP是否正确，两台手机是否在同一WiFi")
                }
            }
        }

        binding.btnTest2.setOnClickListener {
            val testIp = binding.etInput.text.toString().trim()
            binding.tvResult.append("\n\n模拟发送验证码...")
            CoroutineScope(Dispatchers.IO).launch {
                val success = SmsForwarder.forward(testIp, "10086", "您的验证码是：888888，5分钟内有效")
                runOnUiThread {
                    binding.tvResult.append(if (success) "\n发送成功！查看主手机通知" else "\n发送失败！网络不通")
                }
            }
        }

        binding.btnTest3.setOnClickListener {
            val p = getSharedPreferences("sms_forward", Context.MODE_PRIVATE)
            binding.tvResult.text = """
当前配置：
主手机IP：${p.getString("main_phone_ip", "")}
转发开关：${p.getBoolean("forward_enabled", false)}
是主手机：${p.getBoolean("is_main_phone", false)}
通知监听权限：${SmsNotificationListener.isEnabled(this)}
            """.trimIndent()
        }

        binding.btnTest4.setOnClickListener {
            val log = DebugLogger.getLogs()
            binding.tvResult.text = if (log.isEmpty()) "暂无日志" else log
        }
    }
}
