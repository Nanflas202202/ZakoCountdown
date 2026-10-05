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

class PermissionsFragment : ZakoPreferenceFragment() {
    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        com.errorsiayusulif.zakocountdown.utils.MtbThemeEngine.applyToPreferenceFragment(this)
    }
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
                startActivitySafely(intent, requireContext().getString(R.string.perm_error_notification_settings))
            }
            true
        }
        findPreference<Preference>("permission_exact_alarm")?.setOnPreferenceClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivitySafely(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM), requireContext().getString(R.string.perm_error_alarm_settings))
            }
            true
        }
        findPreference<Preference>("permission_battery_optimization")?.setOnPreferenceClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${requireActivity().packageName}")
                }
                startActivitySafely(intent, requireContext().getString(R.string.perm_error_battery_settings))
            }
            true
        }
        // --- 【核心修复】为无障碍服务设置点击事件 ---
        findPreference<Preference>("enable_accessibility")?.setOnPreferenceClickListener {
            startActivitySafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), requireContext().getString(R.string.perm_error_accessibility_settings))
            true
        }
        findPreference<Preference>("permission_overlay")?.setOnPreferenceClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${requireActivity().packageName}"))
                startActivitySafely(intent, requireContext().getString(R.string.perm_error_alarm_settings))
            }
            true
        }
        findPreference<Preference>("permission_autostart")?.setOnPreferenceClickListener {
            PermissionUtils.getAutostartIntent(requireContext())?.let {
                startActivitySafely(it, requireContext().getString(R.string.perm_error_autostart_settings))
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
        pref.summary = if (isEnabled) getString(R.string.perm_status_enabled) else getString(R.string.perm_summary_accessibility_manual)
        // 我们不再禁用它，让用户可以随时点击跳转
    }

    private fun updateMiuiPermissionStatus() {
        val pref = findPreference<Preference>("permission_miui_background") ?: return
        if (SystemUtils.isMiui()) {
            pref.isVisible = true
            // 由于没有公开API检查此权限，我们只能提供入口
            pref.summary = getString(R.string.perm_summary_background_popup)
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
            Toast.makeText(context, R.string.perm_error_miui_page, Toast.LENGTH_SHORT).show()
        }
    }

    // --- 所有 update... 方法 ---
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
            val canSchedule = alarmManager.canScheduleExactAlarms()
            pref.summary = if (canSchedule) getString(R.string.perm_summary_granted) else getString(R.string.perm_summary_not_granted_tap_enable)
            pref.isEnabled = !canSchedule
        } else { pref.isVisible = false }
    }

    @SuppressLint("BatteryLife")
    private fun updateBatteryOptimizationStatus() {
        val pref = findPreference<Preference>("permission_battery_optimization") ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoring = pm.isIgnoringBatteryOptimizations(requireContext().packageName)
            pref.summary = if (isIgnoring) getString(R.string.perm_summary_unrestricted) else getString(R.string.perm_summary_not_exempt)
            pref.isEnabled = !isIgnoring
        } else { pref.isVisible = false }
    }

    private fun updateOverlayPermissionStatus() {
        val pref = findPreference<Preference>("permission_overlay") ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val canDraw = Settings.canDrawOverlays(requireContext())
            pref.summary = if(canDraw) getString(R.string.perm_summary_granted) else getString(R.string.perm_summary_not_granted_tap_enable)
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
