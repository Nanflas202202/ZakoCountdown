// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/LogReaderFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.LogEntry
import com.errorsiayusulif.zakocountdown.databinding.FragmentLogReaderBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

class LogReaderFragment : Fragment() {

    private var _binding: FragmentLogReaderBinding? = null
    private val binding get() = _binding!!
    private var allLogs = listOf<LogEntry>()
    private val args: LogReaderFragmentArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLogReaderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerViewLogs.layoutManager = LinearLayoutManager(context)
        binding.btnRefresh.setOnClickListener { fetchLogs() }

        // 筛选监听
        binding.searchInput.addTextChangedListener { filterLogs() }
        binding.chipGroupLevel.setOnCheckedChangeListener { _, _ -> filterLogs() }

        fetchLogs()
    }

    private fun fetchLogs() {
        lifecycleScope.launch(Dispatchers.IO) {
            val logs = mutableListOf<LogEntry>()
            try {
                val reader: BufferedReader

                if (args.filePath != null) {
                    // 1. 读取本地文件
                    val file = File(args.filePath!!)
                    reader = BufferedReader(InputStreamReader(FileInputStream(file)))
                } else {
                    // 2. 读取实时 Logcat
                    val pid = android.os.Process.myPid()
                    // -d: dump and exit, -v time: format
                    val process = Runtime.getRuntime().exec("logcat -d --pid=$pid -v time")
                    reader = BufferedReader(InputStreamReader(process.inputStream))
                }

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    parseLogLine(line!!)?.let { logs.add(it) }
                }

                // 如果是实时日志，倒序显示最新的；如果是文件，顺序显示
                if (args.filePath == null) {
                    logs.reverse()
                }

                reader.close()
            } catch (e: Exception) {
                logs.add(LogEntry("E", "", "LogReader", "Error: ${e.message}", Color.RED))
            }

            withContext(Dispatchers.Main) {
                allLogs = logs
                filterLogs()
            }
        }
    }

    private fun parseLogLine(line: String): LogEntry? {
        try {
            // 格式示例: 10-04 12:30:45.123 D/Tag: Message
            val parts = line.split(Regex("\\s+"), 3)
            if (parts.size < 3) return null

            val time = parts[1]
            val levelAndTag = parts[2]
            val splitIndex = levelAndTag.indexOf('/')
            if (splitIndex == -1) return null

            val level = levelAndTag.substring(0, splitIndex)
            val tag = levelAndTag.substring(splitIndex + 1, levelAndTag.indexOf(':'))
            val msg = line.substring(line.indexOf("):") + 2).trim()

            val color = when(level) {
                "E" -> Color.RED
                "W" -> Color.parseColor("#FFA000") // Orange
                else -> Color.parseColor("#757575") // Grey
            }

            return LogEntry(level, time, tag, msg, color)
        } catch (e: Exception) {
            return null
        }
    }

    private fun filterLogs() {
        val query = binding.searchInput.text.toString().lowercase()
        val levelFilter = when (binding.chipGroupLevel.checkedChipId) {
            R.id.chip_error -> "E"
            R.id.chip_warn -> "W"
            R.id.chip_info -> "I"
            R.id.chip_debug -> "D"
            else -> "ALL"
        }

        val filtered = allLogs.filter {
            (levelFilter == "ALL" || it.level == levelFilter) &&
                    (it.tag.lowercase().contains(query) || it.message.lowercase().contains(query))
        }

        if (binding.recyclerViewLogs.adapter == null) {
            // --- 【核心修复】传入 lambda 表达式处理长按事件 ---
            binding.recyclerViewLogs.adapter = LogAdapter(filtered) { logEntry ->
                copyLogToClipboard(logEntry)
            }
        } else {
            (binding.recyclerViewLogs.adapter as LogAdapter).updateList(filtered)
        }
    }

    // --- 【新功能】复制日志到剪贴板 ---
    private fun copyLogToClipboard(logEntry: LogEntry) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // 格式化文本：时间 等级/Tag: 内容
        val clipText = "${logEntry.time} ${logEntry.level}/${logEntry.tag}: ${logEntry.message}"
        val clip = ClipData.newPlainText("Log Entry", clipText)
        clipboard.setPrimaryClip(clip)

        // Android 13+ 系统会自动提示已复制，低版本我们手动提示
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(requireContext(), "日志已复制", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}