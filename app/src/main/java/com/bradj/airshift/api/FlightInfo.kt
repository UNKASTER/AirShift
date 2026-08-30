package com.bradj.airshift.api

import com.bradj.airshift.model.RosterAssignment
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.abs

data class AirportPoint(
    val code: String,
    val name: String?,
    val latitude: Double?,
    val longitude: Double?,
)

data class FlightInfo(
    val flightNumber: String,
    val origin: AirportPoint?,
    val destination: AirportPoint?,
    val plannedDeparture: LocalDateTime?,
    val estimatedDeparture: LocalDateTime?,
    val actualDeparture: LocalDateTime?,
    val plannedArrival: LocalDateTime?,
    val estimatedArrival: LocalDateTime?,
    val actualArrival: LocalDateTime?,
    val actualOffBlock: LocalDateTime?,
    val gateClosedObservedAt: LocalDateTime?,
    val boardingGate: String?,
    val departureStand: String?,
    val arrivalStand: String?,
    val arrivalBridge: String?,
)

internal data class FlightLookup(
    val flightNumber: String,
    val date: LocalDate,
) {
    companion object {
        fun of(flightNumber: String, date: LocalDate): FlightLookup {
            val normalized = flightNumber.trim().uppercase(Locale.ROOT)
            require(Regex("[A-Z]{2,3}\\d{3,4}").matches(normalized)) { "航班号格式无效" }
            return FlightLookup(normalized, date)
        }
    }
}

internal fun RosterAssignment.withLiveInfo(
    live: Map<FlightLookup, List<FlightInfo>>,
    fallbackDate: LocalDate,
): RosterAssignment {
    val inboundLookup = inboundFlight?.let { flight ->
        FlightLookup.of(flight, scheduledArrival?.toLocalDate() ?: fallbackDate)
    }
    val outboundLookup = outboundFlight?.let { flight ->
        FlightLookup.of(flight, scheduledDeparture?.toLocalDate() ?: fallbackDate)
    }
    // Same flight number on both sides means the flight passes through our station.
    val throughFlight = inboundLookup != null && inboundLookup == outboundLookup
    val inbound = selectInboundLeg(
        legs = inboundLookup?.let { live[it] },
        scheduledArrival = scheduledArrival,
        localAirportCode = localAirportCode,
        throughFlight = throughFlight,
    )
    val outbound = selectOutboundLeg(
        legs = outboundLookup?.let { live[it] },
        scheduledDeparture = scheduledDeparture,
        localAirportCode = localAirportCode,
        throughFlight = throughFlight,
    )
    val localAirport = inbound?.destination ?: outbound?.origin
    return copy(
        origin = inbound?.origin?.name ?: origin,
        destination = outbound?.destination?.name ?: destination,
        originCode = inbound?.origin?.code ?: originCode,
        destinationCode = outbound?.destination?.code ?: destinationCode,
        localAirportCode = localAirport?.code ?: localAirportCode,
        localAirportName = localAirport?.name ?: localAirportName,
        scheduledArrival = scheduledArrival ?: inbound?.plannedArrival,
        scheduledDeparture = scheduledDeparture ?: outbound?.plannedDeparture,
        estimatedArrival = inbound?.estimatedArrival ?: estimatedArrival,
        actualArrival = inbound?.actualArrival ?: actualArrival,
        estimatedDeparture = outbound?.estimatedDeparture ?: estimatedDeparture,
        actualDeparture = outbound?.actualDeparture ?: actualDeparture,
        inboundBoardingGate = inbound?.boardingGate ?: inboundBoardingGate,
        inboundDepartureStand = inbound?.departureStand ?: inboundDepartureStand,
        boardingGate = outbound?.boardingGate ?: boardingGate,
        departureStand = outbound?.departureStand ?: departureStand,
        arrivalStand = inbound?.arrivalStand ?: arrivalStand,
        inboundGateClosedObservedAt = inbound?.gateClosedObservedAt ?: inboundGateClosedObservedAt,
        outboundGateClosedObservedAt = outbound?.gateClosedObservedAt ?: outboundGateClosedObservedAt,
        inboundActualOffBlock = inbound?.actualOffBlock ?: inboundActualOffBlock,
        outboundActualOffBlock = outbound?.actualOffBlock ?: outboundActualOffBlock,
        outboundArrivalStand = outbound?.arrivalStand ?: outboundArrivalStand,
        arrivalBridge = inbound?.arrivalBridge ?: arrivalBridge,
    )
}

// A stopover flight (e.g. DNH→LHW→PKX) yields one FlightInfo per leg; the roster's
// schedule identifies the leg serving our station. Prefer schedule matching over the
// stored local airport, which may have been derived from a wrong whole-route leg.
private fun selectInboundLeg(
    legs: List<FlightInfo>?,
    scheduledArrival: LocalDateTime?,
    localAirportCode: String?,
    throughFlight: Boolean,
): FlightInfo? {
    if (legs.isNullOrEmpty()) return null
    if (legs.size == 1) return legs.first()
    closestByTime(legs, scheduledArrival) { it.plannedArrival ?: it.estimatedArrival ?: it.actualArrival }
        ?.let { return it }
    localAirportCode?.let { code ->
        legs.firstOrNull { it.destination?.code == code }?.let { return it }
    }
    if (throughFlight) {
        // The inbound leg ends at the stopover where the outbound leg continues.
        legs.lastOrNull { leg ->
            val code = leg.destination?.code
            code != null && legs.any { it !== leg && it.origin?.code == code }
        }?.let { return it }
    }
    return legs.last()
}

private fun selectOutboundLeg(
    legs: List<FlightInfo>?,
    scheduledDeparture: LocalDateTime?,
    localAirportCode: String?,
    throughFlight: Boolean,
): FlightInfo? {
    if (legs.isNullOrEmpty()) return null
    if (legs.size == 1) return legs.first()
    closestByTime(legs, scheduledDeparture) { it.plannedDeparture ?: it.estimatedDeparture ?: it.actualDeparture }
        ?.let { return it }
    localAirportCode?.let { code ->
        legs.firstOrNull { it.origin?.code == code }?.let { return it }
    }
    if (throughFlight) {
        // The outbound leg starts at the stopover where the inbound leg arrived.
        legs.firstOrNull { leg ->
            val code = leg.origin?.code
            code != null && legs.any { it !== leg && it.destination?.code == code }
        }?.let { return it }
    }
    return legs.first()
}

private fun closestByTime(
    legs: List<FlightInfo>,
    scheduled: LocalDateTime?,
    time: (FlightInfo) -> LocalDateTime?,
): FlightInfo? {
    if (scheduled == null) return null
    return legs
        .mapNotNull { leg -> time(leg)?.let { leg to it } }
        .minByOrNull { (_, legTime) -> abs(Duration.between(scheduled, legTime).toMinutes()) }
        ?.first
}
