// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/NotificationSettingsFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.services.CountdownService
import com.errorsiayusulif.zakocountdown.utils.PermissionUtils

class NotificationSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var appPreferenceManager: PreferenceManager

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            val pref = findPreference<SwitchPreferenceCompat>("enable_permanent_notification")
            if (isGranted) {
                startCountdownService()
            } else {
                Toast.makeText(requireContext(), "需要通知权限才能开启常驻通知", Toast.LENGTH_SHORT).show()
                pref?.isChecked = false
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // --- 【核心修复】 ---
        // 1. 直接访问 PreferenceFragmentCompat 自身的 preferenceManager 属性，并设置文件名
        preferenceManager.sharedPreferencesName = "zako_prefs"

        // 2. 加载布局
        setPreferencesFromResource(R.xml.notification_preferences, rootKey)

        // 3. 初始化我们自己的工具实例
        appPreferenceManager = PreferenceManager(requireContext())

        // --- 设置监听器 ---
        setupListeners()
    }

    private fun setupListeners() {
        findPreference<SwitchPreferenceCompat>("enable_permanent_notification")?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) { checkNotificationPermissionAndStartService() }
            else { stopCountdownService() }
            true
        }
        findPreference<ListPreference>("key_reminder_time")?.setOnPreferenceChangeListener { _, newValue ->
            appPreferenceManager.saveReminderTime(newValue as String)
            // TODO: 在这里添加一个重新调度所有闹钟的逻辑
            true
        }
        findPreference<Preference>("permission_notification")?.setOnPreferenceClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                }
                startActivitySafely(intent, "无法打开通知设置")
            }
            true
        }
        findPreference<Preference>("permission_exact_alarm")?.setOnPreferenceClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivitySafely(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM), "无法打开权限页面")
            }
            true
        }
        findPreference<Preference>("permission_autostart")?.setOnPreferenceClickListener {
            PermissionUtils.getAutostartIntent(requireContext())?.let {
                startActivitySafely(it, "无法跳转到自启设置页面")
            }; true
        }
        findPreference<Preference>("permission_battery_optimization")?.setOnPreferenceClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${requireActivity().packageName}")
                }
                startActivitySafely(intent, "无法打开电池优化页面")
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        updateAllPreferenceStatus()
    }

    private fun updateAllPreferenceStatus() {
        updateNotificationPermissionStatus()
        updateExactAlarmPermissionStatus()
        updateAutostartStatus()
        updateBatteryOptimizationStatus()
    }

    private fun updateNotificationPermissionStatus() {
        val pref = findPreference<Preference>("permission_notification") ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            pref.summary = if (isGranted) "已授予" else "未授予，点击设置"
            pref.isEnabled = !isGranted
        } else {
            pref.isVisible = false
        }
    }

    private fun updateExactAlarmPermissionStatus() {
        val pref = findPreference<Preference>("permission_exact_alarm") ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            pref.isVisible = !alarmManager.canScheduleExactAlarms()
        } else { pref.isVisible = false }
    }

    private fun updateAutostartStatus() {
        val pref = findPreference<Preference>("permission_autostart") ?: return
        val intent = PermissionUtils.getAutostartIntent(requireContext())
        pref.isVisible = intent != null && requireActivity().packageManager.resolveActivity(intent, 0) != null
    }

    @SuppressLint("BatteryLife")
    private fun updateBatteryOptimizationStatus() {
        val pref = findPreference<Preference>("permission_battery_optimization") ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pref.isVisible = true
            val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoring = pm.isIgnoringBatteryOptimizations(requireContext().packageName)
            pref.summary = if (isIgnoring) "已设为“无限制”" else "当前未豁免。点击设置。"
            pref.isEnabled = !isIgnoring
        } else {
            pref.isVisible = false
        }
    }

    private fun checkNotificationPermissionAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> startCountdownService()
                else -> requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        } else { startCountdownService() }
    }

    private fun startCountdownService() {
        val intent = Intent(requireContext(), CountdownService::class.java)
        requireContext().startService(intent)
    }

    private fun stopCountdownService() {
        val intent = Intent(requireContext(), CountdownService::class.java)
        requireContext().stopService(intent)
    }

    private fun startActivitySafely(intent: Intent, errorMessage: String) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
        }
    }
}