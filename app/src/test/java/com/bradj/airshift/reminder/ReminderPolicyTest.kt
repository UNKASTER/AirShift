package com.bradj.airshift.reminder

import com.bradj.airshift.model.RosterAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** 提醒只能落在排班那一天：别的日子的同号航班动态不能把它挪走。 */
class ReminderPolicyTest {
    private val day = LocalDate.of(2026, 9, 3)

    @Test
    fun sameDayEstimateMovesTheArrivalReminder() {
        val duty = inbound("MU1001", scheduled = day.atTime(14, 0), estimated = day.atTime(15, 0))

        assertEquals(day.atTime(14, 45), ReminderPolicy.create(duty)?.triggerAt)
    }

    @Test
    fun anEstimateFromAnotherDaysFlightFallsBackToTheScheduledArrival() {
        val duty = inbound("MU1001", scheduled = day.atTime(14, 0), estimated = day.plusDays(2).atTime(14, 10))

        assertEquals(day.atTime(13, 45), ReminderPolicy.create(duty)?.triggerAt)
    }

    @Test
    fun anEstimateFromAnotherDaysFlightFallsBackToTheScheduledDeparture() {
        val duty = outbound("MU1002", scheduled = day.atTime(18, 0), estimated = day.plusDays(1).atTime(18, 5))

        assertEquals(day.atTime(16, 50), ReminderPolicy.create(duty)?.triggerAt)
    }

    @Test
    fun withoutAnyScheduleAForeignEstimateCannotBeTold() {
        val duty = outbound("MU1002", estimated = day.plusDays(1).atTime(18, 5))

        assertEquals(day.plusDays(1).atTime(16, 55), ReminderPolicy.create(duty)?.triggerAt)
        assertNull(ReminderPolicy.create(outbound("MU1002")))
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
