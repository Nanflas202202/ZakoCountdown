package com.errorsiayusulif.zakocountdown.ui.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.errorsiayusulif.zakocountdown.databinding.ItemAppSelectorBinding
import com.google.android.material.color.MaterialColors

class AppSelectorAdapter(
    private var apps: List<AppInfo>,
    private var selectedApps: MutableSet<String>,
    private val onAppSelectionChanged: (String, Boolean) -> Unit
) : RecyclerView.Adapter<AppSelectorAdapter.AppViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppSelectorBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    override fun getItemCount(): Int = apps.size

    inner class AppViewHolder(private val binding: ItemAppSelectorBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(appInfo: AppInfo) {
            binding.appIcon.setImageDrawable(appInfo.icon)
            binding.appName.text = appInfo.appName
            binding.appPackageName.text = appInfo.packageName

            // --- 修复 Bug 1: 动态设置 Switch 颜色 ---
            // 获取当前主题的主色 (Primary)
            val colorPrimary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
            val colorSurface = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurfaceVariant)
            val colorOnSurface = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)

            // 定义状态：选中 vs 未选中
            val states = arrayOf(
                intArrayOf(android.R.attr.state_checked), // 选中
                intArrayOf(-android.R.attr.state_checked) // 未选中
            )

            // Thumb (滑块) 颜色：选中->Primary, 未选中->灰色
            val thumbColors = intArrayOf(
                colorPrimary,
                Color.LTGRAY // 或 colorOnSurface 的半透明
            )

            // Track (轨道) 颜色：选中->Primary(半透明), 未选中->灰色(半透明)
            val trackColors = intArrayOf(
                androidx.core.graphics.ColorUtils.setAlphaComponent(colorPrimary, 128),
                androidx.core.graphics.ColorUtils.setAlphaComponent(Color.GRAY, 80)
            )

            binding.appSwitch.thumbTintList = ColorStateList(states, thumbColors)
            binding.appSwitch.trackTintList = ColorStateList(states, trackColors)
            // ----------------------------------------

            // 必须先移除监听器，防止复用时触发逻辑
            binding.appSwitch.setOnCheckedChangeListener(null)

            val isSelected = selectedApps.contains(appInfo.packageName)
            binding.appSwitch.isChecked = isSelected

            binding.appSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedApps.add(appInfo.packageName)
                } else {
                    selectedApps.remove(appInfo.packageName)
                }
                onAppSelectionChanged(appInfo.packageName, isChecked)
            }

            // 点击整行也能触发开关
            binding.root.setOnClickListener {
                binding.appSwitch.toggle()
            }
        }
    }
}