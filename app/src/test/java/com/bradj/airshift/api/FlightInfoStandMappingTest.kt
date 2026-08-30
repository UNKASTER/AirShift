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
            FlightLookup.of("ZZ1001", arrivalDate) to listOf(flightInfo("ZZ1001", "IN-DEP", "IN-ARR")),
            FlightLookup.of("ZZ1002", departureDate) to listOf(flightInfo("ZZ1002", "OUT-DEP", "OUT-ARR")),
            FlightLookup.of("ZZ1001", departureDate) to listOf(flightInfo("ZZ1001", "WRONG-IN-DEP", "WRONG-IN-ARR")),
            FlightLookup.of("ZZ1002", arrivalDate) to listOf(flightInfo("ZZ1002", "WRONG-OUT-DEP", "WRONG-OUT-ARR")),
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
            FlightLookup.of("ZZ1001", arrivalDate) to listOf(flightInfo("ZZ1001")),
            FlightLookup.of("ZZ1002", departureDate) to listOf(flightInfo("ZZ1002")),
        )

        assertEquals(original, original.withLiveInfo(live, arrivalDate))
    }

    @Test
    fun partialRefreshUpdatesInboundStandsWithoutErasingOutboundStands() {
        val original = assignment()
        val live = mapOf(
            FlightLookup.of("ZZ1001", arrivalDate) to listOf(flightInfo("ZZ1001", "NEW-IN-DEP", "NEW-IN-ARR")),
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
            FlightLookup.of("ZZ1001", arrivalDate) to listOf(flightInfo("ZZ1001", "IN-DEP", "IN-ARR")),
            FlightLookup.of("ZZ1002", arrivalDate) to listOf(flightInfo("ZZ1002", "OUT-DEP", "OUT-ARR")),
        )

        val updated = original.withLiveInfo(live, arrivalDate)

        assertEquals("IN-DEP", updated.inboundDepartureStand)
        assertEquals("OUT-ARR", updated.outboundArrivalStand)
    }

    @Test
    fun stopoverFlightMapsInboundAndOutboundToTheLegsServingTheLocalAirport() {
        // MU2415 DNH→LHW→PKX: the roster's 预落 14:30 / 计离 15:30 identify the LHW legs.
        val date = LocalDate.of(2026, 8, 30)
        val firstLeg = FlightInfo(
            flightNumber = "MU2415",
            origin = AirportPoint("DNH", "敦煌莫高", null, null),
            destination = AirportPoint("LHW", "兰州中川", null, null),
            plannedDeparture = date.atTime(12, 40),
            estimatedDeparture = null,
            actualDeparture = date.atTime(12, 47),
            plannedArrival = date.atTime(14, 30),
            estimatedArrival = null,
            actualArrival = null,
            actualOffBlock = date.atTime(12, 37),
            gateClosedObservedAt = date.atTime(12, 30),
            boardingGate = "301",
            departureStand = null,
            arrivalStand = "105",
            arrivalBridge = null,
        )
        val secondLeg = firstLeg.copy(
            origin = AirportPoint("LHW", "兰州中川", null, null),
            destination = AirportPoint("PKX", "北京大兴", null, null),
            plannedDeparture = date.atTime(15, 30),
            actualDeparture = null,
            plannedArrival = date.atTime(17, 35),
            actualOffBlock = null,
            gateClosedObservedAt = null,
            boardingGate = "D58",
            arrivalStand = null,
        )
        val assignment = RosterAssignment(
            aircraftRegistration = "B6870",
            aircraftType = "320",
            inboundFlight = "MU2415",
            origin = "敦煌莫高",
            scheduledArrival = date.atTime(14, 30),
            outboundFlight = "MU2415",
            destination = "北京大兴",
            scheduledDeparture = date.atTime(15, 30),
            assignees = "TESTUSER",
        )
        val live = mapOf(FlightLookup.of("MU2415", date) to listOf(firstLeg, secondLeg))

        val updated = assignment.withLiveInfo(live, date)

        assertEquals("DNH", updated.originCode)
        assertEquals("PKX", updated.destinationCode)
        assertEquals("LHW", updated.localAirportCode)
        assertEquals("兰州中川", updated.localAirportName)
        // The outbound leg (LHW→PKX) has not departed yet, so no actual departure time.
        assertEquals(null, updated.actualDeparture)
        assertEquals(date.atTime(12, 37), updated.inboundActualOffBlock)
        assertEquals("301", updated.inboundBoardingGate)
        assertEquals("105", updated.arrivalStand)
        assertEquals("D58", updated.boardingGate)
    }

    @Test
    fun stopoverFlightFallsBackToTheStoredLocalAirportWhenSchedulesAreMissing() {
        val date = LocalDate.of(2026, 8, 30)
        val firstLeg = flightInfo("MU2415").copy(
            origin = AirportPoint("DNH", "敦煌莫高", null, null),
            destination = AirportPoint("LHW", "兰州中川", null, null),
        )
        val secondLeg = flightInfo("MU2415").copy(
            origin = AirportPoint("LHW", "兰州中川", null, null),
            destination = AirportPoint("PKX", "北京大兴", null, null),
        )
        val assignment = assignment().copy(
            inboundFlight = "MU2415",
            scheduledArrival = null,
            outboundFlight = "MU2415",
            scheduledDeparture = null,
            localAirportCode = "LHW",
        )
        val live = mapOf(FlightLookup.of("MU2415", date) to listOf(firstLeg, secondLeg))

        val updated = assignment.withLiveInfo(live, date)

        assertEquals("DNH", updated.originCode)
        assertEquals("PKX", updated.destinationCode)
        assertEquals("LHW", updated.localAirportCode)
    }

    @Test
    fun stopoverFlightWithoutAnyHintKeepsTheWholeRouteEndpoints() {
        val date = LocalDate.of(2026, 8, 30)
        val firstLeg = flightInfo("MU2415").copy(
            origin = AirportPoint("DNH", "敦煌莫高", null, null),
            destination = AirportPoint("LHW", "兰州中川", null, null),
        )
        val secondLeg = flightInfo("MU2415").copy(
            origin = AirportPoint("LHW", "兰州中川", null, null),
            destination = AirportPoint("PKX", "北京大兴", null, null),
        )
        val assignment = assignment().copy(
            inboundFlight = "MU2415",
            scheduledArrival = null,
            outboundFlight = "MU2415",
            scheduledDeparture = null,
        )
        val live = mapOf(FlightLookup.of("MU2415", date) to listOf(firstLeg, secondLeg))

        val updated = assignment.withLiveInfo(live, date)

        assertEquals("DNH", updated.originCode)
        assertEquals("PKX", updated.destinationCode)
        assertEquals("LHW", updated.localAirportCode)
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
