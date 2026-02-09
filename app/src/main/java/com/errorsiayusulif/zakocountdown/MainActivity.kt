// file: app/src/main/java/com/errorsiayusulif/zakocountdown/MainActivity.kt
package com.errorsiayusulif.zakocountdown

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.databinding.ActivityMainBinding
import com.errorsiayusulif.zakocountdown.receiver.SecretCodeReceiver
import com.google.android.material.color.DynamicColors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var preferenceManager: PreferenceManager

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

        setupNavigationMode()
        handleIntent(intent)
    }

    private fun setupNavigationMode() {
        val navMode = preferenceManager.getNavMode()
        // 获取开关状态
        val isAgendaEnabled = preferenceManager.isAgendaBookEnabled()


        if (navMode == PreferenceManager.NAV_MODE_BOTTOM) {
            // --- 底部导航模式 ---
            binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            binding.bottomNavView.visibility = View.VISIBLE

            // 1. 基础绑定
            binding.bottomNavView.setupWithNavController(navController)
            // --- 动态控制底部栏可见性 ---
            binding.bottomNavView.menu.findItem(R.id.agendaBookFragment)?.isVisible = isAgendaEnabled

            // 更新 AppBar 配置，将 Agenda 也视为顶级页面 (无返回箭头)
            val topLevelDestinations = mutableSetOf(R.id.homeFragment, R.id.settingsFragment)
            if (isAgendaEnabled) topLevelDestinations.add(R.id.agendaBookFragment)

            appBarConfiguration = AppBarConfiguration(topLevelDestinations)
            setupActionBarWithNavController(navController, appBarConfiguration)
            // 2. 修复高亮逻辑：处理设置页的子页面
            navController.addOnDestinationChangedListener { _, destination, _ ->
                if (binding.bottomNavView.visibility == View.VISIBLE) {
                    when (destination.id) {
                        // --- 【核心修复】将首页的所有子页面都归类为 Home ---
                        R.id.homeFragment,
                        R.id.addEditEventFragment,     // 编辑/添加
                        R.id.cardSettingsFragment,     // 卡片设置
                        R.id.sharePreviewFragment -> { // 分享预览
                            val item = binding.bottomNavView.menu.findItem(R.id.homeFragment)
                            if (!item.isChecked) item.isChecked = true
                        }

                        // --- 设置页及其子页面 ---
                        R.id.settingsFragment,
                        R.id.personalizationFragment,
                        R.id.notificationSettingsFragment,
                        R.id.advancedSettingsFragment,
                        R.id.permissionsFragment,
                        R.id.aboutFragment,
                        R.id.appSelectorFragment,
                        R.id.developerSettingsFragment,
                        R.id.deepDeveloperFragment,
                        R.id.buildDetailsFragment,
                        R.id.licenseFragment,
                        R.id.logReaderFragment,
                        R.id.logFileListFragment,
                        R.id.agendaBookFragment -> { // 日程本列表(如果是从设置进)或者作为独立Tab
                            // 注意：如果日程本是作为第三个Tab存在的，这里不需要处理；
                            // 如果是从设置里进去的，才在这里处理。根据之前的逻辑，它是顶级Tab。
                            val item = binding.bottomNavView.menu.findItem(R.id.settingsFragment)
                            if (!item.isChecked) item.isChecked = true
                        }
                    }

                    // 特殊处理：如果是 AgendaBookFragment 且它在菜单中可见（作为第3个Tab）
                    if (destination.id == R.id.agendaBookFragment) {
                        val agendaItem = binding.bottomNavView.menu.findItem(R.id.agendaBookFragment)
                        if (agendaItem != null && agendaItem.isVisible && !agendaItem.isChecked) {
                            agendaItem.isChecked = true
                        }
                    }
                }
            }

            // 配置 ActionBar，这两个顶级页面不显示返回箭头
            appBarConfiguration = AppBarConfiguration(setOf(R.id.homeFragment, R.id.settingsFragment))
            setupActionBarWithNavController(navController, appBarConfiguration)

        } else {
            // --- 侧滑抽屉模式 ---
            binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            binding.bottomNavView.visibility = View.GONE
            binding.navView.setupWithNavController(navController)
            // --- 动态控制侧滑栏可见性 ---
            binding.navView.menu.findItem(R.id.agendaBookFragment)?.isVisible = isAgendaEnabled
            appBarConfiguration = AppBarConfiguration(setOf(R.id.homeFragment), binding.drawerLayout)
            setupActionBarWithNavController(navController, appBarConfiguration)
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

    private fun applySelectedTheme() {
        val themeKey = preferenceManager.getTheme()
        val colorKey = preferenceManager.getAccentColor() // 这里已经包含了 fallback 逻辑

        // 根据组合确定具体的主题资源 ID
        val themeResId = when (themeKey) {
            PreferenceManager.THEME_M1 -> {
                when (colorKey) {
                    PreferenceManager.ACCENT_PINK -> R.style.Theme_ZakoCountdown_MD1_Pink
                    PreferenceManager.ACCENT_BLUE -> R.style.Theme_ZakoCountdown_MD1_Blue
                    else -> R.style.Theme_ZakoCountdown_MD1
                }
            }
            PreferenceManager.THEME_M2 -> {
                when (colorKey) {
                    PreferenceManager.ACCENT_PINK -> R.style.Theme_ZakoCountdown_MD2_Pink
                    PreferenceManager.ACCENT_BLUE -> R.style.Theme_ZakoCountdown_MD2_Blue
                    else -> R.style.Theme_ZakoCountdown_MD2
                }
            }
            else -> { // M3
                when (colorKey) {
                    PreferenceManager.ACCENT_PINK -> R.style.Theme_ZakoCountdown_M3_Pink
                    PreferenceManager.ACCENT_BLUE -> R.style.Theme_ZakoCountdown_M3_Blue
                    else -> R.style.Theme_ZakoCountdown_M3
                }
            }
        }

        setTheme(themeResId)

        // 仅在 M3 + Monet 且系统支持时应用动态色
        if (themeKey == PreferenceManager.THEME_M3 && colorKey == PreferenceManager.ACCENT_MONET) {
            if (DynamicColors.isDynamicColorAvailable()) {
                DynamicColors.applyToActivityIfAvailable(this)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}