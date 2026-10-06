// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/share/SharePreviewFragment.kt
package com.errorsiayusulif.zakocountdown.ui.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ContextThemeWrapper
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.ImageLoader
import coil.load
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.data.CountdownEvent
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.databinding.FragmentSharePreviewBinding
import com.errorsiayusulif.zakocountdown.databinding.ItemColorSwatchBinding
import com.errorsiayusulif.zakocountdown.utils.TimeCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class SharePreviewFragment : Fragment() {

    private var _binding: FragmentSharePreviewBinding? = null
    private val binding get() = _binding!!
    private val args: SharePreviewFragmentArgs by navArgs()
    private var currentEvent: CountdownEvent? = null
    private lateinit var preferenceManager: PreferenceManager

    // 状态
    private var selectedLayoutId = R.layout.layout_share_template_card
    private var dateMode: Int = MODE_SIMPLE
    private var isShowTargetDate: Boolean = false
    private var selectedBackgroundUri: String? = null
    private var selectedBackgroundColor: Int = Color.parseColor("#F5F5F5")
    private var selectedCardColor: Int = Color.WHITE
    private var currentAlpha: Int = 100
    private var hasUserAdjustedAlpha = false

    /**
     * 当前输出尺寸。模板 XML 里根节点写死了 1080×1920，所以真实尺寸
     * 由 [applyCardSize] 在代码里覆盖，预览与导出共用它，保证所见即所得。
     */
    private var cardSize: ShareCardSize = ShareCardSize.DEFAULT

    /**
     * 把模板根节点的固定 1080×1920 改成当前预设尺寸。
     *
     * 只改高度是安全的（模板内部全是 px 绝对尺寸，宽度是设计基准不能动）。
     * 预览与导出两条路径都会调用它，避免出现「预览是方的、导出是竖的」。
     */
    private fun applyCardSize(view: View) {
        val lp = view.layoutParams
        if (lp != null) {
            lp.width = cardSize.widthPx
            lp.height = cardSize.heightPx
            view.layoutParams = lp
        } else {
            view.layoutParams = ViewGroup.LayoutParams(cardSize.widthPx, cardSize.heightPx)
        }
    }

    companion object {
        const val MODE_SIMPLE = 0
        const val MODE_DETAILED = 1
        const val MODE_FULL = 2
    }

    private val materialColors = listOf(
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

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                val contentResolver = requireActivity().contentResolver
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                selectedBackgroundUri = it.toString()
                updatePreview()
            } catch (e: SecurityException) {
                selectedBackgroundUri = it.toString()
                updatePreview()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        preferenceManager = PreferenceManager(requireContext())
        val themeKey = preferenceManager.getTheme()
        val colorKey = preferenceManager.getAccentColor()

        // --- 核心修复：完整的颜色变体判断逻辑 ---
        val themeResId = when (themeKey) {
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

        // 使用正确的 ContextThemeWrapper
        val themedContext = ContextThemeWrapper(requireContext(), themeResId)
        _binding = FragmentSharePreviewBinding.inflate(inflater.cloneInContext(themedContext), container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            val repo = (requireActivity().application as ZakoCountdownApplication).repository
            currentEvent = repo.getEventById(args.eventId)
            if (currentEvent == null) {
                Toast.makeText(context, R.string.share_load_failed, Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
                return@launch
            }
            currentEvent?.colorHex?.let { selectedCardColor = Color.parseColor(it) }
            selectedBackgroundUri = currentEvent?.backgroundUri
            setupUI()
            updatePreview()
        }
    }

    // ... 其余逻辑代码与之前版本一致，无需变动 ...
    // (setupUI, updatePreview, saveImageToGallery 等方法)
    // 为了节省空间，此处省略未变动的方法实现，请使用上一次提供的 SharePreviewFragment.kt 中的其余部分。

    /**
     * 构建输出尺寸的 Chip 列表，并恢复上次选择。
     *
     * 「上次选择」持久化在 [com.errorsiayusulif.zakocountdown.data.PreferenceKeys.SHARE_CARD_SIZE]，
     * 因为分享尺寸是稳定的个人偏好（比如一直用方形发朋友圈），
     * 每次进来都要重选一遍很烦。
     */
    private fun setupSizeChips() {
        val saved = ShareCardSize.fromKey(preferenceManager.getShareCardSize())
        cardSize = saved
        binding.chipGroupSize.removeAllViews()

        ShareCardSize.entries.forEach { size ->
            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                id = View.generateViewId()
                text = getString(size.labelResId)
                isCheckable = true
                isChecked = size == saved
                tag = size
                setOnClickListener {
                    cardSize = size
                    preferenceManager.saveShareCardSize(size.key)
                    updatePreview()
                }
            }
            binding.chipGroupSize.addView(chip)
        }
    }

    private fun setupUI() {
        setupSizeChips()

        binding.toggleLayout.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedLayoutId = when (checkedId) {
                    R.id.btn_layout_2 -> R.layout.layout_share_template_minimal
                    R.id.btn_layout_3 -> R.layout.layout_share_template_hero
                    else -> R.layout.layout_share_template_card
                }
                if (!hasUserAdjustedAlpha) {
                    currentAlpha = if (selectedLayoutId == R.layout.layout_share_template_hero) 30 else 100
                    binding.sliderAlpha.value = currentAlpha.toFloat()
                }
                updateSettingsVisibility()
                updatePreview()
            }
        }

        binding.toggleDateMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                dateMode = when (checkedId) {
                    R.id.btn_mode_detailed -> MODE_DETAILED
                    R.id.btn_mode_full -> MODE_FULL
                    else -> MODE_SIMPLE
                }
                refreshPreviewDataOnly()
            }
        }

        binding.switchShowDate.setOnCheckedChangeListener { _, isChecked ->
            isShowTargetDate = isChecked
            refreshPreviewDataOnly()
        }

        binding.btnPickImage.setOnClickListener { pickImageLauncher.launch(arrayOf("image/*")) }

        setupColorPalette(binding.paletteBackground) { color ->
            selectedBackgroundColor = color
            if (selectedLayoutId != R.layout.layout_share_template_hero) selectedBackgroundUri = null
            updatePreview()
        }
        setupColorPalette(binding.paletteCard) { color ->
            selectedCardColor = color
            updatePreview()
        }

        binding.sliderAlpha.addOnChangeListener { _, value, fromUser ->
            if (fromUser) hasUserAdjustedAlpha = true
            currentAlpha = value.toInt()
            refreshPreviewPropsOnly()
        }

        binding.btnReset.setOnClickListener { resetSettings() }
        binding.btnSave.setOnClickListener { processImage(isShare = false) }
        binding.btnShare.setOnClickListener { processImage(isShare = true) }
        updateSettingsVisibility()
    }

    private fun updateSettingsVisibility() {
        when (selectedLayoutId) {
            R.layout.layout_share_template_card -> {
                binding.containerCardSettings.visibility = View.VISIBLE
                binding.tvCardColorLabel.text = getString(R.string.card_color_label)
                binding.tvAlphaLabel.text = getString(R.string.card_opacity_label)
            }
            R.layout.layout_share_template_minimal -> {
                binding.containerCardSettings.visibility = View.GONE
                binding.tvAlphaLabel.text = getString(R.string.card_scrim_alpha_label)
            }
            R.layout.layout_share_template_hero -> {
                binding.containerCardSettings.visibility = View.VISIBLE
                binding.tvCardColorLabel.text = getString(R.string.card_info_area_color)
                binding.tvAlphaLabel.text = getString(R.string.card_image_scrim_alpha)
            }
        }
    }

    private fun setupColorPalette(container: LinearLayout, onColorSelected: (Int) -> Unit) {
        val inflater = LayoutInflater.from(context)
        container.removeAllViews()
        for (colorHex in materialColors) {
            val swatchBinding = ItemColorSwatchBinding.inflate(inflater, container, false)
            val color = Color.parseColor(colorHex)
            (swatchBinding.colorView.background as GradientDrawable).setColor(color)
            swatchBinding.root.setOnClickListener {
                container.children.forEach { ItemColorSwatchBinding.bind(it).checkMark.visibility = View.GONE }
                swatchBinding.checkMark.visibility = View.VISIBLE
                onColorSelected(color)
            }
            container.addView(swatchBinding.root)
        }
    }

    private fun refreshPreviewDataOnly() {
        if (binding.previewContainer.childCount > 0) updatePreviewViewData(binding.previewContainer.getChildAt(0))
    }

    private fun refreshPreviewPropsOnly() {
        if (binding.previewContainer.childCount > 0) updatePreviewViewProperties(binding.previewContainer.getChildAt(0))
    }

    private fun resetSettings() {
        selectedBackgroundUri = currentEvent?.backgroundUri
        selectedCardColor = if (currentEvent?.colorHex != null) Color.parseColor(currentEvent?.colorHex) else Color.WHITE
        selectedBackgroundColor = Color.parseColor("#F5F5F5")
        hasUserAdjustedAlpha = false
        currentAlpha = 100
        binding.sliderAlpha.value = 100f
        binding.toggleDateMode.check(R.id.btn_mode_simple)
        binding.switchShowDate.isChecked = false
        binding.toggleLayout.check(R.id.btn_layout_1)
    }

    private fun updatePreview() {
        binding.previewContainer.removeAllViews()
        // 这里的 inflater 已经是 wrapped context，所以直接使用即可
        val templateView = LayoutInflater.from(binding.root.context).inflate(selectedLayoutId, binding.previewContainer, false)
        binding.previewContainer.addView(templateView)
        // 先按当前尺寸覆盖模板里写死的 1080×1920，再取数据 / 属性，
        // 这样预览的宽高比与最终导出完全一致
        applyCardSize(templateView)
        updatePreviewViewData(templateView)
        updatePreviewViewProperties(templateView)
        templateView.post {
            val parent = binding.previewContainer.parent as View
            val availW = parent.width.toFloat()
            val availH = parent.height.toFloat()
            if (availW <= 0f || availH <= 0f) return@post

            // 预览缩放：按「宽高两个方向都能放下」取较小比例。
            // 只按宽度算的话，横向尺寸（如 16:9）会竖向溢出被裁掉。
            val scaleW = (availW * 0.85f) / cardSize.widthPx
            val scaleH = (availH * 0.90f) / cardSize.heightPx
            val scale = minOf(scaleW, scaleH)
            binding.previewContainer.scaleX = scale
            binding.previewContainer.scaleY = scale
        }
    }

    private fun updatePreviewViewData(view: View?) {
        if (view == null || currentEvent == null) return
        val event = currentEvent!!
        view.findViewById<TextView>(R.id.tv_title)?.text = event.title
        val diff = TimeCalculator.calculateDifference(event.targetDate)

        view.findViewById<TextView>(R.id.tv_status)?.text = if (diff.isPast) getString(R.string.countdown_passed) else getString(R.string.countdown_remaining)
        view.findViewById<TextView>(R.id.tv_title_prefix)?.text = getString(R.string.countdown_distance)

        val tvDays = view.findViewById<TextView>(R.id.tv_days)
        val tvSuffix = view.findViewById<TextView>(R.id.tv_suffix)

        when (dateMode) {
            MODE_DETAILED -> {
                tvDays?.text = String.format(getString(R.string.duration_dhms_short), diff.totalDays, diff.hours, diff.minutes, diff.seconds)
                tvSuffix?.visibility = View.GONE
            }
            MODE_FULL -> {
                val sb = StringBuilder()
                if (diff.years > 0) sb.append(diff.years.toString() + getString(R.string.unit_year))
                if (diff.months > 0) sb.append(diff.months.toString() + getString(R.string.unit_month))
                if (diff.weeks > 0) sb.append(diff.weeks.toString() + getString(R.string.unit_week))
                sb.append(diff.daysInWeek.toString() + getString(R.string.unit_day))
                tvDays?.text = sb.toString().ifBlank { getString(R.string.duration_zero) }
                tvSuffix?.visibility = View.GONE
            }
            else -> {
                tvDays?.text = diff.totalDays.toString()
                tvSuffix?.visibility = View.VISIBLE
            }
        }
        view.findViewById<TextView>(R.id.tv_target_date)?.apply {
            visibility = if (isShowTargetDate) View.VISIBLE else View.GONE
            if (isShowTargetDate) {
                // 日期格式跟随语言（中文用「年月日」，其他语言用 ISO 风格）
                text = SimpleDateFormat(getString(R.string.share_target_date_format), Locale.getDefault())
                    .format(event.targetDate)
            }
        }
    }

    private fun updatePreviewViewProperties(view: View?) {
        if (view == null) return
        val ivBackground = view.findViewById<ImageView>(R.id.iv_background)
        val alphaFloat = currentAlpha / 100f

        when (selectedLayoutId) {
            R.layout.layout_share_template_card -> {
                val card = view.findViewById<CardView>(R.id.cv_card)
                card?.setCardBackgroundColor(ColorUtils.setAlphaComponent(selectedCardColor, (alphaFloat * 255).toInt()))
                updateTextColorBasedOnBackground(view, selectedCardColor)
                if (selectedBackgroundUri != null) ivBackground?.load(Uri.parse(selectedBackgroundUri)) { allowHardware(false) }
                else { ivBackground?.setImageDrawable(null); ivBackground?.setBackgroundColor(selectedBackgroundColor) }
            }
            R.layout.layout_share_template_minimal -> {
                view.findViewById<View>(R.id.v_scrim)?.alpha = alphaFloat * 0.6f
                if (selectedBackgroundUri != null) ivBackground?.load(Uri.parse(selectedBackgroundUri)) { allowHardware(false) }
                else { ivBackground?.setImageDrawable(null); ivBackground?.setBackgroundColor(selectedBackgroundColor) }
            }
            R.layout.layout_share_template_hero -> {
                if (selectedBackgroundUri != null) ivBackground?.load(Uri.parse(selectedBackgroundUri)) { allowHardware(false) }
                else { ivBackground?.setImageDrawable(null); ivBackground?.setBackgroundColor(Color.parseColor("#E0E0E0")) }
                view.findViewById<View>(R.id.cl_info_area)?.setBackgroundColor(selectedCardColor)
                view.findViewById<View>(R.id.v_scrim)?.apply {
                    setBackgroundColor(selectedBackgroundColor)
                    alpha = alphaFloat
                }
                updateTextColorBasedOnBackground(view, selectedCardColor)
            }
        }
    }

    private fun updateTextColorBasedOnBackground(view: View, color: Int) {
        val isDark = ColorUtils.calculateLuminance(color) < 0.5
        val main = if (isDark) Color.WHITE else Color.parseColor("#212121")
        val sub = if (isDark) Color.parseColor("#B0BEC5") else Color.parseColor("#757575")
        val accent = if (isDark) Color.WHITE else Color.parseColor("#2196F3")
        view.findViewById<TextView>(R.id.tv_title)?.setTextColor(main)
        view.findViewById<TextView>(R.id.tv_days)?.setTextColor(if (dateMode == MODE_SIMPLE) accent else main)
        view.findViewById<TextView>(R.id.tv_suffix)?.setTextColor(main)
        view.findViewById<TextView>(R.id.tv_status)?.setTextColor(sub)
        view.findViewById<TextView>(R.id.tv_title_prefix)?.setTextColor(sub)
        view.findViewById<TextView>(R.id.tv_target_date)?.setTextColor(sub)
    }

    private fun processImage(isShare: Boolean) {
        binding.loadingIndicator.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val context = requireContext()
                val shareView = withContext(Dispatchers.Main) {
                    LayoutInflater.from(context).inflate(selectedLayoutId, null, false)
                }
                // 模板根节点在 XML 里写死了 1080×1920，这里按当前尺寸预设覆盖掉，
                // 否则选择方形 / 横向尺寸时导出的仍是竖图。
                // 内容块是 wrap_content，改高度不会破坏内部比例（宽度保持 1080 不变）。
                withContext(Dispatchers.Main) {
                    applyCardSize(shareView)
                    updatePreviewViewData(shareView)
                    updatePreviewViewProperties(shareView)
                    if (selectedBackgroundUri != null) {
                        val loader = ImageLoader(context)
                        val request = ImageRequest.Builder(context).data(Uri.parse(selectedBackgroundUri))
                            .size(cardSize.widthPx, cardSize.heightPx).allowHardware(false).build()
                        val result = loader.execute(request)
                        if (result is SuccessResult) shareView.findViewById<ImageView>(R.id.iv_background)?.setImageDrawable(result.drawable)
                    }
                }
                val bitmap = withContext(Dispatchers.Main) {
                    shareView.measure(
                        MeasureSpec.makeMeasureSpec(cardSize.widthPx, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(cardSize.heightPx, MeasureSpec.EXACTLY)
                    )
                    shareView.layout(0, 0, cardSize.widthPx, cardSize.heightPx)
                    val bmp = Bitmap.createBitmap(cardSize.widthPx, cardSize.heightPx, Bitmap.Config.ARGB_8888)
                    shareView.draw(Canvas(bmp))
                    bmp
                }
                if (isShare) {
                    val file = File(context.cacheDir, "shared_images").apply { if (!exists()) mkdirs() }
                        .let { File(it, "share_${System.currentTimeMillis()}.png") }
                    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    withContext(Dispatchers.Main) {
                        binding.loadingIndicator.visibility = View.GONE
                        startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_image)))
                    }
                } else {
                    saveToGallery(context, bitmap)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.loadingIndicator.visibility = View.GONE
                    Toast.makeText(context, getString(R.string.common_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun saveToGallery(context: Context, bitmap: Bitmap) {
        val filename = "Zako_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ZakoCountdown")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            resolver.openOutputStream(it)?.use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(it, values, null, null)
            }
        }
        withContext(Dispatchers.Main) {
            binding.loadingIndicator.visibility = View.GONE
            Toast.makeText(context, R.string.share_saved_to_gallery, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}