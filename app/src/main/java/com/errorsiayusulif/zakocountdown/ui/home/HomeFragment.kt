// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/home/HomeFragment.kt
package com.errorsiayusulif.zakocountdown.ui.home

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
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
import com.errorsiayusulif.zakocountdown.databinding.FragmentHomeBinding
import com.errorsiayusulif.zakocountdown.widget.ZakoWidgetProvider
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
        binding.recyclerViewEvents.adapter = adapter
        binding.recyclerViewEvents.layoutManager = LinearLayoutManager(context)

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
        val wallpaperUriString = (requireActivity().application as ZakoCountdownApplication)
            .preferenceManager.getHomepageWallpaperUri()

        if (wallpaperUriString != null) {
            binding.wallpaperImage.load(Uri.parse(wallpaperUriString)) { crossfade(true) }
            val colorPrimary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
            val scrimColor = Color.argb(102, Color.red(colorPrimary), Color.green(colorPrimary), Color.blue(colorPrimary)) // 40% alpha
            binding.recyclerViewScrim.setBackgroundColor(scrimColor)
        } else {
            binding.wallpaperImage.setImageDrawable(null)
            val themeBackgroundColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurface)
            binding.recyclerViewScrim.setBackgroundColor(themeBackgroundColor)
        }
    }

    private fun checkDevModeActivation(events: List<CountdownEvent>) {
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

        popup.menu.findItem(R.id.action_pin).title = if (event.isPinned) "取消置顶" else "设为置顶"
        popup.menu.findItem(R.id.action_mark_important).title = if (event.isImportant) "取消重点" else "设为重点"

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_pin -> {
                    homeViewModel.update(event.copy(isPinned = !event.isPinned))
                    true
                }
                R.id.action_mark_important -> {
                    homeViewModel.update(event.copy(isImportant = !event.isImportant))
                    true
                }
                R.id.action_card_settings -> {
                    val action = HomeFragmentDirections.actionHomeFragmentToCardSettingsFragment(event.id)
                    findNavController().navigate(action)
                    true
                }
                R.id.action_add_widget -> {
                    requestPinWidget(event)
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

    private fun requestPinWidget(event: CountdownEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val widgetProvider = ComponentName(requireContext(), ZakoWidgetProvider::class.java)

                // --- 【核心修复】 ---
                // 1. 创建一个启动 WidgetConfigureActivity 的常规 Intent
                val configureIntent = Intent(
                    context,
                    com.errorsiayusulif.zakocountdown.widget.WidgetConfigureActivity::class.java
                ).apply {
                    // 我们不能直接传递微件ID，因为此时它还未创建
                    // 但我们可以传递一个“预选事件”的ID
                    putExtra("preselected_event_id", event.id)
                    // 添加一个标志，表明这是由快捷方式创建的
                    putExtra("is_shortcut_creation", true)
                }

                // 2. 使用 getActivity() 创建一个启动 Activity 的 PendingIntent
                val successCallback = PendingIntent.getActivity(
                    context,
                    event.id.toInt(), // 使用 event.id 确保每个请求的唯一性
                    configureIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                appWidgetManager.requestPinAppWidget(widgetProvider, null, successCallback)
            } else {
                Toast.makeText(context, "您的桌面不支持此功能", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "此功能需要 Android 8.0 或更高版本", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}