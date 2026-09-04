package com.bradj.airshift.model

import java.time.LocalDateTime

/**
 * 当前执勤页使用的纯函数时间计算。
 * 提前量常量同时供 [com.bradj.airshift.reminder.ReminderPolicy] 与 [com.bradj.airshift.model.shift.ShiftBusPlan] 复用，三处规则一致：
 * 到达登机口时间 = 进港实时到达 - 15 分钟；仅出港 = 实时起飞 - 1 小时 10 分钟。
 */
object DutyTimeline {
    /** 进港到位提前量：实时到达前 15 分钟。 */
    const val INBOUND_GATE_ARRIVAL_LEAD_MINUTES = 15L
    /** 纯出港到位提前量：实时起飞前 70 分钟（1 小时 10 分钟）。 */
    const val DEPARTURE_GATE_ARRIVAL_LEAD_MINUTES = 70L
    private const val BOARDING_START_LEAD_MINUTES = 40L
    private const val GATE_CLOSE_LEAD_MINUTES = 15L

    /** 须到达登机口时间；无法计算（无任何时间）时返回 null。 */
    fun gateArrivalTime(assignment: RosterAssignment): LocalDateTime? {
        if (assignment.inboundFlight != null) {
            val arrival = assignment.actualArrival
                ?: assignment.estimatedArrival
                ?: assignment.scheduledArrival
                ?: return null
            return arrival.minusMinutes(INBOUND_GATE_ARRIVAL_LEAD_MINUTES)
        }
        if (assignment.outboundFlight != null) {
            val departure = liveDeparture(assignment) ?: return null
            return departure.minusMinutes(DEPARTURE_GATE_ARRIVAL_LEAD_MINUTES)
        }
        return null
    }

    /** 出港航段预计登机开始 = 实时起飞 - 40 分钟。无出港航段时返回 null。 */
    fun boardingStartTime(assignment: RosterAssignment): LocalDateTime? {
        if (assignment.outboundFlight == null) return null
        return liveDeparture(assignment)?.minusMinutes(BOARDING_START_LEAD_MINUTES)
    }

    /** 出港航段预计登机口关闭 = 实时起飞 - 15 分钟。无出港航段时返回 null。 */
    fun gateCloseTime(assignment: RosterAssignment): LocalDateTime? {
        if (assignment.outboundFlight == null) return null
        return liveDeparture(assignment)?.minusMinutes(GATE_CLOSE_LEAD_MINUTES)
    }

    /** 以实际起飞时间为准：优先实际起飞，未起飞时回退预计，再回退计划。 */
    private fun liveDeparture(assignment: RosterAssignment): LocalDateTime? =
        assignment.actualDeparture
            ?: assignment.estimatedDeparture
            ?: assignment.scheduledDeparture
}
