package com.bradj.airshift.api

import com.bradj.airshift.model.RosterAssignment
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/** 后台周期任务在排班日的跟踪时段开始前不联网：首轮延迟直接睡到首个任务前 3 小时。 */
class FlightRefreshInitialDelayTest {
    private val now = LocalDateTime.of(2026, 9, 6, 20, 0)

    @Test
    fun aRosterImportedTheEveningBeforeSleepsUntilThreeHoursBeforeItsFirstTask() {
        val tomorrow = listOf(assignment(now.plusDays(1).withHour(7).withMinute(10)))

        // 9/7 04:10 − 9/6 20:00 = 8 h 10 min，再加 1 分钟保证醒来时已进入跟踪时段。
        assertEquals(8 * 60 + 11L, initialDelayMinutes(tomorrow, now))
    }

    @Test
    fun aRosterAlreadyInItsTrackingWindowKeepsTheMinimumPeriod() {
        val startingInFiveMinutes = now.plusHours(3).plusMinutes(5)
        assertEquals(REFRESH_PERIOD_MINUTES, initialDelayMinutes(listOf(assignment(now.plusHours(1))), now))
        assertEquals(REFRESH_PERIOD_MINUTES, initialDelayMinutes(listOf(assignment(now.minusHours(2))), now))
        assertEquals(REFRESH_PERIOD_MINUTES, initialDelayMinutes(listOf(assignment(startingInFiveMinutes)), now))
    }

    @Test
    fun aRosterWithoutTimesKeepsTheMinimumPeriod() {
        assertEquals(REFRESH_PERIOD_MINUTES, initialDelayMinutes(listOf(assignment(null)), now))
        assertEquals(REFRESH_PERIOD_MINUTES, initialDelayMinutes(emptyList(), now))
    }

    private fun assignment(scheduledArrival: LocalDateTime?) = RosterAssignment(
        aircraftRegistration = "B0001",
        aircraftType = null,
        inboundFlight = "MU1001",
        origin = null,
        scheduledArrival = scheduledArrival,
        outboundFlight = null,
        destination = null,
        scheduledDeparture = null,
        assignees = "测试甲",
    )
}
