package com.bradj.airshift.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class DutyTimelineTest {
    private val base = LocalDateTime.of(2026, 8, 29, 12, 0)

    private fun assignment(
        inboundFlight: String? = null,
        outboundFlight: String? = null,
        scheduledArrival: LocalDateTime? = null,
        estimatedArrival: LocalDateTime? = null,
        actualArrival: LocalDateTime? = null,
        scheduledDeparture: LocalDateTime? = null,
        estimatedDeparture: LocalDateTime? = null,
        actualDeparture: LocalDateTime? = null,
    ) = RosterAssignment(
        aircraftRegistration = "B-1234",
        aircraftType = null,
        inboundFlight = inboundFlight,
        origin = null,
        scheduledArrival = scheduledArrival,
        outboundFlight = outboundFlight,
        destination = null,
        scheduledDeparture = scheduledDeparture,
        assignees = "张三",
        estimatedArrival = estimatedArrival,
        actualArrival = actualArrival,
        estimatedDeparture = estimatedDeparture,
        actualDeparture = actualDeparture,
    )

    @Test
    fun `gateArrivalTime inbound uses scheduled arrival minus 10 minutes`() {
        val target = assignment(inboundFlight = "MU5101", scheduledArrival = base)
        assertEquals(base.minusMinutes(10), DutyTimeline.gateArrivalTime(target))
    }

    @Test
    fun `gateArrivalTime inbound prefers actual over estimated over scheduled`() {
        val scheduledOnly = assignment(inboundFlight = "MU5101", scheduledArrival = base)
        assertEquals(base.minusMinutes(10), DutyTimeline.gateArrivalTime(scheduledOnly))

        val withEstimated = assignment(
            inboundFlight = "MU5101",
            scheduledArrival = base,
            estimatedArrival = base.plusMinutes(20),
        )
        assertEquals(base.plusMinutes(10), DutyTimeline.gateArrivalTime(withEstimated))

        val withActual = assignment(
            inboundFlight = "MU5101",
            scheduledArrival = base,
            estimatedArrival = base.plusMinutes(20),
            actualArrival = base.plusMinutes(35),
        )
        assertEquals(base.plusMinutes(25), DutyTimeline.gateArrivalTime(withActual))
    }

    @Test
    fun `gateArrivalTime departure only uses live departure minus 1 hour`() {
        val scheduledOnly = assignment(outboundFlight = "MU5102", scheduledDeparture = base)
        assertEquals(base.minusHours(1), DutyTimeline.gateArrivalTime(scheduledOnly))

        val withEstimated = assignment(
            outboundFlight = "MU5102",
            scheduledDeparture = base,
            estimatedDeparture = base.plusMinutes(30),
        )
        assertEquals(base.minusMinutes(30), DutyTimeline.gateArrivalTime(withEstimated))

        val withActual = assignment(
            outboundFlight = "MU5102",
            scheduledDeparture = base,
            estimatedDeparture = base.plusMinutes(30),
            actualDeparture = base.plusMinutes(45),
        )
        assertEquals(base.minusMinutes(15), DutyTimeline.gateArrivalTime(withActual))
    }

    @Test
    fun `gateArrivalTime turnaround follows inbound rule`() {
        val target = assignment(
            inboundFlight = "MU5101",
            outboundFlight = "MU5102",
            scheduledArrival = base,
            scheduledDeparture = base.plusHours(2),
        )
        assertEquals(base.minusMinutes(10), DutyTimeline.gateArrivalTime(target))
    }

    @Test
    fun `gateArrivalTime returns null when no usable time`() {
        assertNull(DutyTimeline.gateArrivalTime(assignment(inboundFlight = "MU5101")))
        assertNull(DutyTimeline.gateArrivalTime(assignment(outboundFlight = "MU5102")))
        assertNull(DutyTimeline.gateArrivalTime(assignment()))
    }

    @Test
    fun `boardingStartTime is 40 minutes before live departure`() {
        val target = assignment(
            outboundFlight = "MU5102",
            scheduledDeparture = base,
            estimatedDeparture = base.plusMinutes(10),
            actualDeparture = base.plusMinutes(25),
        )
        assertEquals(base.minusMinutes(15), DutyTimeline.boardingStartTime(target))
    }

    @Test
    fun `gateCloseTime is 15 minutes before live departure`() {
        val target = assignment(
            outboundFlight = "MU5102",
            scheduledDeparture = base,
            estimatedDeparture = base.plusMinutes(10),
        )
        assertEquals(base.minusMinutes(5), DutyTimeline.gateCloseTime(target))
    }

    @Test
    fun `boarding and gate close times are null without outbound leg or time`() {
        val inboundOnly = assignment(inboundFlight = "MU5101", scheduledArrival = base)
        assertNull(DutyTimeline.boardingStartTime(inboundOnly))
        assertNull(DutyTimeline.gateCloseTime(inboundOnly))

        val noTime = assignment(outboundFlight = "MU5102")
        assertNull(DutyTimeline.boardingStartTime(noTime))
        assertNull(DutyTimeline.gateCloseTime(noTime))
    }
}
