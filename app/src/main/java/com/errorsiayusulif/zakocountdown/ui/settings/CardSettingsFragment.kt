// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/CardSettingsFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.data.CountdownEvent
import com.errorsiayusulif.zakocountdown.databinding.FragmentCardSettingsBinding
import com.errorsiayusulif.zakocountdown.databinding.ItemColorSwatchBinding
import com.errorsiayusulif.zakocountdown.ui.home.HomeViewModel
import com.errorsiayusulif.zakocountdown.ui.home.HomeViewModelFactory
import kotlinx.coroutines.launch

class CardSettingsFragment : Fragment() {

    private var _binding: FragmentCardSettingsBinding? = null
    private val binding get() = _binding!!
    private val args: CardSettingsFragmentArgs by navArgs()
    private var currentEvent: CountdownEvent? = null

    private val homeViewModel: HomeViewModel by viewModels {
        val app = requireActivity().application as ZakoCountdownApplication
        HomeViewModelFactory(app.repository, app)
    }

    private val colors = listOf(
        null, // 代表默认颜色
        "#EF9A9A", // 浅红
        "#F48FB1", // 浅粉
        "#CE93D8", // 浅紫
        "#B39DDB", // 薰衣草紫
        "#9FA8DA", // 靛蓝灰
        "#81D4FA", // 天空蓝
        "#A5D6A7", // 浅绿
        "#FFF59D", // 柠檬黄
        "#FFCC80", // 橙黄
        "#FFAB91", // 珊瑚粉
        "#BCAAA4", // 暖灰
        "#EEEEEE"  // 亮灰
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCardSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            currentEvent = homeViewModel.getEventById(args.eventId)
            currentEvent?.let {
                setupInitialState(it)
                setupListeners(it)
                setupColorPalette(it)
                // --- 【新功能】根据是否置顶，显示/隐藏背景图设置 ---
                setupWallpaperOptions(it)
            }
        }
    }
    private fun setupInitialState(event: CountdownEvent) {
        when (event.displayMode) {
            CountdownEvent.DISPLAY_MODE_DETAILED -> binding.radioDetailed.isChecked = true
            CountdownEvent.DISPLAY_MODE_FULL -> binding.radioFull.isChecked = true
            else -> binding.radioSimple.isChecked = true
        }
        binding.sliderAlpha.value = event.cardAlpha ?: 1.0f
    }

    private fun setupListeners(event: CountdownEvent) {
        binding.radioGroupDisplayMode.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                R.id.radio_detailed -> CountdownEvent.DISPLAY_MODE_DETAILED
                R.id.radio_full -> CountdownEvent.DISPLAY_MODE_FULL
                else -> CountdownEvent.DISPLAY_MODE_SIMPLE
            }
            if (newMode != event.displayMode) {
                homeViewModel.update(event.copy(displayMode = newMode))
            }
        }
        binding.sliderAlpha.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                currentEvent?.let {
                    val updatedEvent = it.copy(cardAlpha = value)
                    homeViewModel.update(updatedEvent)
                    this.currentEvent = updatedEvent
                }
            }
        }
    }
    private fun setupColorPalette(event: CountdownEvent) {
        val inflater = LayoutInflater.from(context)
        for (colorHex in colors) {
            val swatchBinding = ItemColorSwatchBinding.inflate(inflater, binding.colorPalette, false)

            val color = if (colorHex == null) {
                // 获取主题中的默认卡片颜色
                com.google.android.material.R.attr.colorSurface
            } else {
                Color.parseColor(colorHex)
            }

            (swatchBinding.colorView.background as GradientDrawable).setColor(color)

            if (event.colorHex == colorHex) {
                swatchBinding.checkMark.visibility = View.VISIBLE
            }

            swatchBinding.root.setOnClickListener {
                currentEvent?.let { current ->
                    val updatedEvent = current.copy(colorHex = colorHex)
                    homeViewModel.update(updatedEvent)
                    this.currentEvent = updatedEvent // 更新本地实例
                    updateColorPaletteSelection()
                }
            }
            binding.colorPalette.addView(swatchBinding.root)
        }
    }

    private fun updateColorPaletteSelection() {
        binding.colorPalette.children.forEachIndexed { index, view ->
            val swatchBinding = ItemColorSwatchBinding.bind(view)
            val colorHexForThisSwatch = colors[index]
            swatchBinding.checkMark.visibility = if (currentEvent?.colorHex == colorHexForThisSwatch) View.VISIBLE else View.GONE
        }
    }
    // --- 【新功能】图片选择器启动器 ---
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val contentResolver = requireActivity().contentResolver
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)

                currentEvent?.let { event ->
                    val updatedEvent = event.copy(backgroundUri = uri.toString())
                    homeViewModel.update(updatedEvent)
                    this.currentEvent = updatedEvent
                }
            }
        }
    private fun setupWallpaperOptions(event: CountdownEvent) {
        Log.d("CardSettings", "Event: '${event.title}', isPinned: ${event.isPinned}")

        // --- 【核心修复】使用 LinearLayout 的 ID ---
        if (event.isPinned) {
            binding.wallpaperSettingsLayout.visibility = View.VISIBLE
            binding.buttonChangeWallpaper.setOnClickListener {
                pickImageLauncher.launch("image/*")
            }
            binding.buttonRemoveWallpaper.setOnClickListener {
                currentEvent?.let { current ->
                    val updatedEvent = current.copy(backgroundUri = null)
                    homeViewModel.update(updatedEvent)
                    this.currentEvent = updatedEvent
                    Toast.makeText(context, "背景图已移除", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            binding.wallpaperSettingsLayout.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}