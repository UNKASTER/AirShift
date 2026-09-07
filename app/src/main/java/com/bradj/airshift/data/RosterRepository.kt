package com.bradj.airshift.data

import com.bradj.airshift.api.FlightInfo
import com.bradj.airshift.api.FlightLookup
import com.bradj.airshift.api.FlightRefreshScope
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.shift.ManualShiftGroup
import com.bradj.airshift.model.shift.ShiftCalibration
import com.bradj.airshift.model.shift.ShiftTeam
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 编排层（[com.bradj.airshift.duty.DutyViewModel]）看到的排班存储。
 * 生产实现是 [RosterStore]；JVM 测试用内存实现替换，因此这里只列出编排层真正用到的成员。
 */
internal interface RosterRepository {
    var userName: String?
    var variFlightApiKey: String?
    val hasVariFlightApiKey: Boolean
    fun clearVariFlightApiKey()

    var shiftReportMarginMinutes: Int

    /** 手动大组：只在没有任何校准表时决定日历按哪一大组计算。 */
    var manualShiftTeam: ShiftTeam?
    var manualShiftGroup: ManualShiftGroup?
    var shiftCalibration: ShiftCalibration?

    val currentDutyIndex: Int
    val rosterGeneration: Long
    fun loadSnapshot(): RosterSnapshot
    fun replaceAssignments(assignments: List<RosterAssignment>): Long

    fun completeCurrentDuty(expectedGeneration: Long, expectedDutyIndex: Int, now: LocalDateTime): DutyCompletion?

    fun mergeLiveInfoIfGeneration(
        live: Map<FlightLookup, List<FlightInfo>>,
        expectedGeneration: Long,
        fallbackDate: LocalDate,
        refreshedAtEpochMillis: Long? = null,
        scope: FlightRefreshScope = FlightRefreshScope.DUTY_WINDOW,
    ): RosterSnapshot?

    fun runIfGenerationCurrent(expectedGeneration: Long, action: () -> Unit): Boolean
}
