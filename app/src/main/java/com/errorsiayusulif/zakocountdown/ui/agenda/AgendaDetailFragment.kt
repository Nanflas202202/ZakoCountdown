package com.errorsiayusulif.zakocountdown.ui.agenda

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat // 导入修复
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.data.AgendaBook
import com.errorsiayusulif.zakocountdown.data.CountdownEvent
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.databinding.FragmentAgendaDetailBinding
import com.errorsiayusulif.zakocountdown.ui.home.CountdownAdapter
import com.errorsiayusulif.zakocountdown.ui.home.HomeViewModel
import com.errorsiayusulif.zakocountdown.ui.home.HomeViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File

class AgendaDetailFragment : Fragment() {

    private var _binding: FragmentAgendaDetailBinding? = null
    private val binding get() = _binding!!
    private val args: AgendaDetailFragmentArgs by navArgs()

    private val homeViewModel: HomeViewModel by viewModels {
        val app = requireActivity().application as ZakoCountdownApplication
        HomeViewModelFactory(app.repository, app)
    }

    private val agendaViewModel: AgendaViewModel by viewModels({ requireActivity() })
    private var currentBook: AgendaBook? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAgendaDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val adapter = CountdownAdapter(
            onItemClicked = { event ->
                val action = AgendaDetailFragmentDirections.actionAgendaDetailFragmentToAddEditEventFragment(
                    title = "编辑日程",
                    eventId = event.id
                )
                findNavController().navigate(action)
            },
            onLongItemClicked = { event, anchorView ->
                showContextMenu(event, anchorView)
                true
            }
        )
        adapter.setCompactMode(false)

        binding.recyclerViewEvents.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewEvents.adapter = adapter
        binding.recyclerViewEvents.itemAnimator = null

        loadPageDetails()

        homeViewModel.allEvents.observe(viewLifecycleOwner) { events ->
            val filtered = when (args.bookId) {
                -1L -> events
                -2L -> events.filter { it.isImportant }
                else -> events.filter { it.bookId == args.bookId }
            }
            adapter.submitList(filtered)
        }

        binding.fabAddToBook.setOnClickListener {
            val targetBookId = if (args.bookId > 0) args.bookId else -1L
            val action = AgendaDetailFragmentDirections.actionAgendaDetailFragmentToAddEditEventFragment(
                title = "新建日程",
                defaultBookId = targetBookId
            )
            findNavController().navigate(action)
        }

        if (args.bookId > 0) {
            binding.toolbar.inflateMenu(R.menu.event_card_context_menu)
            binding.toolbar.menu.clear()
            val editItem = binding.toolbar.menu.add(0, 1, 0, "编辑本子")
            editItem.setIcon(R.drawable.ic_settings)
            editItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

            binding.toolbar.setOnMenuItemClickListener {
                if (it.itemId == 1) {
                    val action = AgendaDetailFragmentDirections.actionAgendaDetailFragmentToAddEditAgendaBookFragment(
                        title = "编辑日程本",
                        bookId = args.bookId
                    )
                    findNavController().navigate(action)
                    true
                } else false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.hide()
    }

    override fun onStop() {
        super.onStop()
        // 恢复逻辑交给 MainActivity
    }

    private fun loadPageDetails() {
        if (args.bookId > 0) {
            lifecycleScope.launch {
                val repo = (requireActivity().application as ZakoCountdownApplication).repository
                currentBook = repo.getBookById(args.bookId)

                currentBook?.let { book ->
                    binding.collapsingToolbar.title = book.name
                    binding.toolbar.title = book.name

                    try {
                        val color = Color.parseColor(book.colorHex)
                        binding.collapsingToolbar.setContentScrimColor(color)
                        binding.collapsingToolbar.setStatusBarScrimColor(color)
                        updateTitleTextColor(color)

                        if (book.coverImageUri == null) {
                            binding.ivHeaderImage.setImageDrawable(null)
                            binding.ivHeaderImage.setBackgroundColor(color)
                            binding.ivHeaderImage.alpha = book.cardAlpha
                        }
                    } catch (e: Exception) {}

                    if (book.coverImageUri != null) {
                        binding.ivHeaderImage.load(Uri.parse(book.coverImageUri)) {
                            crossfade(true)
                        }
                        binding.ivHeaderImage.alpha = book.cardAlpha
                        updateTitleTextColor(Color.BLACK)
                    }
                }
            }
        } else if (args.bookId == -1L) {
            binding.collapsingToolbar.title = "全部日程"
            binding.toolbar.title = "全部日程"
            setupStaticHeader(Color.DKGRAY)
        } else if (args.bookId == -2L) {
            binding.collapsingToolbar.title = "重点日程"
            binding.toolbar.title = "重点日程"
            setupStaticHeader(Color.parseColor("#F44336"))
        }
    }

    private fun setupStaticHeader(color: Int) {
        binding.collapsingToolbar.setContentScrimColor(color)
        binding.collapsingToolbar.setStatusBarScrimColor(color)
        binding.ivHeaderImage.setBackgroundColor(color)
        binding.ivHeaderImage.setImageDrawable(null)
        updateTitleTextColor(color)
    }

    private fun updateTitleTextColor(backgroundColor: Int) {
        val luminance = ColorUtils.calculateLuminance(backgroundColor)
        val isDark = luminance < 0.5
        val textColor = if (isDark) Color.WHITE else Color.BLACK

        binding.collapsingToolbar.setExpandedTitleColor(textColor)
        binding.collapsingToolbar.setCollapsedTitleTextColor(textColor)
        binding.toolbar.setTitleTextColor(textColor)

        // --- 修复：使用 DrawableCompat 设置图标颜色 ---
        binding.toolbar.navigationIcon?.let { icon ->
            val wrapped = DrawableCompat.wrap(icon)
            DrawableCompat.setTint(wrapped, textColor)
            binding.toolbar.navigationIcon = wrapped
        }

        val menu = binding.toolbar.menu
        if (menu.size() > 0) {
            menu.getItem(0).icon?.let { icon ->
                val wrapped = DrawableCompat.wrap(icon)
                DrawableCompat.setTint(wrapped, textColor)
                menu.getItem(0).icon = wrapped
            }
        }
    }

    private fun showContextMenu(event: CountdownEvent, anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menuInflater.inflate(R.menu.event_card_context_menu, popup.menu)

        val pinMenuItem = popup.menu.findItem(R.id.action_pin)
        pinMenuItem.title = if (event.isPinned) "取消置顶" else "设为置顶"
        val importantMenuItem = popup.menu.findItem(R.id.action_mark_important)
        importantMenuItem.title = if (event.isImportant) "取消重点" else "设为重点"

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_pin -> { homeViewModel.update(event.copy(isPinned = !event.isPinned)); true }
                R.id.action_mark_important -> { homeViewModel.update(event.copy(isImportant = !event.isImportant)); true }
                R.id.action_delete -> {
                    homeViewModel.delete(event)
                    Snackbar.make(binding.root, "日程已删除", Snackbar.LENGTH_LONG)
                        .setAction("撤销") { homeViewModel.insert(event) }.show()
                    true
                }
                R.id.action_card_settings -> {
                    val action = AgendaDetailFragmentDirections.actionAgendaDetailFragmentToCardSettingsFragment(event.id)
                    findNavController().navigate(action)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}