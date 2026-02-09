#!/usr/bin/env kotlin

// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/CountdownEvent.kt

package com.errorsiayusulif.zakocountdown.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 定义倒数日事件的数据实体类 (Entity)。
 * 这个类既是我们在代码中使用的对象，也对应着数据库中的一张表。
 * @param id 主键，自动增长，确保每个事件都有唯一的ID。
 * @param title 事件的标题，例如 "毕业典礼"。
 * @param targetDate 目标日期和时间。我们使用Date类型来存储。
 * @param isImportant 标记是否为重点事件，用于后续的弹窗提醒功能。
 * @param creationDate 事件创建的日期，方便排序或追溯。
 * @param colorHex 卡片的自定义颜色，以16进制字符串形式存储（例如 "#FF0000"）。
 * @param backgroundUri 卡片的自定义背景图URI，以字符串形式存储。
 * @param isPinned 是否置顶。
 */
@Entity(tableName = "countdown_events") // @Entity注解表明这是一个Room数据库的表，tableName指定了表名。
data class CountdownEvent(
    @PrimaryKey(autoGenerate = true) // @PrimaryKey指定id是主键，autoGenerate = true表示ID由数据库自动生成。
    var id: Long = 0, // <-- 从 val 修改为 var
    var title: String,
    var targetDate: Date,
    var isImportant: Boolean = false,
    var creationDate: Date = Date(), // 默认为当前创建时间
    var colorHex: String? = null,    // 可为空，表示使用默认颜色
    var backgroundUri: String? = null, // 可为空，表示没有背景图
    var isPinned: Boolean = false,
    // --- 【新功能】添加显示模式字段 ---
    var displayMode: String = DISPLAY_MODE_SIMPLE,
    // data/CountdownEvent.kt
    var cardAlpha: Float? = null, // 添加这个字段
    // --- 【新增】所属日程本ID ---
    // null 代表“默认/全部”分类，不属于任何特定自定义日程本
    var bookId: Long? = null
) {
    companion object {
            const val DISPLAY_MODE_SIMPLE = "SIMPLE" // 只显示总天数
            const val DISPLAY_MODE_DETAILED = "DETAILED" // 显示 dd:hh:mm:ss
            const val DISPLAY_MODE_FULL = "FULL" // 显示 yy:mm:ww:dd:mm:ss
    }
}