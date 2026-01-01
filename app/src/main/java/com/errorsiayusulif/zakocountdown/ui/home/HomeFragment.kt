// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/home/HomeFragment.kt
package com.errorsiayusulif.zakocountdown.ui.home

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
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

        // --- 【核心修复】禁用默认动画器，防止透明度被重置为 1.0 ---
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
                // 读取自定义颜色
                Color.parseColor((requireActivity().application as ZakoCountdownApplication).preferenceManager.getScrimCustomColor())
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
            // 有壁纸时，应用计算出的遮罩
            binding.recyclerViewScrim.setBackgroundColor(finalScrimColor)
        } else {
            binding.wallpaperImage.setImageDrawable(null)
            // 无壁纸时，逻辑稍微特殊：
            // 如果用户强制选了黑/白遮罩，我们就在默认背景上叠加这个遮罩
            // 如果选的是主题色，我们通常希望保持默认背景清爽，但也可以叠加一层淡淡的主题色

            // 为了视觉一致性，无壁纸时我们通常让遮罩“隐形”或者作为底色
            // 这里我们采取的策略是：无壁纸时，recyclerViewScrim 充当不透明背景
            // 这样能让列表在任何情况下都清晰
            val themeSurfaceColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurface)
            binding.recyclerViewScrim.setBackgroundColor(themeSurfaceColor)
        }
    }

    private fun checkDevModeActivation(events: List<CountdownEvent>) {
        val app = requireActivity().application as ZakoCountdownApplication

        // 1. 首先检查开关是否开启
        if (!app.preferenceManager.isEnableEnterDevMode()) {
            return // 如果没开启权限，直接忽略
        }

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
                R.id.action_card_settings -> {
                    val action = HomeFragmentDirections.actionHomeFragmentToCardSettingsFragment(event.id)
                    findNavController().navigate(action); true
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}