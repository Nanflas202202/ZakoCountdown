// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/PersonalizationFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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

class PersonalizationFragment : PreferenceFragmentCompat() {

    private lateinit var appPreferenceManager: PreferenceManager
    private lateinit var paletteContainer: View
    private lateinit var paletteLayout: LinearLayout

    private val colors = listOf(
        "#000000", "#FFFFFF", "#FFCDD2", "#F8BBD0", "#E1BEE7", "#D1C4E9",
        "#C5CAE9", "#BBDEFB", "#B3E5FC", "#B2EBF2", "#B2DFDB", "#C8E6C9",
        "#DCEDC8", "#F0F4C3", "#FFF9C4", "#FFECB3", "#FFE0B2", "#FFCCBC"
    )

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                try {
                    val contentResolver = requireActivity().contentResolver
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                    appPreferenceManager.saveHomepageWallpaperUri(uri.toString())
                    Toast.makeText(requireContext(), "背景图已设置", Toast.LENGTH_SHORT).show()
                } catch (e: SecurityException) {
                    Toast.makeText(requireContext(), "无法获取图片权限", Toast.LENGTH_LONG).show()
                }
            }
        }

    // 1. 初始化 Preference 逻辑
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "zako_prefs"
        setPreferencesFromResource(R.xml.personalization_preferences, rootKey)
        appPreferenceManager = PreferenceManager(requireContext())

        setupPreferenceListeners()
    }

    // 2. 初始化 View 结构
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_personalization, container, false)
        val listContainer = view.findViewById<ViewGroup>(android.R.id.list_container)
        val prefsView = super.onCreateView(inflater, listContainer, savedInstanceState)
        listContainer.addView(prefsView)

        paletteContainer = view.findViewById(R.id.scrim_color_palette_container)
        paletteLayout = view.findViewById(R.id.scrim_color_palette)

        return view
    }

    // 3. 在 View 创建完成后，初始化与 View 相关的逻辑
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 放在这里执行，确保 paletteContainer 已经初始化
        setupColorPalette()
        updatePaletteVisibility(appPreferenceManager.getScrimColorMode())
    }

    private fun setupPreferenceListeners() {
        findPreference<ListPreference>("theme")?.setOnPreferenceChangeListener { _, newValue ->
            appPreferenceManager.saveTheme(newValue as String)
            activity?.recreate()
            true
        }

        findPreference<ListPreference>("accent_color")?.setOnPreferenceChangeListener { _, newValue ->
            appPreferenceManager.saveAccentColor(newValue as String)
            activity?.recreate()
            true
        }

        findPreference<Preference>("change_wallpaper")?.setOnPreferenceClickListener {
            pickImageLauncher.launch("image/*")
            true
        }

        findPreference<Preference>("remove_wallpaper")?.setOnPreferenceClickListener {
            appPreferenceManager.saveHomepageWallpaperUri(null)
            Toast.makeText(requireContext(), "背景图已移除", Toast.LENGTH_SHORT).show()
            true
        }

        // 遮罩模式监听
        findPreference<ListPreference>("key_scrim_color_mode")?.setOnPreferenceChangeListener { _, newValue ->
            // 这里调用 View 相关方法是安全的，因为监听器触发时 View 肯定已经创建了
            updatePaletteVisibility(newValue as String)
            true
        }

        findPreference<SeekBarPreference>("key_scrim_alpha")?.setOnPreferenceChangeListener { _, _ ->
            true
        }
    }

    private fun updatePaletteVisibility(mode: String) {
        // 增加空安全检查，防止极端情况
        if (::paletteContainer.isInitialized) {
            if (mode == PreferenceManager.SCRIM_MODE_CUSTOM) {
                paletteContainer.visibility = View.VISIBLE
            } else {
                paletteContainer.visibility = View.GONE
            }
        }
    }

    private fun setupColorPalette() {
        val inflater = LayoutInflater.from(context)
        val currentSelected = appPreferenceManager.getScrimCustomColor()

        // 清空旧视图防止重复添加
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

                // 更新选中状态
                paletteLayout.children.forEach { view ->
                    val binding = ItemColorSwatchBinding.bind(view)
                    binding.checkMark.visibility = View.GONE
                }
                swatchBinding.checkMark.visibility = View.VISIBLE
            }
            paletteLayout.addView(swatchBinding.root)
        }
    }
}