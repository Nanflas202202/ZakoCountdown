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
import com.errorsiayusulif.zakocountdown.data.PreferenceKeys
import com.errorsiayusulif.zakocountdown.data.ImportedPalette
import com.errorsiayusulif.zakocountdown.data.MtbColorScheme
import com.errorsiayusulif.zakocountdown.data.ThemeArchive
import com.errorsiayusulif.zakocountdown.data.MtbThemeData
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.navigationrail.NavigationRailView
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

object MtbThemeEngine {

    const val TAG = "MtbThemeEngine"
    const val PREF_IS_MTB_ENABLED = PreferenceKeys.MTB_THEME_ENABLED
    /**
     * 偏好键前缀。**唯一来源是 [ThemeArchive]** ——
     * 写入和读取必须用同一套前缀，所以这里只做转发，不另立常量。
     */
    private val PREF_PREFIX_LIGHT = ThemeArchive.PREF_PREFIX_LIGHT
    private val PREF_PREFIX_DARK = ThemeArchive.PREF_PREFIX_DARK

    fun isMtbActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PreferenceKeys.PREFS_FILE, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_IS_MTB_ENABLED, false) &&
                prefs.getString(PreferenceKeys.ACCENT_COLOR, "") ==
                com.errorsiayusulif.zakocountdown.data.PreferenceManager.ACCENT_CUSTOM_MTB
    }

    /**
     * 导入 `material-theme.json`：解析 + 写入偏好，并把配色交回调用方供存档。
     *
     * 写入统一走 [ThemeArchive.applyColors]，不再在本类里另写一套键名拼接 ——
     * 之前 ZIP 与 JSON 两条路径各自写键，很容易悄悄漂移。
     *
     * @return 解析失败返回 null
     */
    suspend fun importThemeJson(context: Context, uri: Uri): ImportedPalette? = withContext(Dispatchers.IO) {
        val palette = parseThemeJson(context, uri) ?: return@withContext null
        ThemeArchive.applyColors(context, palette.light, palette.dark)
        palette
    }

    /**
     * 把 `material-theme.json` 解析成两套配色表，供 [com.errorsiayusulif.zakocountdown.data.ThemeArchive] 存档。
     *
     * 与 [importThemeJson] 分开是有意的：
     *   · [importThemeJson] 负责「写进偏好」这件事（保持原有行为不动）
     *   · 本方法只负责「读出数据」
     * 两者各自读一遍文件 —— MTB 导出的 JSON 只有几 KB，重复读取的代价可以忽略，
     * 换来的是两条路径互不干扰、都容易单独测试。
     *
     * @return 解析失败返回 null
     */
    fun parseThemeJson(context: Context, uri: Uri): ImportedPalette? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val themeData = Gson().fromJson(InputStreamReader(input), MtbThemeData::class.java)
                    ?: return null

                val light = themeData.schemes?.light.toColorMap()
                val dark = themeData.schemes?.dark.toColorMap()
                if (light.isEmpty() && dark.isEmpty()) null
                else ImportedPalette(light = light, dark = dark)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "解析 MTB JSON 失败", t)
            null
        }
    }

    /**
     * 把整个 [MtbColorScheme] 转成「角色 → 色值」表。
     *
     * 之所以逐字段列出而不是用反射：字段名到角色名的映射是**契约**
     * （必须与 [MtbZipImporter] 解析 zip 时得到的键名完全一致），
     * 用反射一旦有人改字段名就会静默失配，这里显式写出来，编译期就能发现遗漏。
     */
    private fun MtbColorScheme?.toColorMap(): Map<String, String> {
        if (this == null) return emptyMap()
        val pairs = listOf(
            "primary" to primary,
            "onPrimary" to onPrimary,
            "primaryContainer" to primaryContainer,
            "onPrimaryContainer" to onPrimaryContainer,
            "secondary" to secondary,
            "onSecondary" to onSecondary,
            "secondaryContainer" to secondaryContainer,
            "onSecondaryContainer" to onSecondaryContainer,
            "tertiary" to tertiary,
            "onTertiary" to onTertiary,
            "tertiaryContainer" to tertiaryContainer,
            "onTertiaryContainer" to onTertiaryContainer,
            "error" to error,
            "onError" to onError,
            "errorContainer" to errorContainer,
            "onErrorContainer" to onErrorContainer,
            "background" to background,
            "onBackground" to onBackground,
            "surface" to surface,
            "onSurface" to onSurface,
            "surfaceVariant" to surfaceVariant,
            "onSurfaceVariant" to onSurfaceVariant,
            "outline" to outline,
            "outlineVariant" to outlineVariant,
            "scrim" to scrim,
            "inverseSurface" to inverseSurface,
            "inverseOnSurface" to inverseOnSurface,
            "inversePrimary" to inversePrimary,
            "primaryFixed" to primaryFixed,
            "onPrimaryFixed" to onPrimaryFixed,
            "primaryFixedDim" to primaryFixedDim,
            "onPrimaryFixedVariant" to onPrimaryFixedVariant,
            "secondaryFixed" to secondaryFixed,
            "onSecondaryFixed" to onSecondaryFixed,
            "secondaryFixedDim" to secondaryFixedDim,
            "onSecondaryFixedVariant" to onSecondaryFixedVariant,
            "tertiaryFixed" to tertiaryFixed,
            "onTertiaryFixed" to onTertiaryFixed,
            "tertiaryFixedDim" to tertiaryFixedDim,
            "onTertiaryFixedVariant" to onTertiaryFixedVariant,
            "surfaceDim" to surfaceDim,
            "surfaceBright" to surfaceBright,
            "surfaceContainerLowest" to surfaceContainerLowest,
            "surfaceContainerLow" to surfaceContainerLow,
            "surfaceContainer" to surfaceContainer,
            "surfaceContainerHigh" to surfaceContainerHigh,
            "surfaceContainerHighest" to surfaceContainerHighest
        )
        return pairs.mapNotNull { (role, value) -> value?.let { role to it } }.toMap()
    }

    fun isDarkMode(context: Context): Boolean {
        val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

    /** 当前生效的导入主题名；没导入过则返回空串。 */
    fun currentThemeName(context: Context): String {
        val prefs = context.getSharedPreferences(PreferenceKeys.PREFS_FILE, Context.MODE_PRIVATE)
        return prefs.getString(PreferenceKeys.MTB_THEME_NAME, "").orEmpty()
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
        val primary = getResolvedColor(context, "primary", android.R.attr.colorPrimary, android.R.attr.textColorPrimary, "#6750A4")
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
        val secondary = getResolvedColor(context, "secondary", com.google.android.material.R.attr.colorSecondary, android.R.attr.colorPrimary, "#625B71")
        val onSecondary = getResolvedColor(context, "onSecondary", com.google.android.material.R.attr.colorOnSecondary, com.google.android.material.R.attr.colorOnPrimary, "#FFFFFF")

        val navStateList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(primary, ColorUtils.setAlphaComponent(onSurface, 160))
        )

        val primaryStateList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(primary, ColorUtils.setAlphaComponent(onSurface, 120))
        )

        // 只给「确实没有任何背景」的容器兜底上 Surface 色，
        // 已经带了背景（图片、自定义色、shape drawable）的视图保持原样，避免吞掉用户自定义外观。
        if (rootView.background == null) {
            rootView.setBackgroundColor(surface)
        }

        traverseView(
            rootView, primary, onPrimary, primaryContainer, onPrimaryContainer,
            secondary, onSecondary, secondaryContainer, onSecondaryContainer,
            surface, onSurface, surfaceVariant, onSurfaceVariant, outline, outlineVariant,
            navStateList, primaryStateList
        )
    }

    private fun traverseView(
        v: View,
        primary: Int, onPrimary: Int, primaryContainer: Int, onPrimaryContainer: Int,
        secondary: Int, onSecondary: Int,
        secondaryContainer: Int, onSecondaryContainer: Int, surface: Int, onSurface: Int,
        surfaceVariant: Int, onSurfaceVariant: Int, outline: Int, outlineVariant: Int,
        navStateList: ColorStateList, primaryStateList: ColorStateList
    ) {
        when (v) {
            is Toolbar -> {
                // MD1 的 Toolbar 样式显式设置了 colorPrimary 背景；只有在没有背景时才兜底，
                // 否则会把旧版主题的彩色标题栏刷成纯 Surface，丢掉设计差异。
                if (v.background == null) v.setBackgroundColor(surface)
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

            // ------------------------------------------------------------------
            // 【核心修复】不再强制改写 MaterialButton 的底色。
            // backgroundTint 从 XML 样式解析后可能是 null（outlined/tonal/text 都如此），
            // 旧代码把 null 当成「填充按钮」并强制涂成 Primary，
            // 导致 Outlined / Tonal / Text 按钮以及 MD1/MD2 下的按钮外观全部错乱。
            // 现在只保证「已经明确着色」的按钮有可读的前景色。
            // ------------------------------------------------------------------
            is MaterialButton -> {
                val tint = v.backgroundTintList
                if (tint == null) {
                    // 真正的透明/文字按钮：用当前主题的 Primary 保证可见
                    v.setTextColor(primary)
                    v.iconTint = ColorStateList.valueOf(primary)
                } else {
                    // 有底色：根据底色亮度自动选择黑或白前景，任何主题下都有对比度
                    v.setTextColor(contrastForegroundFor(tint.defaultColor, primary, onPrimary, onSurface))
                    v.iconTint = ColorStateList.valueOf(v.currentTextColor)
                }
            }

            // 卡片背景交给主题属性 / 用户自定义色，这里只统一描边
            is MaterialCardView -> {
                if (v.strokeWidth > 0) v.strokeColor = outlineVariant
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
                if (v.background == null) v.setBackgroundColor(surface)
                v.setSelectedTabIndicatorColor(primary)
                v.setTabTextColors(ColorUtils.setAlphaComponent(onSurface, 150), primary)
            }

            // 开关 / 单选 / 复选 / 滑杆：交给主题属性解析（MD1/MD2/MD3 各自正确）
            is Slider -> {
                v.thumbTintList = ColorStateList.valueOf(primary)
                v.trackActiveTintList = ColorStateList.valueOf(primary)
                v.trackInactiveTintList = ColorStateList.valueOf(surfaceVariant)
            }

            is RadioButton -> {
                (v as android.widget.CompoundButton).buttonTintList = primaryStateList
            }

            is CheckBox -> {
                (v as android.widget.CompoundButton).buttonTintList = primaryStateList
            }

            is TextInputLayout -> {
                // 只在描边本来就是主题色时同步为 Primary，避免覆盖自定义描边
                v.setHintTextColor(ColorStateList.valueOf(onSurfaceVariant))
                v.defaultHintTextColor = ColorStateList.valueOf(onSurfaceVariant)
            }

            is TextView -> {
                // 只处理 Preference 行标题/摘要，其他 TextView 完全交给 XML/主题，
                // 避免把用户自定义的颜色（如日程卡片标题色）覆盖掉。
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
                    secondary, onSecondary, secondaryContainer, onSecondaryContainer,
                    surface, onSurface, surfaceVariant, onSurfaceVariant, outline, outlineVariant,
                    navStateList, primaryStateList
                )
            }
        }
    }

    /**
     * 根据按钮底色的亮度挑一个可读的前景色。
     * 这样无论用户选的是 Monet、内建粉/蓝、还是 MTB 导入的动态色，
     * 按钮文字都不会出现"深底深字/浅底浅字"的情况。
     */
    private fun contrastForegroundFor(background: Int, primary: Int, onPrimary: Int, onSurface: Int): Int {
        if (background == Color.TRANSPARENT) return primary
        return if (ColorUtils.calculateLuminance(background) > 0.5) {
            // 浅色底 → 用深色前景
            if (ColorUtils.calculateLuminance(onSurface) < 0.5) onSurface else Color.BLACK
        } else {
            // 深色底 → 用浅色前景
            if (ColorUtils.calculateLuminance(onPrimary) > 0.5) onPrimary else Color.WHITE
        }
    }
}
