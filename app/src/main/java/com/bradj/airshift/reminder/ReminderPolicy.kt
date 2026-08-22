package com.bradj.airshift.reminder

import com.bradj.airshift.model.RosterAssignment
import java.time.LocalDateTime

data class ReminderSpec(
    val triggerAt: LocalDateTime,
    val title: String,
    val message: String,
)

object ReminderPolicy {
    fun create(assignment: RosterAssignment): ReminderSpec? {
        if (assignment.inboundFlight != null) {
            if (assignment.actualArrival != null) return null
            val arrival = assignment.estimatedArrival ?: assignment.scheduledArrival ?: return null
            val gate = assignment.arrivalGate?.let { "，到达桥位/机位 $it" }.orEmpty()
            return ReminderSpec(
                triggerAt = arrival.minusMinutes(10),
                title = "${assignment.inboundFlight} 即将进港",
                message = "预计 10 分钟后落地$gate，请准备接机保障",
            )
        }

        val outbound = assignment.outboundFlight ?: return null
        if (assignment.actualDeparture != null) return null
        val departure = assignment.estimatedDeparture ?: assignment.scheduledDeparture ?: return null
        return ReminderSpec(
            triggerAt = departure.minusHours(1),
            title = "$outbound 出港保障提醒",
            message = "计划 1 小时后起飞，请开始出港保障准备",
        )
    }
}
