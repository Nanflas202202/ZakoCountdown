// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/BuildDetailsFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.errorsiayusulif.zakocountdown.BuildConfig
import com.errorsiayusulif.zakocountdown.R
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

        val buildTime = Date(BuildConfig.BUILD_TIME)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        // 1. 动态生成构建信息
        val buildInfos = listOf(
            "版本名称" to BuildConfig.VERSION_NAME,
            "内部版本号" to BuildConfig.VERSION_CODE.toString(),
            "构建类型" to BuildConfig.BUILD_TYPE,
            "构建时间" to sdf.format(buildTime)
        )

        buildInfos.forEach { (title, value) ->
            val rowBinding = ItemAboutRowBinding.inflate(layoutInflater, binding.llBuildInfo, false)
            setupRow(rowBinding, title, value)
            binding.llBuildInfo.addView(rowBinding.root)
        }

        // 2. 动态生成依赖库信息
        val dependenciesList = listOf(
            "AndroidX Core KTX" to BuildConfig.LIB_CORE_KTX,
            "AndroidX AppCompat" to BuildConfig.LIB_APPCOMPAT,
            "Material Components" to BuildConfig.LIB_MATERIAL,
            "ConstraintLayout" to BuildConfig.LIB_CONSTRAINT,
            "Lifecycle (ViewModel/LiveData)" to BuildConfig.LIB_LIFECYCLE,
            "Fragment KTX" to BuildConfig.LIB_FRAGMENT,
            "Room Database" to BuildConfig.LIB_ROOM,
            "Kotlin Coroutines" to BuildConfig.LIB_COROUTINES,
            "Navigation Component" to BuildConfig.LIB_NAV,
            "AndroidX Preference" to BuildConfig.LIB_PREFERENCE,
            "WorkManager" to BuildConfig.LIB_WORK,
            "Coil (Image Loading)" to BuildConfig.LIB_COIL,
            "Gson (JSON Parser)" to BuildConfig.LIB_GSON
        )

        dependenciesList.forEach { (title, value) ->
            val rowBinding = ItemAboutRowBinding.inflate(layoutInflater, binding.llDependencies, false)
            setupRow(rowBinding, title, value)
            binding.llDependencies.addView(rowBinding.root)
        }
    }

    private fun setupRow(rowBinding: ItemAboutRowBinding, title: String, value: String) {
        rowBinding.rowTitle.text = title
        rowBinding.rowValue.text = value
        rowBinding.rowTitle.textSize = 14f
        rowBinding.rowValue.textSize = 14f
        // 隐藏不需要的箭头
        rowBinding.rowArrow.visibility = View.GONE

        // 设置一点内边距，因为我们在动态添加
        val padding = (12 * resources.displayMetrics.density).toInt()
        rowBinding.root.setPadding(0, padding, 0, padding)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}