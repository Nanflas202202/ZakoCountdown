// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/SavedThemesFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.data.SavedTheme
import com.errorsiayusulif.zakocountdown.databinding.FragmentSavedThemesBinding
import com.errorsiayusulif.zakocountdown.databinding.ItemSavedThemeBinding
import com.errorsiayusulif.zakocountdown.utils.MtbThemeEngine
import com.errorsiayusulif.zakocountdown.utils.MtbThemeHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「已保存的主题」二级页面。
 *
 * 每次从 Material Theme Builder 导入（zip / json）都会自动在这里留一条记录，
 * 可以随时切回任意一套，不必再去找原始文件。
 *
 * 交互：
 *   · 点条目      → 应用这套主题（随后重建 Activity 生效）
 *   · 点右侧色块  → 同「点条目」，仅作视觉锚点
 *   · 长按条目    → 重命名
 *   · 菜单/按钮   → 删除
 *
 * 用普通 [Fragment] + RecyclerView 而不是 PreferenceFragmentCompat：
 * 这里的每一条需要「名称 + 时间 + 右侧色块 + 使用中标记」四要素，
 * 用 Preference 体系要绕很多弯（自定义 Preference + 自定义布局），
 * 直接写 Adapter 更直观，也与 [LogFileListFragment] 的写法一致。
 */
class SavedThemesFragment : Fragment() {

    private var _binding: FragmentSavedThemesBinding? = null
    private val binding get() = _binding!!

    private lateinit var appPreferenceManager: PreferenceManager
    private lateinit var adapter: ThemeAdapter

    /** 当前正在使用的主题名，用来给条目打「使用中」标记。 */
    private var activeThemeName: String = ""

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedThemesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appPreferenceManager = PreferenceManager(requireContext())
        activeThemeName = MtbThemeEngine.currentThemeName(requireContext())

        adapter = ThemeAdapter()
        binding.themesList.layoutManager = LinearLayoutManager(requireContext())
        binding.themesList.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        // 从重命名/删除等操作回来后重新取一次数据
        refresh()
    }

    override fun onDestroyView() {
        // 避免 RecyclerView 持有已销毁视图的引用
        binding.themesList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun refresh() {
        activeThemeName = MtbThemeEngine.currentThemeName(requireContext())
        val themes = MtbThemeHelper.savedThemes(requireContext())
        adapter.submit(themes)

        val empty = themes.isEmpty()
        binding.emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        binding.themesList.visibility = if (empty) View.GONE else View.VISIBLE
    }

    // ========================================================================
    // 操作
    // ========================================================================

    private fun applyTheme(theme: SavedTheme) {
        if (theme.name == activeThemeName) {
            // 已经是当前主题，不必重建 Activity
            Toast.makeText(
                requireContext(),
                getString(R.string.personalization_saved_theme_already_active, theme.name),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        MtbThemeHelper.applySavedTheme(requireContext(), theme, appPreferenceManager)
        Toast.makeText(
            requireContext(),
            getString(R.string.personalization_saved_theme_applied, theme.name),
            Toast.LENGTH_SHORT
        ).show()
        // 主题色整体变了，重建 Activity 才能让所有页面生效
        activity?.recreate()
    }

    /** 重命名：弹出输入框，预填当前名称。 */
    private fun renameTheme(theme: SavedTheme) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(theme.name)
            setSelection(theme.name.length)
            setPadding(48, 32, 48, 32)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.personalization_saved_theme_rename)
            .setView(input)
            .setPositiveButton(R.string.common_ok) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) return@setPositiveButton

                MtbThemeHelper.renameSavedTheme(requireContext(), theme.id, newName)
                // 如果改的正是当前使用中的那套，同步更新「使用中」的记录名，
                // 否则列表里会出现「改名后就不是当前主题了」的错觉
                if (theme.name == activeThemeName) {
                    com.errorsiayusulif.zakocountdown.data.ThemeArchive
                        .rememberName(requireContext(), newName)
                }
                refresh()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun confirmDelete(theme: SavedTheme) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.personalization_saved_theme_delete)
            .setMessage(getString(R.string.personalization_saved_theme_delete_confirm, theme.name))
            .setPositiveButton(R.string.common_delete) { _, _ ->
                MtbThemeHelper.deleteSavedTheme(requireContext(), theme.id)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.personalization_saved_theme_deleted, theme.name),
                    Toast.LENGTH_SHORT
                ).show()
                refresh()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    // ========================================================================
    // Adapter
    // ========================================================================

    private inner class ThemeAdapter : RecyclerView.Adapter<ThemeAdapter.Holder>() {

        private val items = mutableListOf<SavedTheme>()

        fun submit(themes: List<SavedTheme>) {
            items.clear()
            items.addAll(themes)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val itemBinding = ItemSavedThemeBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return Holder(itemBinding)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class Holder(private val itemBinding: ItemSavedThemeBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(theme: SavedTheme) {
                itemBinding.themeName.text = theme.name
                itemBinding.themeTime.text = timeFormat.format(Date(theme.importedAt))

                // 右侧色块：取该主题浅色方案的 primary
                itemBinding.themeColor.backgroundTintList =
                    ColorStateList.valueOf(resolveSwatchColor(theme))

                // 「使用中」标记
                val isActive = theme.name == activeThemeName
                itemBinding.themeActiveBadge.visibility = if (isActive) View.VISIBLE else View.GONE

                // 点条目 / 点色块 → 应用主题
                itemBinding.root.setOnClickListener { applyTheme(theme) }
                itemBinding.themeColor.setOnClickListener { applyTheme(theme) }

                // 长按 → 重命名 / 删除。
                // 没有为删除单独放一个图标按钮：列表项右侧已经被色块占用，
                // 再塞删除图标会显得拥挤；长按菜单是这类「素材库」的常见做法。
                itemBinding.root.setOnLongClickListener {
                    showItemMenu(theme)
                    true
                }
            }
        }
    }

    /** 长按条目的操作菜单：重命名 / 删除。 */
    private fun showItemMenu(theme: SavedTheme) {
        val options = arrayOf(
            getString(R.string.personalization_saved_theme_rename),
            getString(R.string.personalization_saved_theme_delete)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(theme.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> renameTheme(theme)
                    1 -> confirmDelete(theme)
                }
            }
            .show()
    }

    /**
     * 解析色块颜色。
     *
     * primary 缺失或非法时退回主题属性色，保证色块永远有可见颜色
     * （否则会出现一个透明或全黑的圆，看起来像渲染错误）。
     */
    private fun resolveSwatchColor(theme: SavedTheme): Int {
        val hex = theme.light["primary"] ?: theme.dark["primary"]
        if (hex != null) {
            try {
                return Color.parseColor(hex)
            } catch (e: IllegalArgumentException) {
                // 落到下面的兜底
            }
        }
        // 注意用 android.R.attr.colorPrimary：Material 1.13 起不再定义 colorPrimary，
        // 合成 material.R.attr.colorPrimary 会编译不过（同 MainActivity 里的处理）
        return com.google.android.material.color.MaterialColors.getColor(
            binding.root,
            android.R.attr.colorPrimary,
            Color.GRAY
        )
    }
}
