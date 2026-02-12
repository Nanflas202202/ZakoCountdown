package com.errorsiayusulif.zakocountdown.ui.settings

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.databinding.FragmentAppSelectorBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppSelectorFragment : Fragment() {

    private var _binding: FragmentAppSelectorBinding? = null
    private val binding get() = _binding!!
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppSelectorBinding.inflate(inflater, container, false)
        preferenceManager = PreferenceManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerViewApps.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewApps.addItemDecoration(
            DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
        )
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun loadApps() {
        // 现在 XML 中有了 progressBar，这行代码就不会报错了
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val pm = requireActivity().packageManager
            val showSystemApps = preferenceManager.getShowSystemApps()

            // 获取应用列表可能耗时，放在 IO 线程
            val allAppsRaw = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            val appInfoList = allAppsRaw
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { app ->
                    AppInfo(
                        appName = app.loadLabel(pm).toString(),
                        packageName = app.packageName,
                        icon = app.loadIcon(pm),
                        isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                }
                .filter { showSystemApps || !it.isSystemApp }
                .sortedBy { it.appName.lowercase() }

            val selectedApps = preferenceManager.getImportantApps().toMutableSet()

            withContext(Dispatchers.Main) {
                // 隐藏进度条
                binding.progressBar.visibility = View.GONE

                binding.recyclerViewApps.adapter = AppSelectorAdapter(appInfoList, selectedApps) { packageName, isSelected ->
                    if (isSelected) {
                        selectedApps.add(packageName)
                    } else {
                        selectedApps.remove(packageName)
                    }
                    preferenceManager.saveImportantApps(selectedApps)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}