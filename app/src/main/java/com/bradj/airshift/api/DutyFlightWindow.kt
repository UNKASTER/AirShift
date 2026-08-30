package com.bradj.airshift.api

import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.dutyWindowIndices
import java.time.LocalDateTime

internal enum class FlightRefreshScope {
    DUTY_WINDOW,
    ALL_ROSTER,
}

internal fun List<RosterAssignment>.refreshIndices(
    manuallyCompletedCount: Int,
    scope: FlightRefreshScope,
    now: LocalDateTime = LocalDateTime.now(),
): List<Int> = when (scope) {
    FlightRefreshScope.DUTY_WINDOW -> dutyWindowIndices(manuallyCompletedCount, now)
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
    refreshIndices(manuallyCompletedCount, scope, now).forEach { index ->
        val assignment = this@refreshLookups[index]
        assignment.inboundFlight?.let { flight ->
            add(FlightLookup.of(flight, assignment.scheduledArrival?.toLocalDate() ?: now.toLocalDate()))
        }
        assignment.outboundFlight?.let { flight ->
            add(FlightLookup.of(flight, assignment.scheduledDeparture?.toLocalDate() ?: now.toLocalDate()))
        }
    }
}
