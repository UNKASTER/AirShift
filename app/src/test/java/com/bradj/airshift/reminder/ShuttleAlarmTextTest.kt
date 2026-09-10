package com.bradj.airshift.reminder

import com.bradj.airshift.model.shift.ShuttleAlarmDay
import com.bradj.airshift.model.shift.ShuttleAlarmPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ShuttleAlarmTextTest {
    private val today = LocalDate.of(2026, 9, 10)
    private val now = today.atTime(10, 0)

    private fun day(date: LocalDate, hour: Int, minute: Int) =
        ShuttleAlarmDay(date, LocalTime.of(hour, minute), ShuttleAlarmPlan.alarmTimes(LocalTime.of(hour, minute)))

    private fun record(date: LocalDate, outcome: ShuttleAlarmOutcome) = ShuttleAlarmRecord(
        date, LocalTime.of(5, 55), ShuttleAlarmPlan.alarmTimes(LocalTime.of(5, 55)), now, outcome, true, 1,
    )

    @Test
    fun `row line carries the status only for today and tomorrow`() {
        val tomorrow = day(today.plusDays(1), 5, 55)
        assertEquals("闹铃 05:25 起 · 6 响 · 待设", ShuttleAlarmText.rowLine(tomorrow, null, now))
        assertEquals(
            "闹铃 05:25 起 · 6 响 · 已设",
            ShuttleAlarmText.rowLine(tomorrow, record(tomorrow.date, ShuttleAlarmOutcome.CONFIRMED), now),
        )
        assertEquals(
            "闹铃 05:25 起 · 6 响 · 后台被拦",
            ShuttleAlarmText.rowLine(tomorrow, record(tomorrow.date, ShuttleAlarmOutcome.BLOCKED), now),
        )
        assertEquals(
            "闹铃 05:25 起 · 6 响 · 未核对",
            ShuttleAlarmText.rowLine(tomorrow, record(tomorrow.date, ShuttleAlarmOutcome.UNCONFIRMED), now),
        )
        assertEquals("闹铃 07:00 起 · 6 响", ShuttleAlarmText.rowLine(day(today.plusDays(4), 8, 0), null, now))
        assertEquals("无闹铃 · 班车早于 05:00", ShuttleAlarmText.rowLine(day(today.plusDays(1), 4, 50), null, now))
        assertNull(ShuttleAlarmText.rowLine(null, null, now))
    }

    @Test
    fun `status line picks the next day that still has alarms ahead`() {
        val state = ShuttleAlarmState(records = listOf(record(today.plusDays(1), ShuttleAlarmOutcome.CONFIRMED)))
        val window = listOf(day(today, 5, 55), day(today.plusDays(1), 5, 55), null)

        assertEquals("明天 05:25–05:50 · 6 响 · 已设", ShuttleAlarmText.statusLine(window, state, now))
        assertEquals("今天 05:25–05:50 · 6 响 · 待设", ShuttleAlarmText.statusLine(window, state, today.atTime(3, 0)))
        assertEquals(
            "明天 班车 04:50 · 无闹铃（早于 05:00）",
            ShuttleAlarmText.statusLine(listOf(null, day(today.plusDays(1), 4, 50)), state, now),
        )
        assertEquals("—", ShuttleAlarmText.statusLine(listOf(null, null), state, now))
        assertEquals("近几天没有到岗日", ShuttleAlarmText.statusLine(listOf(day(today, 5, 55), null), state, now))
    }

    @Test
    fun `stale lines group by day and drop what has already rung`() {
        val stale = listOf(
            ShuttleAlarm(today.plusDays(1), LocalTime.of(7, 10)),
            ShuttleAlarm(today.plusDays(1), LocalTime.of(7, 0)),
            ShuttleAlarm(today, LocalTime.of(12, 0)),
            ShuttleAlarm(today, LocalTime.of(5, 0)),
        )

        assertEquals(listOf("今天：12:00", "明天：07:00、07:10"), ShuttleAlarmText.staleLines(stale, now))
    }

    @Test
    fun `attempt and summary lines`() {
        val attempt =
            ShuttleAlarmAttempt(ShuttleAlarmReason.WAKE, today.atTime(6, 35), ShuttleAlarmOutcome.BLOCKED, true, "")
        assertEquals("被拦截 · 9/10 06:35", ShuttleAlarmText.attemptLine(attempt))
        assertEquals("—", ShuttleAlarmText.attemptLine(null))
        assertEquals("明天 05:25–05:50 共 6 响", ShuttleAlarmText.summary(day(today.plusDays(1), 5, 55), today))
        assertEquals("明天 无闹铃", ShuttleAlarmText.summary(day(today.plusDays(1), 4, 50), today))
    }
}
