// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/PersonalizationFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.children
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.databinding.ItemColorSwatchBinding
import java.io.File
import java.io.FileOutputStream

class PersonalizationFragment : PreferenceFragmentCompat() {

    private lateinit var appPreferenceManager: PreferenceManager
    private lateinit var paletteContainer: View
    private lateinit var paletteLayout: LinearLayout

    private val colors = listOf(
        "#000000", "#FFFFFF", "#FFCDD2", "#F8BBD0", "#E1BEE7", "#D1C4E9",
        "#C5CAE9", "#BBDEFB", "#B3E5FC", "#B2EBF2", "#B2DFDB", "#C8E6C9",
        "#DCEDC8", "#F0F4C3", "#FFF9C4", "#FFECB3", "#FFE0B2", "#FFCCBC"
    )

    override fun onResume() {
        super.onResume()
        // 每次页面可见时，检查是否需要禁用主题设置
        updateThemePreferenceState()
    }

    private fun updateThemePreferenceState() {
        val themePref = findPreference<ListPreference>("theme") ?: return
        val layoutMode = appPreferenceManager.getHomeLayoutMode()
        val isCompact = layoutMode == PreferenceManager.HOME_LAYOUT_COMPACT
        val isLegacyUnlocked = appPreferenceManager.isLegacyThemeUnlockedInCompact()

        if (isCompact && !isLegacyUnlocked) {
            // 紧凑模式且未解锁旧主题，禁用主题切换并提示
            themePref.isEnabled = false
            themePref.summary = "紧凑模式下强制使用 MD3 主题"
        } else {
            // 正常状态
            themePref.isEnabled = true
            // 恢复原来的 summary 逻辑，或者简单使用 entries 里的显示值
            themePref.summary = themePref.entry
        }
    }
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { sourceUri ->
                try {
                    val context = requireContext()
                    val inputStream = context.contentResolver.openInputStream(sourceUri)
                    if (inputStream != null) {
                        val file = File(context.filesDir, "home_wallpaper_cache.png")
                        val outputStream = FileOutputStream(file)
                        inputStream.use { input -> outputStream.use { output -> input.copyTo(output) } }
                        val localUri = Uri.fromFile(file).toString()
                        appPreferenceManager.saveHomepageWallpaperUri(localUri)
                        Toast.makeText(requireContext(), "壁纸设置成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "无法读取图片", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "设置失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "zako_prefs"
        setPreferencesFromResource(R.xml.personalization_preferences, rootKey)
        appPreferenceManager = PreferenceManager(requireContext())
        setupPreferenceListeners()

        // 初始化时更新 Monet 选项状态
        updateAccentColorOptions(appPreferenceManager.getTheme())
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
    }

    private fun setupPreferenceListeners() {
        findPreference<ListPreference>("theme")?.setOnPreferenceChangeListener { _, newValue ->
            val newTheme = newValue as String
            appPreferenceManager.saveTheme(newTheme)

            // 切换主题时，检查是否需要禁用 Monet
            updateAccentColorOptions(newTheme)

            activity?.recreate()
            true
        }
        findPreference<ListPreference>("accent_color")?.setOnPreferenceChangeListener { _, newValue ->
            appPreferenceManager.saveAccentColor(newValue as String)
            activity?.recreate()
            true
        }
        findPreference<Preference>("change_wallpaper")?.setOnPreferenceClickListener {
            pickImageLauncher.launch(arrayOf("image/*"))
            true
        }
        findPreference<Preference>("remove_wallpaper")?.setOnPreferenceClickListener {
            appPreferenceManager.saveHomepageWallpaperUri(null)
            val file = File(requireContext().filesDir, "home_wallpaper_cache.png")
            if (file.exists()) file.delete()
            Toast.makeText(requireContext(), "背景图已移除", Toast.LENGTH_SHORT).show()
            true
        }
        findPreference<ListPreference>("key_scrim_color_mode")?.setOnPreferenceChangeListener { _, newValue ->
            updatePaletteVisibility(newValue as String)
            true
        }
        findPreference<SeekBarPreference>("key_scrim_alpha")?.setOnPreferenceChangeListener { _, _ -> true }
    }

    /**
     * 根据当前主题和系统版本，动态调整强调色选项
     */
    private fun updateAccentColorOptions(currentTheme: String) {
        val accentPref = findPreference<ListPreference>("accent_color") ?: return

        // 允许 Monet 的条件：系统 >= Android 12 且 主题 == M3
        val isMonetSupported = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) &&
                (currentTheme == PreferenceManager.THEME_M3)

        if (isMonetSupported) {
            // 显示所有选项
            accentPref.entries = arrayOf("跟随壁纸 (Monet)", "活力粉", "天空蓝")
            accentPref.entryValues = arrayOf(PreferenceManager.ACCENT_MONET, PreferenceManager.ACCENT_PINK, PreferenceManager.ACCENT_BLUE)
        } else {
            // 移除 Monet 选项
            accentPref.entries = arrayOf("活力粉", "天空蓝")
            accentPref.entryValues = arrayOf(PreferenceManager.ACCENT_PINK, PreferenceManager.ACCENT_BLUE)

            // 如果当前选中的是 Monet，强制切换回 蓝色 (默认)
            if (accentPref.value == PreferenceManager.ACCENT_MONET) {
                accentPref.value = PreferenceManager.ACCENT_BLUE
                appPreferenceManager.saveAccentColor(PreferenceManager.ACCENT_BLUE)
            }
        }
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
            if (colorHex.equals(currentSelected, ignoreCase = true)) {
                swatchBinding.checkMark.visibility = View.VISIBLE
            }
            swatchBinding.root.setOnClickListener {
                appPreferenceManager.saveScrimCustomColor(colorHex)
                paletteLayout.children.forEach { view -> ItemColorSwatchBinding.bind(view).checkMark.visibility = View.GONE }
                swatchBinding.checkMark.visibility = View.VISIBLE
            }
            paletteLayout.addView(swatchBinding.root)
        }
    }
}