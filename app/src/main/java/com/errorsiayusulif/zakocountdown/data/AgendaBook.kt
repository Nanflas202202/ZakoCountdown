// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/AgendaBook.kt
package com.errorsiayusulif.zakocountdown.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 日程本实体类
 * @param id 主键
 * @param name 日程本名称
 * @param colorHex 标识色 (Hex String, e.g., "#FF0000")
 * @param createTime 创建时间 (用于排序)
 */
@Entity(tableName = "agenda_books")
data class AgendaBook(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    var name: String,
    var colorHex: String,
    val createTime: Long = System.currentTimeMillis()
)