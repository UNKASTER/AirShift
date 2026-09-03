package com.bradj.airshift.model.shift

import com.bradj.airshift.model.RosterAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ShiftBusPlanTest {
    private val early1 = ShiftSlot(ShiftTier.EARLY, 1)
    private val early2 = ShiftSlot(ShiftTier.EARLY, 2)
    private val mid1 = ShiftSlot(ShiftTier.MID, 1)
    private val mid3 = ShiftSlot(ShiftTier.MID, 3)
    private val night1 = ShiftSlot(ShiftTier.NIGHT, 1)
    private val night3 = ShiftSlot(ShiftTier.NIGHT, 3)

    private fun bus(hour: Int, minute: Int) = LocalTime.of(hour, minute)

    // ---------- 班车时刻表 ----------

    @Test
    fun `the regular schedule adds the three early runs to the two hourly cadence`() {
        assertEquals(
            listOf(
                bus(4, 50), bus(5, 25), bus(5, 55),
                bus(8, 0), bus(10, 0), bus(12, 0), bus(14, 0),
                bus(16, 0), bus(18, 0), bus(20, 0), bus(22, 0),
            ),
            ShiftBusPlan.REGULAR_DEPARTURES,
        )
    }

    @Test
    fun `only handover related days offer the nine o clock run`() {
        assertTrue(ShiftBusPlan.hasExtraHandoverBus(ShiftDayKind.WORK_FIRST))
        assertTrue(ShiftBusPlan.hasExtraHandoverBus(ShiftDayKind.HANDOVER))
        assertFalse(ShiftBusPlan.hasExtraHandoverBus(ShiftDayKind.WORK_SECOND))
        assertFalse(ShiftBusPlan.hasExtraHandoverBus(ShiftDayKind.WORK_THIRD))

        assertTrue(ShiftBusPlan.departures(ShiftDayKind.WORK_FIRST).contains(bus(9, 0)))
        assertFalse(ShiftBusPlan.departures(ShiftDayKind.WORK_SECOND).contains(bus(9, 0)))
        assertEquals(ShiftBusPlan.departures(ShiftDayKind.WORK_FIRST).sorted(), ShiftBusPlan.departures(ShiftDayKind.WORK_FIRST))
    }

    // ---------- 到位时间 ----------

    @Test
    fun `the expected report time subtracts an hour for an outbound first task`() {
        // 早一在第 2、3 天的首个任务是 07:20 出港，故最晚 06:20 到位。
        assertEquals(ShiftClock.of(6, 20), ShiftBusPlan.expectedReportByMinutes(ShiftDayKind.WORK_SECOND, early1))
        assertEquals(ShiftClock.of(6, 10), ShiftBusPlan.expectedReportByMinutes(ShiftDayKind.WORK_SECOND, night1))
    }

    @Test
    fun `the expected report time subtracts ten minutes for an inbound first task`() {
        // 中二至中四下午上岗，首个任务是过站航班的进港段：12:50 进港 → 最晚 12:40 到位。
        assertEquals(
            ShiftClock.of(12, 40),
            ShiftBusPlan.expectedReportByMinutes(ShiftDayKind.WORK_SECOND, ShiftSlot(ShiftTier.MID, 2)),
        )
        // 接班日的早班同样从进港航班接手。
        assertEquals(
            ShiftClock.of(10, 45),
            ShiftBusPlan.expectedReportByMinutes(ShiftDayKind.WORK_FIRST, early1),
        )
    }

    @Test
    fun `the morning slots keep the outbound hour of lead`() {
        assertFalse(ShiftBusPlan.expectedFirstTask(ShiftDayKind.WORK_SECOND, early1)!!.inbound)
        assertFalse(ShiftBusPlan.expectedFirstTask(ShiftDayKind.WORK_SECOND, mid1)!!.inbound)
        assertFalse(ShiftBusPlan.expectedFirstTask(ShiftDayKind.WORK_SECOND, night1)!!.inbound)
        assertFalse(ShiftBusPlan.expectedFirstTask(ShiftDayKind.HANDOVER, mid3)!!.inbound)
        // 接班日只有夜班槽位赶上 10:40 的出港。
        assertFalse(ShiftBusPlan.expectedFirstTask(ShiftDayKind.WORK_FIRST, night1)!!.inbound)
        assertTrue(ShiftBusPlan.expectedFirstTask(ShiftDayKind.WORK_FIRST, mid1)!!.inbound)
    }

    /**
     * 不变量：任何日型与槽位、任何可选余量下，推荐班车的到场时间都不能晚于到位时间。
     * 早先把下午上岗的中班误标为出港，就是被这条规则挡住的。
     */
    @Test
    fun `no recommended bus ever arrives after the report deadline`() {
        val kinds = listOf(
            ShiftDayKind.WORK_FIRST,
            ShiftDayKind.WORK_SECOND,
            ShiftDayKind.WORK_THIRD,
            ShiftDayKind.HANDOVER,
        )
        val slots = ShiftTier.entries.flatMap { tier -> (1..4).map { ShiftSlot(tier, it) } }
        var checked = 0
        kinds.forEach { kind ->
            slots.forEach { slot ->
                if (ShiftBusPlan.expectedFirstTask(kind, slot) == null) return@forEach
                ShiftBusPlan.REPORT_MARGIN_OPTIONS.forEach { margin ->
                    val recommendation = ShiftBusPlan.recommend(kind, slot, marginMinutes = margin)
                    requireNotNull(recommendation) { "$kind ${slot.label} 余量 $margin 没有班车" }
                    assertTrue(
                        "$kind ${slot.label} 余量 $margin 的到场时间晚于到位时间",
                        recommendation.spareMinutes >= 0,
                    )
                    checked++
                }
            }
        }
        assertTrue(checked > 60)
    }

    @Test
    fun `rest days have no expected first task`() {
        assertNull(ShiftBusPlan.expectedFirstTask(ShiftDayKind.REST, early1))
        assertNull(ShiftBusPlan.expectedReportByMinutes(ShiftDayKind.REST, early1))
        assertNull(ShiftBusPlan.expectedOffDutyMinutes(ShiftDayKind.REST, early1))
    }

    @Test
    fun `the handover day ends at ten for every attending slot`() {
        assertEquals(ShiftClock.of(10, 0), ShiftBusPlan.expectedOffDutyMinutes(ShiftDayKind.HANDOVER, early1))
        assertEquals(ShiftClock.of(10, 0), ShiftBusPlan.expectedOffDutyMinutes(ShiftDayKind.HANDOVER, mid3))
    }

    @Test
    fun `night slots finish after midnight`() {
        val night = ShiftBusPlan.expectedOffDutyMinutes(ShiftDayKind.WORK_SECOND, night3)!!
        assertTrue(night >= ShiftClock.MINUTES_PER_DAY)
        assertEquals("次日 01:35", ShiftClock.format(night))
    }

    // ---------- 固定班车（用户既定习惯） ----------

    @Test
    fun `the first day sends early and night slots on the nine o clock run`() {
        assertEquals(bus(9, 0), ShiftBusPlan.fixedDeparture(ShiftDayKind.WORK_FIRST, early1))
        assertEquals(bus(9, 0), ShiftBusPlan.fixedDeparture(ShiftDayKind.WORK_FIRST, night3))

        val recommendation = ShiftBusPlan.recommend(ShiftDayKind.WORK_FIRST, night1)!!
        assertEquals(bus(9, 0), recommendation.departure)
        assertTrue(recommendation.isExtraHandoverBus)
        assertTrue(recommendation.isFixedByRule)
    }

    @Test
    fun `the first day sends every mid slot on the noon run`() {
        listOf(1, 2, 3, 4).forEach { number ->
            val slot = ShiftSlot(ShiftTier.MID, number)
            assertEquals("中$number", bus(12, 0), ShiftBusPlan.fixedDeparture(ShiftDayKind.WORK_FIRST, slot))
        }
    }

    @Test
    fun `full workdays send mid two onwards on the noon run but not mid one`() {
        assertNull(ShiftBusPlan.fixedDeparture(ShiftDayKind.WORK_SECOND, mid1))
        listOf(2, 3, 4).forEach { number ->
            val slot = ShiftSlot(ShiftTier.MID, number)
            assertEquals("中$number", bus(12, 0), ShiftBusPlan.fixedDeparture(ShiftDayKind.WORK_THIRD, slot))
        }
    }

    @Test
    fun `the handover day has no fixed rule and is computed from the report time`() {
        assertNull(ShiftBusPlan.fixedDeparture(ShiftDayKind.HANDOVER, early1))
        assertNull(ShiftBusPlan.fixedDeparture(ShiftDayKind.HANDOVER, mid3))
    }

    // ---------- 选车与到位余量 ----------

    @Test
    fun `the default margin staggers the morning slots across the early runs`() {
        val kind = ShiftDayKind.WORK_SECOND
        assertEquals(bus(5, 25), ShiftBusPlan.recommend(kind, night1)!!.departure)
        assertEquals(bus(5, 55), ShiftBusPlan.recommend(kind, night3)!!.departure)
        assertEquals(bus(5, 55), ShiftBusPlan.recommend(kind, early1)!!.departure)
        assertEquals(bus(5, 55), ShiftBusPlan.recommend(kind, early2)!!.departure)
        assertEquals(bus(5, 55), ShiftBusPlan.recommend(kind, mid1)!!.departure)
    }

    @Test
    fun `a zero margin picks the latest legal run`() {
        val kind = ShiftDayKind.WORK_SECOND
        assertEquals(bus(5, 55), ShiftBusPlan.recommend(kind, night1, marginMinutes = 0)!!.departure)
        assertEquals(bus(5, 55), ShiftBusPlan.recommend(kind, early1, marginMinutes = 0)!!.departure)
    }

    @Test
    fun `a thirty minute margin pulls the morning slots one run earlier`() {
        val kind = ShiftDayKind.WORK_SECOND
        assertEquals(bus(5, 25), ShiftBusPlan.recommend(kind, early1, marginMinutes = 30)!!.departure)
        assertEquals(bus(5, 25), ShiftBusPlan.recommend(kind, night1, marginMinutes = 30)!!.departure)
    }

    @Test
    fun `a negative margin is treated as zero`() {
        val kind = ShiftDayKind.WORK_SECOND
        assertEquals(
            ShiftBusPlan.recommend(kind, early1, marginMinutes = 0)!!.departure,
            ShiftBusPlan.recommend(kind, early1, marginMinutes = -60)!!.departure,
        )
    }

    @Test
    fun `the recommendation reports arrival and spare minutes`() {
        val recommendation = ShiftBusPlan.recommend(ShiftDayKind.WORK_SECOND, early1)!!
        assertEquals(ShiftClock.of(6, 0), recommendation.arriveAtMinutes)
        assertEquals(ShiftClock.of(6, 20), recommendation.reportByMinutes)
        assertEquals(20, recommendation.spareMinutes)
        assertEquals(ShiftEstimateSource.ESTIMATE, recommendation.source)
        assertFalse(recommendation.isFixedByRule)
    }

    @Test
    fun `the handover morning uses the early runs`() {
        assertEquals(bus(5, 25), ShiftBusPlan.recommend(ShiftDayKind.HANDOVER, early1)!!.departure)
        assertEquals(bus(5, 55), ShiftBusPlan.recommend(ShiftDayKind.HANDOVER, early2)!!.departure)
        assertEquals(bus(5, 55), ShiftBusPlan.recommend(ShiftDayKind.HANDOVER, ShiftSlot(ShiftTier.MID, 4))!!.departure)
    }

    @Test
    fun `rest days get no bus`() {
        assertNull(ShiftBusPlan.recommend(ShiftDayKind.REST, early1))
    }

    @Test
    fun `a report time before the first run yields no bus`() {
        val result = ShiftBusPlan.recommend(
            ShiftDayKind.WORK_SECOND,
            early1,
            rosterReportByMinutes = ShiftClock.of(4, 30),
        )
        assertNull(result)
    }

    // ---------- 真实排班优先 ----------

    @Test
    fun `a real roster report time overrides the historical estimate`() {
        val recommendation = ShiftBusPlan.recommend(
            ShiftDayKind.WORK_SECOND,
            early1,
            rosterReportByMinutes = ShiftClock.of(8, 40),
        )!!
        assertEquals(bus(8, 0), recommendation.departure)
        assertEquals(ShiftEstimateSource.ROSTER, recommendation.source)
    }

    @Test
    fun `a fixed rule still applies when real roster data exists`() {
        val recommendation = ShiftBusPlan.recommend(
            ShiftDayKind.WORK_SECOND,
            mid3,
            rosterReportByMinutes = ShiftClock.of(11, 50),
        )!!
        assertEquals(bus(12, 0), recommendation.departure)
        assertEquals(ShiftEstimateSource.ROSTER, recommendation.source)
        assertTrue(recommendation.isFixedByRule)
    }

    @Test
    fun `the roster bridge derives the report time from the earliest task`() {
        val day = LocalDate.of(2026, 8, 30)
        val assignments = listOf(
            outbound("MU6771", day.atTime(7, 10)),
            outbound("MU2249", day.atTime(7, 20)),
            inbound("MU6813", day.plusDays(1).atTime(0, 40)),
        )
        assertEquals(day, ShiftRosterBridge.rosterDate(assignments))
        // 最早任务 07:10 出港 → 到位 06:10。
        assertEquals(ShiftClock.of(6, 10), ShiftRosterBridge.reportByMinutes(assignments))
        // 末项 00:40 次日 → 1480 分钟。
        assertEquals(ShiftClock.of(0, 40, nextDay = true), ShiftRosterBridge.lastTaskMinutes(assignments))
    }

    @Test
    fun `the roster bridge applies the ten minute lead to an inbound first task`() {
        val day = LocalDate.of(2026, 8, 29)
        val assignments = listOf(inbound("KN5621", day.atTime(10, 55)))
        assertEquals(ShiftClock.of(10, 45), ShiftRosterBridge.reportByMinutes(assignments))
    }

    @Test
    fun `the roster bridge tolerates an empty or timeless roster`() {
        assertNull(ShiftRosterBridge.rosterDate(emptyList()))
        assertNull(ShiftRosterBridge.reportByMinutes(emptyList()))
        assertNull(ShiftRosterBridge.lastTaskMinutes(emptyList()))
        val timeless = listOf(assignment(outboundFlight = "MU1000"))
        assertNull(ShiftRosterBridge.rosterDate(timeless))
        assertNull(ShiftRosterBridge.reportByMinutes(timeless))
    }

    private fun outbound(flight: String, departure: LocalDateTime) =
        assignment(outboundFlight = flight, scheduledDeparture = departure)

    private fun inbound(flight: String, arrival: LocalDateTime) =
        assignment(inboundFlight = flight, scheduledArrival = arrival)

    private fun assignment(
        inboundFlight: String? = null,
        outboundFlight: String? = null,
        scheduledArrival: LocalDateTime? = null,
        scheduledDeparture: LocalDateTime? = null,
    ) = RosterAssignment(
        aircraftRegistration = "B-1234",
        aircraftType = null,
        inboundFlight = inboundFlight,
        origin = null,
        scheduledArrival = scheduledArrival,
        outboundFlight = outboundFlight,
        destination = null,
        scheduledDeparture = scheduledDeparture,
        assignees = "甲子 甲丑",
    )
}
