// file: app/src/main/java/com/errorsiayusulif/zakocountdown/MainActivity.kt
package com.errorsiayusulif.zakocountdown

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.databinding.ActivityMainBinding
import com.errorsiayusulif.zakocountdown.receiver.SecretCodeReceiver
import com.google.android.material.color.DynamicColors // 确保导入

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

        appBarConfiguration = AppBarConfiguration(setOf(R.id.homeFragment), binding.drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(SecretCodeReceiver.NAVIGATE_TO_DEV_OPTIONS, false) == true) {
            Handler(Looper.getMainLooper()).postDelayed({
                // 使用 R 文件中的 ID，这是最可靠的方式
                navController.navigate(R.id.action_global_deepDeveloperFragment)
            }, 100)
            intent.removeExtra(SecretCodeReceiver.NAVIGATE_TO_DEV_OPTIONS)
        }
    }

    // ... applySelectedTheme 和 onSupportNavigateUp 方法保持不变 ...

    private fun applySelectedTheme() {
        val themeKey = preferenceManager.getTheme()
        val colorKey = preferenceManager.getAccentColor()

        // 1. 确定基础主题资源 ID
        // 注意：我们这里统一先应用 M3 基础，后续如果是 MD1/MD2 再覆盖
        // 这样做是为了让 DynamicColors 有机会在 M3 模式下生效

        var finalThemeResId = R.style.Theme_ZakoCountdown_M3

        if (themeKey == PreferenceManager.THEME_M1) {
            finalThemeResId = R.style.Theme_ZakoCountdown_MD1
        } else if (themeKey == PreferenceManager.THEME_M2) {
            finalThemeResId = R.style.Theme_ZakoCountdown_MD2
        }

        // 2. 处理颜色叠加
        // 如果不是 M3 + Monet，我们需要叠加自定义颜色
        if (!(themeKey == PreferenceManager.THEME_M3 && colorKey == PreferenceManager.ACCENT_MONET)) {

            // 先应用基础主题
            setTheme(finalThemeResId)

            // 再应用颜色叠加
            val colorOverlayId = when (colorKey) {
                PreferenceManager.ACCENT_PINK -> R.style.Theme_ZakoCountdown_Overlay_Pink
                PreferenceManager.ACCENT_BLUE -> R.style.Theme_ZakoCountdown_Overlay_Blue
                // 如果是 Monet 或者是 MD1/MD2 的默认色，这里返回 0
                else -> 0
            }
            if (colorOverlayId != 0) {
                theme.applyStyle(colorOverlayId, true)
            }
        } else {
            // --- 【Monet 核心修复】 ---
            // 如果是 M3 + Monet，我们需要启用动态取色
            // 这一步必须在 setTheme 之前或之后紧接着调用
            // 注意：applyToActivityIfAvailable 会尝试应用一个系统动态主题覆盖当前主题

            // 1. 先应用我们的 M3 基础主题 (无颜色定义)
            setTheme(R.style.Theme_ZakoCountdown_M3)

            // 2. 尝试应用动态颜色
            if (DynamicColors.isDynamicColorAvailable()) {
                DynamicColors.applyToActivityIfAvailable(this)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp()
    }
}