// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/LogEntry.kt
package com.errorsiayusulif.zakocountdown.data

data class LogEntry(
    val level: String, // V, D, I, W, E
    val time: String,
    val tag: String,
    val message: String,
    val color: Int // 用于UI显示颜色
)