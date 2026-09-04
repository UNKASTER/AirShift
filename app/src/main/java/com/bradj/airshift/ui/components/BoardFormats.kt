package com.bradj.airshift.ui.components

import java.time.Duration
import java.time.LocalDate

private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")
private const val MINUTES_PER_HOUR = 60L

/** 板头日期："9月4日 周五"。 */
fun LocalDate.boardDateText(): String = "${monthValue}月${dayOfMonth}日 周${WEEKDAYS[dayOfWeek.value - 1]}"

/** 剩余时长文案："2 小时 33 分" / "2 小时" / "57 分"。 */
fun Duration.remainingText(): String {
    val totalMinutes = toMinutes()
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return when {
        hours > 0 && minutes > 0 -> "$hours 小时 $minutes 分"
        hours > 0 -> "$hours 小时"
        else -> "$minutes 分"
    }
}
