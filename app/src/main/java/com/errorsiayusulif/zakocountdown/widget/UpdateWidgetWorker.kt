// file: app/src/main/java/com/errorsiayusulif/zakocountdown/widget/UpdateWidgetWorker.kt
package com.errorsiayusulif.zakocountdown.widget

import android.app.PendingIntent // <-- 【修复】
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent // <-- 【修复】
import android.net.Uri // 确保导入
import android.util.Log
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import kotlinx.coroutines.CoroutineScope // <-- 【修复#1】导入
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.TimeUnit


class UpdateWidgetWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "UpdateWidgetWorker"
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                updateSingleWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, ZakoWidgetProvider::class.java))
        if (appWidgetIds.isEmpty()) return Result.success()

        appWidgetIds.forEach { appWidgetId ->
            updateSingleWidget(context, appWidgetManager, appWidgetId)
        }

        return Result.success()
    }
}

private suspend fun updateSingleWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) = withContext(Dispatchers.IO) {
    val preferenceManager = PreferenceManager(context)
    val repository = (context.applicationContext as ZakoCountdownApplication).repository
    val eventId = preferenceManager.getWidgetEventId(appWidgetId)
    if (eventId == -1L) return@withContext

    val event = repository.getEventById(eventId)
    val views = RemoteViews(context.packageName, R.layout.widget_layout)

    // --- 【修复】backgroundType 只在这里定义一次 ---
    val backgroundType = preferenceManager.getWidgetBackground(appWidgetId)
    when (backgroundType) {
        "solid" -> views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_background_solid)
        "image" -> {
            // val imageUri = preferenceManager.getWidgetImageUri(appWidgetId) // <-- 【修复】我们没有这个方法，暂时注释掉
            // TODO: 异步加载图片并设置为背景
        }
        else -> views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_background_transparent)
    }

    if (event != null) {
        val now = Date()
        val diffInMillis = event.targetDate.time - now.time
        val daysLeft = TimeUnit.MILLISECONDS.toDays(diffInMillis)
        val finalDays = if (diffInMillis > 0 && daysLeft == 0L) 1L else daysLeft
        views.setTextViewText(R.id.widget_title, event.title)
        views.setTextViewText(R.id.widget_days, finalDays.toString())
    } else {
        views.setTextViewText(R.id.widget_title, "日程已删除")
        views.setTextViewText(R.id.widget_days, "!")
    }
    // 1. 创建一个跳转到“通知设置”页面的Deep Link Intent
    val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("errorsiayusulif://zakocountdown/notifications")
    )
    // 2. 将 Intent 包装成 PendingIntent
    val pendingIntent = PendingIntent.getActivity(
        context,
        appWidgetId, // 使用 appWidgetId 确保每个微件的 PendingIntent 是唯一的
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    // 3. 将 PendingIntent 设置给微件的根布局
    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
    appWidgetManager.updateAppWidget(appWidgetId, views)
}