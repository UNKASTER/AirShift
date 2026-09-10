package com.bradj.airshift.reminder

import com.bradj.airshift.data.RosterRepository
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.shift.LearnedTimes
import com.bradj.airshift.model.shift.ShiftCalendarRows
import com.bradj.airshift.model.shift.ShiftRosterBridge
import com.bradj.airshift.model.shift.ShiftSchedule
import com.bradj.airshift.model.shift.ShiftTeam
import com.bradj.airshift.model.shift.ShuttleAlarmDay
import com.bradj.airshift.model.shift.ShuttleAlarmPlan
import java.time.LocalDate

/** 与排班日历同一组输入（[ShiftCalendarRows.build]），界面从状态取、后台从存储取。 */
internal data class ShuttleAlarmInputs(
    val schedule: ShiftSchedule,
    val groupId: Int?,
    val assignments: List<RosterAssignment>,
    val marginMinutes: Int,
    val learned: LearnedTimes,
)

/** 从排班日历得到接下来几天的闹铃序列；下标 0 是今天。班组解析不到时全为 null。 */
internal object ShuttleAlarmSource {
    fun window(
        inputs: ShuttleAlarmInputs,
        today: LocalDate,
        horizonDays: Int = ShuttleAlarmPolicy.HORIZON_DAYS,
    ): List<ShuttleAlarmDay?> {
        val groupId = inputs.groupId ?: return List(horizonDays + 1) { null }
        return ShiftCalendarRows.build(
            schedule = inputs.schedule,
            groupId = groupId,
            from = today,
            toInclusive = today.plusDays(horizonDays.toLong()),
            today = today,
            rosterDate = ShiftRosterBridge.rosterDate(inputs.assignments),
            rosterReportByMinutes = ShiftRosterBridge.reportByMinutes(inputs.assignments),
            rosterLastTaskMinutes = ShiftRosterBridge.lastTaskMinutes(inputs.assignments),
            marginMinutes = inputs.marginMinutes,
            learned = inputs.learned,
        ).map(ShuttleAlarmPlan::fromRow)
    }

    fun window(store: RosterRepository, today: LocalDate): List<ShuttleAlarmDay?> = window(inputs(store), today)

    /** 与 DutyViewModel.recordShiftTimes / AirShiftApp 同一套装配规则。 */
    fun inputs(store: RosterRepository): ShuttleAlarmInputs {
        val schedule = ShiftSchedule(store.shiftCalibration, fallbackTeam = store.manualShiftTeam ?: ShiftTeam.FIRST)
        return ShuttleAlarmInputs(
            schedule = schedule,
            groupId = schedule.resolveGroupId(store.userName.orEmpty(), store.manualShiftGroup),
            assignments = store.loadSnapshot().assignments,
            marginMinutes = store.shiftReportMarginMinutes,
            learned = store.shiftTimeHistory.learnedTimes(schedule.team),
        )
    }
}
