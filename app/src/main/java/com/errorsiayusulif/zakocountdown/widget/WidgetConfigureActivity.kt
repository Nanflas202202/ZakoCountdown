// file: app/src/main/java/com/errorsiayusulif/zakocountdown/widget/WidgetConfigureActivity.kt
package com.errorsiayusulif.zakocountdown.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.data.CountdownEvent
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.databinding.ActivityWidgetConfigureBinding
import com.errorsiayusulif.zakocountdown.databinding.ItemWidgetConfigEventBinding
import kotlinx.coroutines.launch

class WidgetConfigureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetConfigureBinding
    private lateinit var preferenceManager: PreferenceManager
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        // TODO: 保存图片 URI
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(Activity.RESULT_CANCELED)

        binding = ActivityWidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)

        val extras = intent?.extras
        appWidgetId = extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val preselectedEventId = extras?.getLong("preselected_event_id", -1L) ?: -1L
        val isShortcutCreation = extras?.getBoolean("is_shortcut_creation", false) ?: false

        if (isShortcutCreation && preselectedEventId != -1L) {
            lifecycleScope.launch {
                val event = (application as ZakoCountdownApplication).repository.getEventById(preselectedEventId)
                if (event != null) {
                    completeConfiguration(event, true)
                } else {
                    finish()
                }
            }
        } else if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setupRecyclerView()
            setupListeners()
        } else {
            finish()
        }
    }

    private fun setupListeners() {
        binding.widgetBgTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            binding.widgetSelectImageButton.visibility = if (checkedId == R.id.widget_bg_image) View.VISIBLE else View.GONE
        }
        binding.widgetSelectImageButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun setupRecyclerView() {
        val repo = (application as ZakoCountdownApplication).repository
        binding.recyclerViewWidgetConfig.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            val allEvents = repo.getAllEventsSuspend()
            binding.recyclerViewWidgetConfig.adapter = ConfigAdapter(allEvents) { selectedEvent ->
                completeConfiguration(selectedEvent, false)
            }
        }
    }

    // --- 【核心修复】为方法添加 isShortcut 参数 ---
    private fun completeConfiguration(event: CountdownEvent, isShortcut: Boolean) {
        // 在快捷方式创建的流程中，我们此时还没有一个有效的 appWidgetId。
        // 但我们仍然可以先准备好返回的 Intent。
        // 系统在创建微件后，会用一个真实的 appWidgetId 再次调用这个 Intent。
        // 这个逻辑比较复杂，我们先简化，确保正常添加流程能跑通。

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !isShortcut) {
            // 如果在正常流程中都拿不到ID，就直接退出
            finish()
            return
        }

        val bgType = when (binding.widgetBgTypeGroup.checkedRadioButtonId) {
            R.id.widget_bg_solid -> "solid"
            R.id.widget_bg_image -> "image"
            else -> "transparent"
        }

        preferenceManager.saveWidgetBackground(appWidgetId, bgType)
        preferenceManager.saveWidgetEventId(appWidgetId, event.id)

        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(Activity.RESULT_OK, resultValue)
        finish()

        if (!isShortcut) {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            UpdateWidgetWorker.updateWidget(this, appWidgetManager, appWidgetId)
        }
    }
}
class ConfigAdapter(
    private val events: List<CountdownEvent>,
    private val onItemClick: (CountdownEvent) -> Unit
) : RecyclerView.Adapter<ConfigAdapter.ViewHolder>() {
    class ViewHolder(val binding: ItemWidgetConfigEventBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWidgetConfigEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = events[position]
        holder.binding.eventTitle.text = event.title
        holder.itemView.setOnClickListener { onItemClick(event) }
    }
    override fun getItemCount() = events.size
}
