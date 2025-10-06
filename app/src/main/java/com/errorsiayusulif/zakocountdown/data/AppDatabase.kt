// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/AppDatabase.kt
package com.errorsiayusulif.zakocountdown.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import java.util.Date

// --- 【修复】将 version 从 1 修改为 2 ---
@Database(entities = [CountdownEvent::class], version = 6, exportSchema = false) // <-- 版本升到 3
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "zako_countdown_database"
                )
                    // --- 【修复】添加这一行，允许破坏性迁移 ---
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}