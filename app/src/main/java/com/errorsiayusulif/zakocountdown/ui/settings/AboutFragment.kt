// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/AboutFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.errorsiayusulif.zakocountdown.BuildConfig
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.databinding.FragmentAboutBinding
import com.errorsiayusulif.zakocountdown.databinding.ItemAboutRowBinding

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.aboutVersion.text = "版本 ${BuildConfig.VERSION_NAME}"

        // --- 绑定所有列表项 ---
        setupRow(binding.rowDeveloper, "开发者", "Shigure Hatsuka")
        setupRow(binding.rowStudio, "开发商", "Errorsia Yusulif Studio")

        setupRow(binding.rowDetails, "详细信息", isClickable = true) {
            findNavController().navigate(R.id.action_aboutFragment_to_buildDetailsFragment)
        }
        // in setupRow for license
        setupRow(binding.rowLicense, "查看许可证", isClickable = true) {
            findNavController().navigate(R.id.action_aboutFragment_to_licenseFragment)
        }

        // 联系方式分组
        setupRow(binding.rowContact, "联系作者 (邮箱)", isClickable = true) {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:your.email@example.com")) // 替换为你的邮箱
            startActivitySafely(intent, "未找到邮件应用")
        }
        setupRow(binding.rowWebsite, "访问官网", isClickable = true) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://your.website.com")) // 替换为你的官网
            startActivitySafely(intent, "未找到浏览器")
        }
        setupRow(binding.rowGithub, "访问Github主页", isClickable = true) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/your_id")) // 替换为你的Github主页
            startActivitySafely(intent, "未找到浏览器")
        }
        // --- 【UI优化】将所有联系方式分组 ---
        setupRow(binding.rowContactTelegram, "加入Telegram群组", isClickable = true) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/your_group")) // 替换为你的群组链接
            startActivitySafely(intent, "未找到应用打开链接")
        }
        setupRow(binding.rowContactBilibili, "访问Bilibili频道", isClickable = true) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://space.bilibili.com/your_id")) // 替换为你的B站主页
            startActivitySafely(intent, "未找到应用打开链接")
        }
    }

    // --- 【核心修复】补全这个缺失的辅助方法 ---
    private fun setupRow(
        rowBinding: ItemAboutRowBinding,
        title: String,
        value: String? = null,
        isClickable: Boolean = false,
        onClick: (() -> Unit)? = null
    ) {
        rowBinding.rowTitle.text = title
        if (value != null) {
            rowBinding.rowValue.text = value
            rowBinding.rowValue.visibility = View.VISIBLE
        } else {
            rowBinding.rowValue.visibility = View.GONE
        }

        if (isClickable) {
            rowBinding.rowArrow.visibility = View.VISIBLE
            rowBinding.root.setOnClickListener { onClick?.invoke() }
        } else {
            rowBinding.rowArrow.visibility = View.GONE
            rowBinding.root.isClickable = false
        }
    }

    // --- 【核心修复】补全这个缺失的辅助方法 ---
    private fun startActivitySafely(intent: Intent, errorMessage: String) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}