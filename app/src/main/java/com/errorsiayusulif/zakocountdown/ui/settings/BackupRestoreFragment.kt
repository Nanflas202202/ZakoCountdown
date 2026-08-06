// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/BackupRestoreFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.data.*
import com.errorsiayusulif.zakocountdown.databinding.FragmentBackupRestoreBinding
import com.errorsiayusulif.zakocountdown.utils.BackupManager
import com.errorsiayusulif.zakocountdown.utils.ImportConflictAnalyzer
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupRestoreFragment : Fragment() {

    private var _binding: FragmentBackupRestoreBinding? = null
    private val binding get() = _binding!!

    private lateinit var exportView: View
    private lateinit var importView: View

    private lateinit var spinnerTargetVersion: Spinner
    private lateinit var rvExport: RecyclerView
    private lateinit var fabExport: ExtendedFloatingActionButton

    private lateinit var llImportEmpty: LinearLayout
    private lateinit var btnSelectFile: Button
    private lateinit var rvImport: RecyclerView
    private lateinit var fabImport: ExtendedFloatingActionButton

    private lateinit var exportAdapter: BackupNodeAdapter
    private lateinit var importAdapter: BackupNodeAdapter
    private lateinit var preferenceManager: PreferenceManager

    private var currentParsedPackage: BackupManager.ParsedEyfPackage? = null

    // 当前选中的目标版本，默认为最新
    private var targetExportVersion: AppVersion = AppVersion.V_0_9_0

    private val createEyfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { performExportToUri(it) }
    }

    private val pickEyfFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { analyzeImportFile(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBackupRestoreBinding.inflate(inflater, container, false)
        preferenceManager = PreferenceManager(requireContext())

        exportView = inflater.inflate(R.layout.layout_backup_export, null, false)
        importView = inflater.inflate(R.layout.layout_backup_import, null, false)
        bindSubViews()

        return binding.root
    }

    private fun bindSubViews() {
        spinnerTargetVersion = exportView.findViewById(R.id.spinner_target_version)
        rvExport = exportView.findViewById(R.id.rv_export)
        fabExport = exportView.findViewById(R.id.fab_export)

        llImportEmpty = importView.findViewById(R.id.ll_import_empty)
        btnSelectFile = importView.findViewById(R.id.btn_select_file)
        rvImport = importView.findViewById(R.id.rv_import)
        fabImport = importView.findViewById(R.id.fab_import)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPagerAndTabs()
        setupVersionSpinner()

        // 初始加载一次
        setupExportView()

        rvImport.layoutManager = LinearLayoutManager(requireContext())
        importAdapter = BackupNodeAdapter(emptyList())
        rvImport.adapter = importAdapter

        btnSelectFile.setOnClickListener { pickEyfFileLauncher.launch("*/*") }
        fabImport.setOnClickListener { executeImport() }

        fabExport.setOnClickListener {
            val nodes = exportAdapter.getRootNodes()
            if (nodes.none { it.isChecked && it.type != NodeType.HEADER }) {
                Toast.makeText(requireContext(), "请至少选择一项", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            createEyfLauncher.launch("ZakoBackup_$dateStr.eyf")
        }
    }

    private fun setupViewPagerAndTabs() {
        val viewPager = binding.root.findViewById<ViewPager2>(R.id.view_pager)
        val tabLayout = binding.root.findViewById<TabLayout>(R.id.tab_layout)

        viewPager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = if (viewType == 0) exportView else importView
                (view.parent as? ViewGroup)?.removeView(view)
                view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                return object : RecyclerView.ViewHolder(view) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
            override fun getItemCount() = 2
            override fun getItemViewType(position: Int) = position
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = if (position == 0) "导出备份" else "导入恢复"
        }.attach()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == 0) {
                    fabExport.show()
                    fabImport.hide()
                } else {
                    fabExport.hide()
                    if (rvImport.visibility == View.VISIBLE) fabImport.show()
                }
            }
        })
    }

    private fun setupVersionSpinner() {
        val versions = AppVersion.values().map { it.versionName }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, versions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTargetVersion.adapter = adapter

        // 默认选中当前版本 (通常是数组最后一个)
        val defaultIndex = AppVersion.values().indexOf(AppVersion.V_0_9_0)
        spinnerTargetVersion.setSelection(defaultIndex)

        spinnerTargetVersion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newTarget = AppVersion.values()[position]
                if (newTarget != targetExportVersion) {
                    targetExportVersion = newTarget
                    // 版本改变时，重新生成列表以过滤不支持的设置
                    setupExportView()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun getExcludedSettingsKeys(version: AppVersion): Set<String> {
        val excluded = mutableSetOf<String>()

        // ==========================================
        // 永远排除的键（设备绑定的向导状态）
        // ==========================================
        excluded.add("key_oobe_completed")
        excluded.add("key_eula_accepted")
        excluded.add("key_has_prompted_accessibility")
        excluded.add("key_has_prompted_swipe")

        // ==========================================
        // 如果目标版本 <= V0.8.11 (Nightly)
        // 排除 V0.9.0 新增的所有核心引擎设置
        // ==========================================
        if (version.internalCode <= AppVersion.V_0_8_11.internalCode) {
            excluded.add("key_swipe_left_action")
            excluded.add("key_swipe_right_action")
            excluded.add("key_app_icon")
            excluded.add("key_auto_update")
            excluded.add("key_sync_update_urls")
            excluded.add("key_custom_update_urls")
            excluded.add("key_log_persistence")
            excluded.add("key_enable_about_easter_egg")
            excluded.add("key_scrim_color_mode")
            excluded.add("key_scrim_alpha")
            excluded.add("key_scrim_custom_color")
            excluded.add("is_mtb_theme_enabled")
        }

        // ==========================================
        // 如果目标版本 <= V0.8.10 (Nightly)
        // 根据日志，0.8.10的默认日程本背景存在严重Bug，直至0.8.11才修复
        // 因此禁止将默认封面配置导出给 0.8.10 及更早版本
        // ==========================================
        if (version.internalCode <= AppVersion.V_0_8_10.internalCode) {
            excluded.add("cover_book_all")
            excluded.add("cover_book_important")
            excluded.add("alpha_book_all")
            excluded.add("alpha_book_important")
        }

        // ==========================================
        // 如果目标版本 <= V0.8.9 (Debug)
        // 0.8.9 时没有紧凑模式、没有导航栏切换，也没有日程集开关
        // ==========================================
        if (version.internalCode <= AppVersion.V_0_8_9.internalCode) {
            excluded.add("key_home_layout_mode")
            excluded.add("key_nav_mode")
            excluded.add("key_enable_agenda_book")
            excluded.add("key_unlock_legacy_theme_compact")
        }

        return excluded
    }

    private fun setupExportView() {
        rvExport.layoutManager = LinearLayoutManager(requireContext())

        lifecycleScope.launch(Dispatchers.IO) {
            val app = requireActivity().application as ZakoCountdownApplication
            val books = app.repository.getAllBooksSuspend()
            val events = app.repository.getAllEventsSuspend()
            val prefs = requireContext().getSharedPreferences("zako_prefs", Context.MODE_PRIVATE).all

            val nodes = mutableListOf<SelectableNode>()
            val excludedKeys = getExcludedSettingsKeys(targetExportVersion)

            if (prefs.isNotEmpty()) {
                nodes.add(SelectableNode(NodeType.HEADER, "hdr_settings", "应用配置", isExpanded = true, isChecked = true))
                val prefMapping = mapOf(
                    "key_theme" to "主题风格",
                    "key_accent_color" to "全局强调色",
                    "key_nav_mode" to "导航栏样式",
                    "key_home_layout_mode" to "主页布局模式",
                    "enable_popup_reminder" to "开屏弹窗开关",
                    "key_popup_duration" to "弹窗显示时长",
                    "key_popup_skippable" to "允许手动关闭弹窗",
                    "key_popup_skip_delay" to "弹窗关闭延迟",
                    "important_apps_list" to "触发弹窗应用名单",
                    "enable_permanent_notification" to "常驻通知",
                    "key_reminder_time" to "提前提醒时间",
                    "key_unlock_global_alpha" to "解锁卡片透明度",
                    "key_enable_enter_dev_mode" to "开发者模式权限",

                    // --- v0.9.0 新增项目 ---
                    "key_swipe_left_action" to "向左滑动卡片操作",
                    "key_swipe_right_action" to "向右滑动卡片操作",
                    "key_app_icon" to "应用桌面图标样式",
                    "key_auto_update" to "自动检查更新",
                    "key_sync_update_urls" to "在线同步备用更新源",
                    "key_custom_update_urls" to "自定义更新节点池",
                    "key_log_persistence" to "日志持久化保存",
                    "key_enable_about_easter_egg" to "“关于”页面彩蛋",
                    "key_homepage_wallpaper" to "主页背景壁纸",
                    "cover_book_all" to "全部日程默认封面",
                    "cover_book_important" to "重点日程默认封面",
                    "alpha_book_all" to "全部日程透明度",
                    "alpha_book_important" to "重点日程透明度",
                    "key_scrim_color_mode" to "背景遮罩颜色模式",
                    "key_scrim_alpha" to "背景遮罩浓度",
                    "key_scrim_custom_color" to "自定义背景遮罩色",
                    "is_mtb_theme_enabled" to "MTB 动态主题开关",
                    "key_agenda_view_mode" to "日程本列表视图模式"
                )

                prefs.forEach { (key, value) ->
                    // 1. 过滤掉微件配置 (不跨设备导出)
                    // 2. 过滤掉目标版本不支持的被排除的键
                    // 3. 如果目标版本不支持 v0.9.0，则批量过滤掉 MTB 生成的动态色板
                    val isMtbColorKey = key.startsWith("mtb_light_") || key.startsWith("mtb_dark_")
                    val isOldVersion = targetExportVersion.internalCode <= AppVersion.V_0_8_11.internalCode

                    if (!key.startsWith("widget_") && !excludedKeys.contains(key)) {

                        if (isOldVersion && isMtbColorKey) {
                            // 旧版本不导出 MTB 色彩
                            return@forEach
                        }

                        // 如果是 MTB 生成的动态颜色代码，做友好的文字映射
                        val title = prefMapping[key] ?: when {
                            key.startsWith("mtb_light_") -> "MTB 明色: ${key.removePrefix("mtb_light_")}"
                            key.startsWith("mtb_dark_") -> "MTB 暗色: ${key.removePrefix("mtb_dark_")}"
                            else -> key
                        }
                        nodes.add(SelectableNode(NodeType.SETTING, "set_$key", title, value.toString(), true, rawSettingValue = value))
                    }
                }

                prefs.forEach { (key, value) ->
                    if (!key.startsWith("widget_") && !excludedKeys.contains(key)) {
                        val title = prefMapping[key] ?: key
                        nodes.add(SelectableNode(NodeType.SETTING, "set_$key", title, value.toString(), true, rawSettingValue = value))
                    }
                }
            }

            if (books.isNotEmpty()) {
                nodes.add(SelectableNode(NodeType.HEADER, "hdr_books", "日程集 (${books.size})", isExpanded = true, isChecked = true))
                books.forEach { b ->
                    val raw = ExportAgendaBook(b.id, b.name, b.colorHex, if(b.coverImageUri != null) "cover" else null, b.cardAlpha, b.sortOrder)
                    val bookNode = SelectableNode(NodeType.BOOK, "book_${b.id}", b.name, null, true, rawBook = raw)
                    bookNode.children.add(SelectableNode(NodeType.SUB_OPTION, "${b.id}_color", "包含标识色", null, true, subOptionType = SubOptionType.COLOR))
                    bookNode.children.add(SelectableNode(NodeType.SUB_OPTION, "${b.id}_cover", "包含封面图", null, true, subOptionType = SubOptionType.COVER))
                    bookNode.children.add(SelectableNode(NodeType.SUB_OPTION, "${b.id}_alpha", "包含透明度", null, true, subOptionType = SubOptionType.ALPHA))
                    nodes.add(bookNode)
                }
            }

            if (events.isNotEmpty()) {
                nodes.add(SelectableNode(NodeType.HEADER, "hdr_events", "日程卡片 (${events.size})", isExpanded = true, isChecked = true))
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                events.forEach { e ->
                    val raw = ExportEvent(e.title, e.targetDate.time, e.isImportant, e.bookId, e.colorHex, e.backgroundUri, e.isPinned, e.displayMode, e.cardAlpha)
                    val sub = if (e.isImportant) "重点 · 目标: ${sdf.format(e.targetDate)}" else "目标: ${sdf.format(e.targetDate)}"
                    val eventNode = SelectableNode(NodeType.EVENT, "event_${e.id}", e.title, sub, true, rawEvent = raw)
                    eventNode.children.add(SelectableNode(NodeType.SUB_OPTION, "${e.id}_color", "包含卡片颜色", null, true, subOptionType = SubOptionType.COLOR))
                    eventNode.children.add(SelectableNode(NodeType.SUB_OPTION, "${e.id}_cover", "包含背景图", null, true, subOptionType = SubOptionType.COVER))
                    eventNode.children.add(SelectableNode(NodeType.SUB_OPTION, "${e.id}_alpha", "包含显示设置", null, true, subOptionType = SubOptionType.ALPHA))
                    nodes.add(eventNode)
                }
            }

            withContext(Dispatchers.Main) {
                // 确保在 Adapter 中使用之前修改过的、支持树形结构的 BackupNodeAdapter
                exportAdapter = BackupNodeAdapter(nodes)
                rvExport.adapter = exportAdapter
            }
        }
    }

    private fun performExportToUri(targetUri: Uri) {
        binding.root.findViewById<View>(R.id.fl_loading_overlay).visibility = View.VISIBLE
        fabExport.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val outputStream = requireContext().contentResolver.openOutputStream(targetUri)
                if (outputStream != null) {
                    val app = requireActivity().application as ZakoCountdownApplication

                    BackupManager.exportToStream(
                        requireContext(),
                        outputStream,
                        app.repository,
                        exportAdapter.getRootNodes(),
                        targetExportVersion.internalCode // 核心修复：传入 Int 类型的 internalCode 而不是枚举对象本身
                    )

                    withContext(Dispatchers.Main) {
                        binding.root.findViewById<View>(R.id.fl_loading_overlay).visibility = View.GONE
                        fabExport.isEnabled = true
                        Snackbar.make(binding.root, "备份已保存", Snackbar.LENGTH_LONG)
                            .setAction("分享") {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, targetUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(Intent.createChooser(shareIntent, "分享备份"))
                            }.show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.root.findViewById<View>(R.id.fl_loading_overlay).visibility = View.GONE
                    fabExport.isEnabled = true
                    Toast.makeText(requireContext(), "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun analyzeImportFile(uri: Uri) {
        binding.root.findViewById<View>(R.id.fl_loading_overlay).visibility = View.VISIBLE
        llImportEmpty.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val context = requireContext()
                val app = requireActivity().application as ZakoCountdownApplication
                val parsedPackage = BackupManager.parseEyf(context, uri)
                currentParsedPackage = parsedPackage

                val analyzedNodes = ImportConflictAnalyzer.analyze(app.repository, preferenceManager, parsedPackage)

                withContext(Dispatchers.Main) {
                    binding.root.findViewById<View>(R.id.fl_loading_overlay).visibility = View.GONE
                    if (analyzedNodes.isEmpty()) {
                        Toast.makeText(context, "文件为空或格式错误", Toast.LENGTH_SHORT).show()
                        llImportEmpty.visibility = View.VISIBLE
                        return@withContext
                    }
                    importAdapter.updateNodes(analyzedNodes)
                    rvImport.visibility = View.VISIBLE
                    fabImport.show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.root.findViewById<View>(R.id.fl_loading_overlay).visibility = View.GONE
                    llImportEmpty.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun executeImport() {
        val selectedNodes = importAdapter.getRootNodes()
        if (selectedNodes.none { it.isChecked && it.type != NodeType.HEADER }) {
            Toast.makeText(requireContext(), "请选择导入项", Toast.LENGTH_SHORT).show()
            return
        }

        val parsedPackage = currentParsedPackage
        if (parsedPackage == null) {
            Toast.makeText(requireContext(), "内部错误：解析包丢失", Toast.LENGTH_SHORT).show()
            return
        }

        binding.root.findViewById<View>(R.id.fl_loading_overlay).visibility = View.VISIBLE
        fabImport.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = requireActivity().application as ZakoCountdownApplication
                BackupManager.executeImport(
                    requireContext(),
                    app.repository,
                    selectedNodes,
                    parsedPackage
                )

                withContext(Dispatchers.Main) {
                    binding.root.findViewById<View>(R.id.fl_loading_overlay).visibility = View.GONE
                    Snackbar.make(binding.root, "导入成功！建议重启应用", Snackbar.LENGTH_INDEFINITE)
                        .setAction("重启") { requireActivity().recreate() }.show()

                    rvImport.visibility = View.GONE
                    fabImport.hide()
                    llImportEmpty.visibility = View.VISIBLE
                    currentParsedPackage = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.root.findViewById<View>(R.id.fl_loading_overlay).visibility = View.GONE
                    fabImport.isEnabled = true
                    Toast.makeText(requireContext(), "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}