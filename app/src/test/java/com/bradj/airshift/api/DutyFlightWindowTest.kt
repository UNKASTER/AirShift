package com.bradj.airshift.api

import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.dutyWindowIndices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DutyFlightWindowTest {
    private val now = LocalDateTime.of(2026, 8, 30, 12, 0)

    @Test
    fun windowSkipsTheManualPrefixAndAutomaticallyCompletedDuties() {
        val duties = listOf(
            assignment("MU1001"),
            assignment("MU1002").copy(actualArrival = now.minusMinutes(1)),
            assignment("MU1003"),
            assignment("MU1004"),
            assignment("MU1005"),
        )

        assertEquals(listOf(0, 2), duties.dutyWindowIndices(0, now))
        assertEquals(listOf(2, 3), duties.dutyWindowIndices(1, now))
        assertEquals(listOf(3, 4), duties.dutyWindowIndices(3, now))
        assertEquals(setOf(lookup("MU1003"), lookup("MU1004")), duties.dutyWindowLookups(1, now))
    }

    @Test
    fun windowIncludesBothLegsAndDeduplicatesByNumberAndDate() {
        val duties = listOf(
            assignment(" mu1001 ").copy(outboundFlight = "MU1002", scheduledDeparture = now.plusHours(2)),
            assignment("MU1001").copy(outboundFlight = "MU1001", scheduledDeparture = now.plusDays(1)),
            assignment("MU1003"),
        )

        assertEquals(
            setOf(lookup("MU1001"), lookup("MU1002"), FlightLookup.of("MU1001", now.toLocalDate().plusDays(1))),
            duties.dutyWindowLookups(0, now),
        )
    }

    @Test
    fun delayedAndDistantDutiesAreNotExcludedByTheOldTimeRange() {
        val delayed = assignment("MU1001").copy(
            scheduledArrival = now.minusHours(6),
            estimatedArrival = now.plusHours(1),
        )
        val distant = assignment("MU1002").copy(scheduledArrival = now.plusHours(10))

        assertEquals(setOf(lookup("MU1001"), lookup("MU1002")), listOf(delayed, distant).dutyWindowLookups(0, now))
    }

    @Test
    fun missingScheduledDateUsesTodayForAnOtherwiseIncompleteDuty() {
        val inbound = assignment("MU1001").copy(scheduledArrival = null, estimatedArrival = now.plusHours(1))
        val outbound = assignment("MU1002").copy(
            inboundFlight = null,
            outboundFlight = "MU1003",
            scheduledArrival = null,
            scheduledDeparture = null,
            estimatedDeparture = now.plusHours(2),
        )

        assertEquals(setOf(lookup("MU1001"), lookup("MU1003")), listOf(inbound, outbound).dutyWindowLookups(0, now))
    }

    @Test
    fun lastDutyAndCompletedOrEmptyRostersHaveNoExtraTargets() {
        val duties = listOf(assignment("MU1001"), assignment("MU1002"))

        assertEquals(listOf(1), duties.dutyWindowIndices(1, now))
        assertEquals(setOf(lookup("MU1002")), duties.dutyWindowLookups(1, now))
        assertTrue(duties.dutyWindowLookups(2, now).isEmpty())
        assertTrue(emptyList<RosterAssignment>().dutyWindowLookups(0, now).isEmpty())
        assertEquals(listOf(0, 1), duties.dutyWindowIndices(-1, now))
        assertTrue(duties.dutyWindowIndices(Int.MAX_VALUE, now).isEmpty())
    }

    @Test
    fun allRosterScopeIncludesManuallyAndAutomaticallyCompletedDuties() {
        val duties = listOf(
            assignment("MU1001").copy(actualArrival = now.minusMinutes(1)),
            assignment("MU1002").copy(scheduledArrival = now.minusHours(4)),
            assignment("MU1003"),
        )

        assertTrue(duties.refreshLookups(duties.size, FlightRefreshScope.DUTY_WINDOW, now).isEmpty())
        assertEquals(listOf(0, 1, 2), duties.refreshIndices(duties.size, FlightRefreshScope.ALL_ROSTER, now))
        assertEquals(
            setOf(lookup("MU1001"), lookup("MU1002"), lookup("MU1003")),
            duties.refreshLookups(duties.size, FlightRefreshScope.ALL_ROSTER, now),
        )
        val automaticallyCompleted = duties.take(2)
        assertTrue(automaticallyCompleted.refreshLookups(0, FlightRefreshScope.DUTY_WINDOW, now).isEmpty())
        assertEquals(
            setOf(lookup("MU1001"), lookup("MU1002")),
            automaticallyCompleted.refreshLookups(0, FlightRefreshScope.ALL_ROSTER, now),
        )
    }

    @Test
    fun allRosterScopeRetainsOperationDatesAndDeduplicationAfterCompletion() {
        val yesterday = now.minusDays(1)
        val duties = listOf(
            assignment(" mu1001 ").copy(scheduledArrival = yesterday),
            assignment("MU1001").copy(scheduledArrival = yesterday, outboundFlight = "MU1001", scheduledDeparture = now),
            assignment("MU1001").copy(scheduledArrival = now.plusDays(1)),
            assignment("MU1002").copy(scheduledArrival = null),
        )

        assertEquals(
            setOf(
                FlightLookup.of("MU1001", yesterday.toLocalDate()),
                lookup("MU1001"),
                FlightLookup.of("MU1001", now.toLocalDate().plusDays(1)),
                lookup("MU1002"),
            ),
            duties.refreshLookups(duties.size, FlightRefreshScope.ALL_ROSTER, now),
        )
        assertTrue(emptyList<RosterAssignment>().refreshLookups(0, FlightRefreshScope.ALL_ROSTER, now).isEmpty())
    }

    private fun lookup(flight: String) = FlightLookup.of(flight, now.toLocalDate())

    private fun assignment(inbound: String) = RosterAssignment(
        aircraftRegistration = "B0001",
        aircraftType = null,
        inboundFlight = inbound,
        origin = null,
        scheduledArrival = now.plusHours(1),
        outboundFlight = null,
        destination = null,
        scheduledDeparture = null,
        assignees = "TESTUSER",
    )
}
