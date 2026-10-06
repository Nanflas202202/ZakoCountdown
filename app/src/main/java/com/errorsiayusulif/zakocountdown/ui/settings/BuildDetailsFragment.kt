// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/BuildDetailsFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.errorsiayusulif.zakocountdown.BuildConfig
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.databinding.FragmentBuildDetailsBinding
import com.errorsiayusulif.zakocountdown.databinding.ItemAboutRowBinding
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class BuildDetailsFragment : Fragment() {
    private var _binding: FragmentBuildDetailsBinding? = null
    private val binding get() = _binding!!

    /**
     * 依赖库条目。
     *
     * 把**许可证类型**也带在数据里，而不是像原来那样「点谁都弹 Apache」——
     * 本应用并非全部依赖都是 Apache 2.0：
     * 「防沉迷」功能改编自 MIT 许可的 anti-addiction，
     * 硬编码成 Apache 会显示错误的许可证（那是法律文本，不能含糊）。
     */
    private data class Dependency(
        val name: String,
        val version: String,
        val license: License
    )

    private enum class License { APACHE_2_0, MIT }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBuildDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val buildTime = Date(BuildConfig.BUILD_TIME)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        // 1. 动态生成构建信息
        val buildInfos = listOf(
            getString(R.string.build_version_name) to BuildConfig.VERSION_NAME,
            getString(R.string.build_version_code) to BuildConfig.VERSION_CODE.toString(),
            getString(R.string.build_type) to BuildConfig.BUILD_TYPE,
            getString(R.string.build_time) to sdf.format(buildTime)
        )

        buildInfos.forEach { (title, value) ->
            // 动态填充 Item 布局并添加到 ll_build_info 容器中
            val rowBinding = ItemAboutRowBinding.inflate(layoutInflater, binding.llBuildInfo, false)
            setupRow(rowBinding, title, value, isClickable = false, onClick = null)
            binding.llBuildInfo.addView(rowBinding.root)
        }

        // 2. 动态生成依赖库信息（点击查看对应的开源许可证）
        //
        // 版本号与 app/build.gradle.kts 里的 `versionXxx` 保持一致；
        // 之前 Material 写的是 1.11.0，而工程实际用的是 1.13.0 —— 顺手更正。
        val dependenciesList = listOf(
            Dependency("Material Components", "1.13.0", License.APACHE_2_0),
            Dependency("AndroidX Core KTX", "1.12.0", License.APACHE_2_0),
            Dependency("AppCompat", "1.6.1", License.APACHE_2_0),
            Dependency("ConstraintLayout", "2.1.4", License.APACHE_2_0),
            Dependency("Fragment", "1.6.2", License.APACHE_2_0),
            Dependency("Lifecycle", "2.7.0", License.APACHE_2_0),
            Dependency("Room", "2.6.1", License.APACHE_2_0),
            Dependency("Preference", "1.2.1", License.APACHE_2_0),
            Dependency("Navigation", "2.7.7", License.APACHE_2_0),
            Dependency("WorkManager", "2.9.0", License.APACHE_2_0),
            Dependency("Coroutines", "1.7.3", License.APACHE_2_0),
            Dependency("Coil", "2.6.0", License.APACHE_2_0),
            Dependency("Gson", "2.10.1", License.APACHE_2_0),

            // 「防沉迷」功能的原始实现来源。作者署名与版本号按 MIT 要求保留 ——
            // 标出基准版本，是为了让「改编自哪一版」有据可查：
            // 上游后续若有改动，能一眼看出本应用是基于哪个版本改的。
            Dependency("anti-addiction", "wd · v3.2.4", License.MIT)
        )

        dependenciesList.forEach { dep ->
            val rowBinding = ItemAboutRowBinding.inflate(layoutInflater, binding.llDependencies, false)
            setupRow(rowBinding, dep.name, dep.version, isClickable = true) {
                showLicenseDialog(dep.name, dep.license)
            }
            binding.llDependencies.addView(rowBinding.root)
        }
    }

    private fun setupRow(
        rowBinding: ItemAboutRowBinding,
        title: String,
        value: String,
        isClickable: Boolean = false,
        onClick: (() -> Unit)? = null
    ) {
        rowBinding.rowTitle.text = title
        rowBinding.rowValue.text = value
        rowBinding.rowTitle.textSize = 14f
        rowBinding.rowValue.textSize = 14f

        val padding = (12 * resources.displayMetrics.density).toInt()
        rowBinding.root.setPadding(0, padding, 0, padding)

        if (isClickable) {
            rowBinding.rowArrow.visibility = View.VISIBLE
            rowBinding.rowArrow.setImageResource(R.drawable.ic_info)
            rowBinding.root.isClickable = true
            rowBinding.root.setOnClickListener { onClick?.invoke() }
        } else {
            rowBinding.rowArrow.visibility = View.GONE
            rowBinding.root.isClickable = false
        }
    }

    /**
     * 许可证查看对话框。
     *
     * 按 [license] 选择要加载的原文，而不是一律加载 Apache ——
     * 每份许可证都必须展示**它自己**的全文，否则等于展示错误的法律文本。
     */
    private fun showLicenseDialog(libraryName: String, license: License) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_component_license, null)
        val tvContent = dialogView.findViewById<TextView>(R.id.tv_license_content)
        val toggleGroup = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggle_group_language)

        fun loadText(resId: Int) {
            try {
                val inputStream: InputStream = resources.openRawResource(resId)
                tvContent.text = inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                tvContent.text = getString(R.string.about_license_load_failed)
            }
        }

        // 两种许可证各自的中/英文原文资源
        val (enRes, zhRes) = when (license) {
            License.APACHE_2_0 -> R.raw.license_apache to R.raw.license_apache_zh
            License.MIT -> R.raw.license_mit_en to R.raw.license_mit_zh
        }

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_lang_zh -> loadText(zhRes)
                    R.id.btn_lang_en -> loadText(enRes)
                }
            }
        }

        // 默认选中中文
        toggleGroup.check(R.id.btn_lang_zh)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.about_license_dialog_title, libraryName))
            .setView(dialogView)
            .setPositiveButton(R.string.common_close, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
