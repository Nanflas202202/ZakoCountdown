// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/Converters.kt
package com.errorsiayusulif.zakocountdown.data

import androidx.room.TypeConverter
import java.util.Date

/**
 * Room 类型转换器
 * 用于在Room不支持的类型和支持的类型之间进行转换。
 */
class Converters {

    /**
     * 将Long类型的时间戳转换为Date对象。
     * Room从数据库读取数据时会调用此方法。
     * @param value 从数据库读取的Long值，可能为空。
     * @return 转换后的Date对象，如果输入为空则返回null。
     */
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    /**
     * 将Date对象转换为Long类型的时间戳。
     * Room向数据库写入数据时会调用此方法。
     * @param date 要写入数据库的Date对象，可能为空。
     * @return 转换后的Long值，如果输入为空则返回null。
     */
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}