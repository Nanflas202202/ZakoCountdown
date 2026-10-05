// file: app/src/main/java/com/errorsiayusulif/zakocountdown/receiver/SecretCodeReceiver.kt
package com.errorsiayusulif.zakocountdown.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.errorsiayusulif.zakocountdown.MainActivity
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.PreferenceManager

class SecretCodeReceiver : BroadcastReceiver() {

    companion object {
        const val NAVIGATE_TO_DEV_OPTIONS = "navigate_to_dev_options"
        const val NAVIGATE_TO_LOG_READER = "navigate_to_log_reader"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if ("android.provider.Telephony.SECRET_CODE" == intent.action) {
            val secretCode = intent.data?.host ?: return

            when (secretCode) {
                "20160627" -> {
                    Toast.makeText(context, R.string.secret_entering_debug, Toast.LENGTH_SHORT).show()
                    launchMainActivitySafely(context, NAVIGATE_TO_DEV_OPTIONS)
                }
                "20220238" -> Toast.makeText(context, R.string.secret_log_level_info, Toast.LENGTH_SHORT).show()
                "20250528" -> Toast.makeText(context, R.string.secret_log_level_off, Toast.LENGTH_SHORT).show()
                "63572202" -> launchMainActivitySafely(context, NAVIGATE_TO_LOG_READER)
                "6357921606" -> {
                    val prefs = PreferenceManager(context)
                    val newState = !prefs.isEnableEnterDevMode()
                    prefs.setEnableEnterDevMode(newState)
                    val status = if (newState) context.getString(R.string.common_enabled) else context.getString(R.string.common_disabled)
                    Toast.makeText(context, context.getString(R.string.secret_dev_mode_trigger, status), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 【核心优化】安全的启动器
    private fun launchMainActivitySafely(context: Context, extraKey: String) {
        // 1. 尝试获取系统当前激活的桌面入口 (Alias)
        var launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)

        // 2. 极端情况兜底：如果找不到，就直接显式调用真实的 MainActivity
        if (launchIntent == null) {
            launchIntent = Intent(context, MainActivity::class.java)
        }

        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        launchIntent.putExtra(extraKey, true)
        context.startActivity(launchIntent)
    }
}