package com.bradj.airshift.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RosterTrackingTest {
    private val day = LocalDate.of(2026, 9, 7)

    @Test
    fun trackingStartsThreeHoursBeforeTheEarliestScheduledTime() {
        val roster = listOf(
            inbound("MU1001", scheduled = day.atTime(14, 0)),
            outbound("MU1002", scheduled = day.atTime(9, 10)),
        )

        assertEquals(day.atTime(6, 10), RosterTracking.startsAt(roster))
        assertEquals(day, roster.rosterDate())
    }

    @Test
    fun hasStartedIsFalseBeforeTheStartAndInclusiveAtTheStart() {
        val roster = listOf(inbound("MU1001", scheduled = day.atTime(7, 10)))
        val start = day.atTime(4, 10)

        assertFalse(RosterTracking.hasStarted(roster, start.minusMinutes(1)))
        assertFalse(RosterTracking.hasStarted(roster, day.minusDays(1).atTime(20, 0)))
        assertTrue(RosterTracking.hasStarted(roster, start))
        assertTrue(RosterTracking.hasStarted(roster, day.atTime(23, 30)))
        assertTrue(RosterTracking.hasStarted(roster, day.plusDays(3).atTime(12, 0)))
    }

    @Test
    fun aRosterWithoutAnyTimeNeverStartsTracking() {
        val roster = listOf(inbound("MU1001"), outbound("MU1002"))

        assertNull(RosterTracking.startsAt(roster))
        assertNull(roster.rosterDate())
        assertFalse(RosterTracking.hasStarted(roster, day.atTime(12, 0)))
        assertNull(RosterTracking.startsAt(emptyList()))
    }

    @Test
    fun estimatedTimesOnlyMatterWhereTheScheduleIsMissing() {
        val scheduledWins = listOf(outbound("MU1002", scheduled = day.atTime(14, 0), estimated = day.atTime(8, 0)))
        val estimatedFallback = listOf(inbound("MU1001", estimated = day.atTime(9, 0)))

        assertEquals(day.atTime(11, 0), RosterTracking.startsAt(scheduledWins))
        assertEquals(day.atTime(6, 0), RosterTracking.startsAt(estimatedFallback))
        assertNull(estimatedFallback.rosterDate())
    }

    @Test
    fun timesOnALegWithoutAFlightNumberAreIgnored() {
        val roster = listOf(outbound("MU1002", scheduled = day.atTime(14, 0)).copy(scheduledArrival = day.atTime(5, 0)))

        assertEquals(day.atTime(11, 0), RosterTracking.startsAt(roster))
    }

    private fun inbound(
        flight: String,
        scheduled: LocalDateTime? = null,
        estimated: LocalDateTime? = null,
    ) = base.copy(inboundFlight = flight, scheduledArrival = scheduled, estimatedArrival = estimated)

    private fun outbound(
        flight: String,
        scheduled: LocalDateTime? = null,
        estimated: LocalDateTime? = null,
    ) = base.copy(outboundFlight = flight, scheduledDeparture = scheduled, estimatedDeparture = estimated)

    private val base = RosterAssignment(
        aircraftRegistration = "B0001",
        aircraftType = null,
        inboundFlight = null,
        origin = null,
        scheduledArrival = null,
        outboundFlight = null,
        destination = null,
        scheduledDeparture = null,
        assignees = "测试甲",
    )
}
