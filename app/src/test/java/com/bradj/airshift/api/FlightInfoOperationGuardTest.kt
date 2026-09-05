package com.bradj.airshift.api

import com.bradj.airshift.model.RosterAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** 同一航班号每天都飞；合并实时数据时只认排班这一班，别的日子的同号航班整段丢弃。 */
class FlightInfoOperationGuardTest {
    private val day = LocalDate.of(2026, 9, 3)
    private val lookup = FlightLookup.of("MU1001", day)

    @Test
    fun aLegFromAnotherDayIsIgnoredAndTheAssignmentKeepsItsValues() {
        val original = inboundAssignment(scheduledArrival = day.atTime(14, 0)).copy(arrivalStand = "OLD")
        val foreign = arrivalLeg(
            planned = day.plusDays(2).atTime(14, 0),
            estimated = day.plusDays(2).atTime(14, 20),
            stand = "NEW",
        )

        val updated = original.withLiveInfo(mapOf(lookup to listOf(foreign)), day)

        assertEquals(original, updated)
    }

    @Test
    fun aDelayWithinTwelveHoursIsStillTheSameFlight() {
        val original = inboundAssignment(scheduledArrival = day.atTime(14, 0))
        val delayed = arrivalLeg(planned = day.atTime(14, 0), estimated = day.plusDays(1).atTime(1, 30))

        val updated = original.withLiveInfo(mapOf(lookup to listOf(delayed)), day)

        assertEquals(day.plusDays(1).atTime(1, 30), updated.estimatedArrival)
    }

    @Test
    fun foreignLegsAreDroppedBeforeStopoverSelection() {
        val original = inboundAssignment(scheduledArrival = day.atTime(14, 30))
        val firstLeg = arrivalLeg(planned = day.atTime(14, 30), stand = "351")
        val secondLeg = arrivalLeg(planned = day.atTime(17, 55), stand = "105")
        val tomorrowFirstLeg = arrivalLeg(planned = day.plusDays(1).atTime(14, 30), stand = "999")

        val updated = original.withLiveInfo(mapOf(lookup to listOf(tomorrowFirstLeg, secondLeg, firstLeg)), day)

        assertEquals("351", updated.arrivalStand)
    }

    @Test
    fun anUnscheduledLegAcceptsOnlyLegsNearTheLookupDate() {
        val original = inboundAssignment(scheduledArrival = null)
        val nextDay = arrivalLeg(planned = day.plusDays(1).atTime(0, 30), stand = "NEAR")
        val farAway = arrivalLeg(planned = day.plusDays(2).atTime(14, 0), stand = "FAR")

        assertEquals(original, original.withLiveInfo(mapOf(lookup to listOf(farAway)), day))
        val updated = original.withLiveInfo(mapOf(lookup to listOf(nextDay)), day)
        assertEquals("NEAR", updated.arrivalStand)
        assertEquals(day.plusDays(1).atTime(0, 30), updated.scheduledArrival)
    }

    @Test
    fun aLegWithoutAnyTimeStillContributesStands() {
        val original = inboundAssignment(scheduledArrival = day.atTime(14, 0))

        val updated = original.withLiveInfo(mapOf(lookup to listOf(arrivalLeg(stand = "STAND"))), day)

        assertEquals("STAND", updated.arrivalStand)
        assertNull(updated.estimatedArrival)
    }

    @Test
    fun outboundLegsAreGuardedByTheDepartureTime() {
        val original = RosterAssignment(
            aircraftRegistration = "B0001",
            aircraftType = null,
            inboundFlight = null,
            origin = null,
            scheduledArrival = null,
            outboundFlight = "MU1001",
            destination = null,
            scheduledDeparture = day.atTime(18, 0),
            assignees = "测试甲",
        )
        val foreign = departureLeg(planned = day.plusDays(1).atTime(18, 0), stand = "FOREIGN")
        val own = departureLeg(planned = day.atTime(18, 0), estimated = day.atTime(18, 40), stand = "OWN")

        assertEquals(original, original.withLiveInfo(mapOf(lookup to listOf(foreign)), day))
        val updated = original.withLiveInfo(mapOf(lookup to listOf(foreign, own)), day)
        assertEquals("OWN", updated.departureStand)
        assertEquals(day.atTime(18, 40), updated.estimatedDeparture)
    }

    @Test
    fun lookupDatesFallBackToTheOtherLegAndThenToTheGivenDate() {
        val turnaround = inboundAssignment(scheduledArrival = null).copy(
            outboundFlight = "MU1002",
            scheduledDeparture = day.atTime(16, 0),
        )
        val fallback = day.plusDays(5)

        assertEquals(day, turnaround.inboundLookupDate(fallback))
        assertEquals(day, turnaround.outboundLookupDate(fallback))
        assertEquals(fallback, inboundAssignment(scheduledArrival = null).inboundLookupDate(fallback))
    }

    @Test
    fun anArrivalBeforeSixInTheMorningIsQueriedOnItsDepartureDay() {
        // 真机核对：飞常准的 date 是出发日。夜班 01:00 到达的航班是前一天晚上出发的，按到达日查会拿到下一班。
        val overnight = inboundAssignment(scheduledArrival = day.plusDays(1).atTime(1, 0))
        val earlyMorning = inboundAssignment(scheduledArrival = day.plusDays(1).atTime(6, 0))

        assertEquals(day, overnight.inboundLookupDate(day.plusDays(9)))
        assertEquals(day.plusDays(1), earlyMorning.inboundLookupDate(day.plusDays(9)))
    }

    @Test
    fun anOvernightArrivalMergesTheLegQueriedOnItsDepartureDay() {
        val overnight = inboundAssignment(scheduledArrival = day.plusDays(1).atTime(1, 0))
        val ownLeg = arrivalLeg(
            planned = day.plusDays(1).atTime(1, 0),
            estimated = day.plusDays(1).atTime(0, 39),
            stand = "342",
        )
        val nextDayLeg = arrivalLeg(planned = day.plusDays(2).atTime(1, 0), estimated = day.plusDays(2).atTime(0, 44))

        val live = mapOf(
            FlightLookup.of("MU1001", day) to listOf(ownLeg),
            FlightLookup.of("MU1001", day.plusDays(1)) to listOf(nextDayLeg),
        )

        val updated = overnight.withLiveInfo(live, day)

        assertEquals(day.plusDays(1).atTime(0, 39), updated.estimatedArrival)
        assertEquals("342", updated.arrivalStand)
    }

    private fun inboundAssignment(scheduledArrival: LocalDateTime?) = RosterAssignment(
        aircraftRegistration = "B0001",
        aircraftType = null,
        inboundFlight = "MU1001",
        origin = null,
        scheduledArrival = scheduledArrival,
        outboundFlight = null,
        destination = null,
        scheduledDeparture = null,
        assignees = "测试甲",
    )

    private fun arrivalLeg(
        planned: LocalDateTime? = null,
        estimated: LocalDateTime? = null,
        stand: String? = null,
    ) = emptyLeg.copy(plannedArrival = planned, estimatedArrival = estimated, arrivalStand = stand)

    private fun departureLeg(
        planned: LocalDateTime? = null,
        estimated: LocalDateTime? = null,
        stand: String? = null,
    ) = emptyLeg.copy(plannedDeparture = planned, estimatedDeparture = estimated, departureStand = stand)

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
