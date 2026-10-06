// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/share/ShareCardSize.kt
package com.errorsiayusulif.zakocountdown.ui.share

import com.errorsiayusulif.zakocountdown.R

/**
 * 分享卡片的输出尺寸。
 *
 * ## 为什么只保留两个
 *
 * 三个分享模板（`layout_share_template_*`）内部**全部使用 `px` 绝对尺寸**：
 * 根节点写死 `1080px × 1920px`，正文 `60px` 字号、底部 Logo 条 `180px`……
 * 这些数值是照「宽 1080、高 1920」这个基准手工调出来的。
 *
 * 由此带来两个约束：
 *
 *   · 改**宽**会破坏整套比例（字号、内边距、圆角相对宽度全部失准）
 *   · 改**高**只有在「比 1920 略矮」的区间里才安全 —— 内容块虽多为
 *     `wrap_content` + `autoSize`，但底部 Logo 条（`180px`）等固定高度不会缩，
 *     高度压得太狠就会挤压或裁切正文
 *
 * 所以这里只提供两个经过确认的比例：
 *
 *   · 9:16（1080×1920）—— 原始设计尺寸，必然正确
 *   · 5:4 （1080×1350）—— 比原始矮 30%，固定元素（Logo 条 180px、
 *     标题行约 70px、autoSize 下限 20~80px）占完仍有富余，不会挤压
 *
 * 其它比例（1:1、4:3、16:9）需要**另做一套按比例布局的模板**才能上，
 * 不能直接复用现有模板。要让它们回来，先补模板再往 [entries] 里加。
 */
enum class ShareCardSize(
    val key: String,
    val widthPx: Int,
    val heightPx: Int,
    val labelResId: Int
) {
    /** 竖版 9:16，社交平台通用，原设计尺寸。 */
    PORTRAIT("portrait", 1080, 1920, R.string.share_size_portrait),

    /** 5:4（1080×1350），比原尺寸矮一档，适合内容较少的卡片。 */
    FIVE_FOUR("five_four", 1080, 1350, R.string.share_size_five_four);

    /** 宽高比，供预览缩放时按比例适配横竖两个方向。 */
    val aspectRatio: Float get() = widthPx.toFloat() / heightPx.toFloat()

    /** 是否比竖版更宽（用于预览时判断该按宽度还是按高度适配）。 */
    val isLandscape: Boolean get() = heightPx < widthPx

    companion object {
        val DEFAULT = PORTRAIT

        /**
         * 按键名还原尺寸。
         *
         * 这里必须能容纳**已废弃**的键（`tall` / `square` / `classic` / `wide`）——
         * 老用户偏好里可能存着它们，落回 [DEFAULT] 即可，不能崩也不能空白。
         */
        fun fromKey(key: String?): ShareCardSize =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
