// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/LogAdapter.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.errorsiayusulif.zakocountdown.data.LogEntry
import com.errorsiayusulif.zakocountdown.databinding.ItemLogEntryBinding

// 【修改】构造函数增加 onItemLongClick 回调
class LogAdapter(
    private var logs: List<LogEntry>,
    private val onItemLongClick: (LogEntry) -> Unit
) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    fun updateList(newLogs: List<LogEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val item = logs[position]
        holder.bind(item)

        // 【新增】设置长按监听器
        holder.itemView.setOnLongClickListener {
            onItemLongClick(item)
            true // 返回 true 表示事件已被消费，不会触发点击事件
        }
    }

    override fun getItemCount() = logs.size

    class LogViewHolder(private val binding: ItemLogEntryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LogEntry) {
            binding.logLevel.text = item.level
            binding.logLevel.setTextColor(item.color)
            binding.logTime.text = item.time
            binding.logTag.text = item.tag
            binding.logMessage.text = item.message
            binding.logMessage.setTextColor(item.color)
        }
    }
}