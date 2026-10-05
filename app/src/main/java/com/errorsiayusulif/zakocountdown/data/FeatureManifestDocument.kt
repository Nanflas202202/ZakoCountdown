// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/FeatureManifestDocument.kt
package com.errorsiayusulif.zakocountdown.data

import com.google.gson.annotations.SerializedName

/**
 * 写进 EYF 备份包的 `DocumentFeatures.json`。
 *
 * 用途：
 *  1. 让用户/维护者一眼看出「这个 0.9.1-debug 的包到底带哪些功能的数据」；
 *  2. 让导入端不必只看 internalCode 猜来猜去，可以直接按功能键处理；
 *  3. 把导出时使用的 **升级代号** 记进档案，方便回溯「这个是哪一版导出的」。
 *
 * 设计取舍：功能名按导出时的界面语言本地化，由调用方传入 [zh] 决定用中文还是英文名，
 * 这样中文用户的备份包里是可读的中文，英文用户看到的是英文 —— 只存一份，不重复。
 */
data class FeatureManifestDocument(
    @SerializedName("schema") val schema: String = "zako.features/1.0",
    @SerializedName("target_version_name") val targetVersionName: String,
    @SerializedName("target_internal_code") val targetInternalCode: Int,
    @SerializedName("upgrade_code") val upgradeCode: Int,
    @SerializedName("upgrade_code_label") val upgradeCodeLabel: String,
    @SerializedName("export_time") val exportTime: Long = System.currentTimeMillis(),
    @SerializedName("feature_count") val featureCount: Int,
    @SerializedName("features") val features: List<FeatureEntry>
) {
    data class FeatureEntry(
        @SerializedName("feature_key") val featureKey: String,
        @SerializedName("display_name") val displayName: String,
        @SerializedName("since_internal_code") val sinceInternalCode: Int,
        @SerializedName("settings_keys") val settingsKeys: List<String>
    )

    companion object {
        /**
         * 按目标版本生成功能清单。
         *
         * @param target 导出时选择的目标兼容版本
         * @param upgradeCode 当前构建的升级代号
         * @param upgradeCodeLabel 升级代号的可读标签
         * @param zh true 时功能名用中文，false 用英文
         */
        fun build(
            target: AppVersion,
            upgradeCode: Int,
            upgradeCodeLabel: String,
            zh: Boolean
        ): FeatureManifestDocument {
            val exportable = FeatureRegistry.exportableFor(target.internalCode)
            return FeatureManifestDocument(
                targetVersionName = target.versionName,
                targetInternalCode = target.internalCode,
                upgradeCode = upgradeCode,
                upgradeCodeLabel = upgradeCodeLabel,
                featureCount = exportable.size,
                features = exportable.map { f ->
                    FeatureEntry(
                        featureKey = f.key,
                        displayName = if (zh) f.displayNameZh else f.displayNameEn,
                        sinceInternalCode = f.sinceCode,
                        settingsKeys = f.settingsKeys
                    )
                }
            )
        }
    }
}
