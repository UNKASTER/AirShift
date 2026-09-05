package com.bradj.airshift.model

import org.junit.Assert.assertEquals
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

    @Test
    fun nextDutySkipsManualPrefixAndAutomaticallyCompletedDuties() {
        val pending = assignment(outboundFlight = "MU1002", scheduledDeparture = now.plusHours(1))
        val done = pending.copy(actualDeparture = now.minusMinutes(1))
        val duties = listOf(pending, done, pending.copy(aircraftRegistration = "B0003"))

        assertEquals(0, duties.nextIncompleteDutyIndex(0, now))
        assertEquals(2, duties.nextIncompleteDutyIndex(1, now))
        assertFalse(duties.allDutiesComplete(now, manuallyCompletedCount = 1))
        assertTrue(duties.allDutiesComplete(now, manuallyCompletedCount = 3))
    }

    @Test
    fun manualProgressIsClampedAndEmptyRosterRemainsIncomplete() {
        val pending = assignment(inboundFlight = "MU1001", scheduledArrival = now.plusHours(1))
        val duties = listOf(pending)

        assertEquals(0, duties.nextIncompleteDutyIndex(-1, now))
        assertEquals(1, duties.nextIncompleteDutyIndex(Int.MAX_VALUE, now))
        assertTrue(duties.allDutiesComplete(now, manuallyCompletedCount = Int.MAX_VALUE))
        assertEquals(0, emptyList<RosterAssignment>().nextIncompleteDutyIndex(10, now))
        assertFalse(emptyList<RosterAssignment>().allDutiesComplete(now, manuallyCompletedCount = 10))
    }

    @Test
    fun correctingEstimatedTimeRestoresAutomaticallySkippedDuty() {
        val old = assignment(outboundFlight = "MU1002", estimatedDeparture = now.minusHours(4))
        val delayed = old.copy(estimatedDeparture = now.plusHours(2))

        assertEquals(1, listOf(old).nextIncompleteDutyIndex(0, now))
        assertEquals(0, listOf(delayed).nextIncompleteDutyIndex(0, now))
        assertEquals(1, listOf(delayed).nextIncompleteDutyIndex(1, now))
    }

    @Test
    fun anEstimateFromAnotherDaysFlightDoesNotKeepTheDutyOpen() {
        // 同号航班明天照飞：把明天的预计时间写进来也不能让昨天的任务重新变成未完成。
        val foreign = assignment(
            inboundFlight = "MU1001",
            scheduledArrival = now.minusHours(5),
            estimatedArrival = now.minusHours(5).plusDays(1),
        )
        val sameOperation = foreign.copy(estimatedArrival = now.plusHours(1))

        assertTrue(foreign.isDutyComplete(now))
        assertFalse(sameOperation.isDutyComplete(now))
        assertTrue(listOf(foreign).allDutiesComplete(now))
    }
}
