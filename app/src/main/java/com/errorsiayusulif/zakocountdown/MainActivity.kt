package com.errorsiayusulif.zakocountdown

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
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.errorsiayusulif.zakocountdown.data.AgendaBook
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.databinding.ActivityMainBinding
import com.errorsiayusulif.zakocountdown.receiver.SecretCodeReceiver
import com.errorsiayusulif.zakocountdown.ui.agenda.AgendaViewModel
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
    }

    override fun onResume() {
        super.onResume()
        // 确保从设置返回时刷新
        setupNavigationMode()
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
                    R.id.aboutFragment, R.id.developerSettingsFragment, R.id.permissionsFragment -> {
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
        if (intent?.getBooleanExtra(SecretCodeReceiver.NAVIGATE_TO_DEV_OPTIONS, false) == true) {
            Handler(Looper.getMainLooper()).postDelayed({ navController.navigate(R.id.action_global_deepDeveloperFragment) }, 100)
            intent.removeExtra(SecretCodeReceiver.NAVIGATE_TO_DEV_OPTIONS)
        }
        if (intent?.getBooleanExtra(SecretCodeReceiver.NAVIGATE_TO_LOG_READER, false) == true) {
            Handler(Looper.getMainLooper()).postDelayed({ navController.navigate(R.id.action_global_logReaderFragment) }, 100)
            intent.removeExtra(SecretCodeReceiver.NAVIGATE_TO_LOG_READER)
        }
    }

    override fun onSupportNavigateUp(): Boolean = NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp()

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) binding.drawerLayout.closeDrawer(GravityCompat.START)
        else if (binding.drawerLayout.isDrawerOpen(GravityCompat.END)) binding.drawerLayout.closeDrawer(GravityCompat.END)
        else super.onBackPressed()
    }

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