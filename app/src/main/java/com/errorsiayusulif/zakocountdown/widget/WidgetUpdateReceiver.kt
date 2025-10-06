// file: app/src/main/java/com/errorsiayusulif/zakocountdown/widget/WidgetUpdateReceiver.kt
package com.errorsiayusulif.zakocountdown.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 收到广播后，立即启动一次性的更新任务
        val oneTimeWorkRequest = OneTimeWorkRequestBuilder<UpdateWidgetWorker>().build()
        WorkManager.getInstance(context).enqueue(oneTimeWorkRequest)
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.errorsiayusulif.zakocountdown.ACTION_UPDATE_WIDGET"
    }
}