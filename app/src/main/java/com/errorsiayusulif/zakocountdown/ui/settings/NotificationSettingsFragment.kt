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
import com.errorsiayusulif.zakocountdown.data.PreferenceKeys
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.services.CountdownService
import com.errorsiayusulif.zakocountdown.utils.PermissionUtils

class NotificationSettingsFragment : ZakoPreferenceFragment() {
    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        com.errorsiayusulif.zakocountdown.utils.MtbThemeEngine.applyToPreferenceFragment(this)
    }
    private lateinit var appPreferenceManager: PreferenceManager

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            val pref = findPreference<SwitchPreferenceCompat>(PreferenceKeys.PERSISTENT_NOTIFICATION_ENABLED)
            if (isGranted) {
                startCountdownService()
            } else {
                Toast.makeText(requireContext(), R.string.notify_permission_needed, Toast.LENGTH_SHORT).show()
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
        findPreference<SwitchPreferenceCompat>(PreferenceKeys.PERSISTENT_NOTIFICATION_ENABLED)?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) { checkNotificationPermissionAndStartService() }
            else { stopCountdownService() }
            true
        }
        findPreference<ListPreference>(PreferenceKeys.REMINDER_LEAD_TIME)?.setOnPreferenceChangeListener { _, newValue ->
            appPreferenceManager.saveReminderTime(newValue as String)
            // TODO: 在这里添加一个重新调度所有闹钟的逻辑
            true
        }
        findPreference<Preference>("permission_notification")?.setOnPreferenceClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                }
                startActivitySafely(intent, getString(R.string.perm_error_notification_settings))
            }
            true
        }
        findPreference<Preference>("permission_exact_alarm")?.setOnPreferenceClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivitySafely(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM), getString(R.string.perm_error_alarm_settings))
            }
            true
        }
        findPreference<Preference>("permission_autostart")?.setOnPreferenceClickListener {
            PermissionUtils.getAutostartIntent(requireContext())?.let {
                startActivitySafely(it, getString(R.string.perm_error_autostart_settings))
            }; true
        }
        findPreference<Preference>("permission_battery_optimization")?.setOnPreferenceClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${requireActivity().packageName}")
                }
                startActivitySafely(intent, getString(R.string.perm_error_battery_settings))
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
            pref.summary = if (isGranted) getString(R.string.perm_summary_granted) else getString(R.string.perm_summary_not_granted_tap)
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
            pref.summary = if (isIgnoring) getString(R.string.perm_summary_unrestricted) else getString(R.string.perm_summary_not_exempt)
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
