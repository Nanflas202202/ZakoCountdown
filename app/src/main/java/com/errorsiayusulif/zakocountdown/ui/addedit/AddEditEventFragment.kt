package com.errorsiayusulif.zakocountdown.ui.addedit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.databinding.FragmentAddEditEventBinding
import com.errorsiayusulif.zakocountdown.ui.agenda.AgendaViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.*

class AddEditEventFragment : Fragment() {

    private var _binding: FragmentAddEditEventBinding? = null
    private val binding get() = _binding!!

    // 这里的 Factory 依赖于上面文件中的类定义
    private val viewModel: AddEditEventViewModel by viewModels {
        AddEditEventViewModelFactory(
            (requireActivity().application as ZakoCountdownApplication).repository,
            requireActivity().application
        )
    }

    private val agendaViewModel: AgendaViewModel by viewModels({ requireActivity() })
    private val args: AddEditEventFragmentArgs by navArgs()

    private var selectedBookId: Long? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddEditEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.start(
            if (args.eventId == -1L) null else args.eventId,
            args.defaultBookId
        )

        setupObservers()
        setupAgendaSelector()
        setupClickListeners()
    }

    private fun setupObservers() {
        viewModel.eventTitle.observe(viewLifecycleOwner) { title ->
            binding.editTextTitle.setText(title ?: "")
        }
        viewModel.selectedDateTime.observe(viewLifecycleOwner) { calendar ->
            val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            binding.buttonDatePicker.text = dateFormat.format(calendar.time)
            binding.buttonTimePicker.text = timeFormat.format(calendar.time)
        }

        viewModel.eventBookId.observe(viewLifecycleOwner) { bookId ->
            selectedBookId = bookId
            // 回显逻辑在 setupAgendaSelector 的 Observer 中统一处理
        }
    }

    private fun setupAgendaSelector() {
        if (!PreferenceManager(requireContext()).isAgendaBookEnabled()) {
            binding.inputLayoutAgenda.visibility = View.GONE
            return
        }

        binding.inputLayoutAgenda.visibility = View.VISIBLE

        agendaViewModel.allBooks.observe(viewLifecycleOwner) { books ->
            val displayList = mutableListOf("默认 / 全部")
            val idList = mutableListOf<Long?>(null)

            books.forEach {
                displayList.add(it.name)
                idList.add(it.id)
            }

            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, displayList)
            (binding.inputLayoutAgenda.editText as? AutoCompleteTextView)?.setAdapter(adapter)

            // 尝试回显
            val currentId = selectedBookId
            val index = idList.indexOf(currentId)

            // 使用 setText(..., false) 防止触发过滤
            if (index != -1) {
                (binding.inputLayoutAgenda.editText as? AutoCompleteTextView)?.setText(displayList[index], false)
            } else {
                (binding.inputLayoutAgenda.editText as? AutoCompleteTextView)?.setText(displayList[0], false)
            }

            (binding.inputLayoutAgenda.editText as? AutoCompleteTextView)?.setOnItemClickListener { _, _, position, _ ->
                selectedBookId = idList[position]
            }
        }
    }

    private fun setupClickListeners() {
        binding.buttonDatePicker.setOnClickListener { showDatePicker() }
        binding.buttonTimePicker.setOnClickListener { showTimePicker() }

        binding.fabSaveEvent.setOnClickListener {
            val title = binding.editTextTitle.text.toString()
            if (viewModel.saveEvent(title, selectedBookId)) {
                findNavController().navigateUp()
            } else {
                Toast.makeText(context, "标题不能为空哦～", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDatePicker() {
        val selectedDateInMillis = viewModel.selectedDateTime.value?.timeInMillis ?: System.currentTimeMillis()
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("选择目标日期")
            .setSelection(selectedDateInMillis)
            .build()
        datePicker.addOnPositiveButtonClickListener { selection ->
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = selection }
            viewModel.updateDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        }
        datePicker.show(parentFragmentManager, "DATE_PICKER")
    }

    private fun showTimePicker() {
        val calendar = viewModel.selectedDateTime.value ?: Calendar.getInstance()
        val timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(calendar.get(Calendar.HOUR_OF_DAY))
            .setMinute(calendar.get(Calendar.MINUTE))
            .setTitleText("选择目标时间")
            .build()
        timePicker.addOnPositiveButtonClickListener {
            viewModel.updateTime(timePicker.hour, timePicker.minute)
        }
        timePicker.show(parentFragmentManager, "TIME_PICKER")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}