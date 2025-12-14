// file: app/src/main/java/com/errorsiayusulif/zakocountdown/services/PopupViewService.kt
package com.errorsiayusulif.zakocountdown.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.appcompat.view.ContextThemeWrapper
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.databinding.ActivityPopupReminderBinding
import com.errorsiayusulif.zakocountdown.utils.TimeCalculator
import kotlinx.coroutines.*
import java.util.Date

class PopupViewService : Service() {

    private lateinit var windowManager: WindowManager
    private var popupView: View? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // --- 【在这里植入窃听器#2】 ---
        Log.i("PopupViewService", ">>> [SERVICE STARTED]")
        if (intent == null) {
            Log.e("PopupViewService", "Intent is NULL!")
            showPopup("错误：无法接收数据")
            return START_NOT_STICKY
        }

        // 打印 Intent 中的所有 Extra 数据，看看里面到底有什么
        Log.d("PopupViewService", "Intent Extras: ${intent.extras?.keySet()?.joinToString { key -> "$key = ${intent.extras?.get(key)}" }}")

        val eventIds = intent.getLongArrayExtra("extra_event_ids")

        // --- 【在这里植入窃听器#3】 ---
        if (eventIds == null) {
            Log.e("PopupViewService", "Event IDs array is NULL in received Intent!")
        } else {
            Log.i("PopupViewService", "Received Event IDs: ${eventIds.joinToString()}")
        }

        serviceScope.launch {
            val details = if (eventIds == null || eventIds.isEmpty()) {
                "没有找到日程信息"
            } else {
                val repository = (application as ZakoCountdownApplication).repository
                withContext(Dispatchers.IO) {
                    // --- 【在这里植入窃听器#4】 ---
                    Log.d("PopupViewService", "Querying database with IDs: ${eventIds.joinToString()}")
                    val events = repository.getEventsByIds(eventIds.toList())
                    Log.d("PopupViewService", "Found ${events.size} events from database.")

                    events.take(3).joinToString("\n") { event ->
                        val diff = TimeCalculator.calculateDifference(event.targetDate)
                        if (diff.isPast) "「${event.title}」已过去 ${diff.totalDays} 天"
                        else "距离「${event.title}」还有 ${diff.totalDays} 天"
                    }
                }
            }
            showPopup(details)
        }

        return START_NOT_STICKY
    }

    private fun showPopup(details: String) {
        // 如果已有一个悬浮窗，先移除
        if (popupView != null) {
            try { windowManager.removeView(popupView) } catch (e: Exception) {}
        }

        val themedContext = ContextThemeWrapper(this, R.style.Theme_ZakoCountdown)
        val inflater = LayoutInflater.from(themedContext)
        val binding = ActivityPopupReminderBinding.inflate(inflater)
        popupView = binding.root

        Log.i("PopupViewService", "Showing popup with final details: '$details'")
        binding.popupDetailsText.text = details

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            windowManager.addView(popupView, params)
        } catch (e: Exception) {
            // Handle exception
        }

        // 3秒后自动移除悬浮窗并停止服务
        Handler(Looper.getMainLooper()).postDelayed({
            stopSelf()
        }, 3000)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel() // 取消所有协程
        popupView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {}
            popupView = null
        }
    }
}