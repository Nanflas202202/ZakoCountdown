package com.errorsiayusulif.zakocountdown.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.data.CountdownEvent
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.databinding.ActivityWidgetConfigureBinding
import com.errorsiayusulif.zakocountdown.databinding.ItemColorSwatchBinding
import com.errorsiayusulif.zakocountdown.databinding.ItemWidgetConfigEventBinding
import kotlinx.coroutines.launch

class WidgetConfigureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetConfigureBinding
    private lateinit var preferenceManager: PreferenceManager
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    // 暂存配置状态
    private var tempImageUri: String? = null
    private var tempSelectedColor: String? = null
    private var tempAlpha: Int = 40
    private var tempImageAlpha: Int = 100
    private var tempShowScrim: Boolean = true
    private var tempScrimAlpha: Int = 40

    private var selectedEvent: CountdownEvent? = null

    // 更新后的色板 (同步 CardSettings 和 SharePreview)
    private val colors = listOf(
        null, // 默认主题色
        "#FFFFFF", "#F5F5F5", "#E0E0E0", "#9E9E9E", "#424242", "#000000",
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

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val contentResolver = contentResolver
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)
            tempImageUri = uri.toString()
            Toast.makeText(this, "图片已选中", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        preferenceManager = PreferenceManager(this)
        applyAppTheme()

        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        binding = ActivityWidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val extras = intent?.extras
        appWidgetId = extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // 核心修复：接收来自 HomeFragment requestPinAppWidget 的参数
        val preselectedEventId = extras?.getLong("preselected_event_id", -1L) ?: -1L
        val isShortcutCreation = extras?.getBoolean("is_shortcut_creation", false) ?: false

        setupColorPalette()
        setupListeners()

        if (isShortcutCreation && preselectedEventId != -1L) {
            // --- 快捷创建逻辑：自动应用默认设置并保存，不显示UI ---
            lifecycleScope.launch {
                val event = (application as ZakoCountdownApplication).repository.getEventById(preselectedEventId)
                if (event != null) {
                    selectedEvent = event
                    // 自动提交 (使用默认透明背景、默认颜色)
                    commitConfiguration(isShortcut = true)
                } else {
                    // 如果ID无效，回退到手动选择
                    setupRecyclerView()
                }
            }
        } else if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            // --- 正常配置/重新配置逻辑 ---
            loadCurrentSettings()
            setupRecyclerView()
        } else {
            finish()
        }
    }

    private fun applyAppTheme() {
        val themeKey = preferenceManager.getTheme()
        val colorKey = preferenceManager.getAccentColor()
        val finalThemeResId = when (themeKey) {
            PreferenceManager.THEME_M1 -> {
                when (colorKey) {
                    PreferenceManager.ACCENT_PINK -> R.style.Theme_ZakoCountdown_MD1_Pink
                    PreferenceManager.ACCENT_BLUE -> R.style.Theme_ZakoCountdown_MD1_Blue
                    else -> R.style.Theme_ZakoCountdown_MD1
                }
            }
            PreferenceManager.THEME_M2 -> {
                when (colorKey) {
                    PreferenceManager.ACCENT_PINK -> R.style.Theme_ZakoCountdown_MD2_Pink
                    PreferenceManager.ACCENT_BLUE -> R.style.Theme_ZakoCountdown_MD2_Blue
                    else -> R.style.Theme_ZakoCountdown_MD2
                }
            }
            else -> { // M3
                when (colorKey) {
                    PreferenceManager.ACCENT_PINK -> R.style.Theme_ZakoCountdown_M3_Pink
                    PreferenceManager.ACCENT_BLUE -> R.style.Theme_ZakoCountdown_M3_Blue
                    else -> R.style.Theme_ZakoCountdown_M3
                }
            }
        }
        setTheme(finalThemeResId)
    }

    private fun loadCurrentSettings() {
        val bgType = preferenceManager.getWidgetBackground(appWidgetId)
        when (bgType) {
            "solid" -> binding.widgetBgSolid.isChecked = true
            "image" -> binding.widgetBgImage.isChecked = true
            else -> binding.widgetBgTransparent.isChecked = true
        }

        tempSelectedColor = preferenceManager.getWidgetColor(appWidgetId)
        updateColorPaletteSelection()

        tempAlpha = preferenceManager.getWidgetAlpha(appWidgetId)
        binding.sliderWidgetAlpha.value = tempAlpha.toFloat()

        tempImageUri = preferenceManager.getWidgetImageUri(appWidgetId)
        tempImageAlpha = preferenceManager.getWidgetImageAlpha(appWidgetId)
        binding.sliderImageAlpha.value = tempImageAlpha.toFloat()

        tempShowScrim = preferenceManager.getWidgetShowScrim(appWidgetId)
        binding.switchShowScrim.isChecked = tempShowScrim
        tempScrimAlpha = preferenceManager.getWidgetScrimAlpha(appWidgetId)
        binding.sliderScrimAlpha.value = tempScrimAlpha.toFloat()

        updateVisibilityBasedOnSelection(binding.widgetBgTypeGroup.checkedRadioButtonId)
    }

    private fun updateVisibilityBasedOnSelection(checkedId: Int) {
        binding.widgetSelectImageButton.visibility = View.GONE
        binding.colorSelectorContainer.visibility = View.GONE
        binding.alphaSliderContainer.visibility = View.GONE
        binding.imageAlphaContainer.visibility = View.GONE
        binding.scrimSettingsContainer.visibility = View.GONE

        when (checkedId) {
            R.id.widget_bg_image -> {
                binding.widgetSelectImageButton.visibility = View.VISIBLE
                binding.imageAlphaContainer.visibility = View.VISIBLE
                binding.scrimSettingsContainer.visibility = View.VISIBLE
            }
            R.id.widget_bg_solid -> {
                binding.colorSelectorContainer.visibility = View.VISIBLE
            }
            R.id.widget_bg_transparent -> {
                binding.colorSelectorContainer.visibility = View.VISIBLE
                binding.alphaSliderContainer.visibility = View.VISIBLE
            }
        }
    }

    private fun setupListeners() {
        binding.widgetBgTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            updateVisibilityBasedOnSelection(checkedId)
        }
        binding.widgetSelectImageButton.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.sliderWidgetAlpha.addOnChangeListener { _, value, _ -> tempAlpha = value.toInt() }
        binding.sliderImageAlpha.addOnChangeListener { _, value, _ -> tempImageAlpha = value.toInt() }
        binding.switchShowScrim.setOnCheckedChangeListener { _, isChecked -> tempShowScrim = isChecked }
        binding.sliderScrimAlpha.addOnChangeListener { _, value, _ -> tempScrimAlpha = value.toInt() }

        binding.btnConfirmAddWidget.setOnClickListener {
            if (binding.widgetBgTypeGroup.checkedRadioButtonId == R.id.widget_bg_image && tempImageUri == null) {
                Toast.makeText(this, "请先选择一张图片", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            commitConfiguration(isShortcut = false)
        }
    }

    private fun setupColorPalette() {
        val inflater = LayoutInflater.from(this)
        binding.colorPalette.removeAllViews()
        for (colorHex in colors) {
            val swatchBinding = ItemColorSwatchBinding.inflate(inflater, binding.colorPalette, false)
            val color = if (colorHex == null) {
                val attrs = intArrayOf(com.google.android.material.R.attr.colorPrimary)
                val typedArray = obtainStyledAttributes(attrs)
                val c = typedArray.getColor(0, Color.BLACK)
                typedArray.recycle()
                c
            } else {
                Color.parseColor(colorHex)
            }
            (swatchBinding.colorView.background as GradientDrawable).setColor(color)

            swatchBinding.root.setOnClickListener {
                tempSelectedColor = colorHex
                updateColorPaletteSelection()
            }
            binding.colorPalette.addView(swatchBinding.root)
        }
    }

    private fun updateColorPaletteSelection() {
        binding.colorPalette.children.forEachIndexed { index, view ->
            val swatchBinding = ItemColorSwatchBinding.bind(view)
            val colorHexForThisSwatch = colors[index]
            swatchBinding.checkMark.visibility = if (tempSelectedColor == colorHexForThisSwatch) View.VISIBLE else View.GONE
        }
    }

    private fun setupRecyclerView() {
        val repo = (application as ZakoCountdownApplication).repository
        binding.recyclerViewWidgetConfig.layoutManager = LinearLayoutManager(this)
        val currentEventId = preferenceManager.getWidgetEventId(appWidgetId)

        lifecycleScope.launch {
            val allEvents = repo.getAllEventsSuspend()
            selectedEvent = allEvents.find { it.id == currentEventId }
            if (selectedEvent != null) {
                binding.btnConfirmAddWidget.isEnabled = true
                binding.btnConfirmAddWidget.text = "更新微件：${selectedEvent?.title}"
            }

            val adapter = ConfigAdapter(allEvents, currentEventId) { event ->
                selectedEvent = event
                binding.btnConfirmAddWidget.isEnabled = true
                binding.btnConfirmAddWidget.text = "添加/更新：${event.title}"
            }
            binding.recyclerViewWidgetConfig.adapter = adapter

            val index = allEvents.indexOfFirst { it.id == currentEventId }
            if (index != -1) binding.recyclerViewWidgetConfig.scrollToPosition(index)
        }
    }

    private fun commitConfiguration(isShortcut: Boolean) {
        val event = selectedEvent ?: return

        // 如果是快捷创建，appWidgetId 已经在 onCreate 中获取
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            // 极端情况处理
            finish()
            return
        }

        // 确定背景类型 (如果是快捷创建，默认为 transparent)
        val bgType = if (isShortcut) "transparent" else when (binding.widgetBgTypeGroup.checkedRadioButtonId) {
            R.id.widget_bg_solid -> "solid"
            R.id.widget_bg_image -> "image"
            else -> "transparent"
        }

        // 保存配置
        preferenceManager.saveWidgetBackground(appWidgetId, bgType)
        preferenceManager.saveWidgetEventId(appWidgetId, event.id)
        preferenceManager.saveWidgetColor(appWidgetId, tempSelectedColor)

        if (bgType == "image" && tempImageUri != null) {
            preferenceManager.saveWidgetImageUri(appWidgetId, tempImageUri)
        }
        preferenceManager.saveWidgetImageAlpha(appWidgetId, tempImageAlpha)
        preferenceManager.saveWidgetShowScrim(appWidgetId, tempShowScrim)
        preferenceManager.saveWidgetScrimAlpha(appWidgetId, tempScrimAlpha)
        preferenceManager.saveWidgetAlpha(appWidgetId, tempAlpha)

        // 设置结果，通知 Launcher 配置成功
        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(Activity.RESULT_OK, resultValue)

        // 立即触发更新 (对于快捷创建，系统会在摆放后发送广播，但手动触发更保险)
        val appWidgetManager = AppWidgetManager.getInstance(this)
        UpdateWidgetWorker.updateWidget(this, appWidgetManager, appWidgetId)

        finish()
    }

    class ConfigAdapter(
        private val events: List<CountdownEvent>,
        private val preSelectedId: Long,
        private val onItemClick: (CountdownEvent) -> Unit
    ) : RecyclerView.Adapter<ConfigAdapter.ViewHolder>() {

        private var selectedPosition = events.indexOfFirst { it.id == preSelectedId }

        class ViewHolder(val binding: ItemWidgetConfigEventBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemWidgetConfigEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val event = events[position]
            holder.binding.eventTitle.text = event.title

            if (selectedPosition == position) {
                holder.binding.cardRoot.strokeWidth = 6
                // 简单高亮色，实际应从 attr 取
                holder.binding.cardRoot.setStrokeColor(Color.parseColor("#6750A4"))
                holder.binding.cardRoot.setCardBackgroundColor(Color.parseColor("#EADDFF"))
            } else {
                holder.binding.cardRoot.strokeWidth = 0
                holder.binding.cardRoot.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.context, com.google.android.material.R.color.material_dynamic_neutral95))
            }

            holder.itemView.setOnClickListener {
                val previous = selectedPosition
                selectedPosition = holder.adapterPosition
                notifyItemChanged(previous)
                notifyItemChanged(selectedPosition)
                onItemClick(event)
            }
        }
        override fun getItemCount() = events.size
    }
}