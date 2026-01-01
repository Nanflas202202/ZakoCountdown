// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/LogFileListFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.databinding.FragmentLogFileListBinding // 确保这里导入正确
import com.errorsiayusulif.zakocountdown.utils.LogRecorder
import java.io.File

class LogFileListFragment : Fragment() {

    private var _binding: FragmentLogFileListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLogFileListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val files = LogRecorder.getLogFiles(requireContext())

        binding.recyclerViewLogFiles.apply {
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            adapter = object : RecyclerView.Adapter<LogFileViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogFileViewHolder {
                    val v = LayoutInflater.from(parent.context).inflate(R.layout.item_log_file, parent, false)
                    return LogFileViewHolder(v)
                }
                override fun onBindViewHolder(holder: LogFileViewHolder, position: Int) {
                    val file = files[position]
                    holder.name.text = file.name
                    holder.size.text = Formatter.formatFileSize(context, file.length())
                    holder.itemView.setOnClickListener {
                        val action = LogFileListFragmentDirections.actionLogFileListFragmentToLogReaderFragment(file.absolutePath)
                        findNavController().navigate(action)
                    }
                }
                override fun getItemCount() = files.size
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class LogFileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.file_name)
        val size: TextView = view.findViewById(R.id.file_size)
    }
}