package com.bradj.airshift.specialservice

import com.bradj.airshift.model.RosterAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RosterFlightMatcherTest {
    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val notificationDate = LocalDate.of(2026, 8, 26)

    @Test
    fun numericShorthandMatchesOnlyUniqueCompleteNumericPart() {
        val flights = listOf(
            flight("MU2473", notificationDate),
            flight("QZ4001", notificationDate),
        )
        assertEquals("MU2473", RosterFlightMatcher.match(candidate("2473"), flights).matched?.flightNumber)
        assertNull(RosterFlightMatcher.match(candidate("473"), flights).matched)
    }

    @Test
    fun numericPartWithMultipleCarriersIsDeterministicallyAutoMatched() {
        val result = RosterFlightMatcher.match(
            candidate("2473"),
            listOf(flight("MU2473", notificationDate), flight("CA2473", notificationDate)),
        )

        assertEquals("CA2473", result.matched?.flightNumber)
        assertEquals(2, result.suggestions.size)
        assertEquals(
            "MU2473",
            RosterFlightMatcher.match(candidate("MU2473"), result.suggestions).matched?.flightNumber,
        )
    }

    @Test
    fun explicitDateWinsThenNotificationAndAdjacentDatesAreUsed() {
        val flights = listOf(
            flight("MU2473", notificationDate.minusDays(1)),
            flight("MU2473", notificationDate),
            flight("MU2473", notificationDate.plusDays(1)),
        )
        assertEquals(notificationDate.plusDays(1), RosterFlightMatcher.match(candidate("MU2473", notificationDate.plusDays(1)), flights).matched?.operationDate)
        assertEquals(notificationDate, RosterFlightMatcher.match(candidate("MU2473"), flights).matched?.operationDate)

        val adjacentOnly = flights.filterNot { it.operationDate == notificationDate }
        assertEquals(
            notificationDate.plusDays(1),
            RosterFlightMatcher.match(candidate("MU2473"), adjacentOnly).matched?.operationDate,
        )
        assertEquals(2, RosterFlightMatcher.match(candidate("MU2473"), adjacentOnly).suggestions.size)
    }

    @Test
    fun indexUsesScheduledLegDateAndActualEstimatedScheduledExpiryPriority() {
        val assignment = RosterAssignment(
            aircraftRegistration = "B0001",
            aircraftType = "320",
            inboundFlight = "CES2473",
            origin = "北京",
            scheduledArrival = LocalDateTime.of(2026, 8, 27, 0, 30),
            outboundFlight = "MU2474",
            destination = "上海",
            scheduledDeparture = LocalDateTime.of(2026, 8, 27, 2, 0),
            assignees = "测试甲",
            estimatedArrival = LocalDateTime.of(2026, 8, 27, 0, 45),
            actualArrival = LocalDateTime.of(2026, 8, 27, 0, 50),
            estimatedDeparture = LocalDateTime.of(2026, 8, 27, 2, 10),
        )

        val indexed = RosterFlightMatcher.index(listOf(assignment), zoneId)
        val inbound = indexed.single { it.flightNumber == "MU2473" }
        val outbound = indexed.single { it.flightNumber == "MU2474" }
        assertEquals(LocalDate.of(2026, 8, 27), inbound.operationDate)
        assertEquals(Instant.parse("2026-08-27T16:50:00Z").toEpochMilli(), inbound.expiresAtEpochMillis)
        assertEquals(Instant.parse("2026-08-27T18:10:00Z").toEpochMilli(), outbound.expiresAtEpochMillis)
        assertTrue(inbound.expiresAtEpochMillis < outbound.expiresAtEpochMillis)
    }

    private fun candidate(token: String, explicitDate: LocalDate? = null) = ParsedServiceCandidate(
        fingerprint = "abc",
        flightToken = token,
        explicitDate = explicitDate,
        notificationDate = notificationDate,
        serviceType = ServiceType.UNACCOMPANIED_MINOR,
        wheelchairLevel = null,
        count = 1,
        confidence = Confidence.HIGH,
        action = CandidateAction.UPSERT,
        sourceEpochMillis = 1L,
        expiresAtEpochMillis = 2L,
    )

    private fun flight(number: String, date: LocalDate) = FlightReference(number, date, Long.MAX_VALUE)
}
