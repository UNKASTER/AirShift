package com.bradj.airshift.api

import com.bradj.airshift.model.RosterAssignment
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FlightInfoStandMappingTest {
    private val arrivalDate = LocalDate.of(2026, 8, 30)
    private val departureDate = arrivalDate.plusDays(1)

    @Test
    fun mapsAllFourStandsFromTheCorrectLegAndOperationDate() {
        val live = mapOf(
            FlightLookup.of("ZZ1001", arrivalDate) to flightInfo("ZZ1001", "IN-DEP", "IN-ARR"),
            FlightLookup.of("ZZ1002", departureDate) to flightInfo("ZZ1002", "OUT-DEP", "OUT-ARR"),
            FlightLookup.of("ZZ1001", departureDate) to flightInfo("ZZ1001", "WRONG-IN-DEP", "WRONG-IN-ARR"),
            FlightLookup.of("ZZ1002", arrivalDate) to flightInfo("ZZ1002", "WRONG-OUT-DEP", "WRONG-OUT-ARR"),
        )

        val updated = assignment().withLiveInfo(live, arrivalDate)

        assertEquals("IN-DEP", updated.inboundDepartureStand)
        assertEquals("IN-ARR", updated.arrivalStand)
        assertEquals("OUT-DEP", updated.departureStand)
        assertEquals("OUT-ARR", updated.outboundArrivalStand)
    }

    @Test
    fun missingLiveStandValuesPreservePreviouslySavedStands() {
        val original = assignment()
        val live = mapOf(
            FlightLookup.of("ZZ1001", arrivalDate) to flightInfo("ZZ1001"),
            FlightLookup.of("ZZ1002", departureDate) to flightInfo("ZZ1002"),
        )

        assertEquals(original, original.withLiveInfo(live, arrivalDate))
    }

    @Test
    fun partialRefreshUpdatesInboundStandsWithoutErasingOutboundStands() {
        val original = assignment()
        val live = mapOf(
            FlightLookup.of("ZZ1001", arrivalDate) to flightInfo("ZZ1001", "NEW-IN-DEP", "NEW-IN-ARR"),
        )

        val updated = original.withLiveInfo(live, arrivalDate)

        assertEquals("NEW-IN-DEP", updated.inboundDepartureStand)
        assertEquals("NEW-IN-ARR", updated.arrivalStand)
        assertEquals(original.departureStand, updated.departureStand)
        assertEquals(original.outboundArrivalStand, updated.outboundArrivalStand)
    }

    @Test
    fun unscheduledLegsUseTheFallbackDateForStandLookups() {
        val original = assignment().copy(scheduledArrival = null, scheduledDeparture = null)
        val live = mapOf(
            FlightLookup.of("ZZ1001", arrivalDate) to flightInfo("ZZ1001", "IN-DEP", "IN-ARR"),
            FlightLookup.of("ZZ1002", arrivalDate) to flightInfo("ZZ1002", "OUT-DEP", "OUT-ARR"),
        )

        val updated = original.withLiveInfo(live, arrivalDate)

        assertEquals("IN-DEP", updated.inboundDepartureStand)
        assertEquals("OUT-ARR", updated.outboundArrivalStand)
    }

    private fun assignment() = RosterAssignment(
        aircraftRegistration = "B0001",
        aircraftType = "320",
        inboundFlight = "ZZ1001",
        origin = "测试始发",
        scheduledArrival = arrivalDate.atTime(23, 30),
        outboundFlight = "ZZ1002",
        destination = "测试到达",
        scheduledDeparture = departureDate.atTime(1, 0),
        assignees = "TESTUSER",
        inboundDepartureStand = "SAVED-IN-DEP",
        arrivalStand = "SAVED-IN-ARR",
        departureStand = "SAVED-OUT-DEP",
        outboundArrivalStand = "SAVED-OUT-ARR",
    )

    private fun flightInfo(
        flightNumber: String,
        departureStand: String? = null,
        arrivalStand: String? = null,
    ) = FlightInfo(
        flightNumber = flightNumber,
        origin = null,
        destination = null,
        plannedDeparture = null,
        estimatedDeparture = null,
        actualDeparture = null,
        plannedArrival = null,
        estimatedArrival = null,
        actualArrival = null,
        actualOffBlock = null,
        gateClosedObservedAt = null,
        boardingGate = null,
        departureStand = departureStand,
        arrivalStand = arrivalStand,
        arrivalBridge = null,
    )
}
