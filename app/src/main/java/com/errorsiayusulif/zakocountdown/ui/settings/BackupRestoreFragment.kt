package com.errorsiayusulif.zakocountdown.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.data.*
import com.errorsiayusulif.zakocountdown.databinding.FragmentBackupRestoreBinding
import com.errorsiayusulif.zakocountdown.utils.BackupManager
import com.errorsiayusulif.zakocountdown.utils.ImportConflictAnalyzer
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupRestoreFragment : Fragment() {

    private var _binding: FragmentBackupRestoreBinding? = null
    private val binding get() = _binding!!

    private lateinit var exportAdapter: BackupNodeAdapter
    private lateinit var importAdapter: BackupNodeAdapter
    private lateinit var preferenceManager: PreferenceManager

    private var pendingImportUri: Uri? = null

    private val createEyfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { performExportToUri(it) }
    }

    private val pickEyfFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { analyzeImportFile(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBackupRestoreBinding.inflate(inflater, container, false)
        preferenceManager = PreferenceManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs()
        setupExportView()

        binding.rvImport.layoutManager = LinearLayoutManager(requireContext())
        importAdapter = BackupNodeAdapter(emptyList())
        binding.rvImport.adapter = importAdapter

        binding.btnSelectFile.setOnClickListener { pickEyfFileLauncher.launch("*/*") }
        binding.fabImport.setOnClickListener { executeImport() }

        binding.fabExport.setOnClickListener {
            val nodes = exportAdapter.getRootNodes()
            if (nodes.none { it.isChecked && it.type != NodeType.HEADER }) {
                Toast.makeText(requireContext(), "请至少选择一项进行导出", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            createEyfLauncher.launch("ZakoBackup_$dateStr.eyf")
        }
        // 修复手势切换逻辑 (滑动方向判定)
        val gestureDetector = android.view.GestureDetector(requireContext(), object : android.view.GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        // 从左向右滑动：e2.x > e1.x，手指向右划
                        // 用户想看左边的内容 -> 切换到 [0] (导出备份)
                        binding.tabLayout.getTabAt(0)?.select()
                    } else {
                        // 从右向左滑动：e2.x < e1.x，手指向左划
                        // 用户想看右边的内容 -> 切换到 [1] (导入恢复)
                        binding.tabLayout.getTabAt(1)?.select()
                    }
                    return true
                }
                return false
            }
        })

        // 绑定手势 (保持不变)
        binding.root.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
        binding.rvExport.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); false }
        binding.rvImport.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); false }
}
    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("导出备份"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("导入恢复"))
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                binding.viewFlipper.displayedChild = tab?.position ?: 0
                if (tab?.position == 0) {
                    binding.fabExport.show()
                    binding.fabImport.hide()
                } else {
                    binding.fabExport.hide()
                    if (binding.rvImport.visibility == View.VISIBLE) binding.fabImport.show()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupExportView() {
        binding.rvExport.layoutManager = LinearLayoutManager(requireContext())

        lifecycleScope.launch(Dispatchers.IO) {
            val app = requireActivity().application as ZakoCountdownApplication
            val books = app.repository.getAllBooksSuspend()
            val events = app.repository.getAllEventsSuspend()
            val prefs = requireContext().getSharedPreferences("zako_prefs", Context.MODE_PRIVATE).all

            val nodes = mutableListOf<SelectableNode>()

            if (prefs.isNotEmpty()) {
                nodes.add(SelectableNode(NodeType.HEADER, "hdr_settings", "应用配置", isChecked = true))
                val prefMapping = mapOf(
                    "key_theme" to "主题风格",
                    "key_accent_color" to "全局强调色",
                    "key_nav_mode" to "导航栏样式",
                    "key_home_layout_mode" to "主页布局模式",
                    "enable_popup_reminder" to "开屏弹窗开关",
                    "important_apps_list" to "弹窗触发白名单"
                )

                prefs.forEach { (key, value) ->
                    if (!key.startsWith("widget_")) {
                        val title = prefMapping[key] ?: key
                        nodes.add(SelectableNode(NodeType.SETTING, "set_$key", title, value.toString(), true, rawSettingValue = value))
                    }
                }
            }

            if (books.isNotEmpty()) {
                nodes.add(SelectableNode(NodeType.HEADER, "hdr_books", "日程集 (${books.size})", isChecked = true))
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
                nodes.add(SelectableNode(NodeType.HEADER, "hdr_events", "日程卡片 (${events.size})", isChecked = true))
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
                binding.rvExport.adapter = exportAdapter
            }
        }
    }

    private fun performExportToUri(targetUri: Uri) {
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.fabExport.isEnabled = false

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
                        binding.loadingIndicator.visibility = View.GONE
                        binding.fabExport.isEnabled = true

                        Snackbar.make(binding.root, "备份已保存到本地", Snackbar.LENGTH_LONG)
                            .setAction("分享") {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, targetUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(Intent.createChooser(shareIntent, "分享备份文件"))
                            }.show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.loadingIndicator.visibility = View.GONE
                    binding.fabExport.isEnabled = true
                    Toast.makeText(requireContext(), "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun analyzeImportFile(uri: Uri) {
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.llImportEmpty.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val context = requireContext()
                val app = requireActivity().application as ZakoCountdownApplication
                val parsedPackage = BackupManager.parseEyf(context, uri)
                val analyzedNodes = ImportConflictAnalyzer.analyze(app.repository, preferenceManager, parsedPackage)

                withContext(Dispatchers.Main) {
                    binding.loadingIndicator.visibility = View.GONE
                    if (analyzedNodes.isEmpty()) {
                        Toast.makeText(context, "空文件", Toast.LENGTH_SHORT).show()
                        binding.llImportEmpty.visibility = View.VISIBLE
                        return@withContext
                    }

                    importAdapter = BackupNodeAdapter(analyzedNodes)
                    binding.rvImport.adapter = importAdapter
                    binding.rvImport.visibility = View.VISIBLE
                    binding.fabImport.show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.loadingIndicator.visibility = View.GONE
                    binding.llImportEmpty.visibility = View.VISIBLE
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

        binding.loadingIndicator.visibility = View.VISIBLE
        binding.fabImport.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = requireActivity().application as ZakoCountdownApplication
                BackupManager.executeImport(
                    requireContext(),
                    app.repository,
                    selectedNodes
                )

                withContext(Dispatchers.Main) {
                    binding.loadingIndicator.visibility = View.GONE
                    Snackbar.make(binding.root, "导入成功！建议重启应用。", Snackbar.LENGTH_INDEFINITE)
                        .setAction("重启") { requireActivity().recreate() }.show()

                    binding.rvImport.visibility = View.GONE
                    binding.fabImport.hide()
                    binding.llImportEmpty.visibility = View.VISIBLE
                    pendingImportUri = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.loadingIndicator.visibility = View.GONE
                    binding.fabImport.isEnabled = true
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