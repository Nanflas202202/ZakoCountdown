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

    fun getRootNodes(): List<SelectableNode> = rootNodes

    fun updateNodes(newRoots: List<SelectableNode>) {
        this.rootNodes = newRoots
        rebuildDisplayList()
        notifyDataSetChanged()
    }

    private fun rebuildDisplayList() {
        displayList.clear()
        var currentHeaderExpanded = true // 默认展开

        for (node in rootNodes) {
            if (node.type == NodeType.HEADER) {
                currentHeaderExpanded = node.isExpanded
                displayList.add(node) // Header 始终显示
            } else {
                if (currentHeaderExpanded) {
                    displayList.add(node)
                    // 如果该项自身也展开了，显示其子选项
                    if (node.isExpanded) {
                        displayList.addAll(node.children)
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val binding = ItemBackupNodeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NodeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        holder.bind(displayList[position], position)
    }

    override fun getItemCount(): Int = displayList.size

    inner class NodeViewHolder(val binding: ItemBackupNodeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(node: SelectableNode, position: Int) {

            // 重置状态
            binding.root.setPadding(0, 0, 0, 0)
            binding.ivExpand.visibility = View.GONE
            binding.tvConflict.visibility = View.GONE
            binding.ivStatusIcon.visibility = View.GONE

            binding.tvTitle.text = node.title
            binding.tvSubtitle.text = node.subtitle
            binding.tvSubtitle.visibility = if (node.subtitle != null) View.VISIBLE else View.GONE

            binding.cbNode.setOnCheckedChangeListener(null)
            binding.cbNode.isChecked = node.isChecked

            // 层级样式
            when (node.type) {
                NodeType.HEADER -> {
                    binding.tvTitle.setTypeface(null, Typeface.BOLD)
                    binding.root.setPadding(32, 24, 32, 8)
                    // Header 始终包含一个假的“展开状态”以供点击全选
                    binding.ivExpand.visibility = View.VISIBLE
                    binding.ivExpand.rotation = if (node.isExpanded) 180f else 0f
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

            // --- 核心修复：交互逻辑 ---

            // 1. 点击 CheckBox 的逻辑 (级联勾选)
            val handleCheckChange = { isChecked: Boolean ->
                node.isChecked = isChecked
                // 如果当前节点有子节点 (无论是 HEADER 还是 BOOK/EVENT)，子节点跟随父节点状态
                if (node.children.isNotEmpty()) {
                    node.children.forEach { it.isChecked = isChecked }
                }

                // 如果是 HEADER，它的“子节点”在数据结构上可能是平级的下一个节点，需要特殊处理
                if (node.type == NodeType.HEADER) {
                    // 找到在 rootNodes 中，该 HEADER 之后、下一个 HEADER 之前的所有节点
                    val headerIndex = rootNodes.indexOf(node)
                    if (headerIndex != -1) {
                        for (i in (headerIndex + 1) until rootNodes.size) {
                            if (rootNodes[i].type == NodeType.HEADER) break
                            rootNodes[i].isChecked = isChecked
                            // 连带勾选其子选项
                            rootNodes[i].children.forEach { it.isChecked = isChecked }
                        }
                    }
                }
                notifyDataSetChanged() // 刷新全局状态
            }

            binding.cbNode.setOnClickListener {
                handleCheckChange(binding.cbNode.isChecked)
            }

            // 2. 点击 Item 整体的逻辑 (展开/折叠)
            binding.root.setOnClickListener {
                if (node.type == NodeType.HEADER) {
                    // Header 点击：展开/折叠它管辖的所有项
                    val headerIndex = rootNodes.indexOf(node)
                    if (headerIndex != -1) {
                        val isCurrentlyExpanded = node.isExpanded
                        node.isExpanded = !isCurrentlyExpanded
                        // 控制下属的 BOOK/EVENT 是否显示
                        for (i in (headerIndex + 1) until rootNodes.size) {
                            if (rootNodes[i].type == NodeType.HEADER) break
                            // 我们可以直接通过重建 displayList 来实现隐藏/显示
                            // 这里我们只需标记 Header 本身的 isExpanded 状态，修改 rebuildDisplayList 逻辑
                        }
                        rebuildDisplayList()
                        notifyDataSetChanged()
                    }
                } else if (node.children.isNotEmpty()) {
                    // Book/Event 点击：展开/折叠其自身的子选项 (SubOptions)
                    node.isExpanded = !node.isExpanded
                    rebuildDisplayList()
                    notifyDataSetChanged()
                } else {
                    // 没有子项的节点点击：切换勾选状态
                    binding.cbNode.isChecked = !binding.cbNode.isChecked
                    handleCheckChange(binding.cbNode.isChecked)
                }
            }
        }
    }
}