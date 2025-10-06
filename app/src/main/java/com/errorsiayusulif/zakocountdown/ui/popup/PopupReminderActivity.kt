// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/popup/PopupReminderActivity.kt

package com.errorsiayusulif.zakocountdown.ui.popup

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.errorsiayusulif.zakocountdown.databinding.ActivityPopupReminderBinding

class PopupReminderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPopupReminderBinding

    companion object {
        // 用于在Intent中传递数据的键
        const val EXTRA_EVENT_NAME = "extra_event_name"
        const val EXTRA_DAYS_LEFT = "extra_days_left"
        const val EXTRA_OTHER_EVENTS_INFO = "extra_other_events_info"
        const val EXTRA_DETAILS_STRING = "extra_details_string"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPopupReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 从启动它的Intent中获取数据
        val eventName = intent.getStringExtra(EXTRA_EVENT_NAME)
        val daysLeft = intent.getLongExtra(EXTRA_DAYS_LEFT, 0)

        // 更新UI
        binding.popupTextEventName.text = "距离 $eventName"
        val details = intent.getStringExtra(EXTRA_DETAILS_STRING)
        binding.popupTextDetails.text = details // <-- 更新UI

        // 设置一个定时器，在3秒后自动关闭这个Activity
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 3000) // 3000毫秒 = 3秒
    }
}