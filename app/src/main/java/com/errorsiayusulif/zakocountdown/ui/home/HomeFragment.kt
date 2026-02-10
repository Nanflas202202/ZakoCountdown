// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/home/HomeFragment.kt
package com.errorsiayusulif.zakocountdown.ui.home

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.ColorUtils
import androidx.core.view.GravityCompat
import androidx.core.view.MenuProvider
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.data.CountdownEvent
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.databinding.FragmentHomeBinding
import com.errorsiayusulif.zakocountdown.ui.agenda.AgendaViewModel
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import java.util.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val homeViewModel: HomeViewModel by viewModels {
        val app = requireActivity().application as ZakoCountdownApplication
        HomeViewModelFactory(app.repository, app)
    }

    // 共享 ViewModel 负责获取日程本颜色和筛选状态
    private val agendaViewModel: AgendaViewModel by viewModels({ requireActivity() })

    private var currentAllEvents: List<CountdownEvent> = emptyList()
    private var currentBookColors: Map<Long, String> = emptyMap()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 设置 Toolbar 菜单
        val menuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // 仅当功能开启时才加载菜单
                if (PreferenceManager(requireContext()).isAgendaBookEnabled()) {
                    menuInflater.inflate(R.menu.home_menu, menu)
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_filter -> {
                        // 打开右侧抽屉进行筛选
                        val drawer = requireActivity().findViewById<DrawerLayout>(R.id.drawer_layout)
                        drawer.openDrawer(GravityCompat.END)
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner)

        // 2. 初始化 Adapter
        val adapter = CountdownAdapter(
            onItemClicked = { event ->
                val action = HomeFragmentDirections.actionHomeFragmentToAddEditEventFragment(
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
        binding.recyclerViewEvents.adapter = adapter
        binding.recyclerViewEvents.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewEvents.itemAnimator = null

        // 3. 滑动删除逻辑
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val eventToDelete = adapter.currentList[position]
                    homeViewModel.delete(eventToDelete)
                    Snackbar.make(binding.root, "日程已删除", Snackbar.LENGTH_LONG)
                        .setAction("撤销") { homeViewModel.insert(eventToDelete) }.show()
                }
            }
        }).attachToRecyclerView(binding.recyclerViewEvents)

        // 4. 数据观察
        homeViewModel.allEvents.observe(viewLifecycleOwner) { events ->
            currentAllEvents = events ?: emptyList()
            applyFilterAndSubmitList(adapter)
            checkDevModeActivation(currentAllEvents)
        }

        agendaViewModel.currentFilterId.observe(viewLifecycleOwner) {
            applyFilterAndSubmitList(adapter)
        }

        agendaViewModel.allBooks.observe(viewLifecycleOwner) { books ->
            currentBookColors = books.associate { it.id to it.colorHex }
            adapter.setBookColorMap(currentBookColors)
        }

        binding.fabAddEvent.setOnClickListener {
            val action = HomeFragmentDirections.actionHomeFragmentToAddEditEventFragment(title = "添加日程")
            findNavController().navigate(action)
        }
    }

    private fun applyFilterAndSubmitList(adapter: CountdownAdapter) {
        val filterId = agendaViewModel.currentFilterId.value ?: -1L
        val filteredList = when (filterId) {
            -1L -> currentAllEvents
            -2L -> currentAllEvents.filter { it.isImportant }
            else -> currentAllEvents.filter { it.bookId == filterId }
        }
        adapter.submitList(filteredList)
    }

    override fun onResume() {
        super.onResume()
        loadWallpaper()
    }

    private fun loadWallpaper() {
        val app = requireActivity().application as ZakoCountdownApplication
        val wallpaperUriString = app.preferenceManager.getHomepageWallpaperUri()

        val scrimMode = app.preferenceManager.getScrimColorMode()
        val scrimAlphaPercent = app.preferenceManager.getScrimAlpha()
        val scrimAlphaInt = (scrimAlphaPercent / 100f * 255).toInt()

        val baseColor = when (scrimMode) {
            PreferenceManager.SCRIM_MODE_BLACK -> Color.BLACK
            PreferenceManager.SCRIM_MODE_WHITE -> Color.WHITE
            PreferenceManager.SCRIM_MODE_CUSTOM -> {
                try {
                    Color.parseColor(app.preferenceManager.getScrimCustomColor())
                } catch (e: Exception) {
                    Color.DKGRAY
                }
            }
            else -> MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
        }

        val finalScrimColor = ColorUtils.setAlphaComponent(baseColor, scrimAlphaInt)

        if (wallpaperUriString != null) {
            binding.wallpaperImage.load(Uri.parse(wallpaperUriString)) { crossfade(true) }
            binding.recyclerViewScrim.setBackgroundColor(finalScrimColor)
        } else {
            binding.wallpaperImage.setImageDrawable(null)
            val themeSurfaceColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurface)
            binding.recyclerViewScrim.setBackgroundColor(themeSurfaceColor)
        }
    }

    private fun checkDevModeActivation(events: List<CountdownEvent>) {
        val app = requireActivity().application as ZakoCountdownApplication
        if (!app.preferenceManager.isEnableEnterDevMode()) return

        val devModeEvents = events.filter { it.title == "EnterDevMode" }
        if (devModeEvents.size >= 5) {
            Toast.makeText(requireContext(), "开发者选项已开启！", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_global_developerSettingsFragment)
            homeViewModel.deleteDevModeEvents()
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
                R.id.action_add_to_calendar -> {
                    addToSystemCalendar(event)
                    true
                }
                R.id.action_share_card -> {
                    val action = HomeFragmentDirections.actionHomeFragmentToSharePreviewFragment(event.id)
                    findNavController().navigate(action)
                    true
                }
                R.id.action_card_settings -> {
                    val action = HomeFragmentDirections.actionHomeFragmentToCardSettingsFragment(event.id)
                    findNavController().navigate(action); true
                }
                R.id.action_add_widget -> {
                    pinWidget(event)
                    true
                }
                R.id.action_delete -> {
                    homeViewModel.delete(event)
                    Snackbar.make(binding.root, "日程已删除", Snackbar.LENGTH_LONG)
                        .setAction("撤销") { homeViewModel.insert(event) }.show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun addToSystemCalendar(event: CountdownEvent) {
        try {
            val startMillis = event.targetDate.time
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, event.title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 3600000)
                putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                putExtra(CalendarContract.Events.DESCRIPTION, "来自 ZakoCountdown 的提醒")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "未找到日历应用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pinWidget(event: CountdownEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = AppWidgetManager.getInstance(requireContext())
            val myProvider = ComponentName(
                requireContext(),
                "com.errorsiayusulif.zakocountdown.widget.ZakoWidgetProvider"
            )
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val extras = Bundle()
                extras.putLong("preselected_event_id", event.id)
                extras.putBoolean("is_shortcut_creation", true)
                appWidgetManager.requestPinAppWidget(myProvider, extras, null)
                Toast.makeText(requireContext(), "请求已发送", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}