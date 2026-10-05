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
import com.errorsiayusulif.zakocountdown.utils.LocaleHelper
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

    /** 让 Service 里的 getString / 通知文案跟随用户选择的语言 */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
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
            .setContentTitle(getString(R.string.service_running_title))
            .setContentText(getString(R.string.service_running_text))
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
                getString(R.string.service_no_important_events)
            } else {
                importantEvents.take(2).joinToString("\n") { event ->
                    val now = Date()
                    val diff = event.targetDate.time - now.time
                    val days = TimeUnit.MILLISECONDS.toDays(diff)
                    val hours = TimeUnit.MILLISECONDS.toHours(diff) % 24
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
                    val timeString = when {
                        days > 0 -> getString(R.string.duration_days_hours, days.toInt(), hours.toInt())
                        hours > 0 -> getString(R.string.duration_hours_minutes, hours.toInt(), minutes.toInt())
                        else -> getString(R.string.duration_minutes, minutes.toInt())
                    }
                    if (diff < 0) getString(R.string.service_event_expired, event.title) else getString(R.string.service_event_remaining, event.title, timeString)
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
                .setContentTitle(getString(R.string.service_friendly_title))
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
                getString(R.string.service_channel_persistent_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.service_channel_persistent_desc)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(serviceChannel)
        }
    }
}