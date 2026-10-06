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

                // 前景色按**背景实际颜色**决定，而不是无条件用 onSurface。
                //
                // 原因：MD1 的标题栏是「始终染成 colorPrimary」的（见
                // Widget.ZakoCountdown.Toolbar.MD1），它自己在样式里写了白色文字。
                // 但这里原来无条件 setTitleTextColor(onSurface)，
                // 浅色主题下 onSurface 是深色 → 深色文字压在 colorPrimary 深色底上，
                // 标题几乎看不见。
                //
                // 现在先取出背景的实际颜色，再据此选前景：
                // 深底用白、浅底用 onSurface。彩色标题栏与 Surface 标题栏都正确。
                val backgroundIsDark = isToolbarBackgroundDark(v, surface)
                val titleColor = if (backgroundIsDark) Color.WHITE else onSurface
                val subtitleColor = if (backgroundIsDark) {
                    ColorUtils.setAlphaComponent(Color.WHITE, 200)
                } else {
                    onSurfaceVariant
                }

                v.setTitleTextColor(titleColor)
                v.setSubtitleTextColor(subtitleColor)
                v.navigationIcon?.setTint(titleColor)
                // 溢出菜单图标同属前景，一起走
                v.overflowIcon?.setTint(titleColor)
            }

            // ------------------------------------------------------------------
            // 【核心修复】FAB 不再无条件刷成 primaryContainer。
            //
            // 旧代码强行 backgroundTint = primaryContainer；而 MD1/MD2 下
            // primaryContainer 继承自 Material 默认（浅紫 #EADDFF），
            // 于是「右下角添加日程」这类按钮在两套旧主题下永远是紫色，
            // 完全无视配色文件与 XML 里的 app:backgroundTint。
            // （这个现象实际被反馈过：「按钮没有被配色文件覆盖，仍为默认紫色」。）
            //
            // 现在的规则与 MaterialButton 分支一致：
            //   · XML/样式已显式着色 → 尊重它，只保证图标有对比度；
            //   · 没着色 → 才用 primaryContainer 兜底（MD3 的默认观感不变）。
            // ------------------------------------------------------------------
            is FloatingActionButton -> {
                val tint = v.backgroundTintList
                if (tint == null) {
                    v.backgroundTintList = ColorStateList.valueOf(primaryContainer)
                    v.imageTintList = ColorStateList.valueOf(onPrimaryContainer)
                } else {
                    // 浅色容器（如 primaryContainer）→ 深色图标；深色容器（如 primary）→ 浅色图标
                    val fg = contrastForegroundFor(
                        tint.defaultColor, primary, onPrimary, onSurface
                    )
                    v.imageTintList = ColorStateList.valueOf(fg)
                }
            }

            is ExtendedFloatingActionButton -> {
                val tint = v.backgroundTintList
                if (tint == null) {
                    v.backgroundTintList = ColorStateList.valueOf(primaryContainer)
                    v.setTextColor(onPrimaryContainer)
                    v.iconTint = ColorStateList.valueOf(onPrimaryContainer)
                } else {
                    val fg = contrastForegroundFor(
                        tint.defaultColor, primary, onPrimary, onSurface
                    )
                    v.setTextColor(fg)
                    v.iconTint = ColorStateList.valueOf(fg)
                }
            }

            // ------------------------------------------------------------------
            // 【核心修复】不再强制改写 MaterialButton 的底色。
            // backgroundTint 从 XML 样式解析后可能是 null（outlined/tonal/text 都如此），
            // 旧代码把 null 当成「填充按钮」并强制涂成 Primary，
            // 导致 Outlined / Tonal / Text 按钮以及 MD1/MD2 下的按钮外观全部错乱。
            // 现在只保证「已经明确着色」的按钮有可读的前景色。
            // ------------------------------------------------------------------
            is MaterialButton -> {
                // 只做一件真正必要的事：**当前前景色在实底上读不清时才兜底**。
                //
                // 为什么不能靠猜按钮类型：
                //   · `MaterialButton` **永远**有 backgroundTintList（基类构造时就
                //     赋了 `mtrl_btn_bg_color_selector`），所以「tint == null」
                //     永远不成立，无法用来识别描边/文字按钮；
                //   · `strokeWidth` 在染色时机也不一定已就绪。
                //   两者都会让判断落到「有实底」那一支，把前景算成白字，
                //   覆盖掉样式里本来正确的 `?attr/colorPrimary`
                //   （现象：MD1/MD2 下「清除」是白字、MD3 正常 —— 实际被反馈过）。
                //
                // 现在直接用**对比度**判断，它同时满足两端：
                //   · 描边按钮：浅色表面上写 colorPrimary，对比度本来就够 → 不碰；
                //   · 填充按钮：底色被主题改成深色后，若还留着深色文字则对比不足
                //     → 才按底色亮度兜底。
                // 这样不需要识别控件类型，也就没有「猜错」的余地。
                val surfaceLum = ColorUtils.calculateLuminance(surface)
                val fgLum = ColorUtils.calculateLuminance(v.currentTextColor)
                val readable = kotlin.math.abs(fgLum - surfaceLum) > 0.3f

                if (!readable) {
                    val bg = v.backgroundTintList?.defaultColor ?: Color.TRANSPARENT
                    v.setTextColor(contrastForegroundFor(bg, primary, onPrimary, onSurface))
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

    /**
     * 判断 Toolbar 的背景是不是深色，用于决定标题/图标用白还是用 onSurface。
     *
     * MD1 的标题栏被样式写成 `?attr/colorPrimary` 实色背景，
     * 取出来的是**已解析**的颜色值，直接算亮度即可。
     *
     * 取不到颜色时（渐变、图片、或自定义 Drawable）就按「浅色」处理 ——
     * 本工程里那种情况只出现在 Surface 系的标题栏上，
     * 用 onSurface 是安全且正确的选择。
     */
    private fun isToolbarBackgroundDark(toolbar: Toolbar, fallbackSurface: Int): Boolean {
        val color = try {
            when (val bg = toolbar.background) {
                is android.graphics.drawable.ColorDrawable -> bg.color
                else -> fallbackSurface
            }
        } catch (t: Throwable) {
            fallbackSurface
        }
        return ColorUtils.calculateLuminance(color) < 0.5
    }
}
