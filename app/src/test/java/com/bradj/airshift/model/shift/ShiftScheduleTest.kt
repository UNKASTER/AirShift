package com.bradj.airshift.model.shift

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShiftScheduleTest {
    private val schedule = ShiftSchedule()
    private fun date(month: Int, day: Int) = LocalDate.of(2026, month, day)

    @Test
    fun `slot labels use chinese numerals after the tier`() {
        assertEquals("早一", ShiftSlot(ShiftTier.EARLY, 1).label)
        assertEquals("中四", ShiftSlot(ShiftTier.MID, 4).label)
        assertEquals("晚三", ShiftSlot(ShiftTier.NIGHT, 3).label)
    }

    @Test
    fun `tier sizes map positions onto the three four three layout`() {
        val sizes = ShiftTierSizes.DEFAULT
        assertEquals(ShiftSlot(ShiftTier.EARLY, 1), sizes.slotAt(0))
        assertEquals(ShiftSlot(ShiftTier.EARLY, 3), sizes.slotAt(2))
        assertEquals(ShiftSlot(ShiftTier.MID, 1), sizes.slotAt(3))
        assertEquals(ShiftSlot(ShiftTier.MID, 4), sizes.slotAt(6))
        assertEquals(ShiftSlot(ShiftTier.NIGHT, 1), sizes.slotAt(7))
        assertEquals(ShiftSlot(ShiftTier.NIGHT, 3), sizes.slotAt(9))
        assertNull(sizes.slotAt(10))
        assertNull(sizes.slotAt(-1))
    }

    @Test
    fun `a full twelve group table exposes the fourth slot of every tier`() {
        val sizes = ShiftTierSizes(early = 4, mid = 4, night = 4)
        assertEquals(ShiftSlot(ShiftTier.EARLY, 4), sizes.slotAt(3))
        assertEquals(ShiftSlot(ShiftTier.MID, 4), sizes.slotAt(7))
        assertEquals(ShiftSlot(ShiftTier.NIGHT, 4), sizes.slotAt(11))
    }

    @Test
    fun `rest days carry no slot and no attendance`() {
        listOf(date(9, 2), date(9, 3)).forEach { day ->
            val result = schedule.dayFor(groupId = 1, date = day)
            assertEquals(ShiftDayKind.REST, result.kind)
            assertNull(result.slot)
            assertFalse(result.attends)
        }
    }

    @Test
    fun `the handover day inherits the third workday slot`() {
        // 2026-08-31 的早1-3 是组 11、8、9；中1-4 是组 2、6、4、10。
        val handover = date(9, 1)
        assertEquals(ShiftSlot(ShiftTier.EARLY, 1), schedule.dayFor(11, handover).slot)
        assertEquals(ShiftSlot(ShiftTier.MID, 4), schedule.dayFor(10, handover).slot)
        assertEquals(date(8, 31), schedule.dayFor(11, handover).slotInheritedFrom)
    }

    @Test
    fun `only the early and mid groups of the third workday attend the handover morning`() {
        val handover = date(9, 1)
        val attending = ShiftGroupTable.DEFAULT.cycleOrder.filter { schedule.dayFor(it, handover).attends }
        // 9.1 的排班表中实际出现的 7 个组。
        assertEquals(setOf(11, 8, 9, 2, 6, 4, 10), attending.toSet())
    }

    @Test
    fun `the night groups of the third workday are exempt from the handover morning`() {
        val handover = date(9, 1)
        listOf(3, 1, 5).forEach { group ->
            val result = schedule.dayFor(group, handover)
            assertEquals(ShiftTier.NIGHT, result.slot?.tier)
            assertFalse("组$group 前一晚干到凌晨，不应到岗", result.attends)
            assertTrue(result.isHandoverExempt)
        }
    }

    @Test
    fun `full workdays always require attendance for an active group`() {
        listOf(date(8, 29), date(8, 30), date(8, 31)).forEach { day ->
            ShiftGroupTable.DEFAULT.cycleOrder.forEach { group ->
                val result = schedule.dayFor(group, day)
                assertTrue("$day 组$group", result.attends)
                assertNotNull(result.slot)
            }
        }
    }

    @Test
    fun `a date range yields one entry per calendar day`() {
        val days = schedule.daysFor(1, date(8, 29), date(9, 3))
        assertEquals(6, days.size)
        assertEquals(date(8, 29), days.first().date)
        assertEquals(date(9, 3), days.last().date)
    }

    @Test
    fun `an inverted date range is empty`() {
        assertTrue(schedule.daysFor(1, date(9, 3), date(8, 29)).isEmpty())
    }

    @Test
    fun `calibrating on an observed sheet keeps that sheet exact`() {
        val calibrated = ShiftSchedule(
            ShiftCalibration(
                date = date(8, 25),
                observed = ObservedShiftGroups(
                    early = listOf(8, 9, 2),
                    mid = listOf(6, 4, 10, 3),
                    night = listOf(1, 5, 11),
                    members = mapOf(11 to listOf("丙丑", "丙寅")),
                ),
            ),
        )
        assertTrue(calibrated.isCalibrated)
        assertEquals(listOf(8, 9, 2, 6, 4, 10, 3, 1, 5, 11), calibrated.orderFor(date(8, 25)))
    }

    @Test
    fun `calibration still reproduces the other observed sheets`() {
        val calibrated = ShiftSchedule(
            ShiftCalibration(
                date = date(8, 25),
                observed = ObservedShiftGroups(
                    early = listOf(8, 9, 2),
                    mid = listOf(6, 4, 10, 3),
                    night = listOf(1, 5, 11),
                    members = emptyMap(),
                ),
            ),
        )
        assertEquals(listOf(1, 5, 11, 8, 9, 2, 6, 4, 10, 3), calibrated.orderFor(date(8, 24)))
        assertEquals(listOf(11, 8, 9, 2, 6, 4, 10, 3, 1, 5), calibrated.orderFor(date(8, 31)))
    }

    @Test
    fun `a calibration on a non working day is ignored`() {
        val calibrated = ShiftSchedule(
            ShiftCalibration(
                date = date(9, 1),
                observed = ObservedShiftGroups(
                    early = listOf(11, 8, 9),
                    mid = listOf(2, 6, 4, 10),
                    night = listOf(3, 1, 5),
                    members = emptyMap(),
                ),
            ),
        )
        assertFalse(calibrated.isCalibrated)
        assertEquals(ShiftGroupTable.DEFAULT.cycleOrder, calibrated.table.cycleOrder)
    }

    @Test
    fun `an incomplete calibration falls back to the built in table`() {
        val calibrated = ShiftSchedule(
            ShiftCalibration(
                date = date(8, 25),
                observed = ObservedShiftGroups(
                    early = listOf(8, 9, 2),
                    mid = emptyList(),
                    night = emptyList(),
                    members = emptyMap(),
                ),
            ),
        )
        assertFalse(calibrated.isCalibrated)
        assertEquals(ShiftGroupTable.DEFAULT.cycleOrder, calibrated.table.cycleOrder)
    }
}
