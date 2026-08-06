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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.GravityCompat
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
import com.errorsiayusulif.zakocountdown.utils.MtbThemeEngine
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var preferenceManager: PreferenceManager
    private var destinationListener: NavController.OnDestinationChangedListener? = null

    private val agendaViewModel: AgendaViewModel by viewModels()

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
        val primary = engine.getResolvedColor(this, "primary", com.google.android.material.R.attr.colorPrimary, com.google.android.material.R.attr.colorPrimary, "#37693D")
        val onPrimary = engine.getResolvedColor(this, "onPrimary", com.google.android.material.R.attr.colorOnPrimary, android.R.attr.textColorPrimaryInverse, "#FFFFFF")

        // 2. 全局染色 Window 状态栏 & 导航栏
        window.statusBarColor = surface
        window.navigationBarColor = surface

        // 3. 染色左侧 Drawer Header (nav_header.xml)
        if (binding.navView.headerCount > 0) {
            val headerView = binding.navView.getHeaderView(0)
            headerView.setBackgroundColor(primary)
            headerView.findViewById<android.widget.TextView>(R.id.drawer_app_name)?.setTextColor(onPrimary)
            headerView.findViewById<android.widget.ImageView>(R.id.drawer_logo)?.imageTintList = android.content.res.ColorStateList.valueOf(onPrimary)
        }

        // 4. 深度染色整个界面的 View 树 (覆盖 Toolbar, BottomNav, FAB 等)
        engine.applyThemeToViewTree(binding.root, this)
    }

    private fun checkAccessibilityAndPopup() {
        val prefs = preferenceManager
        val isPopupEnabled = prefs.isPopupReminderEnabled()
        val isServiceRunning = com.errorsiayusulif.zakocountdown.utils.AccessibilityStatusHelper.isAccessibilityServiceEnabled(this)

        if (isPopupEnabled && !isServiceRunning) {
            // 如果开关开着但服务没跑，自动禁用开关 (防止死循环检测)
            // prefs.setPopupReminderEnabled(false) // 如果你想强制关掉

            // 检查是否是首次提示（用一个标志位记录）
            if (!prefs.hasPromptedAccessibility()) {
                prefs.setHasPromptedAccessibility(true)
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("需要无障碍权限")
                    .setMessage("为了在您打开指定应用时弹出倒数日提醒，我们需要开启无障碍服务。")
                    .setPositiveButton("去开启") { _, _ ->
                        startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    .setNegativeButton("取消并禁用弹窗", { _, _ ->
                        // prefs.setPopupReminderEnabled(false)
                    })
                    .show()
            }
        }
    }

    private fun setupNavigationMode() {
        // 清除旧监听器防止冲突
        destinationListener?.let { navController.removeOnDestinationChangedListener(it) }

        val navMode = preferenceManager.getNavMode()
        val layoutMode = preferenceManager.getHomeLayoutMode()
        val isAgendaEnabled = preferenceManager.isAgendaBookEnabled()
        val isCompactMode = layoutMode == PreferenceManager.HOME_LAYOUT_COMPACT

        // 默认显示标题栏
        supportActionBar?.show()

        val topLevelDestinations = mutableSetOf<Int>()

        if (isCompactMode) {
            // 紧凑模式：主页是唯一顶级
            topLevelDestinations.add(R.id.homeFragment)
            binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            binding.navView.visibility = View.GONE
            binding.bottomNavView.visibility = View.GONE
            // 紧凑模式强制显示 Toolbar 菜单
            invalidateOptionsMenu()
        } else if (navMode == PreferenceManager.NAV_MODE_BOTTOM) {
            // 底部导航模式
            topLevelDestinations.add(R.id.homeFragment)
            topLevelDestinations.add(R.id.settingsFragment)
            if (isAgendaEnabled) topLevelDestinations.add(R.id.agendaBookFragment)

            binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
            binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)
            binding.navView.visibility = View.GONE
            binding.bottomNavView.visibility = View.VISIBLE
            binding.bottomNavView.setupWithNavController(navController)
            binding.bottomNavView.menu.findItem(R.id.agendaBookFragment)?.isVisible = isAgendaEnabled
        } else {
            // 侧滑模式
            topLevelDestinations.add(R.id.homeFragment)
            topLevelDestinations.add(R.id.settingsFragment)
            if (isAgendaEnabled) topLevelDestinations.add(R.id.agendaBookFragment)

            binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            binding.navView.visibility = View.VISIBLE
            binding.bottomNavView.visibility = View.GONE
            binding.navView.setupWithNavController(navController)
            binding.navView.menu.findItem(R.id.agendaBookFragment)?.isVisible = isAgendaEnabled
        }

        appBarConfiguration = if (isCompactMode || navMode == PreferenceManager.NAV_MODE_BOTTOM) {
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
                    R.id.aboutFragment, R.id.developerSettingsFragment, R.id.permissionsFragment, R.id.backupRestoreFragment -> {
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

    private fun setupRightDrawer() {
        val themeKey = preferenceManager.getTheme()
        val colorOnSurface = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
        val colorPrimary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
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
        listItems.add(AgendaItem(-1, "全部日程", "#9E9E9E"))
        listItems.add(AgendaItem(-2, "重点日程", "#F44336"))
        books.forEach { listItems.add(AgendaItem(it.id, it.name, it.colorHex)) }
        binding.recyclerViewAgenda.adapter = AgendaAdapter(listItems) { id ->
            agendaViewModel.setFilter(id)
            binding.drawerLayout.closeDrawer(GravityCompat.END)
        }
    }

    private fun showAddBookDialog() {
        val input = EditText(this)
        input.hint = "日程本名称"
        AlertDialog.Builder(this)
            .setTitle("新建日程本")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString()
                if (name.isNotBlank()) agendaViewModel.createBook(name, "#2196F3")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun applySelectedTheme() {
        val themeKey = preferenceManager.getTheme()
        val colorKey = preferenceManager.getAccentColor()
        val layoutMode = preferenceManager.getHomeLayoutMode()
        val isCompact = layoutMode == PreferenceManager.HOME_LAYOUT_COMPACT
        val isLegacyUnlocked = preferenceManager.isLegacyThemeUnlockedInCompact()

        var finalThemeKey = themeKey
        if (isCompact && !isLegacyUnlocked) {
            finalThemeKey = PreferenceManager.THEME_M3
        }

        val themeResId = when (finalThemeKey) {
            PreferenceManager.THEME_M1 -> when (colorKey) {
                PreferenceManager.ACCENT_PINK -> R.style.Theme_ZakoCountdown_MD1_Pink
                PreferenceManager.ACCENT_BLUE -> R.style.Theme_ZakoCountdown_MD1_Blue
                else -> R.style.Theme_ZakoCountdown_MD1
            }
            PreferenceManager.THEME_M2 -> when (colorKey) {
                PreferenceManager.ACCENT_PINK -> R.style.Theme_ZakoCountdown_MD2_Pink
                PreferenceManager.ACCENT_BLUE -> R.style.Theme_ZakoCountdown_MD2_Blue
                else -> R.style.Theme_ZakoCountdown_MD2
            }
            else -> when (colorKey) {
                PreferenceManager.ACCENT_PINK -> R.style.Theme_ZakoCountdown_M3_Pink
                PreferenceManager.ACCENT_BLUE -> R.style.Theme_ZakoCountdown_M3_Blue
                else -> R.style.Theme_ZakoCountdown_M3
            }
        }
        setTheme(themeResId)
        if (finalThemeKey == PreferenceManager.THEME_M3 && colorKey == PreferenceManager.ACCENT_MONET) {
            if (DynamicColors.isDynamicColorAvailable()) DynamicColors.applyToActivityIfAvailable(this)
        }
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
        val title = uri.getQueryParameter("title") ?: "未命名共享日程"
        val dateStr = uri.getQueryParameter("date") ?: return
        val targetDateMillis = dateStr.toLongOrNull() ?: return
        val colorHex = uri.getQueryParameter("color")

        val sdf = java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm", java.util.Locale.getDefault())
        val dateFormatted = sdf.format(java.util.Date(targetDateMillis))

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("导入分享的日程")
            .setMessage("您收到了一个日程分享：\n\n 标题：$title\n 目标日：$dateFormatted\n\n是否立即将其导入到您的 ZakoCountdown？")
            .setPositiveButton("导入") { _, _ ->
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
                        android.widget.Toast.makeText(this@MainActivity, "日程「$title」已成功导入！", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .setCancelable(false)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean = NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp()

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