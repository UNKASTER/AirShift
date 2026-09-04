package com.bradj.airshift.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DutyProgressDayTest {
    private val date = LocalDate.of(2026, 9, 4)

    @Test
    fun `an evening completion belongs to the same duty day`() {
        assertEquals(date, DutyProgressDay.of(date.atTime(23, 59)))
    }

    @Test
    fun `shortly after midnight still belongs to the previous duty day`() {
        assertEquals(date, DutyProgressDay.of(date.plusDays(1).atTime(0, 5)))
    }

    @Test
    fun `the latest night shift end still belongs to the previous duty day`() {
        // 晚三下班约次日 01:35（ShiftBusPlan.OFF_DUTY），此时昨晚的人工完成不能被清零。
        assertEquals(date, DutyProgressDay.of(date.plusDays(1).atTime(1, 35)))
    }

    @Test
    fun `the duty day rolls over at six in the morning`() {
        assertEquals(date, DutyProgressDay.of(date.plusDays(1).atTime(5, 59)))
        assertEquals(date.plusDays(1), DutyProgressDay.of(date.plusDays(1).atTime(6, 0)))
    }

    @Test
    fun `an early morning start already belongs to its own duty day`() {
        // 早班最早班车 04:50、首个任务 07:10，06:30 已经属于当天。
        assertEquals(date, DutyProgressDay.of(date.atTime(6, 30)))
    }
}
