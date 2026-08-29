package com.bradj.airshift.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RosterAssignmentCompletionTest {
    private val now = LocalDateTime.of(2026, 8, 30, 18, 0)

    private fun assignment(
        inboundFlight: String? = null,
        outboundFlight: String? = null,
        scheduledArrival: LocalDateTime? = null,
        scheduledDeparture: LocalDateTime? = null,
        estimatedArrival: LocalDateTime? = null,
        estimatedDeparture: LocalDateTime? = null,
        actualArrival: LocalDateTime? = null,
        actualDeparture: LocalDateTime? = null,
    ) = RosterAssignment(
        aircraftRegistration = "B0001",
        aircraftType = null,
        inboundFlight = inboundFlight,
        origin = null,
        scheduledArrival = scheduledArrival,
        outboundFlight = outboundFlight,
        destination = null,
        scheduledDeparture = scheduledDeparture,
        assignees = "测试甲",
        estimatedArrival = estimatedArrival,
        actualArrival = actualArrival,
        estimatedDeparture = estimatedDeparture,
        actualDeparture = actualDeparture,
    )

    @Test
    fun legWithActualTimeIsComplete() {
        val duty = assignment(
            outboundFlight = "CES1002",
            scheduledDeparture = now.plusHours(2),
            actualDeparture = now.minusMinutes(30),
        )
        assertTrue(duty.isDutyComplete(now))
    }

    @Test
    fun legPastGracePeriodIsComplete() {
        val duty = assignment(
            inboundFlight = "CES1001",
            scheduledArrival = now.minusHours(3),
        )
        assertTrue(duty.isDutyComplete(now))
    }

    @Test
    fun legWithinGracePeriodIsNotComplete() {
        val duty = assignment(
            inboundFlight = "CES1001",
            scheduledArrival = now.minusHours(2).minusMinutes(59),
        )
        assertFalse(duty.isDutyComplete(now))
    }

    @Test
    fun estimatedTimeOverridesScheduledForGrace() {
        val delayed = assignment(
            outboundFlight = "CES1002",
            scheduledDeparture = now.minusHours(5),
            estimatedDeparture = now.minusHours(1),
        )
        assertFalse(delayed.isDutyComplete(now))
    }

    @Test
    fun legWithoutAnyTimeIsComplete() {
        val duty = assignment(outboundFlight = "CES1002")
        assertTrue(duty.isDutyComplete(now))
    }

    @Test
    fun turnaroundRequiresBothLegsComplete() {
        val base = assignment(
            inboundFlight = "CES1001",
            outboundFlight = "CES1002",
            scheduledArrival = now.minusHours(4),
            scheduledDeparture = now.plusHours(1),
        )
        assertFalse(base.isDutyComplete(now))
        assertTrue(base.copy(actualDeparture = now.minusMinutes(10)).isDutyComplete(now))
    }

    @Test
    fun emptyListIsNotComplete() {
        assertFalse(emptyList<RosterAssignment>().allDutiesComplete(now))
    }

    @Test
    fun allDutiesCompleteRequiresEveryAssignmentComplete() {
        val done = assignment(
            inboundFlight = "CES1001",
            scheduledArrival = now.minusHours(4),
        )
        val pending = assignment(
            outboundFlight = "CES1002",
            scheduledDeparture = now.plusHours(1),
        )
        assertFalse(listOf(done, pending).allDutiesComplete(now))
        assertTrue(listOf(done, done.copy(aircraftRegistration = "B0002")).allDutiesComplete(now))
    }
}
