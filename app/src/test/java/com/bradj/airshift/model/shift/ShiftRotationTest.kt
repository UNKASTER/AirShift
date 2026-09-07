package com.bradj.airshift.model.shift

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 回归锁：把六份一组真实排班表右侧“候机早班/中班/夜班”行的分组顺序原样固定下来，
 * 再加上 2026-09-07 二组表的位次。只要 [ShiftCycle] 的周期、轮转步长或相位被改动，这里就会失败。
 */
class ShiftRotationTest {
    private val schedule = ShiftSchedule()

    /** 实测的每日班次行：日期 → (早, 中, 夜)。 */
    private val observed = mapOf(
        LocalDate.of(2026, 8, 24) to Triple(listOf(1, 5, 11), listOf(8, 9, 2, 6), listOf(4, 10, 3)),
        LocalDate.of(2026, 8, 25) to Triple(listOf(8, 9, 2), listOf(6, 4, 10, 3), listOf(1, 5, 11)),
        LocalDate.of(2026, 8, 29) to Triple(listOf(6, 4, 10), listOf(3, 1, 5, 11), listOf(8, 9, 2)),
        LocalDate.of(2026, 8, 30) to Triple(listOf(3, 1, 5), listOf(11, 8, 9, 2), listOf(6, 4, 10)),
        LocalDate.of(2026, 8, 31) to Triple(listOf(11, 8, 9), listOf(2, 6, 4, 10), listOf(3, 1, 5)),
    )

    @Test
    fun `the rotated group order reproduces every observed roster sheet`() {
        observed.forEach { (date, tiers) ->
            val (early, mid, night) = tiers
            assertEquals("$date 的班组顺序", early + mid + night, schedule.orderFor(date))
        }
    }

    @Test
    fun `each observed sheet maps every group to its printed slot`() {
        observed.forEach { (date, tiers) ->
            val (early, mid, night) = tiers
            early.forEachIndexed { index, group ->
                assertEquals("$date 组$group", ShiftSlot(ShiftTier.EARLY, index + 1), schedule.slotOnFullWorkday(group, date))
            }
            mid.forEachIndexed { index, group ->
                assertEquals("$date 组$group", ShiftSlot(ShiftTier.MID, index + 1), schedule.slotOnFullWorkday(group, date))
            }
            night.forEachIndexed { index, group ->
                assertEquals("$date 组$group", ShiftSlot(ShiftTier.NIGHT, index + 1), schedule.slotOnFullWorkday(group, date))
            }
        }
    }

    @Test
    fun `the anchor day precedes the first observed sheet by one rotation step`() {
        // 2026-08-23 的表格已丢失，但相位必须让它比 08-24 早一个整班工作日。
        assertEquals(listOf(4, 10, 3, 1, 5, 11, 8, 9, 2, 6), schedule.orderFor(ShiftTeam.FIRST.anchor))
    }

    @Test
    fun `rest days and the handover day have no rotation of their own`() {
        assertNull(schedule.orderFor(LocalDate.of(2026, 8, 26)))
        assertNull(schedule.orderFor(LocalDate.of(2026, 8, 27)))
        assertNull(schedule.orderFor(LocalDate.of(2026, 8, 28)))
        assertNull(schedule.orderFor(LocalDate.of(2026, 9, 1)))
    }

    @Test
    fun `the rotation advances three positions per full workday across the rest break`() {
        val size = ShiftGroupTable.DEFAULT.size
        val workdays = listOf(
            LocalDate.of(2026, 8, 24),
            LocalDate.of(2026, 8, 25),
            LocalDate.of(2026, 8, 29),
            LocalDate.of(2026, 8, 30),
            LocalDate.of(2026, 8, 31),
        )
        workdays.zipWithNext().forEach { (previous, next) ->
            val before = ShiftGroupTable.DEFAULT.cycleIndexOf(schedule.orderFor(previous)!!.first())!!
            val after = ShiftGroupTable.DEFAULT.cycleIndexOf(schedule.orderFor(next)!!.first())!!
            assertEquals(
                "$previous → $next 应左移 3 位",
                ShiftCycle.ROTATION_STEP,
                Math.floorMod(after - before, size),
            )
        }
    }

    @Test
    fun `an unknown group id has no position`() {
        assertNull(schedule.positionOf(7, LocalDate.of(2026, 8, 24)))
        assertNull(schedule.slotOnFullWorkday(12, LocalDate.of(2026, 8, 24)))
    }

    @Test
    fun `every active group appears exactly once per working day`() {
        observed.keys.forEach { date ->
            val order = schedule.orderFor(date)!!
            assertEquals(ShiftGroupTable.DEFAULT.size, order.size)
            assertEquals(order.size, order.distinct().size)
            assertTrue(order.containsAll(ShiftGroupTable.DEFAULT.cycleOrder))
        }
    }

    /**
     * 二组回归锁：2026-09-07 二组表（接班日）右侧三行的位次。二组小组没有编号，这里的 1..10 是
     * 解析时按早→中→晚位次给的合成 id（不含姓名）；用户确认二组与一组轮转规则相同。
     */
    private val secondTeamSheet = ShiftCalibration(
        date = LocalDate.of(2026, 9, 7),
        observed = ObservedShiftGroups(
            early = listOf(1, 2, 3),
            mid = listOf(4, 5, 6, 7),
            night = listOf(8, 9, 10),
            members = emptyMap(),
            hasSyntheticIds = true,
        ),
    )

    @Test
    fun `the second team sheet calibrates its own schedule`() {
        val second = ShiftSchedule(secondTeamSheet)
        assertEquals(ShiftTeam.SECOND, second.team)
        assertEquals((1..10).toList(), second.orderFor(LocalDate.of(2026, 9, 7)))
        assertEquals(ShiftSlot(ShiftTier.EARLY, 1), second.slotOnFullWorkday(1, LocalDate.of(2026, 9, 7)))
        assertEquals(ShiftSlot(ShiftTier.MID, 4), second.slotOnFullWorkday(7, LocalDate.of(2026, 9, 7)))
        assertEquals(ShiftSlot(ShiftTier.NIGHT, 3), second.slotOnFullWorkday(10, LocalDate.of(2026, 9, 7)))
    }

    @Test
    fun `the second team rotates three positions per full workday from the observed sheet`() {
        val second = ShiftSchedule(secondTeamSheet)
        assertEquals(listOf(4, 5, 6, 7, 8, 9, 10, 1, 2, 3), second.orderFor(LocalDate.of(2026, 9, 8)))
        assertEquals(listOf(7, 8, 9, 10, 1, 2, 3, 4, 5, 6), second.orderFor(LocalDate.of(2026, 9, 9)))
        // 9/3 是二组上一个整班日（D3），比 9/7 早一个整班日。
        assertEquals(listOf(8, 9, 10, 1, 2, 3, 4, 5, 6, 7), second.orderFor(LocalDate.of(2026, 9, 3)))
        // 交接班与休息日不推进；下一个接班日 9/13 比 9/9 再左移 3 位。
        listOf(10, 11, 12).forEach { assertNull(second.orderFor(LocalDate.of(2026, 9, it))) }
        assertEquals(listOf(10, 1, 2, 3, 4, 5, 6, 7, 8, 9), second.orderFor(LocalDate.of(2026, 9, 13)))
    }

    @Test
    fun `the handover morning of the second team keeps the early and mid groups of the third day`() {
        val second = ShiftSchedule(secondTeamSheet)
        val handover = LocalDate.of(2026, 9, 10)
        val attending = (1..10).filter { second.dayFor(it, handover).attends }
        // 9/9 的早 1-3、中 1-4 是组 7、8、9、10、1、2、3。
        assertEquals(setOf(7, 8, 9, 10, 1, 2, 3), attending.toSet())
        assertTrue(second.dayFor(4, handover).isHandoverExempt)
    }
}
