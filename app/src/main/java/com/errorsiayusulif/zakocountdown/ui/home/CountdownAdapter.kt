// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/home/CountdownAdapter.kt
package com.errorsiayusulif.zakocountdown.ui.home

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import com.errorsiayusulif.zakocountdown.data.AgendaBook
import com.errorsiayusulif.zakocountdown.data.CountdownEvent
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.databinding.*
import com.errorsiayusulif.zakocountdown.utils.TimeCalculator
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import java.util.*

class CountdownAdapter(
    private val onItemClicked: (CountdownEvent) -> Unit,
    private val onLongItemClicked: (CountdownEvent, View) -> Boolean
) : ListAdapter<CountdownEvent, CountdownAdapter.CountdownViewHolder>(EventsComparator()) {

    var agendaBookMap: Map<Long, AgendaBook> = emptyMap()
    private var isCompactMode: Boolean = false
    var preferenceManager: PreferenceManager? = null

    // --- 方法名必须是这个：setAgendaBooks ---
    fun setAgendaBooks(books: List<AgendaBook>, prefs: PreferenceManager) {
        this.agendaBookMap = books.associateBy { it.id }
        this.preferenceManager = prefs
        notifyDataSetChanged()
    }

    // 为了防止部分旧代码报错，保留一个空实现
    fun setBookColorMap(map: Map<Long, String>) {}

    fun setCompactMode(isCompact: Boolean) {
        this.isCompactMode = isCompact
        notifyDataSetChanged()
    }

    companion object {
        private const val VIEW_TYPE_HERO = 0
        private const val VIEW_TYPE_SIMPLE = 1
        private const val VIEW_TYPE_DETAILED = 2
        private const val VIEW_TYPE_FULL = 3
        private const val VIEW_TYPE_COMPACT = 4
    }

    override fun getItemViewType(position: Int): Int {
        if (isCompactMode) return VIEW_TYPE_COMPACT
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
            VIEW_TYPE_COMPACT -> CountdownViewHolder.CompactViewHolder(
                ItemCountdownCardCompactBinding.inflate(inflater, parent, false), this
            )
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

    sealed class CountdownViewHolder(
        binding: ViewBinding,
        protected val adapter: CountdownAdapter
    ) : RecyclerView.ViewHolder(binding.root) {

        abstract fun bind(event: CountdownEvent)
        open fun stopTimer() {}

        protected fun getBookColor(bookId: Long?): Int? {
            if (bookId == null) return null
            val hex = adapter.agendaBookMap[bookId]?.colorHex
            return try { Color.parseColor(hex) } catch (e: Exception) { null }
        }

        protected fun getBookName(bookId: Long?): String? {
            return adapter.agendaBookMap[bookId]?.name
        }

        protected fun applyTitleColor(textView: TextView, event: CountdownEvent) {
            if (event.isImportant) {
                textView.setTextColor(itemView.context.getColor(R.color.m3_error))
                return
            }
            val color = getBookColor(event.bookId)
            if (color != null) {
                textView.setTextColor(color)
            } else {
                val attrs = intArrayOf(android.R.attr.textColorPrimary)
                val typedArray = itemView.context.obtainStyledAttributes(attrs)
                textView.setTextColor(typedArray.getColor(0, Color.BLACK))
                typedArray.recycle()
            }
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

        // --- 紧凑模式 ViewHolder ---
        class CompactViewHolder(private val binding: ItemCountdownCardCompactBinding, adapter: CountdownAdapter)
            : CountdownViewHolder(binding, adapter) {

            // --- 修复：添加缺失的变量声明 ---
            private var timerHandler: Handler? = null
            private var timerRunnable: Runnable? = null

            override fun stopTimer() {
                timerRunnable?.let { timerHandler?.removeCallbacks(it) }
                timerHandler = null
                timerRunnable = null
            }

            override fun bind(event: CountdownEvent) {
                stopTimer()

                binding.tvCompactTitle.text = event.title

                val prefs = adapter.preferenceManager ?: PreferenceManager(itemView.context)
                val isImportant = event.isImportant
                val isCustomBook = event.bookId != null && event.bookId!! > 0L

                var coverUri: String? = null
                var bookColorInt: Int = Color.DKGRAY
                var bookNameText = "未知"
                var finalAlpha = 1.0f

                if (isCustomBook) {
                    val book = adapter.agendaBookMap[event.bookId]
                    coverUri = book?.coverImageUri
                    bookColorInt = getBookColor(event.bookId) ?: Color.DKGRAY
                    bookNameText = book?.name ?: "自定义日程"
                    finalAlpha = book?.cardAlpha ?: 1.0f
                } else if (isImportant) {
                    coverUri = prefs.getDefaultBookCover(isImportantBook = true)
                    bookColorInt = itemView.context.getColor(R.color.m3_error)
                    bookNameText = "重点日程"
                    finalAlpha = prefs.getDefaultBookAlpha(isImportantBook = true)
                } else {
                    coverUri = prefs.getDefaultBookCover(isImportantBook = false)
                    bookColorInt = Color.parseColor("#212121")
                    bookNameText = "默认日程"
                    finalAlpha = prefs.getDefaultBookAlpha(isImportantBook = false)
                }

                binding.vCompactScrim.visibility = View.VISIBLE
                binding.vCompactScrim.alpha = finalAlpha

                val targetBgUri = event.backgroundUri ?: coverUri

                if (targetBgUri != null) {
                    binding.ivCompactCover.load(Uri.parse(targetBgUri)) { crossfade(true) }
                    binding.vCompactScrim.setBackgroundColor(Color.parseColor("#1A000000"))
                } else {
                    binding.ivCompactCover.setImageDrawable(null)
                    binding.ivCompactCover.setBackgroundColor(bookColorInt)
                    binding.vCompactScrim.visibility = View.GONE
                    binding.ivCompactCover.alpha = finalAlpha
                }

                if (isImportant) {
                    binding.tvImportantTag.visibility = View.VISIBLE
                    val shape = GradientDrawable()
                    shape.shape = GradientDrawable.RECTANGLE
                    shape.cornerRadius = 8f
                    shape.setColor(itemView.context.getColor(R.color.m3_error))
                    binding.tvImportantTag.background = shape
                } else {
                    binding.tvImportantTag.visibility = View.GONE
                }

                if (isCustomBook) {
                    binding.tvAgendaTag.visibility = View.VISIBLE
                    binding.tvAgendaTag.text = bookNameText
                    val tagShape = GradientDrawable()
                    tagShape.shape = GradientDrawable.RECTANGLE
                    tagShape.cornerRadius = 8f
                    tagShape.setColor(bookColorInt)
                    binding.tvAgendaTag.background = tagShape
                    val isDark = ColorUtils.calculateLuminance(bookColorInt) < 0.5
                    binding.tvAgendaTag.setTextColor(if (isDark) Color.WHITE else Color.BLACK)
                } else {
                    binding.tvAgendaTag.visibility = View.GONE
                }

                if (!isImportant && !isCustomBook) {
                    binding.llTagsContainer.visibility = View.GONE
                } else {
                    binding.llTagsContainer.visibility = View.VISIBLE
                }

                startCompactTimer(event)

                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                binding.tvCompactDate.text = sdf.format(event.targetDate)

                applyCardAlpha(event)
            }

            private fun startCompactTimer(event: CountdownEvent) {
                stopTimer() // 确保之前的被取消
                timerHandler = Handler(Looper.getMainLooper())
                timerRunnable = object : Runnable {
                    override fun run() {
                        val diff = TimeCalculator.calculateDifference(event.targetDate)
                        val status = if (diff.isPast) "已过" else "还有"

                        val timeText = when (event.displayMode) {
                            CountdownEvent.DISPLAY_MODE_DETAILED -> {
                                "${diff.totalDays}天 ${String.format("%02d", diff.hours)}时 ${String.format("%02d", diff.minutes)}分"
                            }
                            CountdownEvent.DISPLAY_MODE_FULL -> {
                                val sb = StringBuilder()
                                if (diff.years > 0) sb.append("${diff.years}年")
                                if (diff.months > 0) sb.append("${diff.months}月")
                                sb.append("${diff.daysInWeek}天")
                                if (sb.isEmpty()) "0天" else sb.toString()
                            }
                            else -> {
                                "${diff.totalDays} 天"
                            }
                        }

                        binding.tvCompactTimeMain.text = "$status $timeText"

                        if (event.isImportant) {
                            binding.tvCompactTimeMain.setTextColor(itemView.context.getColor(R.color.m3_error))
                        } else {
                            val colorPrimary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
                            binding.tvCompactTimeMain.setTextColor(colorPrimary)
                        }

                        timerHandler?.postDelayed(this, 1000)
                    }
                }
                timerHandler?.post(timerRunnable!!)
            }
        }

        class SimpleViewHolder(private val binding: ItemCountdownCardSimpleBinding, adapter: CountdownAdapter)
            : CountdownViewHolder(binding, adapter) {
            override fun bind(event: CountdownEvent) {
                applyCardColor(event)
                applyTitleColor(binding.textViewTitle, event)
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
                applyTitleColor(binding.textViewTitle, event)
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
                applyTitleColor(binding.textViewTitle, event)
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
            override fun bind(event: CountdownEvent) {
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

                    applyTitleColor(binding.heroTitle, event)

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