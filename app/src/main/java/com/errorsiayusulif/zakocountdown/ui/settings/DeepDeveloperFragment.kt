package com.errorsiayusulif.zakocountdown.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.errorsiayusulif.zakocountdown.R

class DeepDeveloperFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.deep_developer_preferences, rootKey)
    }
}