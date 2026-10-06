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
import com.errorsiayusulif.zakocountdown.data.PreferenceKeys
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

    // 当前选中的目标版本，默认为最新（v0.9.1-debug）
    private var targetExportVersion: AppVersion = AppVersion.CURRENT

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
                Toast.makeText(requireContext(), R.string.backup_select_at_least_one, Toast.LENGTH_SHORT).show()
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
            tab.text = if (position == 0) getString(R.string.backup_tab_export) else getString(R.string.backup_tab_import)
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

        // 默认选中当前最新版本（v0.9.1-debug）
        val defaultIndex = AppVersion.values().indexOf(AppVersion.CURRENT)
        spinnerTargetVersion.setSelection(defaultIndex)
        updateFeatureSummary(AppVersion.CURRENT)

        spinnerTargetVersion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newTarget = AppVersion.values()[position]
                if (newTarget != targetExportVersion) {
                    targetExportVersion = newTarget
                    updateFeatureSummary(newTarget)
                    // 版本改变时，重新生成列表以过滤不支持的设置
                    setupExportView()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /**
     * 展示「该目标版本下可用功能」的概览，点一下能看到完整清单。
     * 清单本身也会写进备份包的 DocumentFeatures.json，这里只是提前给用户看一眼。
     */
    private fun updateFeatureSummary(version: AppVersion) {
        val label = view?.findViewById<android.widget.TextView>(R.id.tv_feature_summary) ?: return
        val available = FeatureRegistry.availableFor(version.internalCode)
        val exportable = available.count { it.exportable }
        label.text = getString(R.string.backup_feature_summary, available.size, exportable)
        label.setOnClickListener { showFeatureListDialog(version) }
    }

    private fun showFeatureListDialog(version: AppVersion) {
        val available = FeatureRegistry.availableFor(version.internalCode)
        val skipped = FeatureRegistry.addedAfter(version.internalCode)
        val zh = java.util.Locale.getDefault().language.equals("zh", ignoreCase = true)

        val body = buildString {
            appendLine(getString(R.string.backup_feature_dialog_available, version.versionName))
            appendLine()
            available.forEach { f ->
                val name = if (zh) f.displayNameZh else f.displayNameEn
                append("• ").append(name)
                if (!f.exportable) append("  ").append(getString(R.string.backup_feature_runtime_only))
                appendLine()
            }
            if (skipped.isNotEmpty()) {
                appendLine()
                appendLine(getString(R.string.backup_feature_dialog_skipped))
                skipped.forEach { f ->
                    val name = if (zh) f.displayNameZh else f.displayNameEn
                    appendLine("• $name")
                }
            }
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.backup_feature_dialog_title, version.versionName))
            .setMessage(body.trim())
            .setPositiveButton(R.string.common_ok, null)
            .show()
    }

    private fun getExcludedSettingsKeys(version: AppVersion): Set<String> {
        val excluded = mutableSetOf<String>()

        // ==========================================
        // 永远排除的键（设备绑定的向导状态）
        // ==========================================
        excluded.add(PreferenceKeys.OOBE_COMPLETED)
        excluded.add(PreferenceKeys.EULA_ACCEPTED)
        excluded.add(PreferenceKeys.ACCESSIBILITY_GUIDE_PROMPTED)
        excluded.add(PreferenceKeys.SWIPE_GUIDE_PROMPTED)

        // ==========================================
        // 开发者选项相关：无论导入还是导出**都不显示**
        // ==========================================
        // DEV_MODE_ENTRY_ENABLED 控制「是否允许进入开发者选项」。
        // 它属于**设备级的能力开关**，跨设备还原没有意义，还有副作用：
        // 恢复到另一台机器上会直接打开那边的开发者入口，
        // 而用户在那台机器上未必走过解锁流程。
        excluded.add(PreferenceKeys.DEV_MODE_ENTRY_ENABLED)

        // ==========================================
        // 如果目标版本 <= V0.8.11 (Nightly)
        // 排除 V0.9.0 新增的所有核心引擎设置
        // ==========================================
        if (version.internalCode <= AppVersion.V_0_8_11.internalCode) {
            excluded.add(PreferenceKeys.SWIPE_LEFT_ACTION)
            excluded.add(PreferenceKeys.SWIPE_RIGHT_ACTION)
            excluded.add(PreferenceKeys.APP_ICON_ALIAS)
            excluded.add(PreferenceKeys.AUTO_UPDATE_ENABLED)
            excluded.add(PreferenceKeys.UPDATE_SOURCE_SYNC_ENABLED)
            excluded.add(PreferenceKeys.UPDATE_SOURCE_NODES)
            excluded.add(PreferenceKeys.LOG_PERSISTENCE_ENABLED)
            excluded.add(PreferenceKeys.ABOUT_EASTER_EGG_ENABLED)
            excluded.add(PreferenceKeys.SCRIM_COLOR_MODE)
            excluded.add(PreferenceKeys.SCRIM_ALPHA)
            excluded.add(PreferenceKeys.SCRIM_CUSTOM_COLOR)
            excluded.add(PreferenceKeys.MTB_THEME_ENABLED)
        }

        // ==========================================
        // 如果目标版本 <= V0.8.10 (Nightly)
        // 根据日志，0.8.10的默认日程本背景存在严重Bug，直至0.8.11才修复
        // 因此禁止将默认封面配置导出给 0.8.10 及更早版本
        // ==========================================
        if (version.internalCode <= AppVersion.V_0_8_10.internalCode) {
            excluded.add(PreferenceKeys.DEFAULT_BOOK_COVER_ALL)
            excluded.add(PreferenceKeys.DEFAULT_BOOK_COVER_IMPORTANT)
            excluded.add(PreferenceKeys.DEFAULT_BOOK_ALPHA_ALL)
            excluded.add(PreferenceKeys.DEFAULT_BOOK_ALPHA_IMPORTANT)
        }

        // ==========================================
        // 如果目标版本 <= V0.8.9 (Debug)
        // 0.8.9 时没有紧凑模式、没有导航栏切换，也没有日程集开关
        // ==========================================
        if (version.internalCode <= AppVersion.V_0_8_9.internalCode) {
            excluded.add(PreferenceKeys.HOME_LAYOUT_MODE)
            excluded.add(PreferenceKeys.APP_LAYOUT_MODE)
            excluded.add(PreferenceKeys.AGENDA_BOOK_ENABLED)
            excluded.add(PreferenceKeys.LEGACY_THEME_IN_COMPACT)
        }

        // ==========================================
        // 如果目标版本 <= V0.9.0 (code 900)
        // 排除 V0.9.1 引入的悬浮导航、MD3 Expressive 与侧滑栏自定义图像
        // ==========================================
        if (version.internalCode < PreferenceManager.TARGET_VERSION_FLOATING_NAV) {
            excluded.add(PreferenceKeys.DRAWER_HEADER_IMAGE_URI)
            excluded.add(PreferenceKeys.DRAWER_HEADER_IMAGE_ENABLED)
        }

        return excluded
    }

    private fun setupExportView() {
        rvExport.layoutManager = LinearLayoutManager(requireContext())

        lifecycleScope.launch(Dispatchers.IO) {
            val app = requireActivity().application as ZakoCountdownApplication
            val books = app.repository.getAllBooksSuspend()
            val events = app.repository.getAllEventsSuspend()
            val prefs = requireContext().getSharedPreferences(PreferenceKeys.PREFS_FILE, Context.MODE_PRIVATE).all

            val nodes = mutableListOf<SelectableNode>()
            val excludedKeys = getExcludedSettingsKeys(targetExportVersion)

            if (prefs.isNotEmpty()) {
                nodes.add(SelectableNode(NodeType.HEADER, "hdr_settings", getString(R.string.backup_header_settings), isExpanded = true, isChecked = true))
                val prefMapping = mapOf(
                    PreferenceKeys.THEME_MODE to getString(R.string.pref_theme_style),
                    PreferenceKeys.ACCENT_COLOR to getString(R.string.pref_accent_color),
                    PreferenceKeys.APP_LAYOUT_MODE to getString(R.string.pref_nav_mode),
                    PreferenceKeys.HOME_LAYOUT_MODE to getString(R.string.pref_home_layout),
                    PreferenceKeys.POPUP_REMINDER_ENABLED to getString(R.string.pref_popup_enabled),
                    PreferenceKeys.POPUP_DURATION_SECONDS to getString(R.string.pref_popup_duration),
                    PreferenceKeys.POPUP_SKIPPABLE to getString(R.string.pref_popup_skippable),
                    PreferenceKeys.POPUP_SKIP_DELAY_SECONDS to getString(R.string.pref_popup_skip_delay),
                    PreferenceKeys.POPUP_TARGET_APPS to getString(R.string.pref_popup_apps),
                    PreferenceKeys.PERSISTENT_NOTIFICATION_ENABLED to getString(R.string.pref_persistent_notification),
                    PreferenceKeys.REMINDER_LEAD_TIME to getString(R.string.pref_reminder_time),
                    PreferenceKeys.CARD_ALPHA_UNLOCKED to getString(R.string.pref_unlock_alpha),
                    PreferenceKeys.DEV_MODE_ENTRY_ENABLED to getString(R.string.pref_dev_mode),

                    // --- v0.9.0 新增项目 ---
                    PreferenceKeys.SWIPE_LEFT_ACTION to getString(R.string.pref_swipe_left),
                    PreferenceKeys.SWIPE_RIGHT_ACTION to getString(R.string.pref_swipe_right),
                    PreferenceKeys.APP_ICON_ALIAS to getString(R.string.pref_app_icon),
                    PreferenceKeys.AUTO_UPDATE_ENABLED to getString(R.string.pref_auto_update),
                    PreferenceKeys.UPDATE_SOURCE_SYNC_ENABLED to getString(R.string.pref_sync_update_urls),
                    PreferenceKeys.UPDATE_SOURCE_NODES to getString(R.string.pref_custom_update_urls),
                    PreferenceKeys.LOG_PERSISTENCE_ENABLED to getString(R.string.pref_log_persistence),
                    PreferenceKeys.ABOUT_EASTER_EGG_ENABLED to getString(R.string.pref_about_easter_egg),
                    PreferenceKeys.HOME_WALLPAPER_URI to getString(R.string.pref_homepage_wallpaper),
                    PreferenceKeys.DEFAULT_BOOK_COVER_ALL to getString(R.string.pref_cover_all),
                    PreferenceKeys.DEFAULT_BOOK_COVER_IMPORTANT to getString(R.string.pref_cover_important),
                    PreferenceKeys.DEFAULT_BOOK_ALPHA_ALL to getString(R.string.pref_alpha_all),
                    PreferenceKeys.DEFAULT_BOOK_ALPHA_IMPORTANT to getString(R.string.pref_alpha_important),
                    PreferenceKeys.SCRIM_COLOR_MODE to getString(R.string.pref_scrim_color_mode),
                    PreferenceKeys.SCRIM_ALPHA to getString(R.string.pref_scrim_alpha),
                    PreferenceKeys.SCRIM_CUSTOM_COLOR to getString(R.string.pref_scrim_custom_color),
                    PreferenceKeys.MTB_THEME_ENABLED to getString(R.string.pref_mtb_enabled),
                    PreferenceKeys.AGENDA_VIEW_IS_GRID to getString(R.string.pref_agenda_view_mode),
                    // --- v0.9.1 新增项目 ---
                    PreferenceKeys.DRAWER_HEADER_IMAGE_URI to getString(R.string.pref_drawer_header_image),
                    PreferenceKeys.DRAWER_HEADER_IMAGE_ENABLED to getString(R.string.pref_drawer_header_image_enabled)
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
                            key.startsWith("mtb_light_") -> getString(R.string.backup_mtb_light, key.removePrefix("mtb_light_"))
                            key.startsWith("mtb_dark_") -> getString(R.string.backup_mtb_dark, key.removePrefix("mtb_dark_"))
                            else -> key
                        }
                        nodes.add(SelectableNode(NodeType.SETTING, "set_$key", title, value.toString(), true, rawSettingValue = value))
                    }
                }
            }

            if (books.isNotEmpty()) {
                nodes.add(SelectableNode(NodeType.HEADER, "hdr_books", getString(R.string.backup_header_books, books.size), isExpanded = true, isChecked = true))
                books.forEach { b ->
                    val raw = ExportAgendaBook(b.id, b.name, b.colorHex, if(b.coverImageUri != null) "cover" else null, b.cardAlpha, b.sortOrder)
                    val bookNode = SelectableNode(NodeType.BOOK, "book_${b.id}", b.name, null, true, rawBook = raw)
                    bookNode.children.add(SelectableNode(NodeType.SUB_OPTION, "${b.id}_color", getString(R.string.backup_sub_color), null, true, subOptionType = SubOptionType.COLOR))
                    bookNode.children.add(SelectableNode(NodeType.SUB_OPTION, "${b.id}_cover", getString(R.string.backup_sub_cover), null, true, subOptionType = SubOptionType.COVER))
                    bookNode.children.add(SelectableNode(NodeType.SUB_OPTION, "${b.id}_alpha", getString(R.string.backup_sub_alpha), null, true, subOptionType = SubOptionType.ALPHA))
                    nodes.add(bookNode)
                }
            }

            if (events.isNotEmpty()) {
                nodes.add(SelectableNode(NodeType.HEADER, "hdr_events", getString(R.string.backup_header_events, events.size), isExpanded = true, isChecked = true))
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                events.forEach { e ->
                    val raw = ExportEvent(e.title, e.targetDate.time, e.isImportant, e.bookId, e.colorHex, e.backgroundUri, e.isPinned, e.displayMode, e.cardAlpha)
                    val sub = if (e.isImportant) getString(R.string.backup_event_subtitle_important, sdf.format(e.targetDate)) else getString(R.string.backup_event_subtitle, sdf.format(e.targetDate))
                    val eventNode = SelectableNode(NodeType.EVENT, "event_${e.id}", e.title, sub, true, rawEvent = raw)
                    eventNode.children.add(SelectableNode(NodeType.SUB_OPTION, "${e.id}_color", getString(R.string.backup_sub_event_color), null, true, subOptionType = SubOptionType.COLOR))
                    eventNode.children.add(SelectableNode(NodeType.SUB_OPTION, "${e.id}_cover", getString(R.string.backup_sub_event_cover), null, true, subOptionType = SubOptionType.COVER))
                    eventNode.children.add(SelectableNode(NodeType.SUB_OPTION, "${e.id}_alpha", getString(R.string.backup_sub_event_alpha), null, true, subOptionType = SubOptionType.ALPHA))
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
                        Snackbar.make(binding.root, R.string.backup_saved, Snackbar.LENGTH_LONG)
                            .setAction(R.string.common_share) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, targetUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(Intent.createChooser(shareIntent, getString(R.string.backup_share_chooser)))
                            }.show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.root.findViewById<View>(R.id.fl_loading_overlay).visibility = View.GONE
                    fabExport.isEnabled = true
                    Toast.makeText(requireContext(), getString(R.string.backup_export_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
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

                val analyzedNodes = ImportConflictAnalyzer.analyze(context, app.repository, preferenceManager, parsedPackage)

                withContext(Dispatchers.Main) {
                    binding.root.findViewById<View>(R.id.fl_loading_overlay).visibility = View.GONE
                    if (analyzedNodes.isEmpty()) {
                        Toast.makeText(context, R.string.backup_empty_or_corrupt, Toast.LENGTH_SHORT).show()
                        llImportEmpty.visibility = View.VISIBLE
                        return@withContext
                    }
                    importAdapter.updateNodes(analyzedNodes)
                    rvImport.visibility = View.VISIBLE
                    fabImport.show()
                    showImportMeta(parsedPackage)
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

    /**
     * 展示导入包的元信息：目标版本、导出时的升级代号、功能条目数。
     * 旧包（v1.0 或没有 DocumentFeatures.json 的）只显示能拿到的部分。
     */
    private fun showImportMeta(parsed: BackupManager.ParsedEyfPackage) {
        val meta = importView.findViewById<android.widget.TextView>(R.id.tv_import_meta) ?: return
        val version = AppVersion.fromInternalCode(parsed.appVersionCode)

        val parts = mutableListOf<String>()
        parts += getString(R.string.import_meta_origin, version.versionName)

        // 升级代号只在开发者模式打开时显示 —— 它本来就属于开发者信息
        if (preferenceManager.isEnableEnterDevMode() && parsed.upgradeCode != null) {
            parts += getString(
                R.string.import_meta_upgrade_code,
                parsed.upgradeCodeLabel ?: "",
                parsed.upgradeCode
            )
        }

        parsed.features?.let { parts += getString(R.string.import_meta_features, it.featureCount) }

        meta.text = parts.joinToString("  ·  ")
        meta.visibility = View.VISIBLE
    }

    private fun executeImport() {
        val selectedNodes = importAdapter.getRootNodes()
        if (selectedNodes.none { it.isChecked && it.type != NodeType.HEADER }) {
            Toast.makeText(requireContext(), R.string.backup_select_import_items, Toast.LENGTH_SHORT).show()
            return
        }

        val parsedPackage = currentParsedPackage
        if (parsedPackage == null) {
            Toast.makeText(requireContext(), R.string.backup_internal_error, Toast.LENGTH_SHORT).show()
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
                    Snackbar.make(binding.root, R.string.backup_imported_restart_hint, Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.backup_restart_action) { requireActivity().recreate() }.show()

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
                    Toast.makeText(requireContext(), getString(R.string.backup_import_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
