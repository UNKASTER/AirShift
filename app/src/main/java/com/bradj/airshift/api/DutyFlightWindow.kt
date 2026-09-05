package com.bradj.airshift.api

import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.RosterTracking
import com.bradj.airshift.model.dutyWindowIndices
import com.bradj.airshift.model.rosterDate
import java.time.LocalDate
import java.time.LocalDateTime

internal enum class FlightRefreshScope {
    DUTY_WINDOW,
    ALL_ROSTER,
}

/**
 * 参与刷新的任务下标。
 *
 * `DUTY_WINDOW` 是导入后首刷、前台自动、后台周期与完成补查共用的范围，只在排班日的跟踪时段内非空
 * （见 [RosterTracking]）：上班前、休息日、请假日都不会自动查询。`ALL_ROSTER` 只服务显式的手动下拉，不受时段限制。
 */
internal fun List<RosterAssignment>.refreshIndices(
    manuallyCompletedCount: Int,
    scope: FlightRefreshScope,
    now: LocalDateTime = LocalDateTime.now(),
): List<Int> = when (scope) {
    FlightRefreshScope.DUTY_WINDOW ->
        if (RosterTracking.hasStarted(this, now)) dutyWindowIndices(manuallyCompletedCount, now) else emptyList()
    FlightRefreshScope.ALL_ROSTER -> indices.toList()
}

internal fun List<RosterAssignment>.dutyWindowLookups(
    manuallyCompletedCount: Int,
    now: LocalDateTime = LocalDateTime.now(),
): Set<FlightLookup> = refreshLookups(manuallyCompletedCount, FlightRefreshScope.DUTY_WINDOW, now)

internal fun List<RosterAssignment>.refreshLookups(
    manuallyCompletedCount: Int,
    scope: FlightRefreshScope,
    now: LocalDateTime = LocalDateTime.now(),
): Set<FlightLookup> = buildSet {
    val fallbackDate = lookupFallbackDate(now.toLocalDate())
    refreshIndices(manuallyCompletedCount, scope, now).forEach { index ->
        val assignment = this@refreshLookups[index]
        assignment.inboundFlight?.let { flight ->
            add(FlightLookup.of(flight, assignment.inboundLookupDate(fallbackDate)))
        }
        assignment.outboundFlight?.let { flight ->
            add(FlightLookup.of(flight, assignment.outboundLookupDate(fallbackDate)))
        }
    }
}

/**
 * 没有计划时间的航段按排班日查询，而不是按“今天”：休息日手动下拉时，“今天”会把别的日子的同号航班
 * 当成排班里的这一班。只有整份排班都没有日期时才退回 [today]。
 */
internal fun List<RosterAssignment>.lookupFallbackDate(today: LocalDate): LocalDate = rosterDate() ?: today
