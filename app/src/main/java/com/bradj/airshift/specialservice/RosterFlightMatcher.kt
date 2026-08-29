package com.bradj.airshift.specialservice

import com.bradj.airshift.model.RosterAssignment
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class FlightMatchResult(
    val matched: FlightReference?,
    val suggestions: List<FlightReference>,
)

object RosterFlightMatcher {
    fun index(
        assignments: List<RosterAssignment>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<FlightReference> = assignments.flatMap { assignment ->
        buildList {
            assignment.inboundFlight?.let { flight ->
                val scheduled = assignment.scheduledArrival
                if (scheduled != null) {
                    add(
                        FlightReference(
                            flightNumber = normalizeFlight(flight),
                            operationDate = scheduled.toLocalDate(),
                            expiresAtEpochMillis = expiresAtEpochMillis(
                                assignment.actualArrival ?: assignment.estimatedArrival ?: scheduled,
                                zoneId,
                            ),
                        ),
                    )
                }
            }
            assignment.outboundFlight?.let { flight ->
                val scheduled = assignment.scheduledDeparture
                if (scheduled != null) {
                    add(
                        FlightReference(
                            flightNumber = normalizeFlight(flight),
                            operationDate = scheduled.toLocalDate(),
                            expiresAtEpochMillis = expiresAtEpochMillis(
                                assignment.actualDeparture ?: assignment.estimatedDeparture ?: scheduled,
                                zoneId,
                            ),
                        ),
                    )
                }
            }
        }
    }.filter { it.flightNumber.isNotBlank() }
        .distinctBy(FlightReference::key)
        .sortedWith(compareBy(FlightReference::operationDate, FlightReference::flightNumber))

    fun match(
        candidate: ParsedServiceCandidate,
        assignments: List<RosterAssignment>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): FlightMatchResult = match(candidate, index(assignments, zoneId))

    fun match(
        candidate: ParsedServiceCandidate,
        flights: List<FlightReference>,
    ): FlightMatchResult = matchFlightToken(
        flightToken = candidate.flightToken,
        explicitDate = candidate.explicitDate,
        notificationDate = candidate.notificationDate,
        flights = flights,
    )

    fun matchFlightToken(
        flightToken: String,
        explicitDate: LocalDate?,
        notificationDate: LocalDate,
        flights: List<FlightReference>,
    ): FlightMatchResult {
        val token = normalizeFlight(flightToken)
        val digits = token.filter(Char::isDigit)
        if (digits.length !in 3..4) return FlightMatchResult(null, emptyList())
        val tokenMatches = flights.filter { flight ->
            flight.flightNumber.filter(Char::isDigit) == digits
        }
        if (tokenMatches.isEmpty()) return FlightMatchResult(null, emptyList())
        val exactCarrierMatches = tokenMatches.filter { it.flightNumber == token }
            .takeIf { token.any(Char::isLetter) && it.isNotEmpty() }
        val candidates = exactCarrierMatches ?: tokenMatches
        val targetDate = explicitDate ?: notificationDate
        val selected = candidates.minWithOrNull(
            compareBy<FlightReference>(
                { abs(ChronoUnit.DAYS.between(targetDate, it.operationDate)) },
                { it.operationDate.isBefore(targetDate) },
                { it.operationDate },
                { it.flightNumber },
            ),
        )
        return FlightMatchResult(selected, candidates)
    }

    fun normalizeFlight(value: String): String {
        val normalized = value.trim().uppercase().replace(" ", "")
        return if (normalized.startsWith("CES")) "MU${normalized.removePrefix("CES")}" else normalized
    }

    private fun expiresAtEpochMillis(value: LocalDateTime, zoneId: ZoneId): Long =
        value.atZone(zoneId).toInstant().plus(Duration.ofHours(24)).toEpochMilli()
}
