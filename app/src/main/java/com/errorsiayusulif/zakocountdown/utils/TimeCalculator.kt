// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/TimeCalculator.kt
package com.errorsiayusulif.zakocountdown.utils

import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

object TimeCalculator {

    data class TimeDifference(
        val years: Int,
        val months: Int,
        val weeks: Int,
        val daysInWeek: Int, // 一周中的天数 (0-6)
        val totalDays: Long, // 总天数
        val hours: Long,
        val minutes: Long,
        val seconds: Long,
        val isPast: Boolean
    )

    fun calculateDifference(targetDate: Date, nowDate: Date = Date()): TimeDifference {
        val diffInMillis = targetDate.time - nowDate.time
        val isPast = diffInMillis < 0

        val startCal = Calendar.getInstance()
        val endCal = Calendar.getInstance()

        if (isPast) {
            startCal.time = targetDate
            endCal.time = nowDate
        } else {
            startCal.time = nowDate
            endCal.time = targetDate
        }

        var years = endCal.get(Calendar.YEAR) - startCal.get(Calendar.YEAR)
        var months = endCal.get(Calendar.MONTH) - startCal.get(Calendar.MONTH)
        var daysOfMonth = endCal.get(Calendar.DAY_OF_MONTH) - startCal.get(Calendar.DAY_OF_MONTH)

        if (daysOfMonth < 0) {
            months--
            daysOfMonth += startCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
        if (months < 0) {
            years--
            months += 12
        }

        val weeks = daysOfMonth / 7
        val daysInWeekValue = daysOfMonth % 7

        val totalDaysValue = kotlin.math.abs(TimeUnit.MILLISECONDS.toDays(diffInMillis))
        val diffForHms = kotlin.math.abs(diffInMillis)
        val hours = TimeUnit.MILLISECONDS.toHours(diffForHms) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffForHms) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(diffForHms) % 60

        return TimeDifference(
            years = years,
            months = months,
            weeks = weeks,
            daysInWeek = daysInWeekValue,
            totalDays = totalDaysValue,
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            isPast = isPast
        )
    }
}