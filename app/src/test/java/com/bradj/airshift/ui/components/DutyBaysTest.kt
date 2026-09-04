package com.bradj.airshift.ui.components

import com.bradj.airshift.model.RosterAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DutyBaysTest {
    private val date: LocalDate = LocalDate.of(2026, 9, 4)
    private val now: LocalDateTime = date.atTime(16, 8)

    private fun departure(flight: String, hour: Int, actual: LocalDateTime? = null) = RosterAssignment(
        aircraftRegistration = "B-$flight",
        aircraftType = "320",
        inboundFlight = null,
        origin = null,
        scheduledArrival = null,
        outboundFlight = flight,
        destination = "合肥新桥",
        scheduledDeparture = date.atTime(hour, 0),
        assignees = "测试甲",
        actualDeparture = actual,
    )

    @Test
    fun `manual prefix, auto-completed and untrackable tasks all land in the completed bay`() {
        val roster = listOf(
            departure("MU9977", 10),                              // 人工前缀内
            departure("MU9979", 12, actual = date.atTime(12, 5)), // 有实际时间 → 自动完成
            departure("FM9212", 18),                              // 当前
            departure("MU9771", 19),                              // 接下来
            departure("MU9975", 0).copy(scheduledDeparture = null), // 无任何时间 → 视为完成
            departure("FM9107", 23),                              // 接下来
        )

        val bays = roster.splitIntoBays(manuallyCompletedCount = 1, now = now)

        assertEquals(2, bays.current)
        assertEquals(listOf(3, 5), bays.upcoming)
        assertEquals(listOf(0, 1, 4), bays.completed)
    }

    @Test
    fun `when everything is done there is no current strip and all indices are completed`() {
        val roster = listOf(departure("MU9977", 10), departure("MU9979", 11))

        val bays = roster.splitIntoBays(manuallyCompletedCount = 2, now = now)

        assertNull(bays.current)
        assertEquals(emptyList<Int>(), bays.upcoming)
        assertEquals(listOf(0, 1), bays.completed)
    }

    @Test
    fun `an empty roster yields empty bays`() {
        val bays = emptyList<RosterAssignment>().splitIntoBays(manuallyCompletedCount = 0, now = now)

        assertNull(bays.current)
        assertEquals(emptyList<Int>(), bays.upcoming)
        assertEquals(emptyList<Int>(), bays.completed)
    }
}
