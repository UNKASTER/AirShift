package com.bradj.airshift.model.shift

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** 班车闹铃序列：7 点前 30 分钟 / 5 分钟一档，7 点起 60 分钟 / 10 分钟一档，最早 05:00，发车前一格即止。 */
class ShuttleAlarmPlanTest {
    private fun t(hour: Int, minute: Int) = LocalTime.of(hour, minute)

    private fun times(hour: Int, minute: Int) = ShuttleAlarmPlan.alarmTimes(t(hour, minute))

    @Test
    fun `an early bus rings every five minutes from half an hour before departure`() {
        assertEquals(listOf(t(5, 25), t(5, 30), t(5, 35), t(5, 40), t(5, 45), t(5, 50)), times(5, 55))
    }

    @Test
    fun `a late bus rings every ten minutes from an hour before departure`() {
        assertEquals(listOf(t(7, 0), t(7, 10), t(7, 20), t(7, 30), t(7, 40), t(7, 50)), times(8, 0))
        assertEquals(listOf(t(8, 0), t(8, 10), t(8, 20), t(8, 30), t(8, 40), t(8, 50)), times(9, 0))
        assertEquals(listOf(t(11, 0), t(11, 10), t(11, 20), t(11, 30), t(11, 40), t(11, 50)), times(12, 0))
        assertEquals(listOf(t(21, 0), t(21, 10), t(21, 20), t(21, 30), t(21, 40), t(21, 50)), times(22, 0))
    }

    @Test
    fun `alarms before five o clock are dropped`() {
        assertEquals(listOf(t(5, 0), t(5, 5), t(5, 10), t(5, 15), t(5, 20)), times(5, 25))
    }

    @Test
    fun `the 04 50 bus gets no alarms at all`() {
        assertTrue(times(4, 50).isEmpty())
    }

    @Test
    fun `the seven o clock boundary is judged by the departure`() {
        assertEquals(listOf(t(6, 29), t(6, 34), t(6, 39), t(6, 44), t(6, 49), t(6, 54)), times(6, 59))
        assertEquals(listOf(t(6, 0), t(6, 10), t(6, 20), t(6, 30), t(6, 40), t(6, 50)), times(7, 0))
    }

    @Test
    fun `every timetable departure yields a sorted evenly spaced series below the departure`() {
        val departures = ShiftBusPlan.REGULAR_DEPARTURES + ShiftBusPlan.EXTRA_HANDOVER_DEPARTURE + t(12, 0)
        departures.forEach { departure ->
            val series = ShuttleAlarmPlan.alarmTimes(departure)
            val step = if (departure < ShuttleAlarmPlan.EARLY_BOUNDARY) 5L else 10L
            assertEquals(series.sorted(), series)
            assertTrue(series.size <= 6)
            series.forEach { assertTrue("$departure -> $it", it >= t(5, 0) && it < departure) }
            series.zipWithNext().forEach { (a, b) -> assertEquals(step, java.time.Duration.between(a, b).toMinutes()) }
            if (series.isNotEmpty()) assertEquals(departure.minusMinutes(step), series.last())
        }
    }

    @Test
    fun `only attended rows with a bus have a plan`() {
        // 组 1：8-30 是早二（班车 05:55），9-1 交接班日不到岗，9-2 休息。
        val rows = ShiftCalendarRows.build(
            schedule = ShiftSchedule(),
            groupId = 1,
            from = LocalDate.of(2026, 8, 30),
            toInclusive = LocalDate.of(2026, 9, 2),
            today = LocalDate.of(2026, 8, 30),
        ).associateBy { it.day.date }

        val working = ShuttleAlarmPlan.fromRow(rows.getValue(LocalDate.of(2026, 8, 30)))
        assertNotNull(working)
        assertEquals(LocalDate.of(2026, 8, 30), working!!.date)
        assertEquals(t(5, 55), working.departure)
        assertEquals(times(5, 55), working.times)
        assertEquals(t(5, 25), working.first)
        assertEquals(t(5, 50), working.last)
        assertNull(ShuttleAlarmPlan.fromRow(rows.getValue(LocalDate.of(2026, 9, 1))))
        assertNull(ShuttleAlarmPlan.fromRow(rows.getValue(LocalDate.of(2026, 9, 2))))
    }
}
