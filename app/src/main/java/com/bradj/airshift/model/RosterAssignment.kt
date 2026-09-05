package com.bradj.airshift.model

import java.time.Duration
import java.time.LocalDateTime

data class RosterAssignment(
    val aircraftRegistration: String,
    val aircraftType: String?,
    val inboundFlight: String?,
    val origin: String?,
    val scheduledArrival: LocalDateTime?,
    val outboundFlight: String?,
    val destination: String?,
    val scheduledDeparture: LocalDateTime?,
    val assignees: String,
    val estimatedArrival: LocalDateTime? = null,
    val actualArrival: LocalDateTime? = null,
    val estimatedDeparture: LocalDateTime? = null,
    val actualDeparture: LocalDateTime? = null,
    val inboundBoardingGate: String? = null,
    val inboundDepartureStand: String? = null,
    val boardingGate: String? = null,
    val departureStand: String? = null,
    val arrivalStand: String? = null,
    val inboundGateClosedObservedAt: LocalDateTime? = null,
    val outboundGateClosedObservedAt: LocalDateTime? = null,
    val inboundActualOffBlock: LocalDateTime? = null,
    val outboundActualOffBlock: LocalDateTime? = null,
    val outboundArrivalStand: String? = null,
    val arrivalBridge: String? = null,
    val originCode: String? = null,
    val destinationCode: String? = null,
    val localAirportCode: String? = null,
    val localAirportName: String? = null,
    val inboundHasVip: Boolean = false,
    val outboundHasVip: Boolean = false,
) {
    val kind: AssignmentKind
        get() = when {
            inboundFlight != null && outboundFlight != null -> AssignmentKind.TURNAROUND
            inboundFlight != null -> AssignmentKind.ARRIVAL_ONLY
            else -> AssignmentKind.DEPARTURE_ONLY
        }

    val hasVip: Boolean
        get() = inboundHasVip || outboundHasVip

    val stableId: String
        get() = listOf(
            aircraftRegistration,
            inboundFlight.orEmpty(),
            outboundFlight.orEmpty(),
            (scheduledArrival ?: scheduledDeparture)?.toLocalDate()?.toString().orEmpty(),
        ).joinToString("-")
}

enum class AssignmentKind {
    ARRIVAL_ONLY,
    DEPARTURE_ONLY,
    TURNAROUND,
}

/** 航班过点后仍视为"执勤完成"的宽限期（覆盖无实时数据、航班取消等场景）。 */
val DUTY_COMPLETION_GRACE: Duration = Duration.ofHours(3)

private fun isLegComplete(actual: LocalDateTime?, estimated: LocalDateTime?, scheduled: LocalDateTime?, now: LocalDateTime): Boolean {
    if (actual != null) return true
    // 只信同一班的预计时间（FlightOperation）：别的日子的同号航班不能让任务永远完不成。
    // 无任何时间信息时无法跟踪，视为完成。
    val bestKnown = FlightOperation.trusted(scheduled, estimated) ?: scheduled ?: return true
    return now >= bestKnown + DUTY_COMPLETION_GRACE
}

fun RosterAssignment.isDutyComplete(now: LocalDateTime = LocalDateTime.now()): Boolean {
    val inboundComplete = inboundFlight == null ||
        isLegComplete(actualArrival, estimatedArrival, scheduledArrival, now)
    val outboundComplete = outboundFlight == null ||
        isLegComplete(actualDeparture, estimatedDeparture, scheduledDeparture, now)
    return inboundComplete && outboundComplete
}

fun List<RosterAssignment>.nextIncompleteDutyIndex(
    manuallyCompletedCount: Int,
    now: LocalDateTime = LocalDateTime.now(),
): Int {
    val firstEligibleIndex = manuallyCompletedCount.coerceIn(0, size)
    return (firstEligibleIndex until size).firstOrNull { !this[it].isDutyComplete(now) } ?: size
}

fun List<RosterAssignment>.dutyWindowIndices(
    manuallyCompletedCount: Int,
    now: LocalDateTime = LocalDateTime.now(),
): List<Int> {
    val current = nextIncompleteDutyIndex(manuallyCompletedCount, now)
    if (current == size) return emptyList()
    val next = nextIncompleteDutyIndex(current + 1, now)
    return if (next == size) listOf(current) else listOf(current, next)
}

fun List<RosterAssignment>.allDutiesComplete(
    now: LocalDateTime = LocalDateTime.now(),
    manuallyCompletedCount: Int = 0,
): Boolean = isNotEmpty() && nextIncompleteDutyIndex(manuallyCompletedCount, now) == size
