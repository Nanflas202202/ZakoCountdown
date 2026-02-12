// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/agenda/AgendaBookFragment.kt
package com.errorsiayusulif.zakocountdown.ui.agenda

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.core.graphics.ColorUtils
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.data.AgendaBook
import com.errorsiayusulif.zakocountdown.data.CountdownEvent
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.databinding.FragmentAgendaBookBinding
import com.errorsiayusulif.zakocountdown.databinding.ItemAgendaBookCardBinding
import com.errorsiayusulif.zakocountdown.databinding.ItemAgendaBookListBinding
import com.errorsiayusulif.zakocountdown.ui.home.HomeViewModel
import com.errorsiayusulif.zakocountdown.ui.home.HomeViewModelFactory
import com.errorsiayusulif.zakocountdown.utils.TimeCalculator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.Collections

/**
 * 日程本列表/管理页面
 * 支持网格/列表切换，拖拽排序，长按管理
 */
class AgendaBookFragment : Fragment() {

    private var _binding: FragmentAgendaBookBinding? = null
    private val binding get() = _binding!!

    private val agendaViewModel: AgendaViewModel by viewModels({ requireActivity() })
    private val homeViewModel: HomeViewModel by viewModels {
        val app = requireActivity().application as ZakoCountdownApplication
        HomeViewModelFactory(app.repository, app)
    }

    private lateinit var preferenceManager: PreferenceManager

    private var currentEvents: List<CountdownEvent> = emptyList()
    private var isGridView = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAgendaBookBinding.inflate(inflater, container, false)
        preferenceManager = PreferenceManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- 修复 Bug 3: 从 Preference 读取布局偏好 ---
        isGridView = preferenceManager.isAgendaViewModeGrid()

        setupMenu()
        setupRecyclerView()

        homeViewModel.allEvents.observe(viewLifecycleOwner) { events ->
            currentEvents = events ?: emptyList()
            // 数据变化时刷新列表 (更新计数)
            binding.recyclerViewBooks.adapter?.notifyDataSetChanged()
        }

        agendaViewModel.allBooks.observe(viewLifecycleOwner) { books ->
            (binding.recyclerViewBooks.adapter as? BookAdapter)?.setBooks(books ?: emptyList())
        }

        binding.fabAddBook.setOnClickListener {
            val action = AgendaBookFragmentDirections.actionAgendaBookFragmentToAddEditAgendaBookFragment()
            findNavController().navigate(action)
        }
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // 根据当前状态显示对应图标
                val iconRes = if (isGridView) R.drawable.ic_list else R.drawable.ic_grid_view
                val item = menu.add(0, 1001, 0, "切换视图")
                item.setIcon(iconRes)
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == 1001) {
                    isGridView = !isGridView

                    // --- 修复 Bug 3: 保存布局偏好 ---
                    preferenceManager.setAgendaViewMode(isGridView)

                    // 更新图标
                    val iconRes = if (isGridView) R.drawable.ic_list else R.drawable.ic_grid_view
                    menuItem.setIcon(iconRes)

                    // 重新应用布局管理器并刷新适配器
                    setupRecyclerView()
                    val books = agendaViewModel.allBooks.value ?: emptyList()
                    (binding.recyclerViewBooks.adapter as? BookAdapter)?.setBooks(books)
                    return true
                }
                return false
            }
        }, viewLifecycleOwner)
    }

    private fun setupRecyclerView() {
        val layoutManager = if (isGridView) GridLayoutManager(context, 2) else LinearLayoutManager(context)
        binding.recyclerViewBooks.layoutManager = layoutManager

        val adapter = BookAdapter()
        binding.recyclerViewBooks.adapter = adapter

        // 拖拽排序
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                // 锁定前两项 (全部/重点) 不可移动
                if (fromPos < 2 || toPos < 2) return false

                adapter.onItemMove(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                // 拖拽结束后保存顺序
                adapter.saveOrder()
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewBooks)
    }

    inner class BookAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val books = mutableListOf<AgendaBook>()

        fun setBooks(newBooks: List<AgendaBook>) {
            books.clear()
            books.addAll(newBooks)
            notifyDataSetChanged()
        }

        fun onItemMove(fromPosition: Int, toPosition: Int) {
            if (fromPosition < 2 || toPosition < 2) return
            // Adapter索引 -> List索引 (减去2个header)
            Collections.swap(books, fromPosition - 2, toPosition - 2)
            notifyItemMoved(fromPosition, toPosition)
        }

        fun saveOrder() {
            agendaViewModel.updateBookOrders(books)
        }

        override fun getItemCount() = 2 + books.size

        override fun getItemViewType(position: Int): Int = if (isGridView) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) {
                GridViewHolder(ItemAgendaBookCardBinding.inflate(inflater, parent, false))
            } else {
                ListViewHolder(ItemAgendaBookListBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is GridViewHolder) bindGrid(holder, position)
            else if (holder is ListViewHolder) bindList(holder, position)
        }

        private fun bindGrid(holder: GridViewHolder, position: Int) {
            // ... (Grid 数据绑定逻辑)
            val bookId: Long
            val name: String
            val colorHex: String
            val count: Int
            val coverUri: String?
            val alpha: Float

            if (position == 0) {
                bookId = -1L; name = "全部日程"; colorHex = "#212121"; coverUri = null; alpha = 1f
                count = currentEvents.size
            } else if (position == 1) {
                bookId = -2L; name = "重点日程"; colorHex = "#F44336"; coverUri = null; alpha = 1f
                count = currentEvents.count { it.isImportant }
            } else {
                val book = books[position - 2]
                bookId = book.id; name = book.name; colorHex = book.colorHex; coverUri = book.coverImageUri
                count = currentEvents.count { it.bookId == book.id }
                alpha = book.cardAlpha
            }

            holder.binding.tvBookName.text = name
            holder.binding.tvCount.text = "${count}项"

            try {
                val color = Color.parseColor(colorHex)
                holder.binding.llInfoBar.setBackgroundColor(color)
                val isLight = ColorUtils.calculateLuminance(color) > 0.5
                val textColor = if (isLight) Color.BLACK else Color.WHITE
                holder.binding.tvBookName.setTextColor(textColor)
                holder.binding.tvCount.setTextColor(textColor)
            } catch (e: Exception) {}

            if (coverUri != null) {
                holder.binding.ivCover.load(Uri.parse(coverUri))
                holder.binding.ivCover.alpha = alpha
                holder.binding.vScrim.visibility = View.VISIBLE
            } else {
                holder.binding.ivCover.setImageDrawable(null)
                try {
                    holder.binding.ivCover.setBackgroundColor(Color.parseColor(colorHex))
                    holder.binding.ivCover.alpha = if (position < 2) 0.3f else 1.0f
                } catch (e:Exception) {
                    holder.binding.ivCover.setBackgroundColor(Color.LTGRAY)
                }
                holder.binding.vScrim.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                val action = AgendaBookFragmentDirections.actionAgendaBookFragmentToAgendaDetailFragment(bookId)
                findNavController().navigate(action)
            }
            if (position >= 2) {
                holder.itemView.setOnLongClickListener { showOptions(books[position - 2]); true }
            }
        }

        private fun bindList(holder: ListViewHolder, position: Int) {
            // ... (List 数据绑定逻辑)
            val bookId: Long
            val name: String
            val colorHex: String
            val count: Int

            if (position == 0) {
                bookId = -1L; name = "全部日程"; colorHex = "#212121"
                count = currentEvents.size
            } else if (position == 1) {
                bookId = -2L; name = "重点日程"; colorHex = "#F44336"
                count = currentEvents.count { it.isImportant }
            } else {
                val book = books[position - 2]
                bookId = book.id; name = book.name; colorHex = book.colorHex
                count = currentEvents.count { it.bookId == book.id }
            }

            holder.binding.tvBookName.text = name
            try { holder.binding.indicatorBar.setBackgroundColor(Color.parseColor(colorHex)) } catch(e:Exception){}

            // 统计信息：显示最近的一个日程
            val eventsInBook = if (position == 0) currentEvents
            else if (position == 1) currentEvents.filter { it.isImportant }
            else currentEvents.filter { it.bookId == bookId }

            val nextEvent = eventsInBook.filter { it.targetDate.time >= System.currentTimeMillis() }
                .minByOrNull { it.targetDate }

            val statsText = StringBuilder("$count 项")
            if (nextEvent != null) {
                val diff = TimeCalculator.calculateDifference(nextEvent.targetDate)
                statsText.append(" · 最近: ${nextEvent.title} (${diff.totalDays}天)")
            } else if (count > 0 && eventsInBook.isNotEmpty()) {
                statsText.append(" · 全部已过期")
            } else {
                statsText.append(" · 暂无日程")
            }
            holder.binding.tvStats.text = statsText.toString()

            holder.itemView.setOnClickListener {
                val action = AgendaBookFragmentDirections.actionAgendaBookFragmentToAgendaDetailFragment(bookId)
                findNavController().navigate(action)
            }
            if (position >= 2) {
                holder.itemView.setOnLongClickListener { showOptions(books[position - 2]); true }
            }
        }

        inner class GridViewHolder(val binding: ItemAgendaBookCardBinding) : RecyclerView.ViewHolder(binding.root)
        inner class ListViewHolder(val binding: ItemAgendaBookListBinding) : RecyclerView.ViewHolder(binding.root)
    }

    private fun showOptions(book: AgendaBook) {
        val options = arrayOf("编辑详情", "删除日程本")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(book.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val action = AgendaBookFragmentDirections.actionAgendaBookFragmentToAddEditAgendaBookFragment(
                            bookId = book.id,
                            title = "编辑日程本"
                        )
                        findNavController().navigate(action)
                    }
                    1 -> deleteBook(book)
                }
            }.show()
    }

    private fun deleteBook(book: AgendaBook) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("确认删除？")
            .setMessage("日程本删除后，其中的日程将移回“默认日程本”，不会被删除。")
            .setPositiveButton("删除") { _, _ -> agendaViewModel.deleteBook(book) }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}