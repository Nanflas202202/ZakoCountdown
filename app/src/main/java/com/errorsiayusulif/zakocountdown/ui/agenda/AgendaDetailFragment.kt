// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/agenda/AgendaDetailFragment.kt
package com.errorsiayusulif.zakocountdown.ui.agenda

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
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
import com.errorsiayusulif.zakocountdown.databinding.DialogDefaultBookSettingsBinding
import com.errorsiayusulif.zakocountdown.databinding.FragmentAgendaDetailBinding
import com.errorsiayusulif.zakocountdown.databinding.ItemColorSwatchBinding
import com.errorsiayusulif.zakocountdown.ui.home.CountdownAdapter
import com.errorsiayusulif.zakocountdown.ui.home.HomeViewModel
import com.errorsiayusulif.zakocountdown.ui.home.HomeViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class AgendaDetailFragment : Fragment() {

    private companion object {
        /** 工具栏设置项 id。用常量而不是裸数字 1，避免和别的菜单 id 撞车。 */
        const val MENU_SETTINGS = 1
    }

    private var _binding: FragmentAgendaDetailBinding? = null
    private val binding get() = _binding!!
    private val args: AgendaDetailFragmentArgs by navArgs()

    private val homeViewModel: HomeViewModel by viewModels {
        val app = requireActivity().application as ZakoCountdownApplication
        HomeViewModelFactory(app.repository, app)
    }

    private val agendaViewModel: AgendaViewModel by viewModels({ requireActivity() })
    private var currentBook: AgendaBook? = null

    // ========================================================================
    // 默认本（全部 / 重点）设置对话框的状态与选择器
    // ------------------------------------------------------------------------
    // ⚠️ 全部放在**字段**上，不能写成 showDefaultBookSettingsDialog() 里的局部变量。
    //
    // 原因：registerForActivityResult() 必须在 Fragment 创建之前调用
    // （onAttach / onCreate / 字段初始化阶段）。写在点击回调里会抛
    //   IllegalStateException: ... is attempting to registerForActivityResult
    //   after being created
    // 而选择器的回调又必须是稳定的字段级 lambda —— 它拿不到方法内的局部变量，
    // 所以对话框状态必须提升到字段。
    // （这个崩溃实际踩过。）
    // ========================================================================

    private var defaultBookDialogBinding: DialogDefaultBookSettingsBinding? = null
    private var defaultBookIsImportant: Boolean = false
    private var defaultBookCoverUri: String? = null
    private var defaultBookAlpha: Float = 1f
    private var defaultBookColor: Int = Color.DKGRAY

    /** 默认本封面选择器。字段级注册，回调里刷新对话框预览。 */
    private val defaultBookCoverPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val context = context ?: return@registerForActivityResult
        // 拷进应用私有目录：相册返回的 content:// 授权是临时的，
        // 重启后就失效了（普通日程本的编辑页也是这么处理的）。
        val file = File(context.filesDir, "default_book_cover_${System.currentTimeMillis()}.png")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            defaultBookCoverUri = Uri.fromFile(file).toString()
            renderDefaultBookPreview()
        } catch (t: Throwable) {
            Toast.makeText(context, R.string.agenda_cover_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /** 按当前字段值刷新对话框里的封面预览。对话框不在时什么都不做。 */
    private fun renderDefaultBookPreview() {
        val dialogBinding = defaultBookDialogBinding ?: return
        dialogBinding.ivPreview.alpha = defaultBookAlpha
        val cover = defaultBookCoverUri
        if (cover != null) {
            dialogBinding.ivPreview.load(Uri.parse(cover)) { crossfade(true) }
        } else {
            dialogBinding.ivPreview.setImageDrawable(null)
            dialogBinding.ivPreview.setBackgroundColor(defaultBookColor)
        }
    }

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
                    title = getString(R.string.home_title_edit_event),
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

        // --- 修复：确保 Adapter 能获取到日程本名称和颜色 ---
        val prefs = PreferenceManager(requireContext())
        agendaViewModel.allBooks.observe(viewLifecycleOwner) { books ->
            adapter.setAgendaBooks(books ?: emptyList(), prefs)
        }

        // 注：原先这里给「快捷添加日程」FAB 挂监听，该按钮已从布局移除。
        // 日程的添加入口在主页与日程本页右下角。

        // 设置入口对**所有**日程本都开放。
        //
        // 之前只有 bookId > 0（真实存在的本子）才有这个按钮，
        // 于是「全部」(-1) 与「重点」(-2) 两个虚拟本没有任何设置入口 ——
        // 它们的封面/透明度其实一直支持，只是没人能改。
        setupSettingsMenu()
    }

    /**
     * 日程本设置入口。
     *
     * 两类本子的「设置」含义不同，所以分流：
     *   · 普通本（bookId > 0）→ 跳到日程本编辑页（可改名称、颜色、封面、透明度）
     *   · 全部 / 重点（-1 / -2）→ 弹出专用对话框：只改封面与透明度。
     *     它们没有数据库记录，也就没有名称/颜色字段可编辑。
     */
    private fun setupSettingsMenu() {
        binding.toolbar.menu.clear()
        val editItem = binding.toolbar.menu.add(0, MENU_SETTINGS, 0, getString(R.string.agenda_edit_book_short))
        editItem.setIcon(R.drawable.ic_settings)
        editItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId != MENU_SETTINGS) return@setOnMenuItemClickListener false

            if (args.bookId > 0) {
                findNavController().navigate(
                    AgendaDetailFragmentDirections
                        .actionAgendaDetailFragmentToAddEditAgendaBookFragment(
                            title = getString(R.string.agenda_edit_book),
                            bookId = args.bookId
                        )
                )
            } else {
                showDefaultBookSettingsDialog()
            }
            true
        }

        // ⚠️ 菜单项是**此刻**才建好的，而标题色在 loadPageDetails() 里就算完了
        // （那时 menu 还是空的）。所以这里必须补染一次，
        // 否则齿轮图标会保留 `?attr/colorControlNormal` 的深色。
        tintToolbarMenuIcons(currentToolbarTextColor())
    }

    /**
     * 当前工具栏前景色（与标题、返回箭头一致）。
     *
     * 与 [updateTitleTextColor] 用同一套判据：按头部底色的亮度取白或黑。
     * 彩色头部下算出来就是白色 —— 也就是「始终浅色」的观感。
     */
    private fun currentToolbarTextColor(): Int {
        val prefs = PreferenceManager(requireContext())
        val bg = if (args.bookId > 0) {
            currentBook?.colorHex?.let {
                try { Color.parseColor(it) } catch (t: Throwable) { null }
            } ?: Color.DKGRAY
        } else {
            prefs.getDefaultBookColor(
                args.bookId == -2L,
                if (args.bookId == -2L) Color.parseColor("#F44336") else Color.DKGRAY
            )
        }
        return if (ColorUtils.calculateLuminance(bg) < 0.5) Color.WHITE else Color.BLACK
    }

    /** 默认本（全部 / 重点）的设置对话框：封面 + 透明度 + 主题色。 */
    private fun showDefaultBookSettingsDialog() {
        val isImportant = args.bookId == -2L
        val context = requireContext()
        val prefs = PreferenceManager(context)

        val dialogBinding = DialogDefaultBookSettingsBinding.inflate(layoutInflater)
        defaultBookDialogBinding = dialogBinding

        // ---- 把当前值灌进字段 ----
        // 这些值**必须**存在字段上：封面选择器的回调发生在方法返回之后，
        // 局部变量那时已经不可达（原因详见字段声明处的注释）。
        defaultBookIsImportant = isImportant
        defaultBookCoverUri = prefs.getDefaultBookCover(isImportant)
        defaultBookAlpha = prefs.getDefaultBookAlpha(isImportant)
        defaultBookColor = prefs.getDefaultBookColor(
            isImportant,
            if (isImportant) Color.parseColor("#F44336") else Color.DKGRAY
        )

        dialogBinding.sliderAlpha.value = defaultBookAlpha * 100f
        dialogBinding.sliderAlpha.addOnChangeListener { _, value, _ ->
            defaultBookAlpha = value / 100f
            dialogBinding.ivPreview.alpha = defaultBookAlpha
        }

        renderDefaultBookPreview()

        dialogBinding.btnPickCover.setOnClickListener {
            // 用字段级选择器；绝不能在这里 registerForActivityResult
            defaultBookCoverPicker.launch("image/*")
        }

        dialogBinding.btnClearCover.setOnClickListener {
            defaultBookCoverUri = null
            dialogBinding.ivPreview.setImageDrawable(null)
            dialogBinding.ivPreview.setBackgroundColor(defaultBookColor)
            dialogBinding.ivPreview.alpha = defaultBookAlpha
        }

        // ---- 主题色 ----
        // 复用编辑页同一个 item_color_swatch 布局与绑定类，
        // 这样色块外观、选中打勾的表现两处完全一致。
        fun rebuildPalette() {
            dialogBinding.paletteContainer.removeAllViews()
            AGENDA_BOOK_COLORS.forEach { hex ->
                val swatch = ItemColorSwatchBinding.inflate(
                    layoutInflater, dialogBinding.paletteContainer, false
                )
                val parsed = try { Color.parseColor(hex) } catch (t: Throwable) { Color.GRAY }
                (swatch.colorView.background as? android.graphics.drawable.GradientDrawable)
                    ?.setColor(parsed)
                swatch.colorView.clipToOutline = true

                val selected = hex.equals(
                    String.format("#%06X", 0xFFFFFF and defaultBookColor), ignoreCase = true
                )
                swatch.checkMark.visibility = if (selected) View.VISIBLE else View.GONE

                swatch.root.setOnClickListener {
                    defaultBookColor = parsed
                    if (defaultBookCoverUri == null) {
                        dialogBinding.ivPreview.setBackgroundColor(defaultBookColor)
                    }
                    rebuildPalette()
                }
                dialogBinding.paletteContainer.addView(swatch.root)
            }
        }
        rebuildPalette()

        MaterialAlertDialogBuilder(context)
            .setTitle(
                if (isImportant) R.string.agenda_default_book_important
                else R.string.agenda_default_book_all
            )
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.common_save) { _, _ ->
                prefs.saveDefaultBookCover(isImportant, defaultBookCoverUri)
                prefs.saveDefaultBookAlpha(isImportant, defaultBookAlpha)
                prefs.saveDefaultBookColor(isImportant, defaultBookColor)
                // 立刻按新设置重绘头部，不必退出重进
                loadPageDetails()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.hide()
    }

    override fun onStop() {
        super.onStop()
        val prefs = PreferenceManager(requireContext())
        if (prefs.getHomeLayoutMode() == PreferenceManager.HOME_LAYOUT_STANDARD) {
            (requireActivity() as? AppCompatActivity)?.supportActionBar?.show()
        }
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
            binding.collapsingToolbar.title = getString(R.string.nav_filter_all)
            binding.toolbar.title = getString(R.string.nav_filter_all)
            // --- 修复：加载默认本子的图片 ---
            loadDefaultBookHeader(isImportant = false, defaultColor = Color.DKGRAY)
        } else if (args.bookId == -2L) {
            binding.collapsingToolbar.title = getString(R.string.nav_filter_important)
            binding.toolbar.title = getString(R.string.nav_filter_important)
            // --- 修复：加载默认本子的图片 ---
            loadDefaultBookHeader(isImportant = true, defaultColor = Color.parseColor("#F44336"))
        }
    }

    // --- 核心修复：处理默认日程集的封面显示逻辑 ---
    private fun loadDefaultBookHeader(isImportant: Boolean, defaultColor: Int) {
        val prefs = PreferenceManager(requireContext())
        val coverUri = prefs.getDefaultBookCover(isImportant)
        val alpha = prefs.getDefaultBookAlpha(isImportant)
        // 颜色支持自定义：`defaultColor` 只是「从未设置过」时的初始值，
        // 保留它作为 fallback，老用户的观感（全部=深灰 / 重点=红）不变。
        val color = prefs.getDefaultBookColor(isImportant, defaultColor)

        binding.collapsingToolbar.setContentScrimColor(color)
        binding.collapsingToolbar.setStatusBarScrimColor(color)

        if (coverUri != null) {
            binding.ivHeaderImage.load(Uri.parse(coverUri)) {
                crossfade(true)
            }
            binding.ivHeaderImage.alpha = alpha
            // 有图片时，假设背景偏暗，强制使用白色文字以保证可读性
            updateTitleTextColor(Color.BLACK)
        } else {
            binding.ivHeaderImage.setImageDrawable(null)
            binding.ivHeaderImage.setBackgroundColor(color)
            binding.ivHeaderImage.alpha = alpha
            updateTitleTextColor(color)
        }
    }

    private fun updateTitleTextColor(backgroundColor: Int) {
        val luminance = ColorUtils.calculateLuminance(backgroundColor)
        val isDark = luminance < 0.5
        val textColor = if (isDark) Color.WHITE else Color.BLACK

        binding.collapsingToolbar.setExpandedTitleColor(textColor)
        binding.collapsingToolbar.setCollapsedTitleTextColor(textColor)
        binding.toolbar.setTitleTextColor(textColor)

        binding.toolbar.navigationIcon?.let { icon ->
            val wrapped = DrawableCompat.wrap(icon)
            DrawableCompat.setTint(wrapped, textColor)
            binding.toolbar.navigationIcon = wrapped
        }

        tintToolbarMenuIcons(textColor)
    }

    /**
     * 工具栏菜单图标跟随标题色。
     *
     * 抽成方法是因为有**时序**要求：菜单项由 [setupSettingsMenu] 在
     * loadPageDetails() **之后**才添加，而标题色是在 loadPageDetails() 里算的。
     * 原先染色代码写在 updateTitleTextColor 内部，执行时 menu 还是空的
     * （`if (menu.size() > 0)` 直接跳过），齿轮图标因此从未被染色 ——
     * 之后被 MtbThemeEngine 按 `?attr/colorControlNormal` 刷成深色，
     * 压在彩色 CollapsingToolbar 上就是「彩色头部 + 深色齿轮」。
     *
     * 所以建完菜单要再调一次本方法补染。
     */
    private fun tintToolbarMenuIcons(color: Int) {
        val menu = binding.toolbar.menu
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val icon = item.icon ?: continue
            // mutate()：避免把 tint 染到共享的 ConstantState 上，
            // 影响同一 drawable 在别处（抽屉菜单、底栏）的显示。
            val wrapped = DrawableCompat.wrap(icon.mutate())
            DrawableCompat.setTint(wrapped, color)
            item.icon = wrapped
        }
    }

    private fun showContextMenu(event: CountdownEvent, anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menuInflater.inflate(R.menu.event_card_context_menu, popup.menu)

        val pinMenuItem = popup.menu.findItem(R.id.action_pin)
        pinMenuItem.title = if (event.isPinned) getString(R.string.home_unpin) else getString(R.string.home_pin)
        val importantMenuItem = popup.menu.findItem(R.id.action_mark_important)
        importantMenuItem.title = if (event.isImportant) getString(R.string.home_unmark_important) else getString(R.string.home_mark_important)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_pin -> { homeViewModel.update(event.copy(isPinned = !event.isPinned)); true }
                R.id.action_mark_important -> { homeViewModel.update(event.copy(isImportant = !event.isImportant)); true }
                R.id.action_delete -> {
                    homeViewModel.delete(event)
                    Snackbar.make(binding.root, R.string.home_event_deleted, Snackbar.LENGTH_LONG)
                        .setAction(R.string.common_undo) { homeViewModel.insert(event) }.show()
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
        // 对话框的 binding 持有视图，视图销毁后必须一起放开，
        // 否则选择器回调会往一个已经死掉的视图上写字。
        defaultBookDialogBinding = null
        _binding = null
    }
}