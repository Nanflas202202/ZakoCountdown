// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/popup/PopupReminderActivity.kt
package com.errorsiayusulif.zakocountdown.ui.popup

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.databinding.ActivityPopupReminderBinding
import com.errorsiayusulif.zakocountdown.utils.TimeCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PopupReminderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPopupReminderBinding
    private var countDownTimer: CountDownTimer? = null

    companion object {
        const val EXTRA_EVENT_IDS = "extra_event_ids"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPopupReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val eventIds = intent.getLongArrayExtra(EXTRA_EVENT_IDS)

        // 加载内容
        if (eventIds == null || eventIds.isEmpty()) {
            binding.popupDetailsText.text = "没有找到日程信息"
        } else {
            binding.popupDetailsText.text = "正在加载..."
            lifecycleScope.launch {
                val repository = (application as ZakoCountdownApplication).repository
                val detailsString = withContext(Dispatchers.IO) {
                    val events = repository.getEventsByIds(eventIds.toList())
                    events.take(5).joinToString("\n\n") { event -> // 最多显示5个
                        val diff = TimeCalculator.calculateDifference(event.targetDate)
                        val status = if (diff.isPast) "已过" else "还有"
                        "${event.title}\n$status ${diff.totalDays} 天"
                    }
                }
                binding.popupDetailsText.text = if (detailsString.isBlank()) "没有需要提醒的日程" else detailsString
            }
        }

        // --- 倒计时与关闭逻辑 ---
        val prefs = PreferenceManager(this)
        val durationSec = prefs.getPopupDuration() // 默认5秒
        val isSkippable = prefs.isPopupSkippable()
        val skipDelay = prefs.getPopupSkipDelay()

        // 1. 设置自动关闭倒计时
        startAutoCloseTimer(durationSec)

        // 2. 设置手动关闭按钮
        if (isSkippable) {
            if (skipDelay > 0) {
                // 延迟显示关闭按钮
                binding.btnClose.visibility = View.GONE
                binding.btnClose.postDelayed({
                    binding.btnClose.visibility = View.VISIBLE
                }, skipDelay * 1000L)
            } else {
                binding.btnClose.visibility = View.VISIBLE
            }
            binding.btnClose.setOnClickListener {
                countDownTimer?.cancel()
                finish()
            }
        } else {
            binding.btnClose.visibility = View.GONE
        }
    }

    private fun startAutoCloseTimer(durationSec: Int) {
        val totalMillis = durationSec * 1000L
        countDownTimer = object : CountDownTimer(totalMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secLeft = (millisUntilFinished / 1000) + 1
                binding.tvCountdown.text = "${secLeft}s 后关闭"
            }

            override fun onFinish() {
                binding.tvCountdown.text = "正在关闭..."
                finish()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}