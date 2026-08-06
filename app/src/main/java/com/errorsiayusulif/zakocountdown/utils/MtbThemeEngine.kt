// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/MtbThemeEngine.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import com.errorsiayusulif.zakocountdown.data.MtbColorScheme
import com.errorsiayusulif.zakocountdown.data.MtbThemeData
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.navigation.NavigationView
import com.google.android.material.navigationrail.NavigationRailView
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

object MtbThemeEngine {

    const val TAG = "MtbThemeEngine"
    const val PREF_IS_MTB_ENABLED = "is_mtb_theme_enabled"
    private const val PREF_PREFIX_LIGHT = "mtb_light_"
    private const val PREF_PREFIX_DARK = "mtb_dark_"

    fun isMtbActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences("zako_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_IS_MTB_ENABLED, false) &&
                prefs.getString("key_accent_color", "") == "CUSTOM_MTB"
    }

    suspend fun importThemeJson(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext false
            val reader = InputStreamReader(inputStream)
            val themeData = Gson().fromJson(reader, MtbThemeData::class.java)
            val prefs = context.getSharedPreferences("zako_prefs", Context.MODE_PRIVATE).edit()

            themeData.schemes?.light?.let { saveScheme(prefs, PREF_PREFIX_LIGHT, it) }
            themeData.schemes?.dark?.let { saveScheme(prefs, PREF_PREFIX_DARK, it) }

            prefs.putBoolean(PREF_IS_MTB_ENABLED, true)
            prefs.putString("key_accent_color", "CUSTOM_MTB")
            prefs.apply()

            reader.close()
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse MTB JSON", e)
            return@withContext false
        }
    }

    private fun saveScheme(prefs: android.content.SharedPreferences.Editor, prefix: String, scheme: MtbColorScheme) {
        val map = mapOf(
            "primary" to scheme.primary, "onPrimary" to scheme.onPrimary,
            "primaryContainer" to scheme.primaryContainer, "onPrimaryContainer" to scheme.onPrimaryContainer,
            "secondary" to scheme.secondary, "onSecondary" to scheme.onSecondary,
            "secondaryContainer" to scheme.secondaryContainer, "onSecondaryContainer" to scheme.onSecondaryContainer,
            "surface" to scheme.surface, "onSurface" to scheme.onSurface,
            "surfaceVariant" to scheme.surfaceVariant, "onSurfaceVariant" to scheme.onSurfaceVariant,
            "outline" to scheme.outline, "outlineVariant" to scheme.outlineVariant
        )
        map.forEach { (key, value) -> if (value != null) prefs.putString("$prefix$key", value) }
    }

    fun isDarkMode(context: Context): Boolean {
        val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * 超级色彩解析器：
     * 优先读取 MTB 配置；若未开启，则读取系统当前激活的属性（完美兼容 Monet、MD1、MD2）。
     * 提供降级备选属性，防止旧主题缺失属性导致崩溃。
     */
    fun getResolvedColor(context: Context, mtbKey: String, attrResId: Int, fallbackAttrResId: Int, fallbackHex: String): Int {
        if (isMtbActive(context)) {
            val prefix = if (isDarkMode(context)) PREF_PREFIX_DARK else PREF_PREFIX_LIGHT
            val prefs = context.getSharedPreferences("zako_prefs", Context.MODE_PRIVATE)
            val hexStr = prefs.getString("$prefix$mtbKey", null)
            if (hexStr != null) {
                try { return Color.parseColor(hexStr) } catch (e: Exception) {}
            }
        }

        // 回退：读取当前 Activity 上下文中的属性
        val defaultColor = try { Color.parseColor(fallbackHex) } catch (e: Exception) { Color.GRAY }
        val typedValue = android.util.TypedValue()

        if (context.theme.resolveAttribute(attrResId, typedValue, true)) {
            return MaterialColors.getColor(context, attrResId, defaultColor)
        }
        if (context.theme.resolveAttribute(fallbackAttrResId, typedValue, true)) {
            return MaterialColors.getColor(context, fallbackAttrResId, defaultColor)
        }
        return defaultColor
    }

    fun applyToPreferenceFragment(fragment: PreferenceFragmentCompat) {
        // 关键修复：必须使用 Activity 的 Context，因为它携带了完整的 Monet 和主题配置
        val activityContext = fragment.activity ?: return

        val surface = getResolvedColor(activityContext, "surface", com.google.android.material.R.attr.colorSurface, android.R.attr.windowBackground, "#F7FBF2")

        fragment.view?.setBackgroundColor(surface)
        val listView = fragment.listView
        listView?.setBackgroundColor(surface)

        listView?.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                // 每次有设置项滚入屏幕时，立即用 ActivityContext 染上正确的颜色
                applyThemeToViewTree(view, activityContext)
            }
            override fun onChildViewDetachedFromWindow(view: View) {}
        })
    }

    fun applyThemeToViewTree(rootView: View?, context: Context) {
        if (rootView == null) return

        // 全量提取最适配的颜色（即使是内建粉色、蓝色、Monet 也能被准确提取出来）
        val primary = getResolvedColor(context, "primary", com.google.android.material.R.attr.colorPrimary, com.google.android.material.R.attr.colorPrimary, "#6750A4")
        val onPrimary = getResolvedColor(context, "onPrimary", com.google.android.material.R.attr.colorOnPrimary, android.R.attr.textColorPrimaryInverse, "#FFFFFF")
        val primaryContainer = getResolvedColor(context, "primaryContainer", com.google.android.material.R.attr.colorPrimaryContainer, com.google.android.material.R.attr.colorSurfaceVariant, "#EADDFF")
        val onPrimaryContainer = getResolvedColor(context, "onPrimaryContainer", com.google.android.material.R.attr.colorOnPrimaryContainer, com.google.android.material.R.attr.colorOnSurface, "#21005D")
        val secondaryContainer = getResolvedColor(context, "secondaryContainer", com.google.android.material.R.attr.colorSecondaryContainer, com.google.android.material.R.attr.colorSurfaceVariant, "#E8DEF8")
        val onSecondaryContainer = getResolvedColor(context, "onSecondaryContainer", com.google.android.material.R.attr.colorOnSecondaryContainer, com.google.android.material.R.attr.colorOnSurface, "#1D192B")
        val surface = getResolvedColor(context, "surface", com.google.android.material.R.attr.colorSurface, android.R.attr.windowBackground, "#FFFBFE")
        val onSurface = getResolvedColor(context, "onSurface", com.google.android.material.R.attr.colorOnSurface, android.R.attr.textColorPrimary, "#1C1B1F")
        val surfaceVariant = getResolvedColor(context, "surfaceVariant", com.google.android.material.R.attr.colorSurfaceVariant, com.google.android.material.R.attr.colorSurface, "#E7E0EC")
        val onSurfaceVariant = getResolvedColor(context, "onSurfaceVariant", com.google.android.material.R.attr.colorOnSurfaceVariant, android.R.attr.textColorSecondary, "#49454F")
        val outline = getResolvedColor(context, "outline", com.google.android.material.R.attr.colorOutline, android.R.attr.textColorSecondary, "#79747E")
        val outlineVariant = getResolvedColor(context, "outlineVariant", com.google.android.material.R.attr.colorOutlineVariant, android.R.attr.textColorSecondary, "#CAC4D0")

        val navStateList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(onSecondaryContainer, ColorUtils.setAlphaComponent(onSurface, 160))
        )

        val primaryStateList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(primary, ColorUtils.setAlphaComponent(onSurface, 120))
        )

        if (rootView.background == null && rootView.id != android.R.id.content) {
            rootView.setBackgroundColor(surface)
        }

        traverseView(
            rootView, primary, onPrimary, primaryContainer, onPrimaryContainer,
            secondaryContainer, onSecondaryContainer, surface, onSurface,
            surfaceVariant, onSurfaceVariant, outline, outlineVariant,
            navStateList, primaryStateList
        )
    }

    private fun traverseView(
        v: View,
        primary: Int, onPrimary: Int, primaryContainer: Int, onPrimaryContainer: Int,
        secondaryContainer: Int, onSecondaryContainer: Int, surface: Int, onSurface: Int,
        surfaceVariant: Int, onSurfaceVariant: Int, outline: Int, outlineVariant: Int,
        navStateList: ColorStateList, primaryStateList: ColorStateList
    ) {
        when (v) {
            is Toolbar -> {
                v.setBackgroundColor(surface)
                v.setTitleTextColor(onSurface)
                v.setSubtitleTextColor(onSurfaceVariant)
                v.navigationIcon?.setTint(onSurface)
            }
            is FloatingActionButton -> {
                v.backgroundTintList = ColorStateList.valueOf(primaryContainer)
                v.imageTintList = ColorStateList.valueOf(onPrimaryContainer)
            }
            is ExtendedFloatingActionButton -> {
                v.backgroundTintList = ColorStateList.valueOf(primaryContainer)
                v.setTextColor(onPrimaryContainer)
                v.iconTint = ColorStateList.valueOf(onPrimaryContainer)
            }
            is MaterialButton -> {
                if (v.strokeWidth > 0) {
                    v.strokeColor = ColorStateList.valueOf(outline)
                    v.setTextColor(primary)
                    v.iconTint = ColorStateList.valueOf(primary)
                } else if (v.backgroundTintList == null || v.backgroundTintList == ColorStateList.valueOf(Color.TRANSPARENT)) {
                    v.setTextColor(primary)
                    v.iconTint = ColorStateList.valueOf(primary)
                } else {
                    v.backgroundTintList = ColorStateList.valueOf(primary)
                    v.setTextColor(onPrimary)
                    v.iconTint = ColorStateList.valueOf(onPrimary)
                }
            }
            is MaterialCardView -> {
                v.setCardBackgroundColor(surface)
                v.strokeColor = outlineVariant
            }
            is BottomNavigationView -> {
                v.setBackgroundColor(surface)
                v.itemActiveIndicatorColor = ColorStateList.valueOf(secondaryContainer)
                v.itemIconTintList = navStateList
                v.itemTextColor = navStateList
            }
            is NavigationRailView -> {
                v.setBackgroundColor(surface)
                v.itemActiveIndicatorColor = ColorStateList.valueOf(secondaryContainer)
                v.itemIconTintList = navStateList
                v.itemTextColor = navStateList
            }
            is NavigationView -> {
                v.setBackgroundColor(surface)
                v.itemIconTintList = navStateList
                v.itemTextColor = navStateList
            }
            is TabLayout -> {
                v.setBackgroundColor(surface)
                v.setSelectedTabIndicatorColor(primary)
                v.setTabTextColors(ColorUtils.setAlphaComponent(onSurface, 150), primary)
            }
            is MaterialSwitch -> {
                val trackColors = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
                    intArrayOf(primaryContainer, surfaceVariant)
                )
                val thumbColors = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
                    intArrayOf(primary, outline)
                )
                v.trackTintList = trackColors
                v.thumbTintList = thumbColors
            }
            is RadioButton, is MaterialRadioButton -> {
                (v as android.widget.CompoundButton).buttonTintList = primaryStateList
            }
            is CheckBox, is MaterialCheckBox -> {
                (v as android.widget.CompoundButton).buttonTintList = primaryStateList
            }
            is Slider -> {
                v.thumbTintList = ColorStateList.valueOf(primary)
                v.trackActiveTintList = ColorStateList.valueOf(primary)
                v.trackInactiveTintList = ColorStateList.valueOf(surfaceVariant)
            }
            is TextInputLayout -> {
                v.boxStrokeColor = primary
                v.setHintTextColor(ColorStateList.valueOf(primary))
                v.defaultHintTextColor = ColorStateList.valueOf(onSurfaceVariant)
            }
            is TextView -> {
                if (v.id == android.R.id.title || v.id == com.errorsiayusulif.zakocountdown.R.id.row_title) {
                    v.setTextColor(onSurface)
                } else if (v.id == android.R.id.summary || v.id == com.errorsiayusulif.zakocountdown.R.id.row_value) {
                    v.setTextColor(onSurfaceVariant)
                }
            }
        }

        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                traverseView(
                    v.getChildAt(i), primary, onPrimary, primaryContainer, onPrimaryContainer,
                    secondaryContainer, onSecondaryContainer, surface, onSurface,
                    surfaceVariant, onSurfaceVariant, outline, outlineVariant, navStateList, primaryStateList
                )
            }
        }
    }
}