package com.bradj.airshift.model.shift

import com.bradj.airshift.model.DutyTimeline
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.rosterDate
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 把已导入的当日排班接到排班日历上。
 *
 * App 只保存一份当前排班，因此只有当排班自身的日期等于日历某一行的日期时，
 * 该行才能用真实航班时间代替历史规律推算。到位时间直接复用
 * [DutyTimeline.gateArrivalTime]（进港提前 15 分钟、纯出港提前 70 分钟），不另立规则。
 */
object ShiftRosterBridge {

    /** 排班自身的日期，即 [rosterDate]：取最早的计划时间所在日。 */
    fun rosterDate(assignments: List<RosterAssignment>): LocalDate? = assignments.rosterDate()

    /**
     * 首个任务的到位时间，以排班日 00:00 起的分钟数表示；无可计算任务时返回 null。
     * 早于当日零点（理论上的隔夜到位）会返回负数，交由调用方判断。
     */
    fun reportByMinutes(assignments: List<RosterAssignment>): Int? {
        val date = rosterDate(assignments) ?: return null
        val earliest = assignments.mapNotNull(DutyTimeline::gateArrivalTime).minOrNull() ?: return null
        return minutesFrom(date, earliest)
    }

    /**
     * 最后一项任务的计划时间，以排班日 00:00 起的分钟数表示。
     * 夜班的次日凌晨航班天然大于 1440，与 [ShiftClock] 的表示一致。
     */
    fun lastTaskMinutes(assignments: List<RosterAssignment>): Int? {
        val date = rosterDate(assignments) ?: return null
        val latest = assignments.mapNotNull(::scheduledEnd).maxOrNull() ?: return null
        return minutesFrom(date, latest)
    }

    private fun scheduledEnd(assignment: RosterAssignment): LocalDateTime? =
        listOfNotNull(assignment.scheduledArrival, assignment.scheduledDeparture).maxOrNull()

    private fun minutesFrom(date: LocalDate, moment: LocalDateTime): Int =
        Duration.between(date.atStartOfDay(), moment).toMinutes().toInt()
}
