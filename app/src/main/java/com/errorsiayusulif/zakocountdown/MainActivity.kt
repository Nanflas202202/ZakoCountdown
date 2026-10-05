package com.errorsiayusulif.zakocountdown

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.core.view.GravityCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.doOnLayout
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.errorsiayusulif.zakocountdown.data.AgendaBook
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.databinding.ActivityMainBinding
import com.errorsiayusulif.zakocountdown.receiver.SecretCodeReceiver
import com.errorsiayusulif.zakocountdown.ui.agenda.AgendaViewModel
import com.errorsiayusulif.zakocountdown.utils.LocalizedActivity
import com.errorsiayusulif.zakocountdown.utils.MtbThemeEngine
import com.errorsiayusulif.zakocountdown.utils.NavModeHelper
import com.errorsiayusulif.zakocountdown.utils.ZakoFloatingNavBehavior
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import coil.load

class MainActivity : LocalizedActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var preferenceManager: PreferenceManager
    private var destinationListener: NavController.OnDestinationChangedListener? = null

    private val agendaViewModel: AgendaViewModel by viewModels()

    /**
     * 侧滑栏顶部的快捷换图入口。
     * 和设置页里的是同一件事，只是放在伸手就能点到的位置。
     */
    private val drawerHeaderPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        uri ?: return@registerForActivityResult
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return@registerForActivityResult
            val file = java.io.File(filesDir, DRAWER_HEADER_CACHE_FILE)
            java.io.FileOutputStream(file).use { output ->
                inputStream.use { input -> input.copyTo(output) }
            }
            preferenceManager.saveDrawerHeaderImageUri(android.net.Uri.fromFile(file).toString())
            preferenceManager.setDrawerHeaderImageEnabled(true)
            applyUniversalDynamicTheme()
            android.widget.Toast.makeText(this, R.string.personalization_drawer_header_set, android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, getString(R.string.common_failed, e.message ?: ""), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        preferenceManager = PreferenceManager(this)

        // --- 【v0.9.0 核心拦截】检查是否完成 OOBE ---
        if (!preferenceManager.isOobeCompleted()) {
            super.onCreate(savedInstanceState) // 必须调用，防止 Fragment 状态恢复崩溃
            val intent =
                Intent(this, com.errorsiayusulif.zakocountdown.ui.settings.OobeActivity::class.java)
            startActivity(intent)
            finish()
            return // 直接返回，不加载主界面
        }

        // 应用主题
        applySelectedTheme()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        setupRightDrawer()
        handleIntent(intent)

        // 冷启动立即初始化导航模式
        setupNavigationMode()

        // --- 【v0.9.0 新增：自动检查更新】 ---
        if (preferenceManager.isAutoUpdateEnabled()) {
            lifecycleScope.launch {
                com.errorsiayusulif.zakocountdown.utils.UpdateManager.checkUpdate(
                    this@MainActivity,
                    showToastIfLatest = false
                )
            }
        }
        // --- 替换废弃的 onBackPressed ---
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
                } else if (binding.drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.END)) {
                    binding.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.END)
                } else {
                    // 如果抽屉都没开，交给系统处理（比如退出应用或返回上一页）
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        setupNavigationMode()
        checkAccessibilityAndPopup()
        // --- 核心修复：更名为通用染色引擎调用 ---
        applyUniversalDynamicTheme()
    }

    private fun applyUniversalDynamicTheme() {
        val engine = com.errorsiayusulif.zakocountdown.utils.MtbThemeEngine

        // 1. 获取高度自适应的 Surface 和 Primary 颜色
        val surface = engine.getResolvedColor(this, "surface", com.google.android.material.R.attr.colorSurface, android.R.attr.windowBackground, "#F7FBF2")
        val primary = engine.getResolvedColor(this, "primary", android.R.attr.colorPrimary, android.R.attr.textColorPrimary, "#37693D")
        val onPrimary = engine.getResolvedColor(this, "onPrimary", com.google.android.material.R.attr.colorOnPrimary, android.R.attr.textColorPrimaryInverse, "#FFFFFF")

        // 2. 全局染色 Window 状态栏 & 导航栏
        window.statusBarColor = surface
        window.navigationBarColor = surface

        // 3. 渲染左侧 Drawer Header（纯色品牌头部 或 用户自定义图像）
        renderDrawerHeader(primary, onPrimary)

        // 4. 深度染色整个界面的 View 树 (覆盖 Toolbar, BottomNav, FAB 等)
        engine.applyThemeToViewTree(binding.root, this)
    }

    /**
     * 侧滑栏顶部：用户设置过图像且开关打开时，用图像铺满 + 渐变遮罩；
     * 否则回退为纯色品牌头部（primary 底 + onPrimary 前景）。
     */
    private fun renderDrawerHeader(primary: Int, onPrimary: Int) {
        if (binding.navView.headerCount <= 0) return
        val headerView = binding.navView.getHeaderView(0)

        val imageView = headerView.findViewById<android.widget.ImageView>(R.id.drawer_header_image)
        val scrimView = headerView.findViewById<View>(R.id.drawer_header_scrim)
        val pickButton = headerView.findViewById<android.widget.ImageView>(R.id.btn_pick_header_image)

        val imageUri = preferenceManager.getDrawerHeaderImageUri()
        val useImage = preferenceManager.isDrawerHeaderImageEnabled() && !imageUri.isNullOrBlank()

        if (useImage) {
            imageView?.visibility = View.VISIBLE
            scrimView?.visibility = View.VISIBLE
            // 自定义图像自带背景，头部底色透不出来，避免边缘露色
            headerView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            imageView?.load(imageUri) { crossfade(true) }
        } else {
            imageView?.visibility = View.GONE
            scrimView?.visibility = View.GONE
            imageView?.setImageDrawable(null)
            headerView.setBackgroundColor(primary)
        }

        // 前景始终取 onPrimary：自定义图像上有渐变遮罩兜底对比度
        headerView.findViewById<android.widget.TextView>(R.id.drawer_app_name)?.setTextColor(onPrimary)
        headerView.findViewById<android.widget.ImageView>(R.id.drawer_logo)?.imageTintList =
            android.content.res.ColorStateList.valueOf(onPrimary)
        pickButton?.imageTintList = android.content.res.ColorStateList.valueOf(onPrimary)
    }

    private fun checkAccessibilityAndPopup() {
        val prefs = preferenceManager
        val isPopupEnabled = prefs.isPopupReminderEnabled()
        val isServiceRunning = com.errorsiayusulif.zakocountdown.utils.AccessibilityStatusHelper.isAccessibilityServiceEnabled(this)

        if (!isPopupEnabled || isServiceRunning) return
        if (prefs.hasPromptedAccessibility()) return

        // 只在真正面向用户的恢复时提示一次；配置变更（旋转/深浅色切换/主题切换）
        // 触发的重建不算一次新的提示，否则会在切换界面时反复弹窗。
        if (isChangingConfigurations || isFinishing || isDestroyed) return

        prefs.setHasPromptedAccessibility(true)

        AlertDialog.Builder(this)
            .setTitle(R.string.perm_accessibility_dialog_title)
            .setMessage(R.string.perm_accessibility_dialog_message)
            .setPositiveButton(R.string.perm_accessibility_dialog_positive) { _, _ ->
                startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton(R.string.perm_accessibility_dialog_negative, null)
            .show()
    }

    private fun setupNavigationMode() {
        // 清除旧监听器防止冲突
        destinationListener?.let { navController.removeOnDestinationChangedListener(it) }

        // 解析出有效的导航形态（历史遗留值 floating 会被收敛成 bottom，见 NavModeHelper）
        val navMode = NavModeHelper.resolveNavMode(this)
        val isBottomNavMode = NavModeHelper.isBottomNav(navMode)
        val isAgendaEnabled = preferenceManager.isAgendaBookEnabled()
        val isCompactMode = preferenceManager.getHomeLayoutMode() == PreferenceManager.HOME_LAYOUT_COMPACT

        // 如果存的还是旧值，顺手把设置也改掉，免得设置页显示的和实际不一致
        if (navMode != preferenceManager.getNavMode()) {
            preferenceManager.saveNavMode(navMode)
        }

        // 底部导航栏是否开启「滚动自动隐藏」。
        // ⚠️ 必须放在下面「确定导航形态与可见性」之后：applyBottomBarAppearance 里
        //    会按底栏的可见状态给内容补底部内边距，提前调用时底栏还是 GONE，
        //    内边距会算成 0，内容照样被遮住。
        //（此处仅留注释，真正的调用见下方分支结束后）

        // 默认显示标题栏
        supportActionBar?.show()

        val topLevelDestinations = mutableSetOf<Int>()

        if (isCompactMode) {
            // 紧凑模式：主页是唯一顶级，不显示任何常驻导航
            topLevelDestinations.add(R.id.homeFragment)
            binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            binding.navView.visibility = View.GONE
            binding.bottomNavView.visibility = View.GONE
            // 紧凑模式强制显示 Toolbar 菜单
            invalidateOptionsMenu()
        } else if (isBottomNavMode) {
            // 底部系导航（悬浮 / 贴底）
            topLevelDestinations.add(R.id.homeFragment)
            topLevelDestinations.add(R.id.settingsFragment)
            if (isAgendaEnabled) topLevelDestinations.add(R.id.agendaBookFragment)

            // 底部系导航：左侧抽屉（导航用）锁死；
            // 右侧「日程本筛选」是正常功能（工具栏菜单会 openDrawer 打开），保持可用。
            binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
            binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)
            binding.navView.visibility = View.GONE
            binding.bottomNavView.visibility = View.VISIBLE
            binding.bottomNavView.setupWithNavController(navController)
            binding.bottomNavView.menu.findItem(R.id.agendaBookFragment)?.isVisible = isAgendaEnabled
        } else {
            // 侧滑抽屉模式
            topLevelDestinations.add(R.id.homeFragment)
            topLevelDestinations.add(R.id.settingsFragment)
            if (isAgendaEnabled) topLevelDestinations.add(R.id.agendaBookFragment)

            binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            binding.navView.visibility = View.VISIBLE
            binding.bottomNavView.visibility = View.GONE
            binding.navView.setupWithNavController(navController)
            binding.navView.menu.findItem(R.id.agendaBookFragment)?.isVisible = isAgendaEnabled
        }

        // 导航形态与可见性都确定之后，再应用底栏外观。
        // 它会顺带按底栏可见状态给内容补底部内边距（底栏是覆盖层，需要占位）。
        syncAutoHideNavBar()

        // 悬浮/贴底模式没有抽屉，AppBarConfiguration 不能带 drawerLayout，
        // 否则返回箭头/汉堡图标逻辑会错乱
        appBarConfiguration = if (isCompactMode || isBottomNavMode) {
            AppBarConfiguration(topLevelDestinations)
        } else {
            AppBarConfiguration(topLevelDestinations, binding.drawerLayout)
        }
        setupActionBarWithNavController(navController, appBarConfiguration)

        destinationListener = NavController.OnDestinationChangedListener { _, destination, _ ->
            // 处理详情页特殊情况
            if (destination.id == R.id.agendaDetailFragment) {
                supportActionBar?.hide()
            } else {
                // 在紧凑模式下主页也保持显示标题栏（因为我们要放菜单）
                supportActionBar?.show()
            }

            // 底部栏高亮修复
            if (binding.bottomNavView.visibility == View.VISIBLE) {
                val menu = binding.bottomNavView.menu
                when (destination.id) {
                    R.id.homeFragment, R.id.addEditEventFragment, R.id.cardSettingsFragment, R.id.sharePreviewFragment -> {
                        menu.findItem(R.id.homeFragment)?.isChecked = true
                    }
                    R.id.settingsFragment, R.id.personalizationFragment, R.id.advancedSettingsFragment,
                    R.id.aboutFragment, R.id.developerSettingsFragment, R.id.permissionsFragment,
                    R.id.backupRestoreFragment, R.id.savedThemesFragment -> {
                        menu.findItem(R.id.settingsFragment)?.isChecked = true
                    }
                    R.id.agendaBookFragment -> {
                        menu.findItem(R.id.agendaBookFragment)?.isChecked = true
                    }
                }
            }
        }
        navController.addOnDestinationChangedListener(destinationListener!!)
    }

    /**
     * 底部导航栏外观。
     *
     * 导航栏**始终贴住屏幕底边**（不留外边距、不做悬浮药丸）。
     * 「自动隐藏」只是让它在滚动时上下位移，不改变它的位置形态：
     *   - autoHide = true ：向下滚动时滑出屏幕，向上滚动时滑回来
     *     （位移逻辑在 [com.errorsiayusulif.zakocountdown.utils.ZakoFloatingNavBehavior]）
     *   - autoHide = false：固定不动
     */
    private fun applyBottomBarAppearance(autoHide: Boolean) {
        val bar = binding.bottomNavView
        val params = bar.layoutParams as ViewGroup.MarginLayoutParams
        val density = resources.displayMetrics.density

        // 先让行为复位（两个方向都要）。
        // 关闭自动隐藏时若导航栏正停在屏幕外，必须立刻拉回来，
        // 否则用户会以为「关闭开关没生效」。
        val behavior = (bar.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior as? ZakoFloatingNavBehavior
        behavior?.reset(bar)
        bar.translationY = 0f

        // 贴底：不留任何外边距
        params.setMargins(0, 0, 0, 0)
        bar.layoutParams = params

        // 普通底部导航栏外观（铺满宽度、直角、贴住底边）
        bar.background = android.graphics.drawable.ColorDrawable(
            MaterialColors.getColor(bar, com.google.android.material.R.attr.colorSurfaceContainer)
        )
        // 投影只做视觉层次，不再兼任「是否开启自动隐藏」的信号
        bar.elevation = if (autoHide) 8 * density else 3 * density
        bar.clipToOutline = false

        // 标签随选中项横向平移：M3 导航栏的动效，自动隐藏形态下更明显
        bar.isItemHorizontalTranslationEnabled = autoHide

        // 重新走一遍布局，让 Behavior 用新的高度/边距重算「滑出屏幕」的距离
        bar.requestLayout()

        // 底栏在 CoordinatorLayout 里是**覆盖层**（layout_gravity=bottom），
        // 不再像原来那样在 LinearLayout 里占位。所以必须给内容留出等高的底部内边距，
        // 否则列表最后一项会被压在导航栏下面 —— 表现就是「挡内容」，
        // 而且因为列表被压住，滚到底也看不到被遮住的导航栏。
        syncContentBottomInset()
    }

    /**
     * 让内容区域避开底部导航栏的占位。
     *
     * 底栏是覆盖层，内容需要自己留出 bottom padding = 底栏高度。
     * 底栏隐藏时这段空白保留（与 Material 的做法一致）——
     * 否则内容会在隐藏/显示的瞬间上下跳动，比留一条空白更难受。
     *
     * 高度要等测量完成才有效，所以这里 post 到下一帧；
     * 若此时还没测量出来（高度为 0），再补一次布局回调。
     */
    private fun syncContentBottomInset() {
        val bar = binding.bottomNavView
        val content = binding.navHostFragment

        fun apply() {
            val inset = if (bar.visibility == View.VISIBLE) bar.height else 0
            if (content.paddingBottom != inset) {
                content.setPadding(
                    content.paddingLeft,
                    content.paddingTop,
                    content.paddingRight,
                    inset
                )
            }
        }

        bar.post { apply() }
        bar.doOnLayout { apply() }
    }

    /**
     * 读取自动隐藏开关并立即应用。
     *
     * 从设置页返回时 [onResume] 会走到这里，所以开关「关掉立刻生效」。
     * 之前行为层用 elevation 当代理信号，导致关掉开关后滚动仍会隐藏导航栏；
     * 现在行为直接读偏好值，并且这里显式复位，两个方向都对。
     */
    private fun syncAutoHideNavBar() {
        applyBottomBarAppearance(NavModeHelper.isAutoHideNav(this))
    }

    private fun setupRightDrawer() {
        // 侧滑栏顶部的快捷换图入口
        if (binding.navView.headerCount > 0) {
            binding.navView.getHeaderView(0)
                .findViewById<android.widget.ImageView>(R.id.btn_pick_header_image)
                ?.setOnClickListener { drawerHeaderPicker.launch(arrayOf("image/*")) }
        }

        val themeKey = preferenceManager.getTheme()
        val colorOnSurface = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
        val colorPrimary = MaterialColors.getColor(binding.root, android.R.attr.colorPrimary)
        val colorOnPrimary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnPrimary)
        val colorSurface = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurface)

        when (themeKey) {
            PreferenceManager.THEME_M3 -> {
                binding.rightDrawerContainer.setBackgroundResource(R.drawable.bg_drawer_right_m3)
                val params = binding.rightDrawerContainer.layoutParams as DrawerLayout.LayoutParams
                val margin = (16 * resources.displayMetrics.density).toInt()
                params.setMargins(0, margin, margin, margin)
                binding.rightDrawerContainer.layoutParams = params
                findViewById<View>(R.id.right_drawer_header)?.background = null
                setRightHeaderContentColor(colorOnSurface)
            }
            PreferenceManager.THEME_M2 -> {
                binding.rightDrawerContainer.setBackgroundColor(colorSurface)
                findViewById<View>(R.id.right_drawer_header)?.setBackgroundColor(colorSurface)
                setRightHeaderContentColor(colorOnSurface)
            }
            else -> {
                binding.rightDrawerContainer.setBackgroundColor(colorSurface)
                findViewById<View>(R.id.right_drawer_header)?.setBackgroundColor(colorPrimary)
                setRightHeaderContentColor(colorOnPrimary)
            }
        }
        binding.recyclerViewAgenda.layoutManager = LinearLayoutManager(this)
        agendaViewModel.allBooks.observe(this) { books -> updateRightDrawerList(books) }
        agendaViewModel.currentFilterId.observe(this) { binding.recyclerViewAgenda.adapter?.notifyDataSetChanged() }
        findViewById<View>(R.id.btn_add_book)?.setOnClickListener { showAddBookDialog() }
    }

    private fun setRightHeaderContentColor(color: Int) {
        val container = findViewById<ViewGroup>(R.id.right_drawer_header) ?: return
        for (i in 0 until container.childCount) {
            val v = container.getChildAt(i)
            if (v is TextView) v.setTextColor(color)
            if (v is ImageView) v.imageTintList = ColorStateList.valueOf(color)
        }
    }

    private fun updateRightDrawerList(books: List<AgendaBook>) {
        val listItems = mutableListOf<AgendaItem>()
        listItems.add(AgendaItem(-1, getString(R.string.nav_filter_all), "#9E9E9E"))
        listItems.add(AgendaItem(-2, getString(R.string.nav_filter_important), "#F44336"))
        books.forEach { listItems.add(AgendaItem(it.id, it.name, it.colorHex)) }
        binding.recyclerViewAgenda.adapter = AgendaAdapter(listItems) { id ->
            agendaViewModel.setFilter(id)
            binding.drawerLayout.closeDrawer(GravityCompat.END)
        }
    }

    private fun showAddBookDialog() {
        val input = EditText(this)
        input.hint = getString(R.string.agenda_book_name_hint)
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_new_agenda_book)
            .setView(input)
            .setPositiveButton(R.string.common_create) { _, _ ->
                val name = input.text.toString()
                if (name.isNotBlank()) agendaViewModel.createBook(name, "#2196F3")
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun applySelectedTheme() {
        // 统一走 ZakoThemeApplier，保证主界面与弹窗/微件等入口用的是同一套主题解析逻辑
        com.errorsiayusulif.zakocountdown.utils.ZakoThemeApplier.applyToActivity(this)
    }

    private fun handleIntent(intent: Intent?) {
        // 1. 处理暗码调试入口
        if (intent?.getBooleanExtra(SecretCodeReceiver.NAVIGATE_TO_DEV_OPTIONS, false) == true) {
            Handler(Looper.getMainLooper()).postDelayed({
                navController.navigate(R.id.action_global_deepDeveloperFragment)
            }, 100)
            intent.removeExtra(SecretCodeReceiver.NAVIGATE_TO_DEV_OPTIONS)
        }
        if (intent?.getBooleanExtra(SecretCodeReceiver.NAVIGATE_TO_LOG_READER, false) == true) {
            Handler(Looper.getMainLooper()).postDelayed({
                navController.navigate(R.id.action_global_logReaderFragment)
            }, 100)
            intent.removeExtra(SecretCodeReceiver.NAVIGATE_TO_LOG_READER)
        }

        // --- 2. 【v0.9.0 核心】拦截并处理外部唤醒的 DeepLink 日程导入 ---
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null && uri.scheme == "errorsiayusulif" && uri.host == "zakocountdown" && uri.path == "/import") {
                handleImportFromUri(uri)
            }
        }
    }

    private fun handleImportFromUri(uri: android.net.Uri) {
        val title = uri.getQueryParameter("title") ?: getString(R.string.dialog_untitled_shared_event)
        val dateStr = uri.getQueryParameter("date") ?: return
        val targetDateMillis = dateStr.toLongOrNull() ?: return
        val colorHex = uri.getQueryParameter("color")

        // 用与语言无关的 ISO 风格格式，避免硬编码中文年月日
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        val dateFormatted = sdf.format(java.util.Date(targetDateMillis))

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_import_shared_event_title)
            .setMessage(getString(R.string.dialog_import_shared_event_message, title, dateFormatted))
            .setPositiveButton(R.string.dialog_import) { _, _ ->
                val event = com.errorsiayusulif.zakocountdown.data.CountdownEvent(
                    title = title,
                    targetDate = java.util.Date(targetDateMillis),
                    colorHex = colorHex
                )
                // 借助 Repository 写入数据库
                lifecycleScope.launch(Dispatchers.IO) {
                    val app = application as ZakoCountdownApplication
                    app.repository.insert(event)
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            getString(R.string.dialog_import_success, title),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .setCancelable(false)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean = NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp()

    companion object {
        /** 侧滑栏顶部图像的私有沙盒文件名（与设置页共用同一个缓存文件）。 */
        const val DRAWER_HEADER_CACHE_FILE = "drawer_header_cache.png"
    }

    /*override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) binding.drawerLayout.closeDrawer(GravityCompat.START)
        else if (binding.drawerLayout.isDrawerOpen(GravityCompat.END)) binding.drawerLayout.closeDrawer(GravityCompat.END)
        else super.onBackPressed()
    }*/

    data class AgendaItem(val id: Long, val name: String, val colorHex: String)
    inner class AgendaAdapter(private val items: List<AgendaItem>, private val onClick: (Long) -> Unit) : RecyclerView.Adapter<AgendaAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.book_name)
            val color: View = view.findViewById(R.id.book_color_indicator)
            val check: View = view.findViewById(R.id.check_mark)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_agenda_book, parent, false)
            return ViewHolder(v)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.name.text = item.name
            try { holder.color.setBackgroundColor(Color.parseColor(item.colorHex)) } catch (e: Exception) { holder.color.setBackgroundColor(Color.GRAY) }
            val currentId = agendaViewModel.currentFilterId.value
            if (currentId == item.id) {
                holder.check.visibility = View.VISIBLE
                holder.itemView.setBackgroundColor(MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorSurfaceVariant))
            } else {
                holder.check.visibility = View.GONE
                holder.itemView.background = null
            }
            holder.itemView.setOnClickListener { onClick(item.id) }
        }
        override fun getItemCount() = items.size
    }
}