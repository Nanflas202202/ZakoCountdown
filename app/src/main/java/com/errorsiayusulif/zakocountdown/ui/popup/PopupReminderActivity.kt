// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/popup/PopupReminderActivity.kt
package com.errorsiayusulif.zakocountdown.ui.popup

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.databinding.ActivityPopupReminderBinding
import com.errorsiayusulif.zakocountdown.utils.TimeCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PopupReminderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPopupReminderBinding

    companion object {
        const val EXTRA_EVENT_IDS = "extra_event_ids"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPopupReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- 【核心修复】接收 LongArray 并查询数据库 ---
        val eventIds = intent.getLongArrayExtra(EXTRA_EVENT_IDS)

        Log.d("PopupActivity", "Received event IDs: ${eventIds?.joinToString()}")

        if (eventIds == null || eventIds.isEmpty()) {
            binding.popupDetailsText.text = "没有找到日程信息"
        } else {
            binding.popupDetailsText.text = "正在加载日程..."
            lifecycleScope.launch {
                val repository = (application as ZakoCountdownApplication).repository
                val detailsString = withContext(Dispatchers.IO) {
                    val events = repository.getEventsByIds(eventIds.toList())
                    Log.d("PopupActivity", "Found ${events.size} events from database.")

                    events.take(3).joinToString("\n") { event ->
                        val diff = TimeCalculator.calculateDifference(event.targetDate)
                        if (diff.isPast) "「${event.title}」已过去 ${diff.totalDays} 天"
                        else "距离「${event.title}」还有 ${diff.totalDays} 天"
                    }
                }

                binding.popupDetailsText.text = if (detailsString.isBlank()) "没有需要提醒的日程" else detailsString
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 5000) // 延长显示时间到5秒
    }
}