// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/addedit/AddEditEventFragment.kt
package com.errorsiayusulif.zakocountdown.ui.addedit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.databinding.FragmentAddEditEventBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.*

class AddEditEventFragment : Fragment() {

    private var _binding: FragmentAddEditEventBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddEditEventViewModel by viewModels {
        AddEditEventViewModelFactory(
            (requireActivity().application as ZakoCountdownApplication).repository,
            requireActivity().application // 传入 application
        )
    }

    private val args: AddEditEventFragmentArgs by navArgs()

    // --- 【核心修复】恢复为标准写法 ---
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    // ... 其他所有代码保持不变 ...

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.start(if (args.eventId == -1L) null else args.eventId)
        setupObservers()
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
    }

    private fun setupClickListeners() {
        binding.buttonDatePicker.setOnClickListener { showDatePicker() }
        binding.buttonTimePicker.setOnClickListener { showTimePicker() }
        binding.fabSaveEvent.setOnClickListener {
            val title = binding.editTextTitle.text.toString()
            if (viewModel.saveEvent(title)) {
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