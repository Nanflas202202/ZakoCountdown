// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/LicenseFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.databinding.FragmentLicenseBinding
import java.io.InputStream

class LicenseFragment : Fragment() {
    private var _binding: FragmentLicenseBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLicenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toggleButtonGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.button_license_zh -> loadLicense(R.raw.license_mpl_zh)
                    R.id.button_license_en -> loadLicense(R.raw.license_mpl_en)
                    R.id.button_license_eys -> loadLicense(R.raw.eula)
                    // 第三方代码归属（MIT）已移出本页 ——
                    // 它现在在「关于 → 详细信息 → 依赖库」里以独立一行呈现，
                    // 点开即为 MIT 全文。
                }
            }
        }

        // 默认选中并加载中文许可
        binding.toggleButtonGroup.check(R.id.button_license_zh)
    }

    private fun loadLicense(resourceId: Int) {
        try {
            val inputStream: InputStream = resources.openRawResource(resourceId)
            val text = inputStream.bufferedReader().use { it.readText() }
            binding.licenseTextView.text = text
        } catch (e: Exception) {
            binding.licenseTextView.text = getString(R.string.about_license_load_failed_detail, resourceId)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
