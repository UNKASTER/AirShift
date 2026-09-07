package com.bradj.airshift.ui.components

import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.specialservice.FlightCancellationRecord
import com.bradj.airshift.specialservice.FlightCancellationScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 状态灯规则：同一个小方块走完 未起飞 → 已起飞 → 已落地，进港、出港段各自判定。 */
class LegPresentationTest {
    private val date: LocalDate = LocalDate.of(2026, 9, 7)
    private val turnaround = RosterAssignment(
        aircraftRegistration = "B0001",
        aircraftType = "73U",
        inboundFlight = "ZZ5637",
        origin = "北京大兴",
        scheduledArrival = date.atTime(16, 45),
        outboundFlight = "ZZ5638",
        destination = "北京大兴",
        scheduledDeparture = date.atTime(17, 35),
        assignees = "测试甲",
        originCode = "PKX",
        destinationCode = "PKX",
        localAirportCode = "LHW",
        localAirportName = "兰州中川",
    )

    private fun inboundLamp(assignment: RosterAssignment, completed: Boolean = false): LegStatus? =
        legStatus(assignment.legUiModels(MucContext(), DetailLevel.SUMMARY)[0], completed)

    private fun outboundLamp(assignment: RosterAssignment, completed: Boolean = false): LegStatus? =
        legStatus(assignment.legUiModels(MucContext(), DetailLevel.SUMMARY)[1], completed)

    @Test
    fun `a leg with only planned times has not departed`() {
        val inbound = inboundLamp(turnaround)

        assertEquals("未起飞", inbound?.text)
        assertEquals(LampKind.Neutral, inbound?.kind)
        assertFalse(inbound!!.dot)
        assertEquals("未起飞", outboundLamp(turnaround)?.text)
    }

    @Test
    fun `an inbound leg is airborne once it took off from the origin`() {
        // 修复前只看本站到达：进港航班从前站起飞后一路显示"未起飞"，直到落地才变。
        val airborne = turnaround.copy(inboundActualDeparture = date.atTime(14, 50))

        val lamp = inboundLamp(airborne)

        assertEquals("已起飞", lamp?.text)
        assertEquals(LampKind.Ok, lamp?.kind)
        assertTrue(lamp!!.dot)
        assertEquals("未起飞", outboundLamp(airborne)?.text)
    }

    @Test
    fun `an inbound leg that left the origin stand counts as departed`() {
        assertEquals("已起飞", inboundLamp(turnaround.copy(inboundActualOffBlock = date.atTime(14, 40)))?.text)
    }

    @Test
    fun `an inbound leg has landed once the local arrival is known`() {
        val landed = turnaround.copy(inboundActualDeparture = date.atTime(14, 50), actualArrival = date.atTime(16, 40))

        val lamp = inboundLamp(landed)

        assertEquals("已落地", lamp?.text)
        assertEquals(LampKind.Ok, lamp?.kind)
        assertTrue(lamp!!.dot)
    }

    @Test
    fun `an outbound leg is airborne after the local departure and landed after the destination arrival`() {
        val departed = turnaround.copy(actualDeparture = date.atTime(17, 40))
        assertEquals("已起飞", outboundLamp(departed)?.text)

        val landed = departed.copy(outboundActualArrival = date.atTime(19, 30))
        assertEquals("已落地", outboundLamp(landed)?.text)
        // 出港段的动态不影响进港段。
        assertEquals("未起飞", inboundLamp(landed)?.text)
    }

    @Test
    fun `the VariFlight state alone can move the lamp`() {
        assertEquals("已起飞", inboundLamp(turnaround.copy(inboundFlightState = "起飞"))?.text)
        assertEquals("已落地", inboundLamp(turnaround.copy(inboundFlightState = "到达"))?.text)
        assertEquals("已落地", outboundLamp(turnaround.copy(outboundFlightState = "到达"))?.text)
        assertEquals("未起飞", outboundLamp(turnaround.copy(outboundFlightState = "延误"))?.text)
    }

    @Test
    fun `a late estimate shows the delay only until the flight moves`() {
        val late = turnaround.copy(estimatedDeparture = date.atTime(18, 5))

        assertEquals("晚 30 分", outboundLamp(late)?.text)
        assertEquals(LampKind.Estimate, outboundLamp(late)?.kind)
        assertEquals("已起飞", outboundLamp(late.copy(outboundActualOffBlock = date.atTime(18, 1)))?.text)
    }

    @Test
    fun `a completed duty says done only when nothing actually happened`() {
        assertEquals("已完成", inboundLamp(turnaround, completed = true)?.text)
        assertEquals("已完成", outboundLamp(turnaround, completed = true)?.text)

        val flown = turnaround.copy(actualArrival = date.atTime(16, 40), actualDeparture = date.atTime(17, 40))
        assertEquals("已落地", inboundLamp(flown, completed = true)?.text)
        assertEquals("已起飞", outboundLamp(flown, completed = true)?.text)
    }

    @Test
    fun `a cancellation beats the flight phase`() {
        val cancellation = FlightCancellationRecord(
            "ZZ5637", date, FlightCancellationScope.TRIP, 4_000L, 10_000L, "cancel",
        )
        val legs = turnaround.copy(actualArrival = date.atTime(16, 40))
            .legUiModels(MucContext(flightCancellations = listOf(cancellation)), DetailLevel.SUMMARY)

        val lamp = legStatus(legs[0], completed = false)

        assertEquals("已取消", lamp?.text)
        assertEquals(LampKind.Alert, lamp?.kind)
    }

    @Test
    fun `a leg without any time information gets no lamp`() {
        val blank = turnaround.copy(scheduledArrival = null, scheduledDeparture = null)

        assertNull(inboundLamp(blank))
        assertNull(outboundLamp(blank))
    }
}
