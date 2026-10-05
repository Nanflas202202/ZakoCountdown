// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/agenda/AgendaBookFragment.kt
package com.errorsiayusulif.zakocountdown.ui.agenda

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
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
import java.io.File
import java.util.Collections

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

    // -1 = 全部, -2 = 重点, >0 = 自定义
    private var editingBookId: Long? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { sourceUri ->
                val bookId = editingBookId ?: return@let
                val context = requireContext()
                try {
                    val inputStream = context.contentResolver.openInputStream(sourceUri)
                    if (inputStream != null) {
                        val dir = File(context.filesDir, "covers")
                        if (!dir.exists()) dir.mkdirs()
                        val file = File(dir, "cover_${System.currentTimeMillis()}_$bookId.png")
                        val outputStream = java.io.FileOutputStream(file)
                        inputStream.use { input -> outputStream.use { output -> input.copyTo(output) } }

                        val finalUri = Uri.fromFile(file).toString()

                        if (bookId < 0) {
                            // 保存默认本子的封面
                            preferenceManager.saveDefaultBookCover(bookId == -2L, finalUri)
                            binding.recyclerViewBooks.adapter?.notifyDataSetChanged()
                        } else {
                            // 保存自定义本子的封面
                            agendaViewModel.updateBookCover(bookId, finalUri)
                        }
                        android.widget.Toast.makeText(requireContext(), R.string.agenda_cover_updated, android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, R.string.agenda_cover_failed, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAgendaBookBinding.inflate(inflater, container, false)
        preferenceManager = PreferenceManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isGridView = preferenceManager.isAgendaViewModeGrid()

        setupMenu()
        setupRecyclerView()

        homeViewModel.allEvents.observe(viewLifecycleOwner) { events ->
            currentEvents = events ?: emptyList()
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
                val iconRes = if (isGridView) R.drawable.ic_list else R.drawable.ic_grid_view
                val item = menu.add(0, 1001, 0, R.string.agenda_switch_view)
                item.setIcon(iconRes)
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == 1001) {
                    isGridView = !isGridView
                    preferenceManager.setAgendaViewMode(isGridView)

                    val iconRes = if (isGridView) R.drawable.ic_list else R.drawable.ic_grid_view
                    menuItem.setIcon(iconRes)

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
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        // 横屏状态下，网格视图给 4 列，列表视图给 2 列
        val gridSpans = if (isLandscape) 4 else 2
        val listSpans = if (isLandscape) 2 else 1

        val layoutManager = if (isGridView) {
            GridLayoutManager(context, gridSpans)
        } else {
            GridLayoutManager(context, listSpans) // 使用 GridLayoutManager 代替 LinearLayoutManager 以支持多列列表
        }

        binding.recyclerViewBooks.layoutManager = layoutManager

        val adapter = BookAdapter()
        binding.recyclerViewBooks.adapter = adapter

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
                if (fromPos < 2 || toPos < 2) return false
                adapter.onItemMove(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
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
            val bookId: Long
            val name: String
            val colorHex: String
            val count: Int
            val coverUri: String?
            val alpha: Float

            if (position == 0) {
                bookId = -1L; name = getString(R.string.nav_filter_all); colorHex = "#212121"
                coverUri = preferenceManager.getDefaultBookCover(false)
                alpha = preferenceManager.getDefaultBookAlpha(false)
                count = currentEvents.size
            } else if (position == 1) {
                bookId = -2L; name = getString(R.string.nav_filter_important); colorHex = "#F44336"
                coverUri = preferenceManager.getDefaultBookCover(true)
                alpha = preferenceManager.getDefaultBookAlpha(true)
                count = currentEvents.count { it.isImportant }
            } else {
                val book = books[position - 2]
                bookId = book.id; name = book.name; colorHex = book.colorHex; coverUri = book.coverImageUri
                count = currentEvents.count { it.bookId == book.id }
                alpha = book.cardAlpha
            }

            holder.binding.tvBookName.text = name
            holder.binding.tvCount.text = getString(R.string.agenda_event_count, count)

            try {
                val color = Color.parseColor(colorHex)
                holder.binding.llInfoBar.setBackgroundColor(color)
                val isLight = ColorUtils.calculateLuminance(color) > 0.5
                val textColor = if (isLight) Color.BLACK else Color.WHITE
                holder.binding.tvBookName.setTextColor(textColor)
                holder.binding.tvCount.setTextColor(textColor)
            } catch (e: Exception) {}

            // 【核心修复 3】：不管有没有图片，透明度都要生效
            if (coverUri != null) {
                holder.binding.ivCover.load(Uri.parse(coverUri))
                holder.binding.vScrim.visibility = View.VISIBLE
                // 如果是图片，让图片半透明，或者让遮罩变深。这里统一用 alpha 控制 ivCover
                holder.binding.ivCover.alpha = alpha
            } else {
                holder.binding.ivCover.setImageDrawable(null)
                try {
                    holder.binding.ivCover.setBackgroundColor(Color.parseColor(colorHex))
                } catch (e:Exception) {
                    holder.binding.ivCover.setBackgroundColor(Color.LTGRAY)
                }
                holder.binding.vScrim.visibility = View.GONE
                // 无图纯色时，同样应用透明度
                holder.binding.ivCover.alpha = alpha
            }

            holder.itemView.setOnClickListener {
                val action = AgendaBookFragmentDirections.actionAgendaBookFragmentToAgendaDetailFragment(bookId)
                findNavController().navigate(action)
            }

            holder.itemView.setOnLongClickListener {
                if (position < 2) {
                    showDefaultBookOptions(position == 1)
                } else {
                    showOptions(books[position - 2])
                }
                true
            }
        }

        // 【新增】：带有透明度滑块的默认日程本设置对话框
        private fun showDefaultBookOptions(isImportantBook: Boolean) {
            val options = arrayOf(getString(R.string.agenda_set_cover), getString(R.string.agenda_remove_cover), getString(R.string.agenda_adjust_alpha))
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(if (isImportantBook) getString(R.string.nav_filter_important) else getString(R.string.nav_filter_all))
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            editingBookId = if (isImportantBook) -2L else -1L
                            pickImageLauncher.launch(arrayOf("image/*"))
                        }
                        1 -> {
                            preferenceManager.saveDefaultBookCover(isImportantBook, null)
                            binding.recyclerViewBooks.adapter?.notifyDataSetChanged()
                        }
                        2 -> {
                            // 弹出透明度调节
                            val currentAlpha = preferenceManager.getDefaultBookAlpha(isImportantBook)
                            val slider = com.google.android.material.slider.Slider(requireContext()).apply {
                                valueFrom = 0.0f
                                valueTo = 1.0f
                                value = currentAlpha
                                setPadding(48, 48, 48, 48)
                            }
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.agenda_cover_alpha_title)
                                .setView(slider)
                                .setPositiveButton(R.string.common_save) { _, _ ->
                                    preferenceManager.saveDefaultBookAlpha(isImportantBook, slider.value)
                                    binding.recyclerViewBooks.adapter?.notifyDataSetChanged()
                                }
                                .show()
                        }
                    }
                }.show()
        }

        private fun bindList(holder: ListViewHolder, position: Int) {
            val bookId: Long
            val name: String
            val colorHex: String
            val count: Int

            if (position == 0) {
                bookId = -1L; name = getString(R.string.nav_filter_all); colorHex = "#212121"; count = currentEvents.size
            } else if (position == 1) {
                bookId = -2L; name = getString(R.string.nav_filter_important); colorHex = "#F44336"; count = currentEvents.count { it.isImportant }
            } else {
                val book = books[position - 2]
                bookId = book.id; name = book.name; colorHex = book.colorHex
                count = currentEvents.count { it.bookId == book.id }
            }

            holder.binding.tvBookName.text = name
            try { holder.binding.indicatorBar.setBackgroundColor(Color.parseColor(colorHex)) } catch(e:Exception){}

            val eventsInBook = if (position == 0) currentEvents
            else if (position == 1) currentEvents.filter { it.isImportant }
            else currentEvents.filter { it.bookId == bookId }

            val nextEvent = eventsInBook.filter { it.targetDate.time >= System.currentTimeMillis() }
                .minByOrNull { it.targetDate }

            val statsText = StringBuilder(getString(R.string.agenda_event_count, count))
            if (nextEvent != null) {
                val diff = TimeCalculator.calculateDifference(nextEvent.targetDate)
                statsText.append(getString(R.string.agenda_stats_latest, nextEvent.title, getString(R.string.countdown_remaining) + diff.totalDays + getString(R.string.unit_day)))
            } else if (count > 0 && eventsInBook.isNotEmpty()) {
                statsText.append(getString(R.string.agenda_stats_all_expired))
            } else {
                statsText.append(getString(R.string.agenda_stats_empty))
            }
            holder.binding.tvStats.text = statsText.toString()

            holder.itemView.setOnClickListener {
                val action = AgendaBookFragmentDirections.actionAgendaBookFragmentToAgendaDetailFragment(bookId)
                findNavController().navigate(action)
            }
            holder.itemView.setOnLongClickListener {
                if (position < 2) {
                    showDefaultBookOptions(position == 1)
                } else {
                    showOptions(books[position - 2])
                }
                true
            }
        }

        inner class GridViewHolder(val binding: ItemAgendaBookCardBinding) : RecyclerView.ViewHolder(binding.root)
        inner class ListViewHolder(val binding: ItemAgendaBookListBinding) : RecyclerView.ViewHolder(binding.root)
    }

    private fun showDefaultBookOptions(isImportantBook: Boolean) {
        val options = arrayOf(getString(R.string.agenda_set_cover), getString(R.string.agenda_remove_cover))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isImportantBook) getString(R.string.nav_filter_important) else getString(R.string.nav_filter_all))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        editingBookId = if (isImportantBook) -2L else -1L
                        pickImageLauncher.launch(arrayOf("image/*"))
                    }
                    1 -> {
                        preferenceManager.saveDefaultBookCover(isImportantBook, null)
                        binding.recyclerViewBooks.adapter?.notifyDataSetChanged()
                    }
                }
            }.show()
    }

    private fun showOptions(book: AgendaBook) {
        val options = arrayOf(getString(R.string.agenda_edit_details), getString(R.string.agenda_delete_book))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(book.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val action = AgendaBookFragmentDirections.actionAgendaBookFragmentToAddEditAgendaBookFragment(
                            bookId = book.id,
                            title = getString(R.string.agenda_edit_book)
                        )
                        findNavController().navigate(action)
                    }
                    1 -> deleteBook(book)
                }
            }.show()
    }

    private fun deleteBook(book: AgendaBook) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.agenda_confirm_delete_title)
            .setMessage(R.string.agenda_confirm_delete_message)
            .setPositiveButton(R.string.common_delete) { _, _ -> agendaViewModel.deleteBook(book) }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}