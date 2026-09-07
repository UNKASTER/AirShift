package com.bradj.airshift.model.shift

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShiftCycleTest {
    private val team = ShiftTeam.FIRST
    private fun date(month: Int, day: Int) = LocalDate.of(2026, month, day)

    @Test
    fun `the six day cycle repeats work work work handover rest rest from the anchor`() {
        val expected = listOf(
            date(8, 23) to ShiftDayKind.WORK_FIRST,
            date(8, 24) to ShiftDayKind.WORK_SECOND,
            date(8, 25) to ShiftDayKind.WORK_THIRD,
            date(8, 26) to ShiftDayKind.HANDOVER,
            date(8, 27) to ShiftDayKind.REST,
            date(8, 28) to ShiftDayKind.REST,
            date(8, 29) to ShiftDayKind.WORK_FIRST,
            date(8, 30) to ShiftDayKind.WORK_SECOND,
            date(8, 31) to ShiftDayKind.WORK_THIRD,
            date(9, 1) to ShiftDayKind.HANDOVER,
            date(9, 2) to ShiftDayKind.REST,
            date(9, 3) to ShiftDayKind.REST,
            date(9, 4) to ShiftDayKind.WORK_FIRST,
        )
        expected.forEach { (day, kind) -> assertEquals("$day", kind, ShiftCycle.dayKind(day, team)) }
    }

    @Test
    fun `dates before the anchor stay on the same cycle`() {
        assertEquals(ShiftDayKind.REST, ShiftCycle.dayKind(date(8, 22), team))
        assertEquals(ShiftDayKind.REST, ShiftCycle.dayKind(date(8, 21), team))
        assertEquals(ShiftDayKind.HANDOVER, ShiftCycle.dayKind(date(8, 20), team))
        assertEquals(ShiftDayKind.WORK_FIRST, ShiftCycle.dayKind(date(8, 17), team))
    }

    @Test
    fun `the work ordinal counts full workdays only`() {
        assertEquals(0L, ShiftCycle.workOrdinal(date(8, 23), team))
        assertEquals(1L, ShiftCycle.workOrdinal(date(8, 24), team))
        assertEquals(2L, ShiftCycle.workOrdinal(date(8, 25), team))
        assertEquals(3L, ShiftCycle.workOrdinal(date(8, 29), team))
        assertEquals(4L, ShiftCycle.workOrdinal(date(8, 30), team))
        assertEquals(5L, ShiftCycle.workOrdinal(date(8, 31), team))
        assertEquals(6L, ShiftCycle.workOrdinal(date(9, 4), team))
    }

    @Test
    fun `the handover day and rest days have no work ordinal`() {
        assertNull(ShiftCycle.workOrdinal(date(8, 26), team))
        assertNull(ShiftCycle.workOrdinal(date(8, 27), team))
        assertNull(ShiftCycle.workOrdinal(date(8, 28), team))
        assertNull(ShiftCycle.workOrdinal(date(9, 1), team))
    }

    @Test
    fun `the work ordinal goes negative before the anchor without wrapping`() {
        assertEquals(-1L, ShiftCycle.workOrdinal(date(8, 19), team))
        assertEquals(-3L, ShiftCycle.workOrdinal(date(8, 17), team))
    }

    @Test
    fun `the rotation offset is calibrated so the first observed sheet is zero`() {
        assertEquals(0, ShiftCycle.rotationOffset(date(8, 24), team, 10))
        assertEquals(3, ShiftCycle.rotationOffset(date(8, 25), team, 10))
        assertEquals(6, ShiftCycle.rotationOffset(date(8, 29), team, 10))
        assertEquals(9, ShiftCycle.rotationOffset(date(8, 30), team, 10))
        assertEquals(2, ShiftCycle.rotationOffset(date(8, 31), team, 10))
    }

    @Test
    fun `the rotation offset wraps rather than going negative before the anchor`() {
        assertEquals(7, ShiftCycle.rotationOffset(date(8, 23), team, 10))
        assertEquals(4, ShiftCycle.rotationOffset(date(8, 19), team, 10))
    }

    @Test
    fun `an empty table has no rotation offset`() {
        assertNull(ShiftCycle.rotationOffset(date(8, 24), team, 0))
        assertNull(ShiftCycle.rotationOffset(date(9, 7), ShiftTeam.SECOND, 0))
    }

    @Test
    fun `the second team counts ordinal and offset from its own anchor`() {
        assertEquals(0L, ShiftCycle.workOrdinal(date(8, 26), ShiftTeam.SECOND))
        assertEquals(3L, ShiftCycle.workOrdinal(date(9, 1), ShiftTeam.SECOND))
        assertEquals(6L, ShiftCycle.workOrdinal(date(9, 7), ShiftTeam.SECOND))
        assertNull(ShiftCycle.workOrdinal(date(9, 10), ShiftTeam.SECOND))
        // 9/7 → 9/8 → 9/9 每天左移 3 位。
        val base = ShiftCycle.rotationOffset(date(9, 7), ShiftTeam.SECOND, 10)!!
        assertEquals((base + 3) % 10, ShiftCycle.rotationOffset(date(9, 8), ShiftTeam.SECOND, 10))
        assertEquals((base + 6) % 10, ShiftCycle.rotationOffset(date(9, 9), ShiftTeam.SECOND, 10))
    }

    @Test
    fun `the handover day inherits from the immediately preceding third workday`() {
        assertEquals(date(8, 31), ShiftCycle.previousFullWorkday(date(9, 1), team))
        assertEquals(date(8, 25), ShiftCycle.previousFullWorkday(date(8, 26), team))
        assertEquals(date(9, 9), ShiftCycle.previousFullWorkday(date(9, 10), ShiftTeam.SECOND))
    }

    @Test
    fun `rest days look back past the handover day to the third workday`() {
        assertEquals(date(8, 25), ShiftCycle.previousFullWorkday(date(8, 27), team))
        assertEquals(date(8, 25), ShiftCycle.previousFullWorkday(date(8, 28), team))
    }

    @Test
    fun `the cycle start is always the first working day`() {
        listOf(date(8, 29), date(8, 30), date(8, 31), date(9, 1), date(9, 2), date(9, 3)).forEach {
            assertEquals("$it", date(8, 29), ShiftCycle.cycleStart(it, team))
        }
        assertEquals(date(9, 7), ShiftCycle.cycleStart(date(9, 12), ShiftTeam.SECOND))
    }

    @Test
    fun `day kind classification helpers agree with the cycle`() {
        assertTrue(ShiftDayKind.WORK_FIRST.isFullWorkday)
        assertTrue(ShiftDayKind.WORK_SECOND.isFullWorkday)
        assertTrue(ShiftDayKind.WORK_THIRD.isFullWorkday)
        assertFalse(ShiftDayKind.HANDOVER.isFullWorkday)
        assertFalse(ShiftDayKind.REST.isFullWorkday)
        assertTrue(ShiftDayKind.REST.isRest)
        assertFalse(ShiftDayKind.HANDOVER.isRest)
    }
}
