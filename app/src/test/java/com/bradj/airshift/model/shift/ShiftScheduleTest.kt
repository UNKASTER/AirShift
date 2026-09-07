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
    fun `a calibration dated on the workday of the other team switches the schedule to that team`() {
        // 9/1 一组交接班、二组接班：带班次行的表只可能是二组的。
        val calibrated = ShiftSchedule(
            ShiftCalibration(
                date = date(9, 1),
                observed = ObservedShiftGroups(
                    early = listOf(1, 2, 3),
                    mid = listOf(4, 5, 6, 7),
                    night = listOf(8, 9, 10),
                    members = emptyMap(),
                    hasSyntheticIds = true,
                ),
            ),
            fallbackTeam = ShiftTeam.FIRST,
        )
        assertTrue(calibrated.isCalibrated)
        assertEquals(ShiftTeam.SECOND, calibrated.team)
        assertEquals((1..10).toList(), calibrated.orderFor(date(9, 1)))
        assertEquals(ShiftDayKind.WORK_FIRST, calibrated.dayKind(date(9, 1)))
        // 一组的整班日是二组的休息日。
        assertNull(calibrated.orderFor(date(8, 31)))
        assertEquals(ShiftDayKind.REST, calibrated.dayFor(1, date(8, 31)).kind)
    }

    @Test
    fun `without a calibration the second team only yields day kinds`() {
        val second = ShiftSchedule(fallbackTeam = ShiftTeam.SECOND)
        assertEquals(ShiftTeam.SECOND, second.team)
        assertFalse(second.isCalibrated)
        assertEquals(0, second.table.size)
        val workday = second.dayFor(1, date(9, 7))
        assertEquals(ShiftDayKind.WORK_FIRST, workday.kind)
        assertNull(workday.slot)
        assertTrue("上班日仍要到岗，只是不知道槽位", workday.attends)
        val handover = second.dayFor(1, date(9, 10))
        assertEquals(ShiftDayKind.HANDOVER, handover.kind)
        assertTrue(handover.attends)
        assertFalse(handover.isHandoverExempt)
        assertFalse(second.dayFor(1, date(9, 11)).attends)
    }

    @Test
    fun `an unknown group on the first team still does not attend`() {
        assertFalse(schedule.dayFor(7, date(8, 30)).attends)
    }

    private val septemberSeventh = ShiftCalibration(
        date = date(9, 7),
        observed = ObservedShiftGroups(
            early = listOf(1, 2, 3),
            mid = listOf(4, 5, 6, 7),
            night = listOf(8, 9, 10),
            members = mapOf(
                1 to listOf("王甲子", "李乙丑", "周丙寅"),
                2 to listOf("吴丁卯", "郑戊辰"),
                3 to listOf("冯己巳", "陈庚午"),
                4 to listOf("褚辛未", "卫壬申"),
                5 to listOf("蒋癸酉", "沈甲戌", "韩乙亥"),
                6 to listOf("杨丙子", "朱丁丑"),
                7 to listOf("秦戊寅", "尤己卯"),
                8 to listOf("许庚辰", "何辛巳"),
                9 to listOf("吕壬午", "施癸未"),
                10 to listOf("张甲申", "孔乙酉"),
            ),
            hasSyntheticIds = true,
        ),
    )

    @Test
    fun `a later second team sheet is aligned to the previous ids by shared members`() {
        // 9/8：整体左移 3 位后位次全变；组 5 少了一个人、组 10 多了一个人。
        val next = ShiftCalibration(
            date = date(9, 8),
            observed = ObservedShiftGroups(
                early = listOf(1, 2, 3),
                mid = listOf(4, 5, 6, 7),
                night = listOf(8, 9, 10),
                members = mapOf(
                    1 to listOf("褚辛未", "卫壬申"),
                    2 to listOf("蒋癸酉", "韩乙亥"),
                    3 to listOf("杨丙子", "朱丁丑"),
                    4 to listOf("秦戊寅", "尤己卯"),
                    5 to listOf("许庚辰", "何辛巳"),
                    6 to listOf("吕壬午", "施癸未"),
                    7 to listOf("张甲申", "孔乙酉", "沈甲戌"),
                    8 to listOf("王甲子", "李乙丑", "周丙寅"),
                    9 to listOf("吴丁卯", "郑戊辰"),
                    10 to listOf("冯己巳", "陈庚午"),
                ),
                hasSyntheticIds = true,
            ),
        ).alignedWith(septemberSeventh)

        assertEquals(listOf(4, 5, 6), next.observed.early)
        assertEquals(listOf(7, 8, 9, 10), next.observed.mid)
        assertEquals(listOf(1, 2, 3), next.observed.night)
        assertEquals(listOf("张甲申", "孔乙酉", "沈甲戌"), next.observed.members[10])
        assertTrue(next.observed.hasSyntheticIds)
        // 对齐后的校准和 9/7 的校准算出同一张日历。
        val fromFirst = ShiftSchedule(septemberSeventh)
        val fromNext = ShiftSchedule(next)
        listOf(date(9, 7), date(9, 8), date(9, 9), date(9, 13)).forEach { day ->
            assertEquals("$day", fromFirst.orderFor(day), fromNext.orderFor(day))
        }
        assertEquals("王甲子组", fromNext.labelOf(1))
    }

    @Test
    fun `a brand new group gets an id neither sheet has used`() {
        val next = ShiftCalibration(
            date = date(9, 8),
            observed = ObservedShiftGroups(
                early = listOf(1, 2),
                mid = listOf(3),
                night = listOf(4),
                members = mapOf(
                    1 to listOf("王甲子"),
                    2 to listOf("新人甲", "新人乙"),
                    3 to listOf("吴丁卯"),
                    4 to listOf("冯己巳"),
                ),
                hasSyntheticIds = true,
            ),
        ).alignedWith(septemberSeventh)
        assertEquals(listOf(1, 11), next.observed.early)
        assertEquals(listOf(2), next.observed.mid)
        assertEquals(listOf(3), next.observed.night)
        assertEquals(listOf("新人甲", "新人乙"), next.observed.members[11])
    }

    @Test
    fun `alignment leaves first team sheets and other team histories alone`() {
        val firstTeam = ShiftCalibration(
            date = date(8, 25),
            observed = ObservedShiftGroups(listOf(8, 9, 2), listOf(6, 4, 10, 3), listOf(1, 5, 11), emptyMap()),
        )
        assertEquals(firstTeam, firstTeam.alignedWith(septemberSeventh))
        assertEquals(septemberSeventh, septemberSeventh.alignedWith(firstTeam))
        assertEquals(septemberSeventh, septemberSeventh.alignedWith(null))
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
