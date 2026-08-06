// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/BackupNodeAdapter.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.ConflictLevel
import com.errorsiayusulif.zakocountdown.data.NodeType
import com.errorsiayusulif.zakocountdown.data.SelectableNode
import com.errorsiayusulif.zakocountdown.databinding.ItemBackupNodeBinding

class BackupNodeAdapter(
    private var rootNodes: List<SelectableNode>
) : RecyclerView.Adapter<BackupNodeAdapter.NodeViewHolder>() {

    private val displayList = mutableListOf<SelectableNode>()

    init {
        rebuildDisplayList()
    }

    fun getRootNodes(): List<SelectableNode> = rootNodes

    fun updateNodes(newRoots: List<SelectableNode>) {
        this.rootNodes = newRoots
        rebuildDisplayList()
        notifyDataSetChanged()
    }

    private fun rebuildDisplayList() {
        displayList.clear()
        for (node in rootNodes) {
            displayList.add(node)
            if (node.isExpanded) {
                displayList.addAll(node.children)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val binding = ItemBackupNodeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NodeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        holder.bind(displayList[position])
    }

    override fun getItemCount(): Int = displayList.size

    inner class NodeViewHolder(val binding: ItemBackupNodeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(node: SelectableNode) {
            binding.root.setPadding(0, 0, 0, 0)
            binding.ivExpand.visibility = View.GONE
            binding.tvConflict.visibility = View.GONE
            binding.ivStatusIcon.visibility = View.GONE

            binding.tvTitle.text = node.title
            binding.tvSubtitle.text = node.subtitle
            binding.tvSubtitle.visibility = if (node.subtitle != null) View.VISIBLE else View.GONE

            binding.cbNode.setOnCheckedChangeListener(null)
            binding.cbNode.isChecked = node.isChecked

            // --- 视觉优化：层级样式与图标 ---
            when (node.type) {
                NodeType.HEADER -> {
                    binding.tvTitle.setTypeface(null, Typeface.BOLD)
                    binding.tvTitle.textSize = 16f
                    binding.root.setPadding(32, 24, 32, 8)
                    // 使用左侧图标区分不同 Header (可选)
                }
                NodeType.SUB_OPTION -> {
                    binding.tvTitle.setTypeface(null, Typeface.NORMAL)
                    binding.tvTitle.textSize = 13f
                    binding.root.setPadding(140, 0, 32, 0) // 深缩进
                    binding.tvSubtitle.visibility = View.GONE
                }
                NodeType.SETTING -> {
                    binding.tvTitle.setTypeface(null, Typeface.NORMAL)
                    binding.tvTitle.textSize = 15f
                    binding.root.setPadding(64, 16, 32, 16)
                    // 假设添加了一个 ic_settings 小图标在左侧 (需要修改 XML 支持)
                }
                NodeType.BOOK -> {
                    binding.tvTitle.setTypeface(null, Typeface.BOLD)
                    binding.tvTitle.textSize = 15f
                    binding.root.setPadding(64, 16, 32, 16)
                    if (node.children.isNotEmpty()) {
                        binding.ivExpand.visibility = View.VISIBLE
                        binding.ivExpand.rotation = if (node.isExpanded) 180f else 0f
                        binding.ivExpand.setOnClickListener {
                            node.isExpanded = !node.isExpanded
                            rebuildDisplayList()
                            notifyDataSetChanged()
                        }
                    }
                }
                NodeType.EVENT -> {
                    binding.tvTitle.setTypeface(null, Typeface.NORMAL)
                    binding.tvTitle.textSize = 15f
                    binding.root.setPadding(64, 16, 32, 16)
                    if (node.children.isNotEmpty()) {
                        binding.ivExpand.visibility = View.VISIBLE
                        binding.ivExpand.rotation = if (node.isExpanded) 180f else 0f
                        binding.ivExpand.setOnClickListener {
                            node.isExpanded = !node.isExpanded
                            rebuildDisplayList()
                            notifyDataSetChanged()
                        }
                    }
                }
            }

            // 冲突提示
            if (node.conflictMessage != null) {
                binding.tvConflict.visibility = View.VISIBLE
                binding.tvConflict.text = node.conflictMessage
                when (node.conflictLevel) {
                    ConflictLevel.WARNING -> {
                        binding.tvConflict.setTextColor(Color.parseColor("#F57F17"))
                        binding.ivStatusIcon.visibility = View.VISIBLE
                    }
                    ConflictLevel.ERROR -> {
                        binding.tvConflict.setTextColor(Color.parseColor("#D32F2F"))
                        binding.ivStatusIcon.visibility = View.VISIBLE
                    }
                    else -> {}
                }
            }

            // 交互逻辑
            val toggleAction = {
                val newState = !node.isChecked
                node.isChecked = newState
                binding.cbNode.isChecked = newState

                if (node.type == NodeType.HEADER) {
                    var i = rootNodes.indexOf(node) + 1
                    while (i < rootNodes.size && rootNodes[i].type != NodeType.HEADER) {
                        rootNodes[i].isChecked = newState
                        rootNodes[i].children.forEach { it.isChecked = newState }
                        i++
                    }
                    rebuildDisplayList()
                    notifyDataSetChanged()
                } else if (node.children.isNotEmpty()) {
                    node.children.forEach { it.isChecked = newState }
                    // 如果只点复选框，则不触发展开/折叠；如果需要联动，可在此处加入展开逻辑
                    // 这里我们保持只改状态，不改展开折叠
                    rebuildDisplayList()
                    notifyDataSetChanged()
                } else {
                    notifyItemChanged(position)
                }
            }

            binding.cbNode.setOnClickListener { toggleAction() }

            // 点击行触发展开或选中
            binding.root.setOnClickListener {
                if (node.children.isNotEmpty() && node.type != NodeType.HEADER) {
                    node.isExpanded = !node.isExpanded
                    rebuildDisplayList()
                    notifyDataSetChanged()
                } else {
                    toggleAction()
                }
            }
        }
    }
}