// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/BackupRestoreFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.*
import com.errorsiayusulif.zakocountdown.databinding.FragmentBackupRestoreBinding
import com.errorsiayusulif.zakocountdown.utils.BackupManager
import com.errorsiayusulif.zakocountdown.utils.ImportConflictAnalyzer
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
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

    private lateinit var preferenceManager: PreferenceManager

    // 子页面的视图引用
    private lateinit var exportView: View
    private lateinit var importView: View

    // 控件引用 (从 exportView 和 importView 中找出的)
    private lateinit var rvExport: RecyclerView
    private lateinit var fabExport: ExtendedFloatingActionButton

    private lateinit var llImportEmpty: LinearLayout
    private lateinit var btnSelectFile: Button
    private lateinit var rvImport: RecyclerView
    private lateinit var fabImport: ExtendedFloatingActionButton

    private lateinit var exportAdapter: BackupNodeAdapter
    private lateinit var importAdapter: BackupNodeAdapter

    private val createEyfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { performExportToUri(it) }
    }

    private val pickEyfFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { analyzeImportFile(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBackupRestoreBinding.inflate(inflater, container, false)
        preferenceManager = PreferenceManager(requireContext())

        // 预加载两个子视图
        exportView = inflater.inflate(R.layout.layout_backup_export, null, false)
        importView = inflater.inflate(R.layout.layout_backup_import, null, false)

        bindSubViews()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPagerAndTabs()
        setupExportView()

        // 导入视图初始化
        rvImport.layoutManager = LinearLayoutManager(requireContext())
        importAdapter = BackupNodeAdapter(emptyList())
        rvImport.adapter = importAdapter

        btnSelectFile.setOnClickListener { pickEyfFileLauncher.launch("*/*") }
        fabImport.setOnClickListener { executeImport() }

        fabExport.setOnClickListener {
            val nodes = exportAdapter.getRootNodes()
            if (nodes.none { it.isChecked && it.type != NodeType.HEADER }) {
                Toast.makeText(requireContext(), "请至少选择一项进行导出", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            createEyfLauncher.launch("ZakoBackup_$dateStr.eyf")
        }
    }

    private fun bindSubViews() {
        rvExport = exportView.findViewById(R.id.rv_export)
        fabExport = exportView.findViewById(R.id.fab_export)

        llImportEmpty = importView.findViewById(R.id.ll_import_empty)
        btnSelectFile = importView.findViewById(R.id.btn_select_file)
        rvImport = importView.findViewById(R.id.rv_import)
        fabImport = importView.findViewById(R.id.fab_import)
    }

    private fun setupViewPagerAndTabs() {
        // 设置 ViewPager2 适配器
        binding.viewPager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = if (viewType == 0) exportView else importView
                // 确保视图从旧的父容器中移除 (ViewPager2 刷新机制安全保障)
                (view.parent as? ViewGroup)?.removeView(view)
                view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
            override fun getItemCount(): Int = 2
            override fun getItemViewType(position: Int): Int = position
        }

        // 使用 TabLayoutMediator 将 Tabs 和 ViewPager2 丝滑绑定
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) "导出备份" else "导入恢复"
        }.attach()
    }

    // ==========================================
    // 导出逻辑
    // ==========================================
    private fun setupExportView() {
        rvExport.layoutManager = LinearLayoutManager(requireContext())

        lifecycleScope.launch(Dispatchers.IO) {
            val app = requireActivity().application as ZakoCountdownApplication
            val books = app.repository.getAllBooksSuspend()
            val events = app.repository.getAllEventsSuspend()
            val prefs = requireContext().getSharedPreferences("zako_prefs", Context.MODE_PRIVATE).all

            val nodes = mutableListOf<SelectableNode>()

            if (prefs.isNotEmpty()) {
                nodes.add(SelectableNode(NodeType.HEADER, "hdr_settings", "应用配置", isExpanded = true, isChecked = true))
                val prefMapping = mapOf(
                    "key_theme" to "主题风格",
                    "key_accent_color" to "全局强调色",
                    "key_nav_mode" to "导航栏样式",
                    "key_home_layout_mode" to "主页布局模式",
                    "enable_popup_reminder" to "开屏弹窗开关"
                )
                prefs.forEach { (key, value) ->
                    if (!key.startsWith("widget_")) {
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
                    val sub = "目标: ${sdf.format(e.targetDate)}"
                    val eventNode = SelectableNode(NodeType.EVENT, "event_${e.id}", e.title, sub, true, rawEvent = raw)
                    eventNode.children.add(SelectableNode(NodeType.SUB_OPTION, "${e.id}_color", "包含卡片颜色", null, true, subOptionType = SubOptionType.COLOR))
                    eventNode.children.add(SelectableNode(NodeType.SUB_OPTION, "${e.id}_cover", "包含背景图", null, true, subOptionType = SubOptionType.COVER))
                    eventNode.children.add(SelectableNode(NodeType.SUB_OPTION, "${e.id}_alpha", "包含显示设置", null, true, subOptionType = SubOptionType.ALPHA))
                    nodes.add(eventNode)
                }
            }

            withContext(Dispatchers.Main) {
                exportAdapter = BackupNodeAdapter(nodes)
                rvExport.adapter = exportAdapter
            }
        }
    }

    private fun performExportToUri(targetUri: Uri) {
        binding.flLoadingOverlay.visibility = View.VISIBLE
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
                        exportAdapter.getRootNodes()
                    )

                    withContext(Dispatchers.Main) {
                        binding.flLoadingOverlay.visibility = View.GONE
                        fabExport.isEnabled = true

                        Snackbar.make(binding.root, "备份已保存到本地", Snackbar.LENGTH_LONG)
                            .setAction("分享") {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, targetUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(Intent.createChooser(shareIntent, "分享文件"))
                            }.show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.flLoadingOverlay.visibility = View.GONE
                    fabExport.isEnabled = true
                    Toast.makeText(requireContext(), "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ==========================================
    // 导入逻辑
    // ==========================================
    private fun analyzeImportFile(uri: Uri) {
        binding.flLoadingOverlay.visibility = View.VISIBLE
        llImportEmpty.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val context = requireContext()
                val app = requireActivity().application as ZakoCountdownApplication
                val parsedPackage = BackupManager.parseEyf(context, uri)
                val analyzedNodes = ImportConflictAnalyzer.analyze(app.repository, preferenceManager, parsedPackage)

                withContext(Dispatchers.Main) {
                    binding.flLoadingOverlay.visibility = View.GONE
                    if (analyzedNodes.isEmpty()) {
                        Toast.makeText(context, "空文件", Toast.LENGTH_SHORT).show()
                        llImportEmpty.visibility = View.VISIBLE
                        return@withContext
                    }

                    importAdapter.updateNodes(analyzedNodes)
                    rvImport.visibility = View.VISIBLE
                    fabImport.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.flLoadingOverlay.visibility = View.GONE
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

        binding.flLoadingOverlay.visibility = View.VISIBLE
        fabImport.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = requireActivity().application as ZakoCountdownApplication
                BackupManager.executeImport(requireContext(), app.repository, selectedNodes)

                withContext(Dispatchers.Main) {
                    binding.flLoadingOverlay.visibility = View.GONE
                    Snackbar.make(binding.root, "导入成功！建议重启应用。", Snackbar.LENGTH_INDEFINITE)
                        .setAction("重启") { requireActivity().recreate() }.show()

                    rvImport.visibility = View.GONE
                    fabImport.visibility = View.GONE
                    llImportEmpty.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.flLoadingOverlay.visibility = View.GONE
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