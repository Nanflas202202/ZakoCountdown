// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/AppDatabase.kt
package com.errorsiayusulif.zakocountdown.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// --- 版本升级到 7 ---
@Database(entities = [CountdownEvent::class, AgendaBook::class], version = 9, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // --- 定义迁移策略：6 -> 7 ---
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. 创建新的 agenda_books 表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `agenda_books` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `colorHex` TEXT NOT NULL, 
                        `createTime` INTEGER NOT NULL
                    )
                """.trimIndent())

                // 2. 为 countdown_events 表添加 bookId 列
                // SQLite 不支持直接添加带默认值的列并同时也设为可空（虽然我们这里默认就是 NULL）
                // 简单的 ADD COLUMN 即可
                database.execSQL("ALTER TABLE `countdown_events` ADD COLUMN `bookId` INTEGER DEFAULT NULL")
            }
        }

        // --- 【新增】迁移策略：7 -> 8 ---
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 为 agenda_books 表添加 coverImageUri 列
                database.execSQL("ALTER TABLE `agenda_books` ADD COLUMN `coverImageUri` TEXT DEFAULT NULL")
            }
        }
        // --- 【新增】迁移策略：8 -> 9 ---
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `agenda_books` ADD COLUMN `cardAlpha` REAL NOT NULL DEFAULT 1.0")
                database.execSQL("ALTER TABLE `agenda_books` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
            }
        }
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "zako_countdown_database"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9) // 添加新迁移
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}