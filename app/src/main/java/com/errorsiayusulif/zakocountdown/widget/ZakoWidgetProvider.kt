// file: app/src/main/java/com/errorsiayusulif/zakocountdown/widget/ZakoWidgetProvider.kt
package com.errorsiayusulif.zakocountdown.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit
import com.errorsiayusulif.zakocountdown.data.PreferenceManager

class ZakoWidgetProvider : AppWidgetProvider() {

    companion object {
        const val WIDGET_UPDATE_WORK_NAME = "ZakoWidgetUpdateWork"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 当系统请求更新时（例如刚添加时），我们立即启动一次Worker来获取最新数据
        enqueueOneTimeWork(context)
    }

    override fun onEnabled(context: Context) {
        // 当第一个微件被创建时，启动一个周期性的后台任务
        val periodicWorkRequest = PeriodicWorkRequestBuilder<UpdateWidgetWorker>(
            1, TimeUnit.HOURS // 更新频率，至少为15分钟，这里设为1小时
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WIDGET_UPDATE_WORK_NAME, // 任务的唯一名称
            ExistingPeriodicWorkPolicy.KEEP, // 如果任务已存在，则保持不变
            periodicWorkRequest
        )
    }

    override fun onDisabled(context: Context) {
        // 当最后一个微件被删除时，取消所有后台任务
        WorkManager.getInstance(context).cancelUniqueWork(WIDGET_UPDATE_WORK_NAME)
    }

    // 我们把原来的 updateAppWidget 删掉，因为所有更新逻辑都统一由 Worker 处理
    // 我们提供一个公共方法，方便其他地方（比如添加日程后）也能触发一次立即更新
    fun enqueueOneTimeWork(context: Context) {
        val oneTimeWorkRequest = OneTimeWorkRequestBuilder<UpdateWidgetWorker>().build()
        WorkManager.getInstance(context).enqueue(oneTimeWorkRequest)
    }
    /**
     * 当一个或多个微件被删除时调用。
     */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val preferenceManager = PreferenceManager(context)
        for (appWidgetId in appWidgetIds) {
            // 删除这个微件对应的配置
            preferenceManager.deleteWidgetEventId(appWidgetId)
        }
    }
}