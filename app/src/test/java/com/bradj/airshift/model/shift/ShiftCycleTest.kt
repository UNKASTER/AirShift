package com.bradj.airshift.model.shift

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShiftCycleTest {
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
        expected.forEach { (day, kind) -> assertEquals("$day", kind, ShiftCycle.dayKind(day)) }
    }

    @Test
    fun `dates before the anchor stay on the same cycle`() {
        assertEquals(ShiftDayKind.REST, ShiftCycle.dayKind(date(8, 22)))
        assertEquals(ShiftDayKind.REST, ShiftCycle.dayKind(date(8, 21)))
        assertEquals(ShiftDayKind.HANDOVER, ShiftCycle.dayKind(date(8, 20)))
        assertEquals(ShiftDayKind.WORK_FIRST, ShiftCycle.dayKind(date(8, 17)))
    }

    @Test
    fun `the work ordinal counts full workdays only`() {
        assertEquals(0L, ShiftCycle.workOrdinal(date(8, 23)))
        assertEquals(1L, ShiftCycle.workOrdinal(date(8, 24)))
        assertEquals(2L, ShiftCycle.workOrdinal(date(8, 25)))
        assertEquals(3L, ShiftCycle.workOrdinal(date(8, 29)))
        assertEquals(4L, ShiftCycle.workOrdinal(date(8, 30)))
        assertEquals(5L, ShiftCycle.workOrdinal(date(8, 31)))
        assertEquals(6L, ShiftCycle.workOrdinal(date(9, 4)))
    }

    @Test
    fun `the handover day and rest days have no work ordinal`() {
        assertNull(ShiftCycle.workOrdinal(date(8, 26)))
        assertNull(ShiftCycle.workOrdinal(date(8, 27)))
        assertNull(ShiftCycle.workOrdinal(date(8, 28)))
        assertNull(ShiftCycle.workOrdinal(date(9, 1)))
    }

    @Test
    fun `the work ordinal goes negative before the anchor without wrapping`() {
        assertEquals(-1L, ShiftCycle.workOrdinal(date(8, 19)))
        assertEquals(-3L, ShiftCycle.workOrdinal(date(8, 17)))
    }

    @Test
    fun `the rotation offset is calibrated so the first observed sheet is zero`() {
        assertEquals(0, ShiftCycle.rotationOffset(date(8, 24)))
        assertEquals(3, ShiftCycle.rotationOffset(date(8, 25)))
        assertEquals(6, ShiftCycle.rotationOffset(date(8, 29)))
        assertEquals(9, ShiftCycle.rotationOffset(date(8, 30)))
        assertEquals(2, ShiftCycle.rotationOffset(date(8, 31)))
    }

    @Test
    fun `the rotation offset wraps rather than going negative before the anchor`() {
        assertEquals(7, ShiftCycle.rotationOffset(date(8, 23)))
        assertEquals(4, ShiftCycle.rotationOffset(date(8, 19)))
    }

    @Test
    fun `the handover day inherits from the immediately preceding third workday`() {
        assertEquals(date(8, 31), ShiftCycle.previousFullWorkday(date(9, 1)))
        assertEquals(date(8, 25), ShiftCycle.previousFullWorkday(date(8, 26)))
    }

    @Test
    fun `rest days look back past the handover day to the third workday`() {
        assertEquals(date(8, 25), ShiftCycle.previousFullWorkday(date(8, 27)))
        assertEquals(date(8, 25), ShiftCycle.previousFullWorkday(date(8, 28)))
    }

    @Test
    fun `the cycle start is always the first working day`() {
        listOf(date(8, 29), date(8, 30), date(8, 31), date(9, 1), date(9, 2), date(9, 3)).forEach {
            assertEquals("$it", date(8, 29), ShiftCycle.cycleStart(it))
        }
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
