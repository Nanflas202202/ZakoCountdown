// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ZakoCountdownApplication.kt
package com.errorsiayusulif.zakocountdown

import android.app.Application
import android.util.Log
import com.errorsiayusulif.zakocountdown.data.AppDatabase
import com.errorsiayusulif.zakocountdown.data.EventRepository
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.utils.LogRecorder
import com.errorsiayusulif.zakocountdown.utils.ServiceGuardian

class ZakoCountdownApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val repository: EventRepository by lazy { EventRepository(database.eventDao()) }
    val preferenceManager: PreferenceManager by lazy { PreferenceManager(this) }

    override fun onCreate() {
        super.onCreate()
        Log.d("App", "Application.onCreate. Summoning ServiceGuardian.")
        // 直接在这里调用，不再通过 Application 实例
        ServiceGuardian.ensureServicesAreRunning(this)
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