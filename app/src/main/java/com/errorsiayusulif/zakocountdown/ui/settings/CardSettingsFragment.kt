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
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
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

    // --- 【核心修复】声明 preferenceManager ---
    private lateinit var preferenceManager: PreferenceManager

    private val homeViewModel: HomeViewModel by viewModels {
        val app = requireActivity().application as ZakoCountdownApplication
        HomeViewModelFactory(app.repository, app)
    }

    private val colors = listOf(
        null, "#EF9A9A", "#F48FB1", "#CE93D8", "#B39DDB", "#9FA8DA",
        "#81D4FA", "#A5D6A7", "#FFF59D", "#FFCC80", "#FFAB91", "#BCAAA4", "#EEEEEE"
    )

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val contentResolver = requireActivity().contentResolver
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)

                currentEvent?.let { event ->
                    val updatedEvent = event.copy(backgroundUri = uri.toString())
                    homeViewModel.update(updatedEvent)
                    this.currentEvent = updatedEvent
                    Toast.makeText(context, "背景图已设置", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                Toast.makeText(context, "无法获取图片权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCardSettingsBinding.inflate(inflater, container, false)
        // --- 【核心修复】初始化 preferenceManager ---
        preferenceManager = PreferenceManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            currentEvent = homeViewModel.getEventById(args.eventId)
            currentEvent?.let { event ->
                setupInitialState(event)
                setupListeners(event)
                setupColorPalette(event)
                setupWallpaperOptions(event)
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
                // 注意：这里我们使用当前的 currentEvent，因为它可能已经被 update 更新过（比如改了颜色）
                currentEvent?.let {
                    val updated = it.copy(displayMode = newMode)
                    homeViewModel.update(updated)
                    currentEvent = updated
                }
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

    private fun setupWallpaperOptions(event: CountdownEvent) {
        // --- 现在这里可以正常使用 preferenceManager 了 ---
        val isGlobalUnlock = preferenceManager.isGlobalAlphaUnlocked()

        // 1. 壁纸设置仅对置顶有效
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

        // 2. 透明度区域：置顶 OR 开发者解锁 可见
        // 注意：这里我们控制的是独立的 View，不再受 wallpaperSettingsLayout 的影响
        if (event.isPinned || isGlobalUnlock) {
            binding.sliderAlpha.visibility = View.VISIBLE
            binding.textAlphaLabel.visibility = View.VISIBLE
        } else {
            binding.sliderAlpha.visibility = View.GONE
            binding.textAlphaLabel.visibility = View.GONE
        }
    }

    private fun setupColorPalette(event: CountdownEvent) {
        val inflater = LayoutInflater.from(context)
        for (colorHex in colors) {
            val swatchBinding = ItemColorSwatchBinding.inflate(inflater, binding.colorPalette, false)

            val color = if (colorHex == null) {
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
                    this.currentEvent = updatedEvent
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}