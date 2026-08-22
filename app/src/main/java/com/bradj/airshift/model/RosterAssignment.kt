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
    val arrivalGate: String? = null,
    val arrivalBridge: String? = null,
) {
    val kind: AssignmentKind
        get() = when {
            inboundFlight != null && outboundFlight != null -> AssignmentKind.TURNAROUND
            inboundFlight != null -> AssignmentKind.ARRIVAL_ONLY
            else -> AssignmentKind.DEPARTURE_ONLY
        }

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
