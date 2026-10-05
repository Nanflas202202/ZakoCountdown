// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/PersonalizationFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.errorsiayusulif.zakocountdown.data.PreferenceKeys
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.databinding.ItemColorSwatchBinding
import com.errorsiayusulif.zakocountdown.utils.MtbThemeEngine
import com.errorsiayusulif.zakocountdown.utils.MtbThemeHelper
import com.errorsiayusulif.zakocountdown.utils.NavModeHelper
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class PersonalizationFragment : ZakoPreferenceFragment() {

    private lateinit var appPreferenceManager: PreferenceManager
    private lateinit var paletteContainer: View
    private lateinit var paletteLayout: LinearLayout

    private val colors = listOf(
        "#000000", "#FFFFFF", "#FFCDD2", "#F8BBD0", "#E1BEE7", "#D1C4E9",
        "#C5CAE9", "#BBDEFB", "#B3E5FC", "#B2EBF2", "#B2DFDB", "#C8E6C9",
        "#DCEDC8", "#F0F4C3", "#FFF9C4", "#FFECB3", "#FFE0B2", "#FFCCBC"
    )

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { sourceUri ->
            try {
                val context = requireContext()
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                if (inputStream != null) {
                    val file = File(context.filesDir, "home_wallpaper_cache.png")
                    FileOutputStream(file).use { output -> inputStream.use { input -> input.copyTo(output) } }
                    appPreferenceManager.saveHomepageWallpaperUri(Uri.fromFile(file).toString())
                    Toast.makeText(context, R.string.personalization_wallpaper_set, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.common_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 侧滑栏顶部图像。
     * 与主页壁纸同样先拷贝到私有沙盒再记 URI —— 系统授予的 content:// 权限是临时的，
     * 直接存原 URI 会导致重启后侧滑栏图像丢失。
     */
    private val pickDrawerHeaderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { sourceUri ->
            try {
                val context = requireContext()
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                if (inputStream != null) {
                    val file = File(context.filesDir, "drawer_header_cache.png")
                    FileOutputStream(file).use { output -> inputStream.use { input -> input.copyTo(output) } }
                    appPreferenceManager.saveDrawerHeaderImageUri(Uri.fromFile(file).toString())
                    // 用户刚选完图，顺手把开关打开，否则看不到任何变化会以为没生效
                    appPreferenceManager.setDrawerHeaderImageEnabled(true)
                    Toast.makeText(context, R.string.personalization_drawer_header_set, Toast.LENGTH_SHORT).show()
                    activity?.recreate()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.common_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importMtbJsonLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            lifecycleScope.launch {
                val saved = MtbThemeHelper.importTheme(requireContext(), it, appPreferenceManager)
                if (saved != null) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.personalization_theme_imported_named, saved.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    // 重新加载 Activity 以应用新主题
                    activity?.recreate()
                } else {
                    Toast.makeText(requireContext(), R.string.personalization_theme_import_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "zako_prefs"
        setPreferencesFromResource(R.xml.personalization_preferences, rootKey)
        appPreferenceManager = PreferenceManager(requireContext())

        // --- 核心防御 ---
        // 手动检查并修复所有可能的 ListPreference，防止 XML 配置丢失导致崩溃
        safeCheckListPreference(PreferenceKeys.THEME_MODE, R.array.theme_entries, R.array.theme_values)
        safeCheckListPreference(PreferenceKeys.SCRIM_COLOR_MODE, R.array.scrim_color_entries, R.array.scrim_color_values)

        setupPreferenceListeners()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_personalization, container, false)
        val listContainer = view.findViewById<ViewGroup>(android.R.id.list_container)
        val prefsView = super.onCreateView(inflater, listContainer, savedInstanceState)
        listContainer.addView(prefsView)
        paletteContainer = view.findViewById(R.id.scrim_color_palette_container)
        paletteLayout = view.findViewById(R.id.scrim_color_palette)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupColorPalette()
        updatePaletteVisibility(appPreferenceManager.getScrimColorMode())

        // MTB 染色挂载
        MtbThemeEngine.applyToPreferenceFragment(this)
    }

    override fun onResume() {
        super.onResume()
        updateThemePreferenceState()
        // 动态更新强调色选项 (包含 Monet 逻辑)
        updateAccentColorOptions()
        // 非侧滑导航模式下，侧滑栏根本出不来，相关设置一并禁用
        updateDrawerPreferenceState()
        // 导入/删除主题后，摘要里的套数要跟着变
        updateSavedThemesSummary()
    }

    /** 刷新「已保存的主题」摘要，显示当前存了几套。 */
    private fun updateSavedThemesSummary() {
        val pref = findPreference<Preference>("saved_themes") ?: return
        val count = MtbThemeHelper.savedThemes(requireContext()).size
        pref.summary = if (count == 0) {
            getString(R.string.personalization_saved_themes_summary_empty)
        } else {
            getString(R.string.personalization_saved_themes_summary, count)
        }
    }

    /**
     * 侧滑栏相关的个性化设置（抽屉顶图）只在「侧滑抽屉」导航形态下有意义。
     *
     * 底部导航栏 / 紧凑模式下用了底部栏，抽屉既看不到也打不开，
     * 所以这里直接**隐藏**整组设置（而不是置灰）—— 置灰会让人以为功能坏了，
     * 隐藏才是「不提供无关选项」的正确做法。切回侧滑抽屉后自动恢复。
     */
    private fun updateDrawerPreferenceState() {
        val drawerAvailable = NavModeHelper.isDrawer(requireContext())

        // 整组一起隐藏，包括分类标题
        findPreference<androidx.preference.PreferenceCategory>(PreferenceKeys.CATEGORY_DRAWER_HEADER)?.let {
            it.isVisible = drawerAvailable
        }

        listOf(
            PreferenceKeys.DRAWER_HEADER_IMAGE_ENABLED,
            PreferenceKeys.DRAWER_HEADER_PICK,
            PreferenceKeys.DRAWER_HEADER_CLEAR
        ).forEach { key ->
            findPreference<Preference>(key)?.isVisible = drawerAvailable
        }
    }

    /**
     * 兜底检查：确保 ListPreference 绝对拥有数组，否则从资源文件强行重新赋值
     */
    private fun safeCheckListPreference(key: String, entriesResId: Int, valuesResId: Int) {
        val listPref = findPreference<ListPreference>(key)
        if (listPref != null) {
            val e = listPref.entries
            val v = listPref.entryValues
            if (e == null || e.isEmpty() || v == null || v.isEmpty()) {
                Log.e("ZakoDebug", "ListPreference [$key] arrays were null! Forcing reload from resources.")
                listPref.setEntries(entriesResId)
                listPref.setEntryValues(valuesResId)
            } else {
                Log.d("ZakoDebug", "ListPreference [$key] is safe. Entries: ${e.size}, Values: ${v.size}")
            }
        }
    }

    private fun setupPreferenceListeners() {
        findPreference<ListPreference>(PreferenceKeys.THEME_MODE)?.setOnPreferenceChangeListener { _, newValue ->
            val theme = newValue as String
            appPreferenceManager.saveTheme(theme)
            // 主题改变，可能影响 Monet 的可用性，重新计算
            updateAccentColorOptions()
            activity?.recreate()
            true
        }

        findPreference<ListPreference>("accent_color")?.setOnPreferenceChangeListener { _, newValue ->
            val color = newValue as String
            appPreferenceManager.saveAccentColor(color)

            // 如果选了非 MTB 颜色，关闭 MTB 引擎标志位
            if (color != PreferenceManager.ACCENT_CUSTOM_MTB) {
                requireContext().getSharedPreferences("zako_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean(MtbThemeHelper.PREF_IS_MTB_ENABLED, false).apply()
            }
            activity?.recreate()
            true
        }

        findPreference<Preference>("import_mtb_theme")?.setOnPreferenceClickListener {
            importMtbJsonLauncher.launch(arrayOf("application/json", "application/zip", "*/*"))
            true
        }

        findPreference<Preference>("saved_themes")?.setOnPreferenceClickListener {
            // 二级页面展示全部已保存主题（列表形式，含色块 / 名称 / 导入时间）
            findNavController().navigate(R.id.action_personalizationFragment_to_savedThemesFragment)
            true
        }

        findPreference<Preference>("change_wallpaper")?.setOnPreferenceClickListener {
            pickImageLauncher.launch(arrayOf("image/*"))
            true
        }

        findPreference<Preference>("remove_wallpaper")?.setOnPreferenceClickListener {
            appPreferenceManager.saveHomepageWallpaperUri(null)
            activity?.recreate()
            true
        }

        // ==========================================
        // v0.9.1 新增：侧滑栏顶部图像自定义
        // ==========================================
        findPreference<androidx.preference.SwitchPreferenceCompat>(PreferenceKeys.DRAWER_HEADER_IMAGE_ENABLED)
            ?.setOnPreferenceChangeListener { _, newValue ->
                appPreferenceManager.setDrawerHeaderImageEnabled(newValue as Boolean)
                // 抽屉头部由 MainActivity 渲染，重建才能立刻看到效果
                activity?.recreate()
                true
            }

        findPreference<Preference>("drawer_header_pick")?.setOnPreferenceClickListener {
            pickDrawerHeaderLauncher.launch(arrayOf("image/*"))
            true
        }

        findPreference<Preference>("drawer_header_clear")?.setOnPreferenceClickListener {
            appPreferenceManager.saveDrawerHeaderImageUri(null)
            Toast.makeText(requireContext(), R.string.personalization_drawer_header_cleared, Toast.LENGTH_SHORT).show()
            activity?.recreate()
            true
        }

        findPreference<ListPreference>(PreferenceKeys.SCRIM_COLOR_MODE)?.setOnPreferenceChangeListener { _, newValue ->
            updatePaletteVisibility(newValue as String)
            true
        }
    }

    private fun updateThemePreferenceState() {
        val themePref = findPreference<ListPreference>(PreferenceKeys.THEME_MODE) ?: return
        val isCompact = appPreferenceManager.getHomeLayoutMode() == PreferenceManager.HOME_LAYOUT_COMPACT
        val isLegacyUnlocked = appPreferenceManager.isLegacyThemeUnlockedInCompact()

        if (isCompact && !isLegacyUnlocked) {
            themePref.isEnabled = false
            themePref.summary = getString(R.string.theme_md3_forced_compact)
        } else {
            themePref.isEnabled = true
            themePref.summary = themePref.entry
        }
    }

    /**
     * 动态计算并构建“强调色”选项的数组，这是解决崩溃的核心
     */
    private fun updateAccentColorOptions() {
        val accentPref = findPreference<ListPreference>("accent_color") ?: return

        val currentTheme = appPreferenceManager.getTheme()
        val isMonetSupported = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) && (currentTheme == PreferenceManager.THEME_M3)
        val isMtbEnabled = requireContext().getSharedPreferences("zako_prefs", Context.MODE_PRIVATE).getBoolean(MtbThemeHelper.PREF_IS_MTB_ENABLED, false)

        val entriesList = mutableListOf<String>()
        val valuesList = mutableListOf<String>()

        // 强调色名称统一从已本地化的数组资源读取，避免任何硬编码文案
        val localizedAccentNames = resources.getStringArray(R.array.color_entries)
        fun accentName(index: Int, fallbackRes: Int): String =
            localizedAccentNames.getOrNull(index) ?: getString(fallbackRes)

        if (isMonetSupported) {
            entriesList.add(accentName(0, R.string.personalization_accent_color))
            valuesList.add(PreferenceManager.ACCENT_MONET)
        }

        entriesList.add(accentName(1, R.string.personalization_accent_color))
        valuesList.add(PreferenceManager.ACCENT_PINK)

        entriesList.add(accentName(2, R.string.personalization_accent_color))
        valuesList.add(PreferenceManager.ACCENT_BLUE)

        if (isMtbEnabled) {
            entriesList.add(getString(R.string.theme_custom_mtb))
            valuesList.add(PreferenceManager.ACCENT_CUSTOM_MTB)
        }

        Log.d("ZakoDebug", "Rebuilding accent_color array. Size: ${entriesList.size}")

        // 核心防御：绝对不能传入空数组
        if (entriesList.isEmpty() || valuesList.isEmpty()) {
            Log.e("ZakoDebug", "FATAL: Accent color arrays are empty! Forcing fallback.")
            entriesList.add(accentName(2, R.string.personalization_accent_color))
            valuesList.add(PreferenceManager.ACCENT_BLUE)
        }

        // 重新赋值给 ListPreference
        accentPref.entries = entriesList.toTypedArray()
        accentPref.entryValues = valuesList.toTypedArray()

        // 检查当前选中的值是否还在新的列表中
        val currentValue = appPreferenceManager.getAccentColor()
        if (!valuesList.contains(currentValue)) {
            val fallbackValue = if (isMtbEnabled) PreferenceManager.ACCENT_CUSTOM_MTB else PreferenceManager.ACCENT_BLUE
            Log.w("ZakoDebug", "Current accent value [$currentValue] not in list. Falling back to [$fallbackValue]")
            accentPref.value = fallbackValue
            appPreferenceManager.saveAccentColor(fallbackValue)
        } else {
            // 确保 preference 内部状态同步
            accentPref.value = currentValue
        }

        accentPref.summary = accentPref.entry
    }

    private fun updatePaletteVisibility(mode: String) {
        if (::paletteContainer.isInitialized) {
            paletteContainer.visibility = if (mode == PreferenceManager.SCRIM_MODE_CUSTOM) View.VISIBLE else View.GONE
        }
    }

    private fun setupColorPalette() {
        val inflater = LayoutInflater.from(context)
        val currentSelected = appPreferenceManager.getScrimCustomColor()
        paletteLayout.removeAllViews()
        for (colorHex in colors) {
            val swatchBinding = ItemColorSwatchBinding.inflate(inflater, paletteLayout, false)
            val color = Color.parseColor(colorHex)
            (swatchBinding.colorView.background as GradientDrawable).setColor(color)
            if (colorHex.equals(currentSelected, ignoreCase = true)) swatchBinding.checkMark.visibility = View.VISIBLE
            swatchBinding.root.setOnClickListener {
                appPreferenceManager.saveScrimCustomColor(colorHex)
                paletteLayout.children.forEach { ItemColorSwatchBinding.bind(it).checkMark.visibility = View.GONE }
                swatchBinding.checkMark.visibility = View.VISIBLE
            }
            paletteLayout.addView(swatchBinding.root)
        }
    }
}
