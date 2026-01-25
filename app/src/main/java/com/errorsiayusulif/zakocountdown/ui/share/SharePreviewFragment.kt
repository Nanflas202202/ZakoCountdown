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
import androidx.navigation.fragment.findNavController // 修复：补全导入
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

    // 状态控制
    private var selectedLayoutId = R.layout.layout_share_template_card
    private var dateMode: Int = MODE_SIMPLE
    private var isShowTargetDate: Boolean = false
    private var selectedBackgroundUri: String? = null
    private var selectedBackgroundColor: Int = Color.parseColor("#F5F5F5")
    private var selectedCardColor: Int = Color.WHITE
    private var currentAlpha: Int = 100
    private var hasUserAdjustedAlpha = false

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

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedBackgroundUri = it.toString()
            updatePreview()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        preferenceManager = PreferenceManager(requireContext())
        val themeKey = preferenceManager.getTheme()
        val themeResId = when (themeKey) {
            PreferenceManager.THEME_M1 -> R.style.Theme_ZakoCountdown_MD1
            PreferenceManager.THEME_M2 -> R.style.Theme_ZakoCountdown_MD2
            else -> R.style.Theme_ZakoCountdown_M3
        }
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
                Toast.makeText(context, "无法加载日程数据", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
                return@launch
            }
            currentEvent?.colorHex?.let { selectedCardColor = Color.parseColor(it) }
            selectedBackgroundUri = currentEvent?.backgroundUri
            setupUI()
            updatePreview()
        }
    }

    private fun setupUI() {
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

        binding.btnPickImage.setOnClickListener { pickImageLauncher.launch("image/*") }
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
                binding.tvCardColorLabel.text = "卡片颜色"
                binding.tvAlphaLabel.text = "卡片不透明度"
            }
            R.layout.layout_share_template_minimal -> {
                binding.containerCardSettings.visibility = View.GONE
                binding.tvAlphaLabel.text = "背景遮罩浓度"
            }
            R.layout.layout_share_template_hero -> {
                binding.containerCardSettings.visibility = View.VISIBLE
                binding.tvCardColorLabel.text = "下方信息区颜色"
                binding.tvAlphaLabel.text = "上方图片遮罩浓度"
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
        val templateView = LayoutInflater.from(requireContext()).inflate(selectedLayoutId, binding.previewContainer, false)
        binding.previewContainer.addView(templateView)
        updatePreviewViewData(templateView)
        updatePreviewViewProperties(templateView)
        templateView.post {
            val parentWidth = (binding.previewContainer.parent as View).width.toFloat()
            if (parentWidth > 0) {
                val scale = (parentWidth * 0.85f) / 1080f
                binding.previewContainer.scaleX = scale
                binding.previewContainer.scaleY = scale
            }
        }
    }

    private fun updatePreviewViewData(view: View?) {
        if (view == null || currentEvent == null) return
        val event = currentEvent!!
        view.findViewById<TextView>(R.id.tv_title)?.text = event.title
        val diff = TimeCalculator.calculateDifference(event.targetDate)
        view.findViewById<TextView>(R.id.tv_status)?.text = if (diff.isPast) "已过" else "还有"
        view.findViewById<TextView>(R.id.tv_title_prefix)?.text = "距离"
        val tvDays = view?.findViewById<TextView>(R.id.tv_days)
        val tvSuffix = view?.findViewById<TextView>(R.id.tv_suffix)

        when (dateMode) {
            MODE_DETAILED -> {
                // 确保没有空格，节省空间
                val text = "${diff.totalDays}天${String.format("%02d", diff.hours)}时${String.format("%02d", diff.minutes)}分${String.format("%02d", diff.seconds)}秒"
                tvDays?.text = text
                tvSuffix?.visibility = View.GONE
            }
            MODE_FULL -> {
                val sb = StringBuilder()
                if (diff.years > 0) sb.append("${diff.years}年")
                if (diff.months > 0) sb.append("${diff.months}月")
                if (diff.weeks > 0) sb.append("${diff.weeks}周")
                sb.append("${diff.daysInWeek}天")
                tvDays?.text = sb.toString().ifBlank { "0天" }
                tvSuffix?.visibility = View.GONE
            }
            else -> {
                tvDays?.text = diff.totalDays.toString()
                tvSuffix?.visibility = View.VISIBLE
            }
        }
        view.findViewById<TextView>(R.id.tv_target_date)?.apply {
            visibility = if (isShowTargetDate) View.VISIBLE else View.GONE
            if (isShowTargetDate) text = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.getDefault()).format(event.targetDate)
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
                else { ivBackground?.setImageDrawable(null); ivBackground?.setBackgroundColor(selectedBackgroundColor) }
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
                withContext(Dispatchers.Main) {
                    updatePreviewViewData(shareView)
                    updatePreviewViewProperties(shareView)
                    if (selectedBackgroundUri != null) {
                        val loader = ImageLoader(context)
                        val request = ImageRequest.Builder(context).data(Uri.parse(selectedBackgroundUri))
                            .size(1080, 1920).allowHardware(false).build()
                        val result = loader.execute(request)
                        if (result is SuccessResult) shareView.findViewById<ImageView>(R.id.iv_background)?.setImageDrawable(result.drawable)
                    }
                }
                val bitmap = withContext(Dispatchers.Main) {
                    shareView.measure(MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(1920, MeasureSpec.EXACTLY))
                    shareView.layout(0, 0, 1080, 1920)
                    val bmp = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
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
                        startActivity(Intent.createChooser(intent, "分享图片"))
                    }
                } else {
                    saveToGallery(context, bitmap)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.loadingIndicator.visibility = View.GONE
                    Toast.makeText(context, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "图片已保存至相册", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}