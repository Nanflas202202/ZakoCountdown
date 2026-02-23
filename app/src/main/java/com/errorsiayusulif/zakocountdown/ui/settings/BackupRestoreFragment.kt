// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/BackupRestoreFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

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
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.data.ExportConfig
import com.errorsiayusulif.zakocountdown.data.ImportConfig
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.databinding.FragmentBackupRestoreBinding
import com.errorsiayusulif.zakocountdown.utils.BackupManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class BackupRestoreFragment : Fragment() {

    private var _binding: FragmentBackupRestoreBinding? = null
    private val binding get() = _binding!!

    // 导入文件选择器
    private val pickEyfFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            performImport(it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBackupRestoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCheckBoxLinkages()

        binding.btnExport.setOnClickListener {
            performExport()
        }

        binding.btnImport.setOnClickListener {
            // 选择任何文件 (由于某些系统不支持选自定义后缀，用 */* 更稳妥)
            pickEyfFileLauncher.launch("*/*")
        }
    }

    /**
     * 勾选联动逻辑：如果主项（如“日程集”）被取消勾选，则其子项也应该被取消并禁用。
     */
    private fun setupCheckBoxLinkages() {
        // --- 导出联动 ---
        binding.cbExportBooks.setOnCheckedChangeListener { _, isChecked ->
            binding.cbExportBookColors.isEnabled = isChecked
            binding.cbExportBookCovers.isEnabled = isChecked
            if (!isChecked) {
                binding.cbExportBookColors.isChecked = false
                binding.cbExportBookCovers.isChecked = false
            }
        }

        binding.cbExportEvents.setOnCheckedChangeListener { _, isChecked ->
            binding.cbExportEventColors.isEnabled = isChecked
            binding.cbExportEventCovers.isEnabled = isChecked
            binding.cbExportEventAlphas.isEnabled = isChecked
            if (!isChecked) {
                binding.cbExportEventColors.isChecked = false
                binding.cbExportEventCovers.isChecked = false
                binding.cbExportEventAlphas.isChecked = false
            }
        }

        // --- 导入联动 ---
        binding.cbImportBooks.setOnCheckedChangeListener { _, isChecked ->
            binding.cbImportBookColors.isEnabled = isChecked
            binding.cbImportBookCovers.isEnabled = isChecked
            if (!isChecked) {
                binding.cbImportBookColors.isChecked = false
                binding.cbImportBookCovers.isChecked = false
            }
        }

        binding.cbImportEvents.setOnCheckedChangeListener { _, isChecked ->
            binding.cbImportEventColors.isEnabled = isChecked
            binding.cbImportEventCovers.isEnabled = isChecked
            if (!isChecked) {
                binding.cbImportEventColors.isChecked = false
                binding.cbImportEventCovers.isChecked = false
            }
        }
    }

    private fun performExport() {
        binding.flLoadingOverlay.visibility = View.VISIBLE

        val config = ExportConfig(
            includeEvents = binding.cbExportEvents.isChecked,
            includeEventColors = binding.cbExportEventColors.isChecked,
            includeEventCovers = binding.cbExportEventCovers.isChecked,
            includeEventAlphas = binding.cbExportEventAlphas.isChecked,
            includeBooks = binding.cbExportBooks.isChecked,
            includeBookColors = binding.cbExportBookColors.isChecked,
            includeBookCovers = binding.cbExportBookCovers.isChecked,
            includeSettings = binding.cbExportSettings.isChecked
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = requireActivity().application as ZakoCountdownApplication
                val zipFile = BackupManager.exportToEyf(
                    requireContext(),
                    app.repository,
                    app.preferenceManager,
                    config
                )

                // 准备分享意图
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    zipFile
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip" // 或 application/octet-stream
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "ZakoCountdown 备份文件")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    binding.flLoadingOverlay.visibility = View.GONE
                    startActivity(Intent.createChooser(intent, "保存/分享备份文件 (.eyf)"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.flLoadingOverlay.visibility = View.GONE
                    Toast.makeText(requireContext(), "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun performImport(uri: Uri) {
        binding.flLoadingOverlay.visibility = View.VISIBLE

        val config = ImportConfig(
            importEvents = binding.cbImportEvents.isChecked,
            importEventColors = binding.cbImportEventColors.isChecked,
            importEventCovers = binding.cbImportEventCovers.isChecked,
            importEventAlphas = true, // 如果UI没这选项默认导
            importBooks = binding.cbImportBooks.isChecked,
            importBookColors = binding.cbImportBookColors.isChecked,
            importBookCovers = binding.cbImportBookCovers.isChecked,
            importBookAlphas = true,
            importSettings = binding.cbImportSettings.isChecked
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = requireActivity().application as ZakoCountdownApplication
                BackupManager.importFromEyf(
                    requireContext(),
                    uri,
                    app.repository,
                    app.preferenceManager,
                    config
                )

                withContext(Dispatchers.Main) {
                    binding.flLoadingOverlay.visibility = View.GONE
                    Snackbar.make(binding.root, "导入成功！部分设置可能需要重启应用生效。", Snackbar.LENGTH_INDEFINITE)
                        .setAction("去主页") {
                            requireActivity().onBackPressed()
                        }.show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.flLoadingOverlay.visibility = View.GONE
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