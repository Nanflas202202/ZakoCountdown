// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/ZakoFloatingNavBehavior.kt
package com.errorsiayusulif.zakocountdown.utils

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.ViewCompat
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * 「自动隐藏导航栏」的滚动行为。
 *
 * 效果：列表**向下滑**时导航栏整体沉到屏幕外，**向上滑**时再滑回来。
 *
 * 注意导航栏**始终贴在屏幕底边**（不是悬浮药丸），这里只负责它的上下位移 ——
 * 位置形态由 MainActivity.applyBottomBarAppearance() 决定。
 *
 * 关闭「自动隐藏」时本行为完全不介入，导航栏固定不动：
 * 一条贴边的栏突然滑走，用户会以为是渲染 bug。
 *
 * 开关状态**直接读 SharedPreferences**，不使用 elevation 之类的间接信号 ——
 * 早期版本用「elevation > 5f」当代理，结果别处改了投影值开关就失灵
 * （实际表现就是「无法关闭自动隐藏」）。现在只认偏好值本身。
 *
 * 为什么不用 Material 的 HideBottomViewOnScrollBehavior：
 *   它是包内可见（package-private），外部无法继承，硬用只能反射，跨版本易碎。
 *   这里自己实现，只依赖 `isScrollable()` 这个官方约定 ——
 *   NavigationBarView 已实现它并返回 true，所以 CoordinatorLayout 会正常派发嵌套滚动。
 *
 * 关于下面那批 NestedScrollingChild3 空实现：
 *   CoordinatorLayout 是按「child 自己的 behavior 是不是 NestedScrollingChild3」
 *   来决定要不要把滚动回调交给同名 Behavior 的。BottomNavigationView 自身的
 *   behavior 在运行时就是本类，所以这些方法必须存在以满足接口检查；
 *   真正的滚动事件来自 target（RecyclerView），不需要我们中转，
 *   因此保持无害的空实现（**不能**转发给自身，否则无限递归）。
 */
class ZakoFloatingNavBehavior @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : CoordinatorLayout.Behavior<BottomNavigationView>(context, attrs),
    NestedScrollingChild3 {

    /**
     * 自己存一份 Context。
     * CoordinatorLayout.Behavior 只把 context 交给父类构造，并不暴露成可用属性，
     * 构造参数出了 init 块就不在了，所以这里显式持有。
     */
    private val behaviorContext: Context = context

    private companion object {
        const val HIDE_ANIM_DURATION = 220L
        const val SHOW_ANIM_DURATION = 200L

        /** 向下滑多少像素才藏起来。 */
        const val HIDE_THRESHOLD_PX = 10

        /** 向上滑多少像素才叫回来。 */
        const val SHOW_THRESHOLD_PX = 6
    }

    private var hidden = false

    /** 滑出屏幕需要移动的距离 = 自身高度 + 底部外边距。 */
    private var hiddenOffset = 0

    private var animator: ValueAnimator? = null

    /**
     * 是否开启自动隐藏 —— 唯一判据，**直接读偏好值**。
     *
     * 早期版本用「elevation > 5f」当代理信号，只要别处改了投影值开关就会失灵
     * （实际就踩到了：关闭开关后行为仍按「已开启」处理）。
     * 现在只认 SharedPreferences 里的值，不给间接信号留余地。
     */
    private fun isEnabled(): Boolean = PreferenceManager(behaviorContext).isAutoHideNavBar()

    override fun onLayoutChild(
        parent: CoordinatorLayout,
        child: BottomNavigationView,
        layoutDirection: Int
    ): Boolean {
        val handled = super.onLayoutChild(parent, child, layoutDirection)
        // 布局完成后 child.height 才有效
        val lp = child.layoutParams as CoordinatorLayout.LayoutParams
        val offset = child.height + lp.bottomMargin
        hiddenOffset = if (offset > 0) offset else child.height

        if (isEnabled()) {
            // 复位到与当前状态一致的位置，避免 View 复用后残留位移
            child.translationY = if (hidden) hiddenOffset.toFloat() else 0f
        } else {
            // 关掉自动隐藏：必须回到「显示」位置，绝不残留位移
            reset(child)
        }
        return handled
    }

    // ========================================================================
    // 嵌套滚动：只看方向，不消费滚动距离
    // ========================================================================

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: BottomNavigationView,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
        return isEnabled() && (axes and ViewCompat.SCROLL_AXIS_VERTICAL) != 0
    }

    override fun onNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: BottomNavigationView,
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {
        if (!isEnabled()) return
        when {
            // dyConsumed > 0：内容向上走 = 用户在下滑列表 → 把导航栏藏起来
            dyConsumed > HIDE_THRESHOLD_PX && !hidden -> hide(child)
            // dyConsumed < 0：内容向下走 = 用户在上滑列表 → 把导航栏叫回来
            dyConsumed < -SHOW_THRESHOLD_PX && hidden -> show(child)
        }
    }

    // ========================================================================
    // 动画
    // ========================================================================

    private fun hide(child: View) {
        if (hidden || hiddenOffset <= 0) return
        hidden = true
        slideTo(child, hiddenOffset.toFloat(), HIDE_ANIM_DURATION)
    }

    private fun show(child: View) {
        if (!hidden) return
        hidden = false
        slideTo(child, 0f, SHOW_ANIM_DURATION)
    }

    private fun slideTo(child: View, targetY: Float, duration: Long) {
        animator?.cancel()
        val startY = child.translationY
        if (startY == targetY) return
        animator = ValueAnimator.ofFloat(startY, targetY).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { child.translationY = it.animatedValue as Float }
            start()
        }
    }

    /**
     * 立即复位到「显示」位置并清除隐藏状态。
     *
     * MainActivity 在设置变化后调用它：用户刚关掉自动隐藏时，
     * 导航栏必须马上回到屏幕内，而不是等下一次滚动才生效。
     */
    fun reset(child: View) {
        animator?.cancel()
        animator = null
        hidden = false
        child.translationY = 0f
    }

    // ========================================================================
    // NestedScrollingChild3 —— 只为满足 CoordinatorLayout 的接口检查
    // ------------------------------------------------------------------------
    // CoordinatorLayout 判断「这个 Behavior 是不是嵌套滚动子节点」时看的是接口，
    // 真正的滚动事件来自 target（RecyclerView），不需要我们中转，
    // 所以这些方法保持无害的空实现即可（不能转发给自身，否则会无限递归）。
    // ========================================================================

    override fun setNestedScrollingEnabled(enabled: Boolean) = Unit
    override fun isNestedScrollingEnabled(): Boolean = false
    override fun startNestedScroll(axes: Int): Boolean = false
    override fun startNestedScroll(axes: Int, type: Int): Boolean = false
    override fun stopNestedScroll() = Unit
    override fun stopNestedScroll(type: Int) = Unit
    override fun hasNestedScrollingParent(): Boolean = false
    override fun hasNestedScrollingParent(type: Int): Boolean = false

    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
        offsetInWindow: IntArray?
    ): Boolean = false

    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
        offsetInWindow: IntArray?, type: Int
    ): Boolean = false

    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
        offsetInWindow: IntArray?, type: Int, consumed: IntArray
    ) = Unit

    override fun dispatchNestedPreScroll(
        dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?
    ): Boolean = false

    override fun dispatchNestedPreScroll(
        dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?, type: Int
    ): Boolean = false

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean = false
    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean = false
}
