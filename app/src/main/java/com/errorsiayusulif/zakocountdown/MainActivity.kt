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

            val finalThemeResId = when (themeKey) {
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
                else -> { // 默认为 MD3
                    when (colorKey) {
                        PreferenceManager.ACCENT_PINK -> R.style.Theme_ZakoCountdown_M3_Pink
                        PreferenceManager.ACCENT_BLUE -> R.style.Theme_ZakoCountdown_M3_Blue
                        else -> R.style.Theme_ZakoCountdown_M3
                    }
                }
            }
            setTheme(finalThemeResId)
        }

    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp()
    }
}