package com.bradj.airshift.model.shift

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ShiftTeamTest {
    private fun date(month: Int, day: Int) = LocalDate.of(2026, month, day)

    @Test
    fun `the two teams are half a cycle apart`() {
        assertEquals(3L, ChronoUnit.DAYS.between(ShiftTeam.FIRST.anchor, ShiftTeam.SECOND.anchor))
    }

    @Test
    fun `exactly one team is on a full workday on any date`() {
        var cursor = date(8, 1)
        repeat(90) {
            val working = ShiftTeam.entries.filter { ShiftCycle.dayKind(cursor, it).isFullWorkday }
            assertEquals("$cursor", 1, working.size)
            assertEquals("$cursor", working.single(), ShiftTeam.onFullWorkday(cursor))
            cursor = cursor.plusDays(1)
        }
    }

    @Test
    fun `the second team takes over on the handover day of the first`() {
        // 9.7 二组表：首班 10:40 接班、带三行班次行；同一天一组只上上午。
        assertEquals(ShiftTeam.SECOND, ShiftTeam.onFullWorkday(date(9, 7)))
        assertEquals(ShiftDayKind.WORK_FIRST, ShiftCycle.dayKind(date(9, 7), ShiftTeam.SECOND))
        assertEquals(ShiftDayKind.HANDOVER, ShiftCycle.dayKind(date(9, 7), ShiftTeam.FIRST))
        // 9.1 一组表只有上午航班，二组当天接班。
        assertEquals(ShiftTeam.SECOND, ShiftTeam.onFullWorkday(date(9, 1)))
        assertEquals(ShiftDayKind.HANDOVER, ShiftCycle.dayKind(date(9, 1), ShiftTeam.FIRST))
        assertEquals(ShiftTeam.FIRST, ShiftTeam.onFullWorkday(date(8, 24)))
    }

    @Test
    fun `the second team runs the same six day pattern from its own anchor`() {
        val expected = listOf(
            date(8, 26) to ShiftDayKind.WORK_FIRST,
            date(8, 27) to ShiftDayKind.WORK_SECOND,
            date(8, 28) to ShiftDayKind.WORK_THIRD,
            date(8, 29) to ShiftDayKind.HANDOVER,
            date(8, 30) to ShiftDayKind.REST,
            date(8, 31) to ShiftDayKind.REST,
            date(9, 1) to ShiftDayKind.WORK_FIRST,
            date(9, 7) to ShiftDayKind.WORK_FIRST,
            date(9, 10) to ShiftDayKind.HANDOVER,
        )
        expected.forEach { (day, kind) -> assertEquals("$day", kind, ShiftCycle.dayKind(day, ShiftTeam.SECOND)) }
    }

    @Test
    fun `while one team rests or hands over the other works`() {
        var cursor = date(8, 20)
        repeat(30) {
            val first = ShiftCycle.dayKind(cursor, ShiftTeam.FIRST)
            val second = ShiftCycle.dayKind(cursor, ShiftTeam.SECOND)
            assertTrue("$cursor", first.isFullWorkday != second.isFullWorkday)
            cursor = cursor.plusDays(1)
        }
    }
}
