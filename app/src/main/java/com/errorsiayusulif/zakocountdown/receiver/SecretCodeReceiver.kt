// file: app/src/main/java/com/errorsiayusulif/zakocountdown/receiver/SecretCodeReceiver.kt
package com.errorsiayusulif.zakocountdown.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.errorsiayusulif.zakocountdown.MainActivity

class SecretCodeReceiver : BroadcastReceiver() {

    companion object {
        const val NAVIGATE_TO_DEV_OPTIONS = "navigate_to_dev_options"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // 检查广播的Action是否是我们关心的“秘密代码”
        if ("android.provider.Telephony.SECRET_CODE" == intent.action) {
            // 获取用户输入的代码 (例如 "20160627")
            val secretCode = intent.data?.host ?: return

            when (secretCode) {
                "20160627" -> {
                    // 当用户输入 *#*#20160627#*#* 时
                    Toast.makeText(context, "正在进入深层开发者选项...", Toast.LENGTH_SHORT).show()

                    // 创建一个意图来启动我们的主Activity
                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        // 设置标志，因为我们是从广播接收器启动Activity
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        // 【关键】携带一个“信物”，告诉MainActivity要去哪里
                        putExtra(NAVIGATE_TO_DEV_OPTIONS, true)
                    }
                    context.startActivity(launchIntent)
                }
                "20220238" -> {
                    // 当用户输入 *#*#20220238#*#* 时
                    // TODO: 在这里修改PreferenceManager中的日志级别为Info
                    Toast.makeText(context, "日志级别已设为 INFO", Toast.LENGTH_SHORT).show()
                }
                "20250528" -> {
                    // 当用户输入 *#*#20250528#*#* 时
                    // TODO: 在这里修改PreferenceManager中的日志级别为关闭
                    Toast.makeText(context, "日志记录已关闭", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}