// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/agenda/AddEditAgendaBookFragment.kt
package com.errorsiayusulif.zakocountdown.ui.agenda

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.load
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.data.AgendaBook
import com.errorsiayusulif.zakocountdown.databinding.FragmentAddEditAgendaBookBinding
import com.errorsiayusulif.zakocountdown.databinding.ItemColorSwatchBinding
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class AddEditAgendaBookFragment : Fragment() {

    private var _binding: FragmentAddEditAgendaBookBinding? = null
    private val binding get() = _binding!!
    private val args: AddEditAgendaBookFragmentArgs by navArgs()
    private val agendaViewModel: AgendaViewModel by viewModels({ requireActivity() })

    private var selectedColor = "#2196F3"
    private var selectedCoverUri: String? = null
    private var currentAlpha: Float = 1.0f
    private var editingBook: AgendaBook? = null

    // --- 扩展的 Material Design 色板 ---
    private val materialColors = listOf(
        // Red, Pink, Purple, Deep Purple
        "#F44336", "#E91E63", "#9C27B0", "#673AB7",
        // Indigo, Blue, Light Blue, Cyan
        "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
        // Teal, Green, Light Green, Lime
        "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
        // Yellow, Amber, Orange, Deep Orange
        "#FFEB3B", "#FFC107", "#FF9800", "#FF5722",
        // Brown, Grey, Blue Grey, Black
        "#795548", "#9E9E9E", "#607D8B", "#000000"
    )

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { sourceUri ->
            try {
                val context = requireContext()
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                if (inputStream != null) {
                    val file = File(context.filesDir, "book_cover_${System.currentTimeMillis()}.png")
                    val outputStream = FileOutputStream(file)
                    inputStream.use { it.copyTo(outputStream) }
                    selectedCoverUri = Uri.fromFile(file).toString()
                    binding.ivCoverPreview.load(selectedCoverUri)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "图片加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddEditAgendaBookBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (args.bookId != -1L) {
            lifecycleScope.launch {
                val repo = (requireActivity().application as ZakoCountdownApplication).repository
                editingBook = repo.getBookById(args.bookId)
                editingBook?.let {
                    binding.etBookName.setText(it.name)
                    selectedColor = it.colorHex
                    selectedCoverUri = it.coverImageUri
                    currentAlpha = it.cardAlpha

                    binding.ivCoverPreview.load(selectedCoverUri)
                    binding.ivCoverPreview.alpha = currentAlpha
                    binding.sliderAlpha.value = currentAlpha * 100f
                    setupColorPalette()
                }
            }
        } else {
            setupColorPalette()
        }

        binding.btnSelectCover.setOnClickListener { pickImageLauncher.launch(arrayOf("image/*")) }
        binding.btnRemoveCover.setOnClickListener {
            selectedCoverUri = null
            binding.ivCoverPreview.setImageDrawable(null)
        }

        binding.sliderAlpha.addOnChangeListener { _, value, _ ->
            currentAlpha = value / 100f
            binding.ivCoverPreview.alpha = currentAlpha
        }

        binding.fabSaveBook.setOnClickListener {
            val name = binding.etBookName.text.toString()
            if (name.isBlank()) {
                Toast.makeText(context, "请输入名称", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (args.bookId == -1L) {
                agendaViewModel.createBook(name, selectedColor, selectedCoverUri)
            } else {
                editingBook?.let {
                    it.name = name
                    it.colorHex = selectedColor
                    it.coverImageUri = selectedCoverUri
                    it.cardAlpha = currentAlpha
                    agendaViewModel.updateBook(it)
                }
            }
            findNavController().navigateUp()
        }
    }

    private fun setupColorPalette() {
        val inflater = LayoutInflater.from(context)
        binding.paletteContainer.removeAllViews()
        for (colorHex in materialColors) {
            val swatch = ItemColorSwatchBinding.inflate(inflater, binding.paletteContainer, false)
            val color = Color.parseColor(colorHex)
            (swatch.colorView.background as GradientDrawable).setColor(color)

            swatch.checkMark.visibility = if (colorHex.equals(selectedColor, true)) View.VISIBLE else View.GONE

            swatch.root.setOnClickListener {
                selectedColor = colorHex
                binding.paletteContainer.children.forEach {
                    ItemColorSwatchBinding.bind(it).checkMark.visibility = View.GONE
                }
                swatch.checkMark.visibility = View.VISIBLE
            }
            binding.paletteContainer.addView(swatch.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}