package com.bradj.airshift.model.shift

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 回归锁：把六份真实排班表右侧“候机早班/中班/夜班”行的分组顺序原样固定下来。
 * 只要 [ShiftCycle] 的周期、轮转步长或相位被改动，这里就会失败。
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
        assertEquals(listOf(4, 10, 3, 1, 5, 11, 8, 9, 2, 6), schedule.orderFor(ShiftCycle.ANCHOR))
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
}
