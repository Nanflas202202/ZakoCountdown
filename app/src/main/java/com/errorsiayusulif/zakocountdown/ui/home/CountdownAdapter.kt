// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/home/CountdownAdapter.kt
package com.errorsiayusulif.zakocountdown.ui.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import coil.load
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.CountdownEvent
import com.errorsiayusulif.zakocountdown.databinding.ItemCountdownCardDetailedBinding
import com.errorsiayusulif.zakocountdown.databinding.ItemCountdownCardFullBinding
import com.errorsiayusulif.zakocountdown.databinding.ItemCountdownCardHeroBinding
import com.errorsiayusulif.zakocountdown.databinding.ItemCountdownCardSimpleBinding
import com.errorsiayusulif.zakocountdown.utils.TimeCalculator
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import java.util.*
import java.util.concurrent.TimeUnit

class CountdownAdapter(
    private val onItemClicked: (CountdownEvent) -> Unit,
    private val onLongItemClicked: (CountdownEvent, View) -> Boolean
) : ListAdapter<CountdownEvent, CountdownAdapter.CountdownViewHolder>(EventsComparator()) {

    // --- 新增：存储日程本 ID 到 颜色 Hex 的映射 ---
    private var bookColorMap: Map<Long, String> = emptyMap()

    fun setBookColorMap(map: Map<Long, String>) {
        this.bookColorMap = map
        notifyDataSetChanged() // 颜色变化需要刷新列表
    }

    companion object {
        private const val VIEW_TYPE_HERO = 0
        private const val VIEW_TYPE_SIMPLE = 1
        private const val VIEW_TYPE_DETAILED = 2
        private const val VIEW_TYPE_FULL = 3
    }

    override fun getItemViewType(position: Int): Int {
        val event = getItem(position)
        if (event.isPinned) return VIEW_TYPE_HERO
        return when (event.displayMode) {
            CountdownEvent.DISPLAY_MODE_DETAILED -> VIEW_TYPE_DETAILED
            CountdownEvent.DISPLAY_MODE_FULL -> VIEW_TYPE_FULL
            else -> VIEW_TYPE_SIMPLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CountdownViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HERO -> CountdownViewHolder.HeroViewHolder(
                ItemCountdownCardHeroBinding.inflate(inflater, parent, false), this
            )
            VIEW_TYPE_DETAILED -> CountdownViewHolder.DetailedViewHolder(
                ItemCountdownCardDetailedBinding.inflate(inflater, parent, false), this
            )
            VIEW_TYPE_FULL -> CountdownViewHolder.FullViewHolder(
                ItemCountdownCardFullBinding.inflate(inflater, parent, false), this
            )
            else -> CountdownViewHolder.SimpleViewHolder(
                ItemCountdownCardSimpleBinding.inflate(inflater, parent, false), this
            )
        }
    }

    override fun onBindViewHolder(holder: CountdownViewHolder, position: Int) {
        val current = getItem(position)
        holder.itemView.setOnClickListener { onItemClicked(current) }
        holder.itemView.setOnLongClickListener { view -> onLongItemClicked(current, view) }
        holder.bind(current)
    }

    override fun onViewRecycled(holder: CountdownViewHolder) {
        super.onViewRecycled(holder)
        holder.stopTimer()
    }

    // --- 修改：ViewHolder 接收 adapter 实例以访问 bookColorMap ---
    sealed class CountdownViewHolder(
        binding: ViewBinding,
        private val adapter: CountdownAdapter
    ) : RecyclerView.ViewHolder(binding.root) {

        abstract fun bind(event: CountdownEvent)
        abstract fun stopTimer()

        // --- 核心逻辑：应用标题颜色 ---
        protected fun applyTitleColor(textView: TextView, event: CountdownEvent) {
            // 优先级 1: 重点日程 (红色)
            if (event.isImportant) {
                textView.setTextColor(itemView.context.getColor(R.color.m3_error)) // 使用错误色(红)
                return
            }

            // 优先级 2: 自定义日程本颜色
            val bookId = event.bookId
            if (bookId != null) {
                val colorHex = adapter.bookColorMap[bookId]
                if (colorHex != null) {
                    try {
                        textView.setTextColor(Color.parseColor(colorHex))
                        return
                    } catch (e: Exception) {
                        // 颜色解析失败，回退
                    }
                }
            }

            // 优先级 3: 默认 (跟随主题的主要文本色)
            // 在 Hero 模式下有背景图时可能是白色，这里只处理普通模式
            // 获取当前主题的 textColorPrimary
            val attrs = intArrayOf(android.R.attr.textColorPrimary)
            val typedArray = itemView.context.obtainStyledAttributes(attrs)
            val defaultColor = typedArray.getColor(0, Color.BLACK)
            typedArray.recycle()
            textView.setTextColor(defaultColor)
        }

        protected fun applyCardColor(event: CountdownEvent) {
            val card = itemView as MaterialCardView
            if (event.colorHex != null) {
                card.setCardBackgroundColor(Color.parseColor(event.colorHex))
            } else {
                val attrs = intArrayOf(com.google.android.material.R.attr.colorSurface)
                val typedArray = card.context.obtainStyledAttributes(attrs)
                val defaultColor = typedArray.getColor(0, Color.WHITE)
                typedArray.recycle()
                card.setCardBackgroundColor(defaultColor)
            }
        }

        protected fun applyCardAlpha(event: CountdownEvent) {
            itemView.alpha = event.cardAlpha ?: 1.0f
        }

        class SimpleViewHolder(private val binding: ItemCountdownCardSimpleBinding, adapter: CountdownAdapter)
            : CountdownViewHolder(binding, adapter) {
            override fun stopTimer() {}
            override fun bind(event: CountdownEvent) {
                applyCardColor(event)
                applyTitleColor(binding.textViewTitle, event) // 应用颜色
                binding.textViewTitle.text = event.title
                val diff = TimeCalculator.calculateDifference(event.targetDate)
                binding.textViewLabel.text = if (diff.isPast) "已过" else "还有"
                binding.textViewDays.text = diff.totalDays.toString()
                applyCardAlpha(event)
            }
        }

        class DetailedViewHolder(private val binding: ItemCountdownCardDetailedBinding, adapter: CountdownAdapter)
            : CountdownViewHolder(binding, adapter) {
            private var timerHandler: Handler? = null
            private var timerRunnable: Runnable? = null
            override fun stopTimer() {
                timerRunnable?.let { timerHandler?.removeCallbacks(it) }
                timerHandler = null
                timerRunnable = null
            }
            override fun bind(event: CountdownEvent) {
                applyCardColor(event)
                applyTitleColor(binding.textViewTitle, event) // 应用颜色
                binding.textViewTitle.text = event.title
                startTimer(event.targetDate)
                applyCardAlpha(event)
            }
            private fun startTimer(targetDate: Date) {
                stopTimer()
                timerHandler = Handler(Looper.getMainLooper())
                timerRunnable = object : Runnable {
                    override fun run() {
                        val diff = TimeCalculator.calculateDifference(targetDate)
                        binding.textViewTimeLabel.text = if (diff.isPast) "已过" else "还有"
                        binding.timeDaysValue.text = String.format("%d", diff.totalDays)
                        binding.timeHoursValue.text = String.format("%02d", diff.hours)
                        binding.timeMinutesValue.text = String.format("%02d", diff.minutes)
                        binding.timeSecondsValue.text = String.format("%02d", diff.seconds)
                        timerHandler?.postDelayed(this, 1000)
                    }
                }
                timerHandler?.post(timerRunnable!!)
            }
        }

        class FullViewHolder(private val binding: ItemCountdownCardFullBinding, adapter: CountdownAdapter)
            : CountdownViewHolder(binding, adapter) {
            private var timerHandler: Handler? = null
            private var timerRunnable: Runnable? = null
            override fun stopTimer() {
                timerRunnable?.let { timerHandler?.removeCallbacks(it) }
                timerHandler = null
                timerRunnable = null
            }
            override fun bind(event: CountdownEvent) {
                applyCardColor(event)
                applyTitleColor(binding.textViewTitle, event) // 应用颜色
                binding.textViewTitle.text = event.title
                startTimer(event.targetDate)
                applyCardAlpha(event)
            }
            private fun startTimer(targetDate: Date) {
                stopTimer()
                timerHandler = Handler(Looper.getMainLooper())
                timerRunnable = object : Runnable {
                    override fun run() {
                        val diff = TimeCalculator.calculateDifference(targetDate)
                        binding.textViewTimeLabel.text = if (diff.isPast) "已过" else "还有"
                        binding.timeYearsValue.text = diff.years.toString()
                        binding.timeMonthsValue.text = diff.months.toString()
                        binding.timeWeeksValue.text = diff.weeks.toString()
                        binding.timeDaysInWeekValue.text = diff.daysInWeek.toString()
                        binding.timeMinutesValueFull.text = String.format("%02d", diff.minutes)
                        binding.timeSecondsValueFull.text = String.format("%02d", diff.seconds)
                        timerHandler?.postDelayed(this, 1000)
                    }
                }
                timerHandler?.post(timerRunnable!!)
            }
        }

        class HeroViewHolder(private val binding: ItemCountdownCardHeroBinding, adapter: CountdownAdapter)
            : CountdownViewHolder(binding, adapter) {
            override fun stopTimer() {}
            override fun bind(event: CountdownEvent) {

                // Hero 模式特殊处理：如果有背景图，文字强制白色；没有背景图，应用逻辑颜色
                if (event.backgroundUri != null) {
                    binding.heroBackgroundImage.load(Uri.parse(event.backgroundUri))
                    binding.heroScrim.visibility = View.VISIBLE
                    val colorPrimary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
                    val scrimColor = ColorUtils.setAlphaComponent(colorPrimary, 102)
                    binding.heroScrim.setBackgroundColor(scrimColor)

                    binding.heroTitle.setTextColor(Color.WHITE)
                    binding.heroDays.setTextColor(Color.WHITE)
                    binding.heroLabelPrefix.setTextColor(Color.WHITE)
                    binding.heroLabelSuffix.setTextColor(Color.WHITE)
                } else {
                    binding.heroBackgroundImage.setImageDrawable(null)
                    binding.heroScrim.visibility = View.GONE
                    applyCardColor(event)

                    applyTitleColor(binding.heroTitle, event) // 应用颜色

                    // 数字颜色通常跟随主题 Primary
                    val colorPrimary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
                    binding.heroDays.setTextColor(colorPrimary)

                    val colorOnSurface = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
                    binding.heroLabelPrefix.setTextColor(colorOnSurface)
                    binding.heroLabelSuffix.setTextColor(colorOnSurface)
                }

                val diff = TimeCalculator.calculateDifference(event.targetDate)
                binding.heroTitle.text = "距离 ${event.title}"
                binding.heroDays.text = diff.totalDays.toString()
                binding.heroLabelPrefix.text = if (diff.isPast) "已过" else "还有"
                applyCardAlpha(event)
            }
        }
    }

    class EventsComparator : DiffUtil.ItemCallback<CountdownEvent>() {
        override fun areItemsTheSame(oldItem: CountdownEvent, newItem: CountdownEvent): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: CountdownEvent, newItem: CountdownEvent): Boolean = oldItem == newItem
    }
}