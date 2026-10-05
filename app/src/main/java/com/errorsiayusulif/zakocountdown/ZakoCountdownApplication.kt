// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ZakoCountdownApplication.kt
package com.errorsiayusulif.zakocountdown

import android.app.Application
import android.util.Log
import com.errorsiayusulif.zakocountdown.data.AppDatabase
import com.errorsiayusulif.zakocountdown.data.EventRepository
import com.errorsiayusulif.zakocountdown.data.PreferenceKeys
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.utils.LocaleHelper
import com.errorsiayusulif.zakocountdown.utils.LogRecorder
import com.errorsiayusulif.zakocountdown.utils.ServiceGuardian

class ZakoCountdownApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val repository: EventRepository by lazy { EventRepository(database.eventDao()) }
    val preferenceManager: PreferenceManager by lazy { PreferenceManager(this) }

    override fun onCreate() {
        super.onCreate()

        // --- v0.9.1 键名语义化：把旧键（key_xxx / enable_xxx）一次性迁移到新键，保证设置不丢 ---
        val migrated = PreferenceKeys.migrateLegacyKeys(this)
        if (migrated > 0) Log.i("App", "Migrated $migrated legacy preference keys to semantic names.")

        // --- v0.9.1 多语言：把用户选择的语言同步给 AppCompat / 平台 per-app locale ---
        LocaleHelper.syncFromStorage(this)

        Log.d("App", "Application.onCreate. Summoning ServiceGuardian.")
        // 直接在这里调用，不再通过 Application 实例
        ServiceGuardian.ensureServicesAreRunning(this)
        // --- v0.9.1 修复：首次启动时填充默认更新节点池，避免导出/开发者选项一片空白 ---
        preferenceManager.ensureDefaultUpdateUrls()
        // --- 【新功能】检查日志持久化 ---
        if (preferenceManager.isLogPersistenceEnabled()) {
            LogRecorder.startRecording(this)
        }
    }
    override fun onTerminate() {
        super.onTerminate()
        LogRecorder.stopRecording()
    }
}