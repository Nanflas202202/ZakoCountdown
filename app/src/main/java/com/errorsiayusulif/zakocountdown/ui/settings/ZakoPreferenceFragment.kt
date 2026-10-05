// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/ZakoPreferenceFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.utils.ZakoThemeApplier

/**
 * 所有设置页的基类。
 *
 * 职责：在 **M3E** 主题下把设置列表改成 HMA-OSS 主页那种「通栏列表」风格
 * （也是 Android 15 设置页的做法）；M3 / MD2 / MD1 下完全保持原样。
 *
 * 风格要点（参考 HMA-OSS 的 app 主页与 about_content_bg）：
 *   · 整块列表**一个底色**，通栏平铺，不给每一行单独加卡片、也不做分组框
 *   · 行与行之间靠分隔线区分，但分隔线**看不见**（HMA-OSS 的 divider.xml
 *     就是个没有颜色的 1dp shape），于是整块看起来是连续的一整片
 *   · 列表底色与页面底色拉开色差，让整块内容「浮」出来
 *
 * 前两版走弯路的记录，避免以后重复：
 *   ✗ 第一版：给每一行各套一张圆角卡片 → 一堆零散小卡，不是通栏列表
 *   ✗ 第二版：用一个圆角框包住每个 PreferenceCategory → 是「分组框」，
 *             但 HMA-OSS 主页和 Android 15 设置都不是这个做法
 *   ✓ 现在：整块列表一个底色 + 通栏 + 隐藏分隔线
 *
 * ⚠️ 两个时序坑（都实际踩过，务必别踩回去）：
 *
 * 1. [PreferenceFragmentCompat.setDivider] 内部会调用
 *    `mDividerDecoration.mRecyclerView.invalidateItemDecorations()`。
 *    在 onCreateRecyclerView 里调用它时，框架的 mList 字段还没赋值
 *    （字节码里 onCreateRecyclerView 在偏移 133、mList 赋值在 156），
 *    于是必现 NullPointerException 闪退。
 *    ⇒ 分隔线相关设置必须推迟到 onViewCreated。
 *
 * 2. 同理，底层 RecyclerView 也要等 onViewCreated 之后才能通过
 *    [PreferenceFragmentCompat.getListView] 拿到。
 */
abstract class ZakoPreferenceFragment : PreferenceFragmentCompat() {

    /**
     * 是否套用 M3E 的通栏列表样式。
     * 引导流程（OOBE）里那些页面刻意保持朴素，所以在那边会覆写成 false。
     */
    protected open fun useExpressiveCards(): Boolean = true

    /** 当前是否处于 M3E 主题且本页启用了通栏列表样式。 */
    private fun useExpressiveList(): Boolean {
        if (!useExpressiveCards()) return false
        return ZakoThemeApplier.resolveEffectiveThemeKey(requireContext()) == PreferenceManager.THEME_M3E
    }

    /** 通栏列表的底色。直接取资源色值，不走主题属性，避免解析被上层主题改写。 */
    private fun listTintColor(): Int = ContextCompat.getColor(requireContext(), R.color.zako_color_surface_dim)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        if (useExpressiveList()) {
            // 页面容器也铺同一底色：RecyclerView 只覆盖它自己的可视区域，
            // 容器一起上色才能保证「整块列表」从上到下没有断层。
            view?.setBackgroundColor(listTintColor())
        }
        return view!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 到这里框架的 mList 已赋值，setDivider / getListView 才是安全的
        if (!useExpressiveList()) return

        applyDividerStyle()

        // RecyclerView 自身也铺底色：容器与列表都上色，双保险，
        // 任何一方被别处覆盖都还能看到这块底色。
        getListView()?.setBackgroundColor(listTintColor())
    }

    /**
     * 隐藏分隔线。
     *
     * setDivider(null) 彻底关掉画线；再把高度恢复成 1dp，
     * 保留行与行之间那一点呼吸感（HMA-OSS 的 divider.xml 就是这个思路：
     * 只有 size 没有 color，等于一条看不见的 1dp）。
     */
    private fun applyDividerStyle() {
        setDivider(null)
        setDividerHeight(resources.displayMetrics.density.toInt().coerceAtLeast(1))
    }
}
