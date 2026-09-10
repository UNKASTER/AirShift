package com.bradj.airshift.model.shift

import java.time.LocalDate
import java.time.LocalTime

/** 某一天要写进系统时钟的叫醒序列；[times] 为空表示这天的班车早于闹铃下限，"无闹铃"。 */
data class ShuttleAlarmDay(
    val date: LocalDate,
    val departure: LocalTime,
    val times: List<LocalTime>,
) {
    val isEmpty: Boolean get() = times.isEmpty()
    val first: LocalTime? get() = times.firstOrNull()
    val last: LocalTime? get() = times.lastOrNull()
}

/**
 * 班车闹铃规则（2026-09-09 用户拍板）：
 * - 07:00 前发车：发车前 30 分钟起每 5 分钟一响；07:00 起发车：发车前 60 分钟起每 10 分钟一响；
 * - 发车前一格即止，发车时刻本身不响；
 * - 最早不早于 05:00，更早的时刻直接丢弃——04:50 班车因此整段为空。
 * 发车最早 04:50、提前最多 60 分钟，闹铃永远落在班车同一天，不会绕零点。
 */
object ShuttleAlarmPlan {
    private const val EARLIEST_HOUR = 5
    private const val BOUNDARY_HOUR = 7

    /** 闹铃下限。 */
    val EARLIEST: LocalTime = LocalTime.of(EARLIEST_HOUR, 0)

    /** 发车早于它走"早"档；整点归"晚"档。 */
    val EARLY_BOUNDARY: LocalTime = LocalTime.of(BOUNDARY_HOUR, 0)

    const val EARLY_LEAD_MINUTES = 30
    const val EARLY_STEP_MINUTES = 5
    const val LATE_LEAD_MINUTES = 60
    const val LATE_STEP_MINUTES = 10

    /** 时钟按 小时+分钟+重复星期+名称 匹配已有条目并重新启用：名称恒定，条目数就只随不同时刻增长。 */
    const val LABEL = "航勤智排·班车"

    fun alarmTimes(departure: LocalTime): List<LocalTime> {
        val early = departure < EARLY_BOUNDARY
        val lead = if (early) EARLY_LEAD_MINUTES else LATE_LEAD_MINUTES
        val step = if (early) EARLY_STEP_MINUTES else LATE_STEP_MINUTES
        val departureMinutes = ShiftClock.of(departure)
        val floor = ShiftClock.of(EARLIEST)
        return (departureMinutes - lead until departureMinutes step step)
            .filter { it >= floor }
            .map(ShiftClock::toLocalTime)
    }

    /** 休息、不到岗或没有合适班车的日子没有序列。 */
    fun fromRow(row: ShiftCalendarRow): ShuttleAlarmDay? {
        val bus = row.bus?.takeIf { row.day.attends } ?: return null
        return ShuttleAlarmDay(row.day.date, bus.departure, alarmTimes(bus.departure))
    }
}
