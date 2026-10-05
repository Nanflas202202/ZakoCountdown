// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/popup/PopupReminderActivity.kt
package com.errorsiayusulif.zakocountdown.ui.popup

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.databinding.ActivityPopupReminderBinding
import com.errorsiayusulif.zakocountdown.utils.LocalizedActivity
import com.errorsiayusulif.zakocountdown.utils.TimeCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PopupReminderActivity : LocalizedActivity() {

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
            binding.popupDetailsText.text = getString(R.string.service_no_event_info)
        } else {
            binding.popupDetailsText.text = getString(R.string.common_loading)
            lifecycleScope.launch {
                val repository = (application as ZakoCountdownApplication).repository
                val detailsString = withContext(Dispatchers.IO) {
                    val events = repository.getEventsByIds(eventIds.toList())
                    events.take(5).joinToString("\n\n") { event -> // 最多显示5个
                        val diff = TimeCalculator.calculateDifference(event.targetDate)
                        val status = if (diff.isPast) getString(R.string.countdown_passed) else getString(R.string.countdown_remaining)
                        "${event.title}\n$status ${diff.totalDays}${getString(R.string.unit_day)}"
                    }
                }
                binding.popupDetailsText.text = if (detailsString.isBlank()) getString(R.string.popup_no_events) else detailsString
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
                binding.tvCountdown.text = getString(R.string.popup_close_in, secLeft)
            }

            override fun onFinish() {
                binding.tvCountdown.text = getString(R.string.popup_closing)
                finish()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}