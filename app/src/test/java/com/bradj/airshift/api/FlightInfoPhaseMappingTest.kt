package com.bradj.airshift.api

import com.bradj.airshift.model.RosterAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 合并实时数据时，每段航班两端的实际时间与 FlightState 都要留下，状态灯才分得清起飞与落地。 */
class FlightInfoPhaseMappingTest {
    private val day = LocalDate.of(2026, 9, 7)
    private val turnaround = RosterAssignment(
        aircraftRegistration = "B0001",
        aircraftType = null,
        inboundFlight = "MU1001",
        origin = "北京大兴",
        scheduledArrival = day.atTime(16, 45),
        outboundFlight = "MU1002",
        destination = "北京大兴",
        scheduledDeparture = day.atTime(17, 35),
        assignees = "测试甲",
    )
    private val inboundLookup = FlightLookup.of("MU1001", day)
    private val outboundLookup = FlightLookup.of("MU1002", day)

    @Test
    fun theInboundLegKeepsItsOriginDepartureAndTheOutboundLegItsDestinationArrival() {
        val inbound = emptyLeg.copy(
            flightNumber = "MU1001",
            plannedDeparture = day.atTime(14, 30),
            actualDeparture = day.atTime(14, 52),
            plannedArrival = day.atTime(16, 45),
            actualArrival = day.atTime(16, 38),
            flightState = "到达",
        )
        val outbound = emptyLeg.copy(
            flightNumber = "MU1002",
            plannedDeparture = day.atTime(17, 35),
            actualDeparture = day.atTime(17, 41),
            plannedArrival = day.atTime(19, 40),
            actualArrival = day.atTime(19, 25),
            flightState = "起飞",
        )

        val updated = turnaround.withLiveInfo(
            mapOf(inboundLookup to listOf(inbound), outboundLookup to listOf(outbound)),
            day,
        )

        // 修复前进港段只留本站到达、出港段只留本站起飞，另一端的实际时间被丢掉。
        assertEquals(day.atTime(14, 52), updated.inboundActualDeparture)
        assertEquals(day.atTime(16, 38), updated.actualArrival)
        assertEquals(day.atTime(17, 41), updated.actualDeparture)
        assertEquals(day.atTime(19, 25), updated.outboundActualArrival)
        assertEquals("到达", updated.inboundFlightState)
        assertEquals("起飞", updated.outboundFlightState)
    }

    @Test
    fun aLaterResponseWithoutTheFieldsKeepsTheEarlierValues() {
        val airborne = turnaround.copy(inboundActualDeparture = day.atTime(14, 52), inboundFlightState = "起飞")
        val sparse = emptyLeg.copy(
            flightNumber = "MU1001",
            plannedArrival = day.atTime(16, 45),
            estimatedArrival = day.atTime(16, 40),
        )

        val updated = airborne.withLiveInfo(mapOf(inboundLookup to listOf(sparse)), day)

        assertEquals(day.atTime(14, 52), updated.inboundActualDeparture)
        assertEquals("起飞", updated.inboundFlightState)
        assertEquals(day.atTime(16, 40), updated.estimatedArrival)
        assertNull(updated.actualArrival)
        assertNull(updated.outboundActualArrival)
    }

    private val emptyLeg = FlightInfo(
        flightNumber = "MU1001",
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
        departureStand = null,
        arrivalStand = null,
        arrivalBridge = null,
    )
}
