// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/AppSelectorModels.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.graphics.drawable.Drawable

data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable,
    val isSystemApp: Boolean
)