package com.bradj.airshift.model

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
    val boardingGate: String? = null,
    val departureStand: String? = null,
    val arrivalStand: String? = null,
    val inboundGateClosedObservedAt: LocalDateTime? = null,
    val outboundGateClosedObservedAt: LocalDateTime? = null,
    val inboundActualOffBlock: LocalDateTime? = null,
    val outboundActualOffBlock: LocalDateTime? = null,
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
