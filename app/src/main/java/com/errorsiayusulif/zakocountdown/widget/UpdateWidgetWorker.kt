// file: app/src/main/java/com/errorsiayusulif/zakocountdown/widget/UpdateWidgetWorker.kt
package com.errorsiayusulif.zakocountdown.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.utils.TimeCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class UpdateWidgetWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "UpdateWidgetWorker"
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                val simpleIds = appWidgetManager.getAppWidgetIds(ComponentName(context, ZakoWidgetProvider::class.java))
                if (appWidgetId in simpleIds) {
                    updateSingleWidget(context, appWidgetManager, appWidgetId, R.layout.widget_layout)
                    return@launch
                }
                val detailedIds = appWidgetManager.getAppWidgetIds(ComponentName(context, ZakoWidgetProviderDetailed::class.java))
                if (appWidgetId in detailedIds) {
                    updateSingleWidget(context, appWidgetManager, appWidgetId, R.layout.widget_layout_detailed)
                    return@launch
                }
                val fullIds = appWidgetManager.getAppWidgetIds(ComponentName(context, ZakoWidgetProviderFull::class.java))
                if (appWidgetId in fullIds) {
                    updateSingleWidget(context, appWidgetManager, appWidgetId, R.layout.widget_layout_full)
                    return@launch
                }
            }
        }
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(context)

        val simpleIds = appWidgetManager.getAppWidgetIds(ComponentName(context, ZakoWidgetProvider::class.java))
        simpleIds.forEach { updateSingleWidget(context, appWidgetManager, it, R.layout.widget_layout) }

        val detailedIds = appWidgetManager.getAppWidgetIds(ComponentName(context, ZakoWidgetProviderDetailed::class.java))
        detailedIds.forEach { updateSingleWidget(context, appWidgetManager, it, R.layout.widget_layout_detailed) }

        val fullIds = appWidgetManager.getAppWidgetIds(ComponentName(context, ZakoWidgetProviderFull::class.java))
        fullIds.forEach { updateSingleWidget(context, appWidgetManager, it, R.layout.widget_layout_full) }

        return Result.success()
    }
}

private suspend fun updateSingleWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    layoutId: Int
) = withContext(Dispatchers.IO) {
    val preferenceManager = PreferenceManager(context)
    val repository = (context.applicationContext as ZakoCountdownApplication).repository
    val views = RemoteViews(context.packageName, layoutId)

    // 1. 设置点击事件 (跳转到配置页)
    val configureIntent = Intent(context, WidgetConfigureActivity::class.java).apply {
        action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        appWidgetId,
        configureIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

    // 2. 处理背景 (保持不变)
    val backgroundType = preferenceManager.getWidgetBackground(appWidgetId)
    views.setViewVisibility(R.id.widget_background_image, View.GONE)
    views.setViewVisibility(R.id.widget_scrim, View.GONE)
    views.setInt(R.id.widget_root, "setBackgroundResource", 0)
    views.setInt(R.id.widget_background_image, "setColorFilter", 0)
    views.setInt(R.id.widget_scrim, "setColorFilter", 0)

    val savedColorHex = preferenceManager.getWidgetColor(appWidgetId)
    var baseColor = ContextCompat.getColor(context, R.color.m3_seed)
    if (savedColorHex != null) {
        try { baseColor = Color.parseColor(savedColorHex) } catch (e: Exception) {}
    } else {
        try {
            val attrs = intArrayOf(com.google.android.material.R.attr.colorPrimary)
            val typedArray = context.obtainStyledAttributes(R.style.Theme_ZakoCountdown, attrs)
            baseColor = typedArray.getColor(0, baseColor)
            typedArray.recycle()
        } catch (e: Exception) {}
    }

    var themePrimaryColor = Color.BLACK
    try {
        val attrs = intArrayOf(com.google.android.material.R.attr.colorPrimary)
        val typedArray = context.obtainStyledAttributes(R.style.Theme_ZakoCountdown, attrs)
        themePrimaryColor = typedArray.getColor(0, Color.BLACK)
        typedArray.recycle()
    } catch (e: Exception) {}

    when (backgroundType) {
        "solid" -> {
            views.setViewVisibility(R.id.widget_background_image, View.VISIBLE)
            views.setImageViewResource(R.id.widget_background_image, R.drawable.widget_background_base)
            views.setInt(R.id.widget_background_image, "setColorFilter", baseColor)
            views.setInt(R.id.widget_background_image, "setImageAlpha", 255)
        }
        "image" -> {
            val imageUriString = preferenceManager.getWidgetImageUri(appWidgetId)
            var imageLoaded = false
            if (imageUriString != null) {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(Uri.parse(imageUriString))
                    .size(512, 512).allowHardware(false).build()
                try {
                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        views.setViewVisibility(R.id.widget_background_image, View.VISIBLE)
                        views.setImageViewBitmap(R.id.widget_background_image, result.drawable.toBitmap())
                        val imgAlpha = preferenceManager.getWidgetImageAlpha(appWidgetId)
                        views.setInt(R.id.widget_background_image, "setImageAlpha", (imgAlpha / 100f * 255).toInt())
                        imageLoaded = true
                    }
                } catch (e: Exception) {}
            }
            if (imageLoaded) {
                if (preferenceManager.getWidgetShowScrim(appWidgetId)) {
                    views.setViewVisibility(R.id.widget_scrim, View.VISIBLE)
                    val scrimAlpha = preferenceManager.getWidgetScrimAlpha(appWidgetId)
                    views.setInt(R.id.widget_scrim, "setColorFilter", themePrimaryColor)
                    views.setInt(R.id.widget_scrim, "setImageAlpha", (scrimAlpha / 100f * 255).toInt())
                }
            } else {
                views.setViewVisibility(R.id.widget_background_image, View.VISIBLE)
                views.setImageViewResource(R.id.widget_background_image, R.drawable.widget_background_base)
                views.setInt(R.id.widget_background_image, "setColorFilter", Color.DKGRAY)
                views.setInt(R.id.widget_background_image, "setImageAlpha", 255)
            }
        }
        else -> {
            views.setViewVisibility(R.id.widget_background_image, View.VISIBLE)
            views.setImageViewResource(R.id.widget_background_image, R.drawable.widget_background_base)
            val alphaPercent = preferenceManager.getWidgetAlpha(appWidgetId)
            views.setInt(R.id.widget_background_image, "setColorFilter", baseColor)
            views.setInt(R.id.widget_background_image, "setImageAlpha", (alphaPercent / 100f * 255).toInt())
        }
    }

    // 3. 更新内容
    val eventId = preferenceManager.getWidgetEventId(appWidgetId)
    val event = if (eventId != -1L) repository.getEventById(eventId) else null

    if (event != null) {
        val diff = TimeCalculator.calculateDifference(event.targetDate)
        val label = if (diff.isPast) "已过" else "还有"

        when (layoutId) {
            R.layout.widget_layout -> { // Simple (2x2)
                views.setTextViewText(R.id.widget_title, event.title) // 居中标题
                views.setTextViewText(R.id.widget_status_label, label)
                views.setTextViewText(R.id.widget_days, diff.totalDays.toString())
                views.setTextViewText(R.id.widget_days_suffix, "天")
            }
            R.layout.widget_layout_detailed -> { // Detailed (天时分)
                views.setTextViewText(R.id.widget_title, "距离 ${event.title}")
                views.setTextViewText(R.id.widget_detailed_status, label)
                views.setTextViewText(R.id.widget_detailed_days, diff.totalDays.toString())
                val timeString = "${String.format("%02d", diff.hours)}时 ${String.format("%02d", diff.minutes)}分"
                views.setTextViewText(R.id.widget_detailed_time, timeString)
            }
            R.layout.widget_layout_full -> { // Full (年 月 周 天)
                views.setTextViewText(R.id.widget_title, "距离 ${event.title}")
                views.setTextViewText(R.id.widget_full_status, label)

                // --- 核心修改：改为 年+月+周+天 ---
                val sb = StringBuilder()
                if (diff.years > 0) sb.append("${diff.years}年 ")
                if (diff.months > 0) sb.append("${diff.months}月 ")
                if (diff.weeks > 0) sb.append("${diff.weeks}周 ")
                sb.append("${diff.daysInWeek}天")

                val fullText = sb.toString()
                // 使用 widget_full_text (注意 XML 中 ID 要匹配)
                views.setTextViewText(R.id.widget_full_text, if(fullText.isBlank()) "0天" else fullText)
            }
        }
    } else {
        views.setTextViewText(R.id.widget_title, "点击配置")
        if (layoutId == R.layout.widget_layout) {
            views.setTextViewText(R.id.widget_days, "-")
            views.setTextViewText(R.id.widget_status_label, "")
        } else if (layoutId == R.layout.widget_layout_detailed) {
            views.setTextViewText(R.id.widget_detailed_days, "-")
            views.setTextViewText(R.id.widget_detailed_time, "")
            views.setTextViewText(R.id.widget_detailed_status, "")
        } else if (layoutId == R.layout.widget_layout_full) {
            views.setTextViewText(R.id.widget_full_text, "- - -")
            views.setTextViewText(R.id.widget_full_status, "")
        }
    }

    appWidgetManager.updateAppWidget(appWidgetId, views)
}