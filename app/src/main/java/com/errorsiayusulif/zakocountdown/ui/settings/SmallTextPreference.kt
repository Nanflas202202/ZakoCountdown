// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/SmallTextPreference.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.errorsiayusulif.zakocountdown.R

/**
 * 「小字说明」偏好项。
 *
 * ## 为什么需要自定义一个类，而不是只写个 layout
 *
 * 光用 `app:layout="@layout/preference_small_text"` 是**不够**的，会被别处覆盖：
 *
 * 1. [Preference] 默认只会去找 `android.R.id.title` / `android.R.id.summary`，
 *    自定义布局想被正常绑定就得沿用这两个 ID。
 * 2. 但 [com.errorsiayusulif.zakocountdown.utils.MtbThemeEngine] 恰好按
 *    **这两个 ID** 做统一染色，并把标题强制成 `onSurface` —— 字号虽然还是布局里的，
 *    颜色却被拉回「正文」级别，小字的视觉层级就没了。
 *
 * 所以这里改用自有 ID（`pref_small_title` / `pref_small_summary`），
 * 自己完成绑定：既拿得到 Preference 的数据，又不会被那套按 ID 匹配的染色规则命中。
 * 颜色与字号全部由布局里的主题属性决定，三套主题、深浅色都自动跟随。
 */
class SmallTextPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle
) : Preference(context, attrs, defStyleAttr) {

    init {
        // 说明文字不可点击，避免出现无意义的水波纹反馈
        isSelectable = false
        layoutResource = R.layout.preference_small_text
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        holder.findViewById(R.id.pref_small_title)?.let { view ->
            (view as? android.widget.TextView)?.text = title
        }
        holder.findViewById(R.id.pref_small_summary)?.let { view ->
            val summaryText = summary
            (view as? android.widget.TextView)?.apply {
                text = summaryText
                visibility = if (summaryText.isNullOrBlank()) android.view.View.GONE
                else android.view.View.VISIBLE
            }
        }
    }
}
