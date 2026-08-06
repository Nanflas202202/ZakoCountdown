package com.errorsiayusulif.zakocountdown.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.databinding.FragmentOobeEulaBinding
import com.errorsiayusulif.zakocountdown.utils.AccessibilityStatusHelper
import com.errorsiayusulif.zakocountdown.utils.MtbThemeHelper
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

// ==========================================
// 1. 欢迎页面
// ==========================================
class OobeWelcomeFragment : Fragment(R.layout.fragment_oobe_welcome)

// ==========================================
// 2. EULA 许可页面 (带导航条切换)
// ==========================================
class OobeEulaFragment : Fragment() {
    private var _binding: FragmentOobeEulaBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOobeEulaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 导航条切换事件
        binding.toggleButtonGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_eula_eys -> loadLicenseText(R.raw.eula)
                    R.id.btn_eula_zh -> loadLicenseText(R.raw.license_zh)
                    R.id.btn_eula_en -> loadLicenseText(R.raw.license_en)
                }
            }
        }

        // 默认选中工作室协议
        binding.toggleButtonGroup.check(R.id.btn_eula_eys)

        // 同意按钮联动
        binding.checkboxEula.setOnCheckedChangeListener { _, isChecked ->
            val host = activity as? OobeActivity
            host?.isEulaChecked = isChecked
            host?.updateBottomBar(1)
        }
    }

    private fun loadLicenseText(resourceId: Int) {
        try {
            val inputStream: InputStream = resources.openRawResource(resourceId)
            val text = inputStream.bufferedReader().use { it.readText() }
            binding.eulaTextView.text = text
        } catch (e: Exception) {
            binding.eulaTextView.text = "协议加载失败，请重试。"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ==========================================
// 3. 权限引导页面 (采用 Preference 列表)
// ==========================================
class OobePermissionsFragment : PreferenceFragmentCompat() {

    private val requestNotificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) updatePermissionVisibility()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "zako_prefs"
        setPreferencesFromResource(R.xml.oobe_permissions, rootKey)

        findPreference<Preference>("oobe_perm_notif")?.setOnPreferenceClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Toast.makeText(context, "您的系统版本无需手动授予此权限", Toast.LENGTH_SHORT).show()
            }
            true
        }

        findPreference<Preference>("oobe_perm_alarm")?.setOnPreferenceClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) } catch (e: Exception) {}
            }
            true
        }

        findPreference<Preference>("oobe_perm_acc")?.setOnPreferenceClickListener {
            try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (e: Exception) {}
            true
        }

        findPreference<Preference>("oobe_perm_overlay")?.setOnPreferenceClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${requireContext().packageName}"))) } catch (e: Exception) {}
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionVisibility()
    }

    private fun updatePermissionVisibility() {
        val context = requireContext()

        // 1. 通知权限
        val notifPref = findPreference<Preference>("oobe_perm_notif")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotif = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            notifPref?.isVisible = !hasNotif
        } else {
            notifPref?.isVisible = false
        }

        // 2. 精确闹钟
        val alarmPref = findPreference<Preference>("oobe_perm_alarm")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmPref?.isVisible = !alarmManager.canScheduleExactAlarms()
        } else {
            alarmPref?.isVisible = false
        }

        // 3. 无障碍服务
        val accPref = findPreference<Preference>("oobe_perm_acc")
        accPref?.isVisible = !AccessibilityStatusHelper.isAccessibilityServiceEnabled(context)

        // 4. 悬浮窗
        val overlayPref = findPreference<Preference>("oobe_perm_overlay")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            overlayPref?.isVisible = !Settings.canDrawOverlays(context)
        } else {
            overlayPref?.isVisible = false
        }
    }
}

// ==========================================
// 4. 自定义偏好设置页面 (采用 Preference 列表)
// ==========================================
class OobeCustomizationFragment : PreferenceFragmentCompat() {

    private lateinit var appPrefManager: PreferenceManager

    private val pickWallpaperLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                requireActivity().contentResolver.takePersistableUriPermission(it, takeFlags)
                appPrefManager.saveHomepageWallpaperUri(it.toString())
                Toast.makeText(requireContext(), "主页壁纸已设置", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {}
        }
    }

    private val importMtbLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            lifecycleScope.launch {
                val success = MtbThemeHelper.importThemeJson(requireContext(), it, appPrefManager)
                if (success) Toast.makeText(requireContext(), "动态主题导入成功！", Toast.LENGTH_SHORT).show()
                else Toast.makeText(requireContext(), "导入失败: JSON格式错误", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "zako_prefs"
        setPreferencesFromResource(R.xml.oobe_customization, rootKey)
        appPrefManager = PreferenceManager(requireContext())

        // 1. 壁纸
        findPreference<Preference>("oobe_wallpaper")?.setOnPreferenceClickListener {
            pickWallpaperLauncher.launch("image/*")
            true
        }
        // --- 更换应用图标 ---
        findPreference<ListPreference>("key_app_icon")?.setOnPreferenceChangeListener { _, newValue ->
            val aliasName = newValue as String
            com.errorsiayusulif.zakocountdown.utils.IconSwitchHelper.switchIcon(requireContext(), aliasName)

            // 提示用户
            Toast.makeText(
                requireContext(),
                "图标已更改！系统可能需要几秒钟刷新，部分手机桌面可能会短暂闪烁。",
                Toast.LENGTH_LONG
            ).show()
            true
        }
        // 2. 主题色与 MTB 导入逻辑
        val accentPref = findPreference<ListPreference>("accent_color")
        val mtbPref = findPreference<Preference>("oobe_import_mtb")

        // 动态构建颜色列表 (含 MTB 选项)
        val entriesList = mutableListOf("跟随壁纸 (Monet)", "活力粉", "天空蓝", "自定义导入的动态主题")
        val valuesList = mutableListOf(PreferenceManager.ACCENT_MONET, PreferenceManager.ACCENT_PINK, PreferenceManager.ACCENT_BLUE, "CUSTOM_MTB")
        accentPref?.entries = entriesList.toTypedArray()
        accentPref?.entryValues = valuesList.toTypedArray()

        accentPref?.setOnPreferenceChangeListener { _, newValue ->
            appPrefManager.saveAccentColor(newValue as String)
            mtbPref?.isVisible = (newValue == "CUSTOM_MTB")
            true
        }

        mtbPref?.setOnPreferenceClickListener {
            importMtbLauncher.launch(arrayOf("application/json", "*/*"))
            true
        }

        // 初始化 MTB 按钮可见性
        mtbPref?.isVisible = (appPrefManager.getAccentColor() == "CUSTOM_MTB")
    }


    override fun onResume() {
        super.onResume()

        // 动态检查无障碍权限，控制弹窗功能的显示
        val featuresCategory = findPreference<PreferenceCategory>("cat_features")
        val hasAccessibility = AccessibilityStatusHelper.isAccessibilityServiceEnabled(requireContext())

        if (!hasAccessibility) {
            // 如果没给权限，直接隐藏开屏弹窗功能
            findPreference<Preference>("enable_popup_reminder")?.isVisible = false
            findPreference<Preference>("key_popup_duration")?.isVisible = false
        } else {
            findPreference<Preference>("enable_popup_reminder")?.isVisible = true
            findPreference<Preference>("key_popup_duration")?.isVisible = true
        }
    }
}

// ==========================================
// 5. 简单教程页面
// ==========================================
class OobeTutorialFragment : Fragment(R.layout.fragment_oobe_tutorial)