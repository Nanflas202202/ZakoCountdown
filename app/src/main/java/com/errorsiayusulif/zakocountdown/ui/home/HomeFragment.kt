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
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import com.errorsiayusulif.zakocountdown.data.AgendaBook
import com.errorsiayusulif.zakocountdown.data.CountdownEvent
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.databinding.FragmentHomeBinding
import com.errorsiayusulif.zakocountdown.ui.agenda.AgendaViewModel
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlin.math.abs

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val homeViewModel: HomeViewModel by viewModels {
        val app = requireActivity().application as ZakoCountdownApplication
        HomeViewModelFactory(app.repository, app)
    }

    private val agendaViewModel: AgendaViewModel by viewModels({ requireActivity() })

    private var currentAllEvents: List<CountdownEvent> = emptyList()
    private var currentBookColors: Map<Long, String> = emptyMap()
    private var isUpdatingTabs = false

    // 用于保存滑动删除的引用，方便解绑
    private var itemTouchHelper: ItemTouchHelper? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = PreferenceManager(requireContext())
        val layoutMode = prefs.getHomeLayoutMode()
        val isCompact = layoutMode == PreferenceManager.HOME_LAYOUT_COMPACT

        // 1. 设置菜单
        val menuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                if (isCompact) {
                    menuInflater.inflate(R.menu.home_menu_compact, menu)
                } else if (prefs.isAgendaBookEnabled()) {
                    menuInflater.inflate(R.menu.home_menu, menu)
                }
            }
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_filter -> {
                        val drawer = requireActivity().findViewById<DrawerLayout>(R.id.drawer_layout)
                        drawer.openDrawer(GravityCompat.END)
                        true
                    }
                    R.id.action_manage_agenda -> {
                        findNavController().navigate(R.id.agendaBookFragment)
                        true
                    }
                    R.id.action_settings -> {
                        findNavController().navigate(R.id.settingsFragment)
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner)

        // 2. 初始化 Adapter
        val adapter = CountdownAdapter(
            onItemClicked = { event ->
                val action = HomeFragmentDirections.actionHomeFragmentToAddEditEventFragment(title = "编辑日程", eventId = event.id)
                findNavController().navigate(action)
            },
            onLongItemClicked = { event, anchorView ->
                showContextMenu(event, anchorView)
                true
            }
        )
        adapter.setCompactMode(isCompact)

        binding.recyclerViewEvents.adapter = adapter
        binding.recyclerViewEvents.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewEvents.itemAnimator = null

        // 3. 紧凑模式 UI 与手势逻辑
        if (isCompact) {
            (requireActivity() as? AppCompatActivity)?.supportActionBar?.show()
            binding.tabLayoutAgenda.visibility = View.VISIBLE
            setupCompactTabs()
            setupSwipeToSwitchTabs() // 开启左右滑动切页
        } else {
            binding.tabLayoutAgenda.visibility = View.GONE
            setupSwipeToDelete(adapter) // 开启侧滑删除
        }

        // 4. 数据观察
        homeViewModel.allEvents.observe(viewLifecycleOwner) { events ->
            currentAllEvents = events ?: emptyList()
            applyFilterAndSubmitList(adapter)
            checkDevModeActivation(currentAllEvents)
        }

        agendaViewModel.currentFilterId.observe(viewLifecycleOwner) {
            applyFilterAndSubmitList(adapter)
            if (isCompact) syncTabSelection()
        }

        agendaViewModel.allBooks.observe(viewLifecycleOwner) { books ->
            val bookList = books ?: emptyList()

            // 1. 维持旧的颜色映射表以防部分旧逻辑需要
            currentBookColors = bookList.associate { it.id to it.colorHex }
            adapter.setBookColorMap(currentBookColors)

            // 2. 【核心修复】将完整的日程本列表传给 Adapter
            adapter.setAgendaBooks(bookList)

            // 3. 更新紧凑模式下的 Tabs
            if (isCompact) updateTabs(bookList)
        }

        binding.fabAddEvent.setOnClickListener {
            val currentFilter = agendaViewModel.currentFilterId.value ?: -1L
            val defaultBookId = if (currentFilter > 0) currentFilter else -1L
            val action = HomeFragmentDirections.actionHomeFragmentToAddEditEventFragment(title = "添加日程", defaultBookId = defaultBookId)
            findNavController().navigate(action)
        }
    }

    // --- 核心优化：滑动手势控制 ---
    private fun setupSwipeToDelete(adapter: CountdownAdapter) {
        itemTouchHelper?.attachToRecyclerView(null) // 清除旧的
        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val eventToDelete = adapter.currentList[position]
                    homeViewModel.delete(eventToDelete)
                    Snackbar.make(binding.root, "日程已删除", Snackbar.LENGTH_LONG)
                        .setAction("撤销") { homeViewModel.insert(eventToDelete) }.show()
                }
            }
        })
        itemTouchHelper?.attachToRecyclerView(binding.recyclerViewEvents)
    }

    private fun setupSwipeToSwitchTabs() {
        itemTouchHelper?.attachToRecyclerView(null) // 紧凑模式下关闭滑动删除

        val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x

                // 判断是横向滑动
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        val currentTabIndex = binding.tabLayoutAgenda.selectedTabPosition
                        if (diffX > 0) {
                            // 向右滑，切到上一个 Tab
                            if (currentTabIndex > 0) {
                                binding.tabLayoutAgenda.getTabAt(currentTabIndex - 1)?.select()
                            }
                        } else {
                            // 向左滑，切到下一个 Tab
                            if (currentTabIndex < binding.tabLayoutAgenda.tabCount - 1) {
                                binding.tabLayoutAgenda.getTabAt(currentTabIndex + 1)?.select()
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })

        // 拦截 RecyclerView 的触摸事件交给 GestureDetector
        binding.recyclerViewEvents.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                gestureDetector.onTouchEvent(e)
                return false // 返回 false 允许列表继续垂直滚动
            }
        })
    }
    private fun setupCompactTabs() {
        binding.tabLayoutAgenda.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (isUpdatingTabs) return
                val filterId = tab?.tag as? Long ?: -1L

                // --- 优化：动态高亮颜色 ---
                updateTabColors(filterId)

                agendaViewModel.setFilter(filterId)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    // 新增：根据日程本 ID 更新 Tab 颜色
    private fun updateTabColors(filterId: Long) {
        val colorHex = when (filterId) {
            -1L -> "#212121" // 全部: 黑/深灰
            -2L -> "#F44336" // 重点: 红
            else -> currentBookColors[filterId] ?: "#6750A4" // 自定义或默认紫
        }

        try {
            val color = Color.parseColor(colorHex)
            binding.tabLayoutAgenda.setSelectedTabIndicatorColor(color)

            // 设置文字颜色：未选中为灰色，选中为日程本颜色
            val unselectedColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
            binding.tabLayoutAgenda.setTabTextColors(unselectedColor, color)
        } catch (e: Exception) {
            // Fallback
        }
    }

    private fun updateTabs(books: List<AgendaBook>) {
        isUpdatingTabs = true
        val currentFilter = agendaViewModel.currentFilterId.value ?: -1L

        binding.tabLayoutAgenda.removeAllTabs()

        // 1. 全部
        val tabAll = binding.tabLayoutAgenda.newTab().setText("全部").setTag(-1L)
        binding.tabLayoutAgenda.addTab(tabAll)
        if (currentFilter == -1L) tabAll.select()

        // 2. 重点
        val tabImp = binding.tabLayoutAgenda.newTab().setText("重点").setTag(-2L)
        binding.tabLayoutAgenda.addTab(tabImp)
        if (currentFilter == -2L) tabImp.select()

        // 3. 自定义日程本
        books.forEach { book ->
            val tab = binding.tabLayoutAgenda.newTab().setText(book.name).setTag(book.id)
            binding.tabLayoutAgenda.addTab(tab)
            if (currentFilter == book.id) tab.select()
        }

        // 初始化颜色
        updateTabColors(currentFilter)

        isUpdatingTabs = false
    }

    private fun syncTabSelection() {
        if (isUpdatingTabs) return
        val currentFilter = agendaViewModel.currentFilterId.value ?: -1L
        for (i in 0 until binding.tabLayoutAgenda.tabCount) {
            val tab = binding.tabLayoutAgenda.getTabAt(i)
            if (tab?.tag == currentFilter) {
                tab.select()
                updateTabColors(currentFilter) // 同步颜色
                break
            }
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

    // ... loadWallpaper, checkDevModeActivation, showContextMenu, addToSystemCalendar, openShareSettings, pinWidget 保持不变 ...

    // (为了代码完整性，请保留这些方法，逻辑与之前一致)
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
                try { Color.parseColor(app.preferenceManager.getScrimCustomColor()) } catch (e: Exception) { Color.DKGRAY }
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
                R.id.action_add_to_calendar -> { addToSystemCalendar(event); true }
                R.id.action_share_card -> {
                    val action = HomeFragmentDirections.actionHomeFragmentToSharePreviewFragment(event.id)
                    findNavController().navigate(action)
                    true
                }
                R.id.action_card_settings -> {
                    val action = HomeFragmentDirections.actionHomeFragmentToCardSettingsFragment(event.id)
                    findNavController().navigate(action); true
                }
                R.id.action_add_widget -> { pinWidget(event); true }
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