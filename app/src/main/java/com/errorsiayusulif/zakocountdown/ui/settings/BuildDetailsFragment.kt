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

        // 2. 动态生成依赖库信息 (添加点击事件，展示 Apache 2.0 许可证)
        val dependenciesList = listOf(
            "Material Components" to "1.11.0",
            "AppCompat" to "1.6.1",
            "Room" to "2.6.1",
            "Coroutines" to "1.7.3",
            "Navigation" to "2.7.7",
            "WorkManager" to "2.9.0",
            "Coil" to "2.6.0"
        )

        dependenciesList.forEach { (title, value) ->
            // 动态填充 Item 布局并添加到 ll_dependencies 容器中
            val rowBinding = ItemAboutRowBinding.inflate(layoutInflater, binding.llDependencies, false)
            setupRow(rowBinding, title, value, isClickable = true) {
                showApacheLicenseDialog(title)
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

    private fun showApacheLicenseDialog(libraryName: String) {
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

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_lang_zh -> loadText(R.raw.license_apache_zh)
                    R.id.btn_lang_en -> loadText(R.raw.license_apache)
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
