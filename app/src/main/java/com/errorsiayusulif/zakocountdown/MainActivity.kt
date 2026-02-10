// file: app/src/main/java/com/errorsiayusulif/zakocountdown/MainActivity.kt
package com.errorsiayusulif.zakocountdown

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color // 导入修复
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater // 导入修复
import android.view.View
import android.view.ViewGroup // 导入修复
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
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

    // 共享 ViewModel 用于日程本筛选
    private val agendaViewModel: AgendaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        preferenceManager = PreferenceManager(this)
        // 1. 在布局填充前应用主题
        applySelectedTheme()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // 初始化
        setupRightDrawer()
        handleIntent(intent)
    }

    /**
     * 每次回到 Activity 刷新导航状态，确保开发者设置立即生效
     */
    override fun onResume() {
        super.onResume()
        setupNavigationMode()
    }

    /**
     * 设置导航模式 (底部导航 vs 侧滑抽屉)
     */
    private fun setupNavigationMode() {
        val navMode = preferenceManager.getNavMode()
        val isAgendaEnabled = preferenceManager.isAgendaBookEnabled()

        if (navMode == PreferenceManager.NAV_MODE_BOTTOM) {
            // --- 底部导航模式 ---
            // 1. 彻底禁用并隐藏左侧抽屉
            binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
            binding.navView.visibility = View.GONE

            // 2. 启用右侧抽屉 (筛选)
            binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)

            // 3. 显示底部导航栏
            binding.bottomNavView.visibility = View.VISIBLE
            binding.bottomNavView.setupWithNavController(navController)

            // 4. 控制“日程本”入口可见性
            binding.bottomNavView.menu.findItem(R.id.agendaBookFragment)?.isVisible = isAgendaEnabled

            // 5. 核心修复：高亮联动逻辑
            navController.addOnDestinationChangedListener { _, destination, _ ->
                if (binding.bottomNavView.visibility == View.VISIBLE) {
                    when (destination.id) {
                        R.id.homeFragment, R.id.addEditEventFragment, R.id.cardSettingsFragment, R.id.sharePreviewFragment -> {
                            binding.bottomNavView.menu.findItem(R.id.homeFragment)?.isChecked = true
                        }
                        R.id.settingsFragment, R.id.personalizationFragment, R.id.advancedSettingsFragment,
                        R.id.aboutFragment, R.id.developerSettingsFragment, R.id.permissionsFragment -> {
                            binding.bottomNavView.menu.findItem(R.id.settingsFragment)?.isChecked = true
                        }
                        R.id.agendaBookFragment, R.id.addEditAgendaBookFragment -> {
                            binding.bottomNavView.menu.findItem(R.id.agendaBookFragment)?.isChecked = true
                        }
                    }
                }
            }

            // 6. 配置 AppBar (不传 drawerLayout，这样左侧是返回箭头)
            val topLevelDestinations = mutableSetOf(R.id.homeFragment, R.id.settingsFragment)
            if (isAgendaEnabled) topLevelDestinations.add(R.id.agendaBookFragment)
            appBarConfiguration = AppBarConfiguration(topLevelDestinations)

        } else {
            // --- 侧滑抽屉模式 ---
            binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
            binding.navView.visibility = View.VISIBLE
            binding.bottomNavView.visibility = View.GONE

            binding.navView.setupWithNavController(navController)
            binding.navView.menu.findItem(R.id.agendaBookFragment)?.isVisible = isAgendaEnabled

            val topLevelDestinations = mutableSetOf(R.id.homeFragment, R.id.settingsFragment)
            if (isAgendaEnabled) topLevelDestinations.add(R.id.agendaBookFragment)
            appBarConfiguration = AppBarConfiguration(topLevelDestinations, binding.drawerLayout)
        }
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    /**
     * 设置右侧日程本筛选抽屉的视觉样式和数据
     */
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
                // MD2: 头部无颜色 (白色/Surface)
                binding.rightDrawerContainer.setBackgroundColor(colorSurface)
                findViewById<View>(R.id.right_drawer_header)?.setBackgroundColor(colorSurface)
                setRightHeaderContentColor(colorOnSurface)
            }
            else -> {
                // MD1: 头部有主色
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
        val container = findViewById<android.widget.LinearLayout>(R.id.right_drawer_header) ?: return
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

        val themeResId = when (themeKey) {
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

        if (themeKey == PreferenceManager.THEME_M3 && colorKey == PreferenceManager.ACCENT_MONET) {
            if (DynamicColors.isDynamicColorAvailable()) {
                DynamicColors.applyToActivityIfAvailable(this)
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
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
    }

    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else if (binding.drawerLayout.isDrawerOpen(GravityCompat.END)) {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
        } else {
            super.onBackPressed()
        }
    }

    // --- 适配器内部类 ---

    data class AgendaItem(val id: Long, val name: String, val colorHex: String)

    inner class AgendaAdapter(
        private val items: List<AgendaItem>,
        private val onClick: (Long) -> Unit
    ) : RecyclerView.Adapter<AgendaAdapter.ViewHolder>() {

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