// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/BackupNodeAdapter.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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

    // --- 核心修复：公开获取原始节点列表的方法 ---
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

            when (node.type) {
                NodeType.HEADER -> {
                    binding.tvTitle.setTypeface(null, Typeface.BOLD)
                    binding.root.setPadding(32, 24, 32, 8)
                }
                NodeType.SUB_OPTION -> {
                    binding.tvTitle.setTypeface(null, Typeface.NORMAL)
                    binding.root.setPadding(120, 0, 32, 0)
                    binding.tvSubtitle.visibility = View.GONE
                }
                else -> {
                    binding.tvTitle.setTypeface(null, Typeface.NORMAL)
                    binding.root.setPadding(32, 16, 32, 16)
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

            binding.cbNode.setOnCheckedChangeListener { _, isChecked ->
                node.isChecked = isChecked
                // 级联选择：父变子变
                node.children.forEach { it.isChecked = isChecked }
                notifyDataSetChanged()
            }

            binding.root.setOnClickListener {
                if (node.children.isNotEmpty() && node.type != NodeType.HEADER) {
                    node.isExpanded = !node.isExpanded
                    rebuildDisplayList()
                    notifyDataSetChanged()
                } else {
                    binding.cbNode.toggle()
                }
            }
        }
    }
}