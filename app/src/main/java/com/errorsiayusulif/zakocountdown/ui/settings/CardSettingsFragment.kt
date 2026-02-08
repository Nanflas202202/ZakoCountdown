// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/CardSettingsFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
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
import com.errorsiayusulif.zakocountdown.R // <--- 【核心修复】添加这一行导入
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
    private lateinit var preferenceManager: PreferenceManager

    private val homeViewModel: HomeViewModel by viewModels {
        val app = requireActivity().application as ZakoCountdownApplication
        HomeViewModelFactory(app.repository, app)
    }

    private val colors = listOf(
        null, "#FFFFFF", "#F5F5F5", "#E0E0E0", "#9E9E9E", "#424242", "#000000",
        "#FFEBEE", "#FFCDD2", "#EF5350", "#F44336", "#D32F2F", "#B71C1C",
        "#FCE4EC", "#F8BBD0", "#EC407A", "#E91E63", "#C2185B", "#880E4F",
        "#F3E5F5", "#E1BEE7", "#AB47BC", "#9C27B0", "#7B1FA2", "#4A148C",
        "#EDE7F6", "#D1C4E9", "#7E57C2", "#673AB7", "#512DA8", "#311B92",
        "#E8EAF6", "#C5CAE9", "#5C6BC0", "#3F51B5", "#303F9F", "#1A237E",
        "#E3F2FD", "#BBDEFB", "#42A5F5", "#2196F3", "#1976D2", "#0D47A1",
        "#E0F7FA", "#B2EBF2", "#26C6DA", "#00BCD4", "#0097A7", "#006064",
        "#E0F2F1", "#B2DFDB", "#26A69A", "#009688", "#00796B", "#004D40",
        "#E8F5E9", "#C8E6C9", "#66BB6A", "#4CAF50", "#388E3C", "#1B5E20",
        "#FFFDE7", "#FFF9C4", "#FFEE58", "#FFEB3B", "#FBC02D", "#F57F17",
        "#FFF3E0", "#FFE0B2", "#FFA726", "#FF9800", "#F57C00", "#E65100",
        "#D7CCC8", "#8D6E63", "#5D4037", "#CFD8DC", "#78909C", "#455A64"
    )

    // --- 修复：使用 OpenDocument ---
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
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
                e.printStackTrace()
                Toast.makeText(context, "无法获取图片权限，请尝试其他图片", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCardSettingsBinding.inflate(inflater, container, false)
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
        val isGlobalUnlock = preferenceManager.isGlobalAlphaUnlocked()

        if (event.isPinned) {
            binding.wallpaperSettingsLayout.visibility = View.VISIBLE
            binding.buttonChangeWallpaper.setOnClickListener {
                // --- 修复：传入数组 ---
                pickImageLauncher.launch(arrayOf("image/*"))
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
        binding.colorPalette.removeAllViews()
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