package com.bradj.airshift.widget

import com.bradj.airshift.model.RosterAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DutyWidgetModelTest {
    private val now = LocalDateTime.of(2026, 8, 29, 12, 0)

    private fun assignment(
        registration: String = "B-1234",
        inboundFlight: String? = null,
        origin: String? = null,
        originCode: String? = null,
        scheduledArrival: LocalDateTime? = null,
        actualArrival: LocalDateTime? = null,
        outboundFlight: String? = null,
        destination: String? = null,
        destinationCode: String? = null,
        scheduledDeparture: LocalDateTime? = null,
        actualDeparture: LocalDateTime? = null,
        inboundBoardingGate: String? = null,
        boardingGate: String? = null,
        inboundHasVip: Boolean = false,
    ) = RosterAssignment(
        aircraftRegistration = registration,
        aircraftType = null,
        inboundFlight = inboundFlight,
        origin = origin,
        scheduledArrival = scheduledArrival,
        outboundFlight = outboundFlight,
        destination = destination,
        scheduledDeparture = scheduledDeparture,
        assignees = "张三",
        actualArrival = actualArrival,
        actualDeparture = actualDeparture,
        inboundBoardingGate = inboundBoardingGate,
        boardingGate = boardingGate,
        originCode = originCode,
        destinationCode = destinationCode,
        inboundHasVip = inboundHasVip,
    )

    @Test
    fun `empty roster shows single import hint page`() {
        val pages = emptyList<RosterAssignment>().toWidgetPages(manuallyCompletedCount = 0, now = now)
        assertEquals(listOf(WidgetPage.Message("还没有排班", "打开航勤智排导入排班后，这里会显示当前执勤。")), pages)
    }

    @Test
    fun `all duties complete shows single finished page`() {
        val done = assignment(
            inboundFlight = "MU5101",
            scheduledArrival = now.minusHours(4),
            actualArrival = now.minusHours(4),
        )
        val pages = listOf(done).toWidgetPages(manuallyCompletedCount = 0, now = now)
        assertEquals(listOf(WidgetPage.Message("今日执勤全部完成", "今天的保障任务已全部执行完毕，辛苦了。")), pages)
    }

    @Test
    fun `duty before current shows completed, current shows countdown`() {
        val completed = assignment(
            registration = "B-0001",
            inboundFlight = "MU5101",
            scheduledArrival = now.minusHours(2),
            actualArrival = now.minusHours(2),
        )
        val current = assignment(
            registration = "B-0002",
            outboundFlight = "MU5102",
            scheduledDeparture = now.plusHours(3),
        )
        val pages = listOf(completed, current).toWidgetPages(manuallyCompletedCount = 0, now = now)

        assertEquals(2, pages.size)
        val past = pages[0] as WidgetPage.Duty
        assertEquals(WidgetDutyStatus.COMPLETED, past.status)
        assertNull(past.countdownTarget)
        assertEquals("执勤 1/2 · 进港保障 · B-0001", past.header)

        val currentPage = pages[1] as WidgetPage.Duty
        assertEquals(WidgetDutyStatus.COUNTDOWN, currentPage.status)
        // 出港到位时间 = 计划起飞 - 1 小时
        assertEquals(now.plusHours(2), currentPage.countdownTarget)
        assertEquals("到位时钟格式", "14:00", currentPage.gateArrivalClock)
        assertEquals("执勤 2/2 · 出港保障 · B-0002", currentPage.header)
    }

    @Test
    fun `current duty past gate arrival shows overdue`() {
        val overdue = assignment(
            outboundFlight = "MU5102",
            scheduledDeparture = now.plusMinutes(30),
        )
        val pages = listOf(overdue).toWidgetPages(manuallyCompletedCount = 0, now = now)
        val page = pages.single() as WidgetPage.Duty
        assertEquals(WidgetDutyStatus.OVERDUE, page.status)
        assertNull(page.countdownTarget)
        assertEquals("11:30", page.gateArrivalClock)
    }

    @Test
    fun `turnaround missing inbound times shows no time status`() {
        // 进港无任何时间 + 出港有计划起飞：到位时间无法计算但执勤未完成。
        val target = assignment(
            inboundFlight = "MU5101",
            outboundFlight = "MU5102",
            scheduledDeparture = now.plusHours(3),
        )
        val pages = listOf(target).toWidgetPages(manuallyCompletedCount = 0, now = now)
        val page = pages.single() as WidgetPage.Duty
        assertEquals(WidgetDutyStatus.NO_TIME, page.status)
        assertNull(page.countdownTarget)
        assertEquals("--:--", page.gateArrivalClock)
    }

    @Test
    fun `later duty already complete shows completed`() {
        val current = assignment(
            registration = "B-0001",
            outboundFlight = "MU5102",
            scheduledDeparture = now.plusHours(3),
        )
        val alreadyDone = assignment(
            registration = "B-0002",
            inboundFlight = "MU5103",
            scheduledArrival = now.minusHours(1),
            actualArrival = now.minusHours(1),
        )
        val pages = listOf(current, alreadyDone).toWidgetPages(manuallyCompletedCount = 0, now = now)
        val later = pages[1] as WidgetPage.Duty
        assertEquals(WidgetDutyStatus.COMPLETED, later.status)
    }

    @Test
    fun `turnaround legs expose origin gate and destination gate`() {
        val target = assignment(
            inboundFlight = "MU5101",
            origin = "北京首都",
            originCode = "PEK",
            scheduledArrival = now.plusHours(1),
            outboundFlight = "MU5102",
            destination = "广州白云",
            destinationCode = "CAN",
            scheduledDeparture = now.plusHours(3),
            inboundBoardingGate = "A08",
            boardingGate = "B12",
        )
        val page = (listOf(target).toWidgetPages(manuallyCompletedCount = 0, now = now).single() as WidgetPage.Duty)

        assertEquals("执勤 1/1 · 进港后接续出港 · B-1234", page.header)
        assertEquals(2, page.legs.size)
        val inbound = page.legs[0]
        assertEquals("进港", inbound.directionLabel)
        assertEquals("MU5101", inbound.flight)
        assertEquals("北京首都", inbound.place)
        assertEquals("A08", inbound.gate)
        val outbound = page.legs[1]
        assertEquals("出港", outbound.directionLabel)
        assertEquals("广州白云", outbound.place)
        assertEquals("B12", outbound.gate)
    }

    @Test
    fun `vip flag propagates to page`() {
        val vip = assignment(
            outboundFlight = "MU5102",
            scheduledDeparture = now.plusHours(3),
            inboundHasVip = true,
        )
        val page = (listOf(vip).toWidgetPages(manuallyCompletedCount = 0, now = now).single() as WidgetPage.Duty)
        assertTrue(page.hasVip)
    }

    @Test
    fun `missing place and gate fall back to placeholder`() {
        val target = assignment(outboundFlight = "MU5102", scheduledDeparture = now.plusHours(3))
        val page = (listOf(target).toWidgetPages(manuallyCompletedCount = 0, now = now).single() as WidgetPage.Duty)
        val outbound = page.legs.single()
        assertEquals("--", outbound.place)
        assertEquals("--", outbound.gate)
        assertTrue(page.legs.all { it.flight.isNotBlank() })
    }
}
