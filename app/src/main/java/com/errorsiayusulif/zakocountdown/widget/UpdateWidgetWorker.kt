// file: app/src/main/java/com/errorsiayusulif/zakocountdown/widget/UpdateWidgetWorker.kt
package com.errorsiayusulif.zakocountdown.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
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
import com.errorsiayusulif.zakocountdown.widget.WidgetConfigureActivity
import com.errorsiayusulif.zakocountdown.widget.ZakoWidgetProvider
import kotlinx.coroutines.CoroutineScope
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
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, ZakoWidgetProvider::class.java)
        )
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
    val views = RemoteViews(context.packageName, R.layout.widget_layout)

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

    // 2. 处理背景
    val backgroundType = preferenceManager.getWidgetBackground(appWidgetId)

    // 先重置所有视图状态
    views.setViewVisibility(R.id.widget_background_image, View.GONE)
    views.setViewVisibility(R.id.widget_scrim, View.GONE)
    // 清除 root 的背景（设为透明）
    views.setInt(R.id.widget_root, "setBackgroundResource", 0)
    views.setInt(R.id.widget_background_image, "setColorFilter", 0)
    views.setInt(R.id.widget_scrim, "setColorFilter", 0)

    // 获取用户选择的颜色（默认为主题色）
    val savedColorHex = preferenceManager.getWidgetColor(appWidgetId)
    var baseColor = ContextCompat.getColor(context, R.color.m3_seed) // 默认值

    if (savedColorHex != null) {
        try {
            baseColor = Color.parseColor(savedColorHex)
        } catch (e: Exception) {
            // keep default
        }
    } else {
        // 获取主题色
        try {
            val attrs = intArrayOf(com.google.android.material.R.attr.colorPrimary)
            val typedArray = context.obtainStyledAttributes(R.style.Theme_ZakoCountdown, attrs)
            baseColor = typedArray.getColor(0, baseColor)
            typedArray.recycle()
        } catch (e: Exception) {}
    }

    // 获取主题色用于遮罩
    var themePrimaryColor = Color.BLACK
    try {
        val attrs = intArrayOf(com.google.android.material.R.attr.colorPrimary)
        val typedArray = context.obtainStyledAttributes(R.style.Theme_ZakoCountdown, attrs)
        themePrimaryColor = typedArray.getColor(0, Color.BLACK)
        typedArray.recycle()
    } catch (e: Exception) {}


    when (backgroundType) {
        "solid" -> {
            // 不透明模式：显示背景层，设置为白色底，然后染成用户选择的颜色
            views.setViewVisibility(R.id.widget_background_image, View.VISIBLE)
            views.setImageViewResource(R.id.widget_background_image, R.drawable.widget_background_base)
            // 纯色模式：直接染色，不透明
            views.setInt(R.id.widget_background_image, "setColorFilter", baseColor)
            views.setInt(R.id.widget_background_image, "setImageAlpha", 255)
        }
        "image" -> {
            // 图片模式
            val imageUriString = preferenceManager.getWidgetImageUri(appWidgetId)
            var imageLoaded = false

            if (imageUriString != null) {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(Uri.parse(imageUriString))
                    .size(512, 512) // 限制大小，防止 RemoteViews 传输失败
                    .allowHardware(false) // 必须禁用硬件位图
                    .build()

                try {
                    // 【核心修复】使用 imageResult 变量名，避免冲突
                    val imageResult = loader.execute(request)
                    if (imageResult is SuccessResult) {
                        // 使用 toBitmap() 确保它是 Bitmap
                        val bitmap = imageResult.drawable.toBitmap()

                        views.setViewVisibility(R.id.widget_background_image, View.VISIBLE)
                        views.setImageViewBitmap(R.id.widget_background_image, bitmap)

                        // 应用图片透明度
                        val imgAlpha = preferenceManager.getWidgetImageAlpha(appWidgetId)
                        val imgAlphaInt = (imgAlpha / 100f * 255).toInt()
                        views.setInt(R.id.widget_background_image, "setImageAlpha", imgAlphaInt)

                        imageLoaded = true
                    }
                } catch (e: Exception) {
                    Log.e(UpdateWidgetWorker.TAG, "Image load failed", e)
                }
            }

            if (imageLoaded) {
                // 如果图片加载成功，处理遮罩
                val showScrim = preferenceManager.getWidgetShowScrim(appWidgetId)
                if (showScrim) {
                    views.setViewVisibility(R.id.widget_scrim, View.VISIBLE)
                    val scrimAlpha = preferenceManager.getWidgetScrimAlpha(appWidgetId)
                    val scrimAlphaInt = (scrimAlpha / 100f * 255).toInt()

                    views.setInt(R.id.widget_scrim, "setColorFilter", themePrimaryColor)
                    views.setInt(R.id.widget_scrim, "setImageAlpha", scrimAlphaInt)
                } else {
                    views.setViewVisibility(R.id.widget_scrim, View.GONE)
                }
            } else {
                // 回退到默认样式
                views.setViewVisibility(R.id.widget_background_image, View.VISIBLE)
                views.setImageViewResource(R.id.widget_background_image, R.drawable.widget_background_base)
                views.setInt(R.id.widget_background_image, "setColorFilter", Color.DKGRAY)
                views.setInt(R.id.widget_background_image, "setImageAlpha", 255)
            }
        }
        else -> { // transparent / semi-transparent
            // 半透明模式：显示背景层，设置为白色底
            views.setViewVisibility(R.id.widget_background_image, View.VISIBLE)
            views.setImageViewResource(R.id.widget_background_image, R.drawable.widget_background_base)

            // 获取透明度 (0-100) -> (0-255)
            val alphaPercent = preferenceManager.getWidgetAlpha(appWidgetId)
            val alphaInt = (alphaPercent / 100f * 255).toInt()

            // 染成用户选择的颜色
            views.setInt(R.id.widget_background_image, "setColorFilter", baseColor)
            // 设置透明度
            views.setInt(R.id.widget_background_image, "setImageAlpha", alphaInt)
        }
    }

    // 3. 更新文本内容
    val eventId = preferenceManager.getWidgetEventId(appWidgetId)
    if (eventId == -1L) {
        views.setTextViewText(R.id.widget_title, "点击配置")
        views.setTextViewText(R.id.widget_days, "-")
    } else {
        val event = repository.getEventById(eventId)
        if (event != null) {
            val now = Date()
            val diffInMillis = event.targetDate.time - now.time
            val daysLeft = TimeUnit.MILLISECONDS.toDays(diffInMillis)
            val finalDays = if (diffInMillis > 0 && daysLeft == 0L) 1L else kotlin.math.abs(daysLeft)

            views.setTextViewText(R.id.widget_title, event.title)
            views.setTextViewText(R.id.widget_days, finalDays.toString())

            val label = if (diffInMillis < 0) "已过" else "还有"
            views.setTextViewText(R.id.widget_title_prefix, "距离")
            views.setTextViewText(R.id.widget_days_suffix, "天 ($label)")
        } else {
            views.setTextViewText(R.id.widget_title, "日程已删除")
            views.setTextViewText(R.id.widget_days, "!")
        }
    }

    appWidgetManager.updateAppWidget(appWidgetId, views)
    Log.d(UpdateWidgetWorker.TAG, "Widget $appWidgetId updated.")
}