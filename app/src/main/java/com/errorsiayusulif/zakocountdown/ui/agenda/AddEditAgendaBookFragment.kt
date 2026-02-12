package com.errorsiayusulif.zakocountdown.ui.agenda

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.CheckedTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.data.AgendaBook
import com.errorsiayusulif.zakocountdown.data.CountdownEvent
import com.errorsiayusulif.zakocountdown.databinding.FragmentAddEditAgendaBookBinding
import com.errorsiayusulif.zakocountdown.databinding.ItemColorSwatchBinding
import com.errorsiayusulif.zakocountdown.ui.agenda.AgendaViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class AddEditAgendaBookFragment : Fragment() {

    private var _binding: FragmentAddEditAgendaBookBinding? = null
    private val binding get() = _binding!!
    private val args: AddEditAgendaBookFragmentArgs by navArgs()
    private val agendaViewModel: AgendaViewModel by viewModels({ requireActivity() })

    private var selectedColor = "#6750A4" // MD3 默认紫
    private var selectedCoverUri: String? = null
    private var currentAlpha: Float = 1.0f

    private val checkedEventIds = mutableSetOf<Long>()
    private var allEvents: List<CountdownEvent> = emptyList()
    private var editingBook: AgendaBook? = null

    // --- Material Design 3 扩展色板 ---
    private val baseMaterialColors = listOf(
        // MD3 Primary / Secondary Tones
        "#6750A4", // Purple 40 (M3 Default)
        "#9C27B0", // Purple
        "#E91E63", // Pink
        "#B58392", // M3 Pink-ish
        "#B3261E", // M3 Error/Red
        "#F44336", // Red
        "#9C4146", // M3 Brick Red
        "#7D5260", // M3 Rose

        // Warm Tones
        "#9A4058", // M3 Maroon
        "#FF9800", // Orange
        "#FFB300", // Amber
        "#E65100", // Deep Orange
        "#825500", // M3 Gold/Olive

        // Cool Tones
        "#0061A4", // M3 Blue
        "#2196F3", // Blue
        "#03A9F4", // Light Blue
        "#006493", // M3 Deep Blue
        "#386A20", // M3 Green
        "#4CAF50", // Green
        "#006D42", // M3 Teal-Green
        "#009688", // Teal
        "#006064", // Deep Teal

        // Neutral / Earthy Tones
        "#795548", // Brown
        "#605D62", // M3 Neutral
        "#424242", // Grey
        "#000000"  // Black
    )

    private val displayColors = mutableListOf<String>()

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

        initColors()

        binding.recyclerViewEventSelection.layoutManager = LinearLayoutManager(context)

        agendaViewModel.allEvents.observe(viewLifecycleOwner) { events ->
            allEvents = events
            updateEventSelectionList()
        }

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

                    allEvents.filter { event -> event.bookId == it.id }.forEach { event ->
                        checkedEventIds.add(event.id)
                    }
                    updateEventSelectionList()
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

            agendaViewModel.saveBookWithEvents(
                id = args.bookId,
                name = name,
                colorHex = selectedColor,
                coverUri = selectedCoverUri,
                cardAlpha = currentAlpha,
                checkedEventIds = checkedEventIds.toList()
            )
            findNavController().navigateUp()
        }
    }

    private fun initColors() {
        displayColors.clear()
        // 获取系统 Monet 颜色
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val context = requireContext()
            val monetColors = listOf(
                android.R.color.system_accent1_500,
                android.R.color.system_accent2_500,
                android.R.color.system_accent3_500,
                android.R.color.system_neutral1_500,
                android.R.color.system_accent1_200
            )
            monetColors.forEach { resId ->
                try {
                    val colorInt = context.getColor(resId)
                    val hex = String.format("#%06X", (0xFFFFFF and colorInt))
                    displayColors.add(hex)
                } catch (e: Exception) { /* Ignore */ }
            }
        }
        // 追加 M3 扩展色板
        displayColors.addAll(baseMaterialColors)
    }

    private fun setupColorPalette() {
        val inflater = LayoutInflater.from(context)
        binding.paletteContainer.removeAllViews()

        for (colorHex in displayColors) {
            val swatch = ItemColorSwatchBinding.inflate(inflater, binding.paletteContainer, false)
            val color = Color.parseColor(colorHex)
            (swatch.colorView.background as GradientDrawable).setColor(color)

            swatch.colorView.clipToOutline = true

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

    private fun updateEventSelectionList() {
        binding.recyclerViewEventSelection.adapter = EventSelectionAdapter()
    }

    inner class EventSelectionAdapter : RecyclerView.Adapter<EventSelectionAdapter.SelectionViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectionViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_multiple_choice, parent, false)
            return SelectionViewHolder(view)
        }

        override fun onBindViewHolder(holder: SelectionViewHolder, position: Int) {
            val event = allEvents[position]
            val textView = holder.itemView.findViewById<CheckedTextView>(android.R.id.text1)

            textView.text = event.title
            textView.isChecked = checkedEventIds.contains(event.id)

            if (event.bookId != null && event.bookId != args.bookId) {
                textView.text = "${event.title} (将从其他本移入)"
                textView.setTextColor(Color.GRAY)
            } else {
                textView.setTextColor(Color.parseColor("#212121"))
            }

            holder.itemView.setOnClickListener {
                if (checkedEventIds.contains(event.id)) {
                    checkedEventIds.remove(event.id)
                    textView.isChecked = false
                } else {
                    checkedEventIds.add(event.id)
                    textView.isChecked = true
                }
            }
        }

        override fun getItemCount() = allEvents.size

        inner class SelectionViewHolder(v: View) : RecyclerView.ViewHolder(v)
    }
}