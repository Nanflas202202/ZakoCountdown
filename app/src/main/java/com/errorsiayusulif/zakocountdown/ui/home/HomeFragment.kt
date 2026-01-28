package com.errorsiayusulif.zakocountdown.ui.home

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract // 核心导入：日历契约
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.ColorUtils
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
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val homeViewModel: HomeViewModel by viewModels {
        val app = requireActivity().application as ZakoCountdownApplication
        HomeViewModelFactory(app.repository, app)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        // 禁用默认动画器，防止 item 刷新时透明度闪烁
        binding.recyclerViewEvents.itemAnimator = null

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

        homeViewModel.allEvents.observe(viewLifecycleOwner) { events ->
            events?.let {
                adapter.submitList(it)
                checkDevModeActivation(it)
            }
        }

        binding.fabAddEvent.setOnClickListener {
            val action = HomeFragmentDirections.actionHomeFragmentToAddEditEventFragment(title = "添加日程")
            findNavController().navigate(action)
        }
    }

    override fun onResume() {
        super.onResume()
        loadWallpaper()
    }

    private fun loadWallpaper() {
        val app = requireActivity().application as ZakoCountdownApplication
        val wallpaperUriString = app.preferenceManager.getHomepageWallpaperUri()

        // 1. 获取遮罩配置
        val scrimMode = app.preferenceManager.getScrimColorMode()
        val scrimAlphaPercent = app.preferenceManager.getScrimAlpha()
        val scrimAlphaInt = (scrimAlphaPercent / 100f * 255).toInt()

        // 2. 决定基础颜色
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
            else -> { // theme
                MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
            }
        }

        // 3. 计算最终颜色
        val finalScrimColor = ColorUtils.setAlphaComponent(baseColor, scrimAlphaInt)

        // 4. 应用背景和遮罩
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

                // Task 1: 添加到系统日历
                R.id.action_add_to_calendar -> {
                    addToSystemCalendar(event)
                    true
                }

                // Task 3: 分享
                R.id.action_share_card -> {
                    openShareSettings(event)
                    true
                }

                R.id.action_card_settings -> {
                    val action = HomeFragmentDirections.actionHomeFragmentToCardSettingsFragment(event.id)
                    findNavController().navigate(action); true
                }

                // Fix: 修复后的添加微件逻辑
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
            val endMillis = startMillis + 60 * 60 * 1000 // 默认1小时

            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, event.title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                putExtra(CalendarContract.Events.DESCRIPTION, "来自 ZakoCountdown 的日程提醒")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "未找到可用的日历应用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openShareSettings(event: CountdownEvent) {
        val action = HomeFragmentDirections.actionHomeFragmentToSharePreviewFragment(event.id)
        findNavController().navigate(action)
    }

    private fun pinWidget(event: CountdownEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = AppWidgetManager.getInstance(requireContext())
            val myProvider = ComponentName(requireContext(), com.errorsiayusulif.zakocountdown.widget.ZakoWidgetProvider::class.java)

            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                // 传递参数给系统，系统会在添加成功后，将这些参数传给 WidgetConfigureActivity
                val extras = Bundle()
                extras.putLong("preselected_event_id", event.id)
                extras.putBoolean("is_shortcut_creation", true)

                // 成功回调（可选，这里只是为了演示）
                val successIntent = Intent(requireContext(), com.errorsiayusulif.zakocountdown.MainActivity::class.java)
                val successCallback = PendingIntent.getActivity(requireContext(), 0, successIntent, PendingIntent.FLAG_IMMUTABLE)

                appWidgetManager.requestPinAppWidget(myProvider, extras, successCallback)
                Toast.makeText(requireContext(), "请求已发送，请在弹窗中确认", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "当前启动器不支持自动添加微件", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "系统版本过低，请手动在桌面添加", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}