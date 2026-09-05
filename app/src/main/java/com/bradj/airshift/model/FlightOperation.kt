package com.bradj.airshift.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 同一航班号每天都执行，排班里的那一班只有一个。
 *
 * 与计划时间相差不超过 [MAX_DEVIATION] 的动态才属于这一班；相差更多的（典型是整 24 小时）
 * 是别的日子的同号航班，不能当成这一班的预计/实际时间，否则任务永远完不成、提醒会挪到休息日。
 */
object FlightOperation {
    private const val MAX_DEVIATION_HOURS = 12L

    /** 相邻两天的同号航班相隔 24 小时，取一半作为归属边界。 */
    val MAX_DEVIATION: Duration = Duration.ofHours(MAX_DEVIATION_HOURS)

    fun isSameOperation(scheduled: LocalDateTime, candidate: LocalDateTime): Boolean =
        Duration.between(scheduled, candidate).abs() <= MAX_DEVIATION

    /**
     * 可信的实时时间：有计划时间时只接受同一班的；没有计划时间时无从比对，照单全收。
     * 自动完成与提醒都经由此判断，两处都不能被别的日子的同号航班牵着走。
     */
    fun trusted(scheduled: LocalDateTime?, live: LocalDateTime?): LocalDateTime? =
        live?.takeIf { scheduled == null || isSameOperation(scheduled, it) }

    /**
     * 到达航班的运行日（飞常准按出发日查询，真机核对：MU2418 按到达日 9/5 查到的是 9/5 晚出发、9/6 凌晨到达的下一班）。
     * 凌晨 06:00 前到达的是前一天晚上出发的夜班航班，运行日取前一天；与 [DutyProgressDay] 的执勤日切换共用同一边界。
     */
    fun operationDateOfArrival(arrival: LocalDateTime): LocalDate = DutyProgressDay.of(arrival)
}
