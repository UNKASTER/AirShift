package com.bradj.airshift.ui.components

import com.bradj.airshift.model.LegDirection
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.specialservice.Confidence
import com.bradj.airshift.specialservice.FlightCancellationRecord
import com.bradj.airshift.specialservice.FlightCancellationScope
import com.bradj.airshift.specialservice.FlightServiceRecord
import com.bradj.airshift.specialservice.GateChangeRecord
import com.bradj.airshift.specialservice.ReviewStatus
import com.bradj.airshift.specialservice.ServiceType
import com.bradj.airshift.specialservice.StandChangeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AssignmentLegsTest {
    private val date: LocalDate = LocalDate.of(2026, 9, 4)
    private val turnaround = RosterAssignment(
        aircraftRegistration = "B0001",
        aircraftType = "320",
        inboundFlight = "MU5101",
        origin = "北京首都",
        scheduledArrival = date.atTime(10, 0),
        outboundFlight = "MU5102",
        destination = "广州白云",
        scheduledDeparture = date.atTime(12, 0),
        assignees = "测试甲",
        estimatedDeparture = date.atTime(12, 20),
        inboundBoardingGate = "A08",
        inboundDepartureStand = "101",
        boardingGate = "D64",
        departureStand = "358",
        arrivalStand = "105",
        outboundArrivalStand = "C3",
        originCode = "PEK",
        destinationCode = "CAN",
        localAirportCode = "XIY",
        localAirportName = "西安咸阳",
        inboundGateClosedObservedAt = date.atTime(9, 40),
        outboundActualOffBlock = date.atTime(12, 25),
    )
    private val gateChange = GateChangeRecord("MU5102", date, "D65", 1_000L, 10_000L, "gate", previousGate = "D64")
    private val standChange = StandChangeRecord("MU5101", date, "107", 2_000L, 10_000L, "stand")
    private val wheelchair = FlightServiceRecord(
        "MU5102", date, ServiceType.WHEELCHAIR, null, 1, 3_000L, Confidence.HIGH, ReviewStatus.CONFIRMED, 10_000L, "svc",
    )
    private val cancellation = FlightCancellationRecord("MU5101", date, FlightCancellationScope.TRIP, 4_000L, 10_000L, "cancel")
    private val muc = MucContext(
        specialServiceRecords = listOf(wheelchair),
        gateChanges = listOf(gateChange),
        standChanges = listOf(standChange),
        flightCancellations = listOf(cancellation),
    )

    @Test
    fun `a turnaround yields the inbound leg then the outbound leg with the local airport in between`() {
        val legs = turnaround.legUiModels(MucContext(), DetailLevel.SUMMARY)

        assertEquals(listOf(LegDirection.INBOUND, LegDirection.OUTBOUND), legs.map { it.direction })
        val inbound = legs[0]
        assertEquals("MU5101", inbound.flight)
        assertEquals("PEK" to "XIY", inbound.fromCode to inbound.toCode)
        assertEquals("北京首都" to "西安咸阳", inbound.fromName to inbound.toName)
        val outbound = legs[1]
        assertEquals("XIY" to "CAN", outbound.fromCode to outbound.toCode)
        assertEquals(date.atTime(12, 20), outbound.estimated)
        assertEquals(date.atTime(12, 25), outbound.offBlock)
    }

    @Test
    fun `every leg side lists only the stand`() {
        // 登机口不再出现在航线网格里：进港到达侧本来就没有登机口字段，出港侧统一改为只显示机位。
        val legs = turnaround.legUiModels(muc, DetailLevel.FULL)
        legs.forEach { leg ->
            assertEquals(listOf(DetailKind.STAND), leg.originDetails.map { it.kind })
            assertEquals(listOf(DetailKind.STAND), leg.destinationDetails.map { it.kind })
        }
        assertEquals("101", legs[0].originDetails.single().value)
        assertEquals("358", legs[1].originDetails.single().value)
        assertEquals("C3", legs[1].destinationDetails.single().value)
    }

    @Test
    fun `the summary level only flags a change without revealing the new value`() {
        val legs = turnaround.legUiModels(muc, DetailLevel.SUMMARY)
        val inbound = legs[0]
        val outbound = legs[1]

        // MUC 登机口变更在卡片里是单独一行提示；列表页只说有变更，不展开新旧值。
        val gateNotice = outbound.details.single { it.kind == DetailKind.GATE_CHANGE }
        assertEquals("MUC 已通知变更", gateNotice.value)
        assertTrue(gateNotice.hasChange)
        val inboundStand = inbound.destinationDetails.single { it.kind == DetailKind.STAND }
        assertEquals("105", inboundStand.value)
        assertTrue(inboundStand.hasChange)
        assertTrue(outbound.details.none { it.kind == DetailKind.BOARDING_START })
        assertEquals(
            listOf(DetailKind.GATE_CHANGE, DetailKind.GATE_CLOSED, DetailKind.OFF_BLOCK),
            outbound.details.map { it.kind },
        )
        assertTrue(inbound.details.none { it.kind == DetailKind.GATE_CHANGE })
        // 列表页只在标题处打“特服”角标，不展开明细。
        assertTrue(outbound.hasSpecialServices)
        assertTrue(outbound.specialServices.isEmpty())
    }

    @Test
    fun `the full level shows old and new values plus timings and special service details`() {
        val legs = turnaround.legUiModels(muc, DetailLevel.FULL)
        val inbound = legs[0]
        val outbound = legs[1]

        val gateNotice = outbound.details.single { it.kind == DetailKind.GATE_CHANGE }
        assertTrue(gateNotice.value, gateNotice.value.startsWith("D64 → D65 · MUC 更新于 "))
        assertTrue(gateNotice.hasChange)
        assertEquals("105 → 107", inbound.destinationDetails.single { it.kind == DetailKind.STAND }.value)
        assertEquals(
            listOf(
                DetailKind.BOARDING_START,
                DetailKind.BOARDING_END,
                DetailKind.GATE_CHANGE,
                DetailKind.GATE_CLOSED,
                DetailKind.OFF_BLOCK,
            ),
            outbound.details.map { it.kind },
        )
        // 预计登机开始 = 实时起飞 12:20 − 40 分钟。
        assertEquals("11:40", outbound.details.single { it.kind == DetailKind.BOARDING_START }.value)
        assertEquals(
            listOf(DetailKind.STAND_CHANGE_SOURCE, DetailKind.GATE_CLOSED, DetailKind.OFF_BLOCK),
            inbound.details.map { it.kind },
        )
        assertEquals("09:40", inbound.details.single { it.kind == DetailKind.GATE_CLOSED }.value)
        assertEquals(listOf(wheelchair), outbound.specialServices)
        assertTrue(inbound.specialServices.isEmpty())
    }

    @Test
    fun `a cancellation attaches only to the leg it names`() {
        val legs = turnaround.legUiModels(muc, DetailLevel.SUMMARY)
        assertEquals(cancellation, legs[0].flightCancellation)
        assertNull(legs[1].flightCancellation)
    }

    @Test
    fun `aircraft identity sits on the last leg`() {
        val turnaroundLegs = turnaround.legUiModels(MucContext(), DetailLevel.SUMMARY)
        assertNull(turnaroundLegs[0].aircraftRegistration)
        assertEquals("B0001", turnaroundLegs[1].aircraftRegistration)
        assertEquals("320", turnaroundLegs[1].aircraftType)

        val arrivalOnly = turnaround.copy(outboundFlight = null, scheduledDeparture = null, aircraftType = null)
        val single = arrivalOnly.legUiModels(MucContext(), DetailLevel.SUMMARY).single()
        assertEquals("B0001", single.aircraftRegistration)
        assertEquals("--", single.aircraftType)
        assertFalse(single.hasSpecialServices)
    }

    @Test
    fun `mismatched dates keep the roster values untouched`() {
        val staleMuc = MucContext(
            gateChanges = listOf(gateChange.copy(operationDate = date.minusDays(1))),
            standChanges = listOf(standChange.copy(operationDate = date.plusDays(1))),
        )
        val legs = turnaround.legUiModels(staleMuc, DetailLevel.FULL)
        assertTrue(legs[1].details.none { it.kind == DetailKind.GATE_CHANGE })
        assertFalse(legs[1].originDetails.single { it.kind == DetailKind.STAND }.hasChange)
        assertEquals("105", legs[0].destinationDetails.single { it.kind == DetailKind.STAND }.value)
    }
}
