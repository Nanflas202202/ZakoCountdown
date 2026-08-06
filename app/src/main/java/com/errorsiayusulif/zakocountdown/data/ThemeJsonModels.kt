// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/ThemeJsonModels.kt
package com.errorsiayusulif.zakocountdown.data

import com.google.gson.annotations.SerializedName

/**
 * 对应 Material Theme Builder 导出的 material-theme.json
 */
data class MtbThemeData(
    val description: String?,
    val seed: String?,
    val coreColors: Map<String, String>?,
    val schemes: MtbSchemes?
)

data class MtbSchemes(
    val light: MtbColorScheme?,
    @SerializedName("light-medium-contrast") val lightMediumContrast: MtbColorScheme?,
    @SerializedName("light-high-contrast") val lightHighContrast: MtbColorScheme?,
    val dark: MtbColorScheme?,
    @SerializedName("dark-medium-contrast") val darkMediumContrast: MtbColorScheme?,
    @SerializedName("dark-high-contrast") val darkHighContrast: MtbColorScheme?
)

/**
 * 完整的 Material 3 Color Role 映射表
 */
data class MtbColorScheme(
    val primary: String?,
    val surfaceTint: String?,
    val onPrimary: String?,
    val primaryContainer: String?,
    val onPrimaryContainer: String?,
    val secondary: String?,
    val onSecondary: String?,
    val secondaryContainer: String?,
    val onSecondaryContainer: String?,
    val tertiary: String?,
    val onTertiary: String?,
    val tertiaryContainer: String?,
    val onTertiaryContainer: String?,
    val error: String?,
    val onError: String?,
    val errorContainer: String?,
    val onErrorContainer: String?,
    val background: String?,
    val onBackground: String?,
    val surface: String?,
    val onSurface: String?,
    val surfaceVariant: String?,
    val onSurfaceVariant: String?,
    val outline: String?,
    val outlineVariant: String?,
    val shadow: String?,
    val scrim: String?,
    val inverseSurface: String?,
    val inverseOnSurface: String?,
    val inversePrimary: String?,
    val primaryFixed: String?,
    val onPrimaryFixed: String?,
    val primaryFixedDim: String?,
    val onPrimaryFixedVariant: String?,
    val secondaryFixed: String?,
    val onSecondaryFixed: String?,
    val secondaryFixedDim: String?,
    val onSecondaryFixedVariant: String?,
    val tertiaryFixed: String?,
    val onTertiaryFixed: String?,
    val tertiaryFixedDim: String?,
    val onTertiaryFixedVariant: String?,
    val surfaceDim: String?,
    val surfaceBright: String?,
    val surfaceContainerLowest: String?,
    val surfaceContainerLow: String?,
    val surfaceContainer: String?,
    val surfaceContainerHigh: String?,
    val surfaceContainerHighest: String?
)