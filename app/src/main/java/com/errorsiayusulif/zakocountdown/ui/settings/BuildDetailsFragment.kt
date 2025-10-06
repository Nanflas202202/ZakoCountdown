// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/BuildDetailsFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.errorsiayusulif.zakocountdown.BuildConfig
import com.errorsiayusulif.zakocountdown.databinding.FragmentBuildDetailsBinding
import com.errorsiayusulif.zakocountdown.databinding.ItemAboutRowBinding
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

        // --- 构建信息 ---
        val buildTime = Date(BuildConfig.BUILD_TIME)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        setupRow(binding.rowVersionName, "版本名称", BuildConfig.VERSION_NAME)
        setupRow(binding.rowVersionCode, "版本号", BuildConfig.VERSION_CODE.toString())
        setupRow(binding.rowBuildType, "构建类型", BuildConfig.BUILD_TYPE)
        setupRow(binding.rowBuildTime, "构建时间", sdf.format(buildTime))

        // --- 依赖库版本 (直接写死) ---
        setupRow(binding.rowLibMaterial, "Material Components", "1.11.0")
        setupRow(binding.rowLibAppcompat, "AppCompat", "1.6.1")
        setupRow(binding.rowLibRoom, "Room", "2.6.1")
        setupRow(binding.rowLibCoroutines, "Coroutines", "1.7.3")
        setupRow(binding.rowLibNavigation, "Navigation", "2.7.7")
        setupRow(binding.rowLibWorkmanager, "WorkManager", "2.9.0")
        setupRow(binding.rowLibCoil, "Coil", "2.6.0")
    }

    // 复用 AboutFragment 中的 setupRow 逻辑
    private fun setupRow(rowBinding: ItemAboutRowBinding, title: String, value: String) {
        rowBinding.rowTitle.text = title
        rowBinding.rowValue.text = value
        // 在这个页面，所有行都不可点击
        rowBinding.rowArrow.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}