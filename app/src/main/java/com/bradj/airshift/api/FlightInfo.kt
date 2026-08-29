package com.bradj.airshift.api

import com.bradj.airshift.model.RosterAssignment
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

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
    live: Map<FlightLookup, FlightInfo>,
    fallbackDate: LocalDate,
): RosterAssignment {
    val inbound = inboundFlight?.let { flight ->
        live[FlightLookup.of(flight, scheduledArrival?.toLocalDate() ?: fallbackDate)]
    }
    val outbound = outboundFlight?.let { flight ->
        live[FlightLookup.of(flight, scheduledDeparture?.toLocalDate() ?: fallbackDate)]
    }
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
