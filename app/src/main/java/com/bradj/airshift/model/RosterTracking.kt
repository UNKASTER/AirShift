package com.bradj.airshift.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 排班的自动跟踪时段。
 *
 * App 只保存一份排班，而它就是用户上班那天的进程单。排班日之外的日子——休息、请假，或者提前一天导入——
 * 用户都不上班；同一航班号在那些日子照常执行，但与用户无关。因此前台自动刷新、后台周期刷新与提醒
 * 只在排班日进行：从首个任务前 [LEAD] 起，到全部任务完成（[isDutyComplete]）为止。
 * 显式的手动下拉不受此限制。
 */
object RosterTracking {
    private const val LEAD_HOURS = 3L

    /** 首个任务前多久开始自动跟踪；与收尾的 [DUTY_COMPLETION_GRACE] 对称。 */
    val LEAD: Duration = Duration.ofHours(LEAD_HOURS)

    /** 自动跟踪的起点；排班没有任何可用时间时返回 null（此时也没有可跟踪的任务）。 */
    fun startsAt(assignments: List<RosterAssignment>): LocalDateTime? =
        assignments.earliestReferenceTime()?.minus(LEAD)

    fun hasStarted(assignments: List<RosterAssignment>, now: LocalDateTime): Boolean =
        startsAt(assignments)?.let { !now.isBefore(it) } ?: false
}

/** 排班自身的日期：最早计划时间所在日。排班一天从清晨排到次日凌晨，最早那项必然落在当天。 */
fun List<RosterAssignment>.rosterDate(): LocalDate? =
    flatMap { listOfNotNull(it.scheduledArrival, it.scheduledDeparture) }.minOrNull()?.toLocalDate()

/** 每个航段以计划时间为准，缺计划时间时退回预计/实际，与自动完成看的时间一致。 */
private fun List<RosterAssignment>.earliestReferenceTime(): LocalDateTime? = flatMap { assignment ->
    listOfNotNull(
        assignment.inboundFlight?.let {
            assignment.scheduledArrival ?: assignment.estimatedArrival ?: assignment.actualArrival
        },
        assignment.outboundFlight?.let {
            assignment.scheduledDeparture ?: assignment.estimatedDeparture ?: assignment.actualDeparture
        },
    )
}.minOrNull()
