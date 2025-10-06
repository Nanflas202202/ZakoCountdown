// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/AppSelectorAdapter.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.errorsiayusulif.zakocountdown.databinding.ItemAppSelectorBinding

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

            // 移除旧的监听器，防止复用时出错
            binding.appSwitch.setOnCheckedChangeListener(null)

            // 设置开关的当前状态
            binding.appSwitch.isChecked = selectedApps.contains(appInfo.packageName)

            // 设置新的监听器
            binding.appSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedApps.add(appInfo.packageName)
                } else {
                    selectedApps.remove(appInfo.packageName)
                }
                // 通知外部（Fragment），用户的选择发生了变化
                onAppSelectionChanged(appInfo.packageName, isChecked)
            }
        }
    }
}