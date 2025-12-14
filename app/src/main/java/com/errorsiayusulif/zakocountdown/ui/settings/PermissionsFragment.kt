// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/PermissionsFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.utils.AccessibilityStatusHelper
import com.errorsiayusulif.zakocountdown.utils.PermissionUtils
import com.errorsiayusulif.zakocountdown.utils.SystemUtils

class PermissionsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "zako_prefs"
        setPreferencesFromResource(R.xml.permissions_preferences, rootKey)
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateAllPermissionStatus()
    }

    private fun setupListeners() {
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
        findPreference<Preference>("permission_battery_optimization")?.setOnPreferenceClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${requireActivity().packageName}")
                }
                startActivitySafely(intent, "无法打开电池优化页面")
            }
            true
        }
        // --- 【核心修复】为无障碍服务设置点击事件 ---
        findPreference<Preference>("enable_accessibility")?.setOnPreferenceClickListener {
            startActivitySafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), "无法打开无障碍设置页面")
            true
        }
        findPreference<Preference>("permission_overlay")?.setOnPreferenceClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${requireActivity().packageName}"))
                startActivitySafely(intent, "无法打开权限页面")
            }
            true
        }
        findPreference<Preference>("permission_autostart")?.setOnPreferenceClickListener {
            PermissionUtils.getAutostartIntent(requireContext())?.let {
                startActivitySafely(it, "无法跳转到自启设置页面")
            }
            true
        }
        findPreference<Preference>("permission_miui_background")?.setOnPreferenceClickListener {
            goToMiuiPermission(requireContext())
            true
        }
    }

    private fun updateAllPermissionStatus() {
        updateNotificationPermissionStatus()
        updateExactAlarmPermissionStatus()
        updateBatteryOptimizationStatus()
        updateAccessibilityStatus()
        updateOverlayPermissionStatus()
        updateAutostartStatus()
        updateMiuiPermissionStatus()
    }

    // --- 【核心修复】只更新摘要，永不禁用 ---
    private fun updateAccessibilityStatus() {
        val pref = findPreference<Preference>("enable_accessibility") ?: return
        val isEnabled = AccessibilityStatusHelper.isAccessibilityServiceEnabled(requireContext())
        pref.summary = if (isEnabled) "已开启" else "未开启，点击去系统设置中手动开启"
        // 我们不再禁用它，让用户可以随时点击跳转
    }

    private fun updateMiuiPermissionStatus() {
        val pref = findPreference<Preference>("permission_miui_background") ?: return
        if (SystemUtils.isMiui()) {
            pref.isVisible = true
            // 由于没有公开API检查此权限，我们只能提供入口
            pref.summary = "请确保“后台弹出界面”权限已开启"
        } else {
            pref.isVisible = false
        }
    }

    // --- 所有辅助方法 ---
    private fun goToMiuiPermission(context: Context) {
        try {
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
            intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            intent.putExtra("extra_pkgname", context.packageName)
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "无法跳转到MIUI权限页面", Toast.LENGTH_SHORT).show()
        }
    }

    // --- 所有 update... 方法 ---
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
            val canSchedule = alarmManager.canScheduleExactAlarms()
            pref.summary = if (canSchedule) "已授予" else "未授予，点击开启"
            pref.isEnabled = !canSchedule
        } else { pref.isVisible = false }
    }

    @SuppressLint("BatteryLife")
    private fun updateBatteryOptimizationStatus() {
        val pref = findPreference<Preference>("permission_battery_optimization") ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoring = pm.isIgnoringBatteryOptimizations(requireContext().packageName)
            pref.summary = if (isIgnoring) "已设为“无限制”" else "未豁免，点击设置"
            pref.isEnabled = !isIgnoring
        } else { pref.isVisible = false }
    }

    private fun updateOverlayPermissionStatus() {
        val pref = findPreference<Preference>("permission_overlay") ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val canDraw = Settings.canDrawOverlays(requireContext())
            pref.summary = if(canDraw) "已授予" else "未授予，点击开启"
            pref.isEnabled = !canDraw
        } else { pref.isVisible = false }
    }

    private fun updateAutostartStatus() {
        val pref = findPreference<Preference>("permission_autostart") ?: return
        val intent = PermissionUtils.getAutostartIntent(requireContext())
        pref.isVisible = intent != null && requireActivity().packageManager.resolveActivity(intent, 0) != null
    }

    private fun startActivitySafely(intent: Intent, errorMessage: String) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
        }
    }
}