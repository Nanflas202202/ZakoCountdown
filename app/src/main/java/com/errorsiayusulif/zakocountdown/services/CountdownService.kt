// file: app/src/main/java/com/errorsiayusulif/zakocountdown/services/CountdownService.kt
package com.errorsiayusulif.zakocountdown.services

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.errorsiayusulif.zakocountdown.MainActivity
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit

@SuppressLint("MissingPermission")
class CountdownService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    companion object {
        const val CHANNEL_ID = "ZakoCountdownServiceChannel"
        const val NOTIFICATION_ID = 1
        const val TAG = "CountdownService"
        const val ACTION_UPDATE = "com.errorsiayusulif.zakocountdown.services.UPDATE_NOTIFICATION"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received")

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        val initialNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("ZakoCountdown 服务正在运行")
            .setContentText("正在准备倒计时数据...")
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }
        Log.d(TAG, "Service has been promoted to foreground.")

        // --- 【修复】统一方法名 ---
        updateNotification()
        scheduleNextUpdate()

        return START_STICKY
    }

    // --- 【修复】只保留一个正确的 scheduleNextUpdate 定义 ---
    private fun scheduleNextUpdate() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, CountdownService::class.java).apply { action = ACTION_UPDATE }
        val pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val triggerAtMillis = SystemClock.elapsedRealtime() + 60000 - (SystemClock.elapsedRealtime() % 60000)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent)
            Log.d(TAG, "Exact alarm scheduled.")
        } else {
            // 对于没有精确闹钟权限的情况，我们使用 set，它会在系统允许的时候尽快执行，比 setRepeating 更灵活
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent)
            Log.w(TAG, "No exact alarm permission. Using inexact alarm for next update.")
        }
    }

    // --- 【修复】这个方法就是我们的更新逻辑实现 ---
    private fun updateNotification() {
        scope.launch {
            val repo = (application as ZakoCountdownApplication).repository
            val importantEvents = repo.getImportantEvents()
            val contentText = if (importantEvents.isEmpty()) {
                "没有需要提醒的重点日程"
            } else {
                importantEvents.take(2).joinToString("\n") { event ->
                    val now = Date()
                    val diff = event.targetDate.time - now.time
                    val days = TimeUnit.MILLISECONDS.toDays(diff)
                    val hours = TimeUnit.MILLISECONDS.toHours(diff) % 24
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
                    val timeString = when {
                        days > 0 -> "${days}天 ${hours}小时"
                        hours > 0 -> "${hours}小时 ${minutes}分钟"
                        else -> "${minutes}分钟"
                    }
                    if (diff < 0) "「${event.title}」已过期" else "距离「${event.title}」还有 $timeString"
                }
            }

            val openAppIntent = Intent(this@CountdownService, MainActivity::class.java)
            // --- 【核心】创建跳转到“通知设置”页面的意图 ---
            val notificationSettingsIntent = Intent(
                Intent.ACTION_VIEW,
                "errorsiayusulif://zakocountdown/notifications".toUri(), // 使用Deep Link
                this@CountdownService,
                MainActivity::class.java
            )
            val pendingIntent = PendingIntent.getActivity(
                this@CountdownService, 0, notificationSettingsIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val updatedNotification = NotificationCompat.Builder(this@CountdownService, CHANNEL_ID)
                .setContentTitle("杂～鱼～❤️")
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, updatedNotification)
            Log.d(TAG, "Notification content updated.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, CountdownService::class.java).apply { action = ACTION_UPDATE }
        val pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
        job.cancel()
        Log.d(TAG, "Service destroyed and alarm canceled.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "ZakoCountdown 常驻服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于在后台更新常驻通知"
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(serviceChannel)
        }
    }
}