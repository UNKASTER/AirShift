package com.bradj.airshift.reminder

import com.bradj.airshift.model.DutyTimeline
import com.bradj.airshift.model.FlightOperation
import com.bradj.airshift.model.RosterAssignment
import java.time.LocalDateTime

data class ReminderSpec(
    val triggerAt: LocalDateTime,
    val title: String,
    val message: String,
)

/**
 * 每项任务最多一条提醒，时间只来自任务自己的计划时间与同一班的预计时间：
 * 排班是哪天的，提醒就只可能落在哪天；别的日子的同号航班动态不参与（见 [FlightOperation]）。
 */
object ReminderPolicy {
    fun create(assignment: RosterAssignment): ReminderSpec? {
        if (assignment.inboundFlight != null) {
            if (assignment.actualArrival != null) return null
            val arrival = FlightOperation.trusted(assignment.scheduledArrival, assignment.estimatedArrival)
                ?: assignment.scheduledArrival
                ?: return null
            val gate = assignment.arrivalStand?.let { "，到达机位 $it" }.orEmpty()
            return ReminderSpec(
                triggerAt = arrival.minusMinutes(DutyTimeline.INBOUND_GATE_ARRIVAL_LEAD_MINUTES),
                title = "${assignment.inboundFlight} 即将进港",
                message = "预计 15 分钟后落地$gate，请准备接机保障",
            )
        }

        val outbound = assignment.outboundFlight ?: return null
        if (assignment.actualDeparture != null) return null
        val departure = FlightOperation.trusted(assignment.scheduledDeparture, assignment.estimatedDeparture)
            ?: assignment.scheduledDeparture
            ?: return null
        return ReminderSpec(
            triggerAt = departure.minusMinutes(DutyTimeline.DEPARTURE_GATE_ARRIVAL_LEAD_MINUTES),
            title = "$outbound 出港保障提醒",
            message = "计划 1 小时 10 分钟后起飞，请开始出港保障准备",
        )
    }
}
