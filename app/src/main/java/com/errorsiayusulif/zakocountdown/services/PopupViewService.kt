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
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.View
import com.errorsiayusulif.zakocountdown.databinding.ActivityPopupReminderBinding

class PopupViewService : Service() {

    private lateinit var windowManager: WindowManager
    private var popupView: View? = null
    private lateinit var binding: ActivityPopupReminderBinding

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (popupView != null) {
            // 如果已有一个悬浮窗，先移除
            windowManager.removeView(popupView)
        }

        // 从Intent获取数据
        val eventName = intent?.getStringExtra("extra_event_name") ?: "日程"
        val daysLeft = intent?.getLongExtra("extra_days_left", 0) ?: 0

        // 使用我们之前为Activity创建的布局
        binding = ActivityPopupReminderBinding.inflate(LayoutInflater.from(this))
        popupView = binding.root

        // 填充数据
        binding.popupTextEventName.text = "距离 $eventName"
        val details = intent?.getStringExtra("extra_details_string") ?: "加载中..."
        binding.popupTextDetails.text = details // <-- 更新UI

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        try {
            windowManager.addView(popupView, params)
        } catch (e: Exception) {
            // 添加悬浮窗可能会失败
        }

        // 3秒后自动移除悬浮窗并停止服务
        Handler(Looper.getMainLooper()).postDelayed({
            stopSelf()
        }, 3000)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        popupView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {}
            popupView = null
        }
    }
}