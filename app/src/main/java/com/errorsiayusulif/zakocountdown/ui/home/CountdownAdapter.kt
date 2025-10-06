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
                ItemCountdownCardHeroBinding.inflate(inflater, parent, false)
            )
            VIEW_TYPE_DETAILED -> CountdownViewHolder.DetailedViewHolder(
                ItemCountdownCardDetailedBinding.inflate(inflater, parent, false)
            )
            VIEW_TYPE_FULL -> CountdownViewHolder.FullViewHolder(
                ItemCountdownCardFullBinding.inflate(inflater, parent, false)
            )
            else -> CountdownViewHolder.SimpleViewHolder(
                ItemCountdownCardSimpleBinding.inflate(inflater, parent, false)
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

    sealed class CountdownViewHolder(binding: ViewBinding) : RecyclerView.ViewHolder(binding.root) {
        abstract fun bind(event: CountdownEvent)
        abstract fun stopTimer()

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

        protected fun applyTitleColor(textView: android.widget.TextView, event: CountdownEvent) {
            if (event.isImportant) {
                textView.setTextColor(itemView.context.getColor(R.color.m3_error))
            } else {
                val attrs = intArrayOf(android.R.attr.textColorPrimary)
                val typedArray = itemView.context.obtainStyledAttributes(attrs)
                val defaultColor = typedArray.getColor(0, 0)
                typedArray.recycle()
                textView.setTextColor(defaultColor)
            }
        }

        class SimpleViewHolder(private val binding: ItemCountdownCardSimpleBinding) : CountdownViewHolder(binding) {
            override fun stopTimer() {}
            override fun bind(event: CountdownEvent) {
                applyCardColor(event)
                applyCardAlpha(event)
                applyTitleColor(binding.textViewTitle, event)
                binding.textViewTitle.text = event.title
                val diff = TimeCalculator.calculateDifference(event.targetDate)
                binding.textViewLabel.text = if (diff.isPast) "已过" else "还有"
                binding.textViewDays.text = diff.totalDays.toString()
            }
        }

        class DetailedViewHolder(private val binding: ItemCountdownCardDetailedBinding) : CountdownViewHolder(binding) {
            private var timerHandler: Handler? = null
            private var timerRunnable: Runnable? = null
            override fun stopTimer() {
                timerRunnable?.let { timerHandler?.removeCallbacks(it) }
                timerHandler = null
                timerRunnable = null
            }
            override fun bind(event: CountdownEvent) {
                applyCardColor(event)
                applyCardAlpha(event)
                applyTitleColor(binding.textViewTitle, event)
                binding.textViewTitle.text = event.title
                startTimer(event.targetDate)
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

        class FullViewHolder(private val binding: ItemCountdownCardFullBinding) : CountdownViewHolder(binding) {
            private var timerHandler: Handler? = null
            private var timerRunnable: Runnable? = null
            override fun stopTimer() {
                timerRunnable?.let { timerHandler?.removeCallbacks(it) }
                timerHandler = null
                timerRunnable = null
            }
            override fun bind(event: CountdownEvent) {
                applyCardColor(event)
                applyCardAlpha(event)
                applyTitleColor(binding.textViewTitle, event)
                binding.textViewTitle.text = event.title
                startTimer(event.targetDate)
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

        class HeroViewHolder(private val binding: ItemCountdownCardHeroBinding) : CountdownViewHolder(binding) {
            override fun stopTimer() {}
            override fun bind(event: CountdownEvent) {
                applyCardAlpha(event)
                applyTitleColor(binding.heroTitle, event)

                if (event.backgroundUri != null) {
                    binding.heroBackgroundImage.load(Uri.parse(event.backgroundUri))
                    binding.heroScrim.visibility = View.VISIBLE
                    val colorPrimary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
                    val scrimColor = ColorUtils.setAlphaComponent(colorPrimary, 102)
                    binding.heroScrim.setBackgroundColor(scrimColor)
                } else {
                    binding.heroBackgroundImage.setImageDrawable(null)
                    binding.heroScrim.visibility = View.GONE
                    applyCardColor(event)
                }

                val diff = TimeCalculator.calculateDifference(event.targetDate)
                binding.heroTitle.text = "距离 ${event.title}"
                binding.heroDays.text = diff.totalDays.toString()
                binding.heroLabelPrefix.text = if (diff.isPast) "已过" else "还有"
            }
        }
    }

    class EventsComparator : DiffUtil.ItemCallback<CountdownEvent>() {
        override fun areItemsTheSame(oldItem: CountdownEvent, newItem: CountdownEvent): Boolean = oldItem.id == newItem.id
        // --- 【核心修复】比较整个对象，以检测alpha、颜色、模式等所有变化 ---
        override fun areContentsTheSame(oldItem: CountdownEvent, newItem: CountdownEvent): Boolean = oldItem == newItem
    }
}