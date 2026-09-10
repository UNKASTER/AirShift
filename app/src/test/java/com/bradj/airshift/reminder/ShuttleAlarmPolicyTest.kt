package com.bradj.airshift.reminder

import com.bradj.airshift.model.shift.ShuttleAlarmDay
import com.bradj.airshift.model.shift.ShuttleAlarmPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 决策层：系统时钟只认"下一次出现"，所以明天的序列要等今天同一时刻过去（共享条目再加 45 分钟宽限）才写；
 * 今天只写还没到的时刻；写过的不再写；推荐变了把还没到点的旧时刻列为旧闹铃；每次只留一个唤醒。
 */
class ShuttleAlarmPolicyTest {
    private val today: LocalDate = LocalDate.of(2026, 9, 10)
    private val tomorrow: LocalDate = today.plusDays(1)

    private fun t(hour: Int, minute: Int) = LocalTime.of(hour, minute)

    private fun day(date: LocalDate, hour: Int, minute: Int) =
        ShuttleAlarmDay(date, t(hour, minute), ShuttleAlarmPlan.alarmTimes(t(hour, minute)))

    /** 窗口：今天 05:55、明天 05:25、后天休息、大后天 08:00，其余休息。 */
    private fun window(
        todayBus: ShuttleAlarmDay? = day(today, 5, 55),
        tomorrowBus: ShuttleAlarmDay? = day(tomorrow, 5, 25),
    ): List<ShuttleAlarmDay?> =
        listOf(todayBus, tomorrowBus, null, day(today.plusDays(3), 8, 0), null, null, null, null)

    private fun record(
        date: LocalDate,
        departure: LocalTime,
        outcome: ShuttleAlarmOutcome = ShuttleAlarmOutcome.CONFIRMED,
        times: List<LocalTime> = ShuttleAlarmPlan.alarmTimes(departure),
        attempts: Int = 1,
    ) = ShuttleAlarmRecord(
        date, departure, times, date.minusDays(1).atTime(21, 0), outcome, background = true, attempts = attempts,
    )

    private fun request(now: LocalDateTime, enabled: Boolean = true, visible: Boolean = true, force: Boolean = false) =
        ShuttleAlarmRequest(now, enabled, visible, force)

    private fun decide(
        request: ShuttleAlarmRequest,
        state: ShuttleAlarmState = ShuttleAlarmState.EMPTY,
        window: List<ShuttleAlarmDay?> = window(),
    ) = ShuttleAlarmPolicy.decide(request, window, state)

    @Test
    fun `at three in the morning the whole of today is written and tomorrow waits for its ready time`() {
        val decision = decide(request(today.atTime(3, 0)))

        assertEquals(listOf(day(today, 5, 55)), decision.fire)
        // 明天 05:00–05:20 与今天 05:25–05:50 不共享条目：今天 05:20 过 1 分钟即可写。
        assertEquals(today.atTime(5, 21), decision.nextWakeAt)
        assertTrue(decision.newStale.isEmpty())
    }

    @Test
    fun `in the middle of the series only the remaining alarms are written`() {
        val decision = decide(request(today.atTime(5, 30)))

        assertEquals(listOf(t(5, 35), t(5, 40), t(5, 45), t(5, 50)), decision.fire.single { it.date == today }.times)
    }

    @Test
    fun `an alarm within the safety lead is skipped`() {
        val decision = decide(request(today.atTime(5, 34, 30)))

        assertEquals(listOf(t(5, 40), t(5, 45), t(5, 50)), decision.fire.single { it.date == today }.times)
    }

    @Test
    fun `once today is recorded and ringing it is frozen`() {
        val state = ShuttleAlarmState(records = listOf(record(today, t(5, 55))))
        val changed = window(todayBus = day(today, 8, 0))

        val decision = decide(request(today.atTime(5, 30)), state, changed)

        assertTrue(decision.fire.none { it.date == today })
        assertTrue(decision.newStale.isEmpty())
    }

    @Test
    fun `before today first rings a changed bus produces stale alarms and a new series`() {
        val state = ShuttleAlarmState(records = listOf(record(today, t(5, 55))))
        val changed = window(todayBus = day(today, 8, 0))

        val decision = decide(request(today.atTime(0, 30)), state, changed)

        assertEquals(ShuttleAlarmPlan.alarmTimes(t(5, 55)).map { ShuttleAlarm(today, it) }, decision.newStale)
        assertEquals(listOf(day(today, 8, 0)), decision.fire)
        assertEquals(decision.newStale, decision.state.stale)
    }

    @Test
    fun `by mid morning tomorrow is ready and today is already past`() {
        val decision = decide(request(today.atTime(10, 0)))

        assertEquals(listOf(day(tomorrow, 5, 25)), decision.fire)
        // 大后天 08:00 的序列 07:00–07:50：后天同一时刻过 1 分钟。
        assertEquals(today.plusDays(2).atTime(7, 51), decision.nextWakeAt)
    }

    @Test
    fun `a shared time waits forty five minutes after it rang today`() {
        val sameBus = window(tomorrowBus = day(tomorrow, 5, 55))

        val early = decide(request(today.atTime(6, 0)), window = sameBus)
        assertTrue(early.fire.isEmpty())
        assertEquals(today.atTime(6, 35), early.nextWakeAt)

        val ready = decide(request(today.atTime(6, 35)), window = sameBus)
        assertEquals(listOf(day(tomorrow, 5, 55)), ready.fire)
    }

    @Test
    fun `yesterday record counts as rang the day before even when the plan has moved on`() {
        val state = ShuttleAlarmState(records = listOf(record(today.minusDays(1), t(5, 25))))
        val sameAsYesterday = window(todayBus = day(today, 5, 25), tomorrowBus = null)

        val decision = decide(request(today.atTime(3, 0)), state, sameAsYesterday)

        // 今天 05:00–05:20 昨天响过：readyAt = 昨天 05:20 + 45 分，早已过去，照写。
        assertEquals(listOf(day(today, 5, 25)), decision.fire)
    }

    @Test
    fun `a late bus tomorrow is written only after tonight same time`() {
        val lateTomorrow = window(tomorrowBus = day(tomorrow, 22, 0))

        val evening = decide(request(today.atTime(21, 0)), window = lateTomorrow)
        assertTrue(evening.fire.isEmpty())
        assertEquals(today.atTime(21, 51), evening.nextWakeAt)

        val later = decide(request(today.atTime(21, 52)), window = lateTomorrow)
        assertEquals(listOf(day(tomorrow, 22, 0)), later.fire)
    }

    @Test
    fun `confirmed records are never rewritten and produce no wake`() {
        val state = ShuttleAlarmState(records = listOf(record(today, t(5, 55)), record(tomorrow, t(5, 25))))

        val decision = decide(request(today.atTime(10, 0)), state)

        assertTrue(decision.fire.isEmpty())
        assertEquals(today.plusDays(2).atTime(7, 51), decision.nextWakeAt)
    }

    @Test
    fun `a blocked record is retried in the background and in the foreground until the attempt limit`() {
        val blocked = ShuttleAlarmState(records = listOf(record(tomorrow, t(5, 25), ShuttleAlarmOutcome.BLOCKED)))
        val at = today.atTime(10, 0)

        assertEquals(listOf(day(tomorrow, 5, 25)), decide(request(at, visible = false), blocked).fire)
        assertEquals(listOf(day(tomorrow, 5, 25)), decide(request(at, visible = true), blocked).fire)

        val exhausted = ShuttleAlarmState(
            records = listOf(
                record(tomorrow, t(5, 25), ShuttleAlarmOutcome.BLOCKED, attempts = ShuttleAlarmPolicy.MAX_ATTEMPTS),
            ),
        )
        assertTrue(decide(request(at), exhausted).fire.isEmpty())
        assertNull(decide(request(at), exhausted, window = window().take(2)).nextWakeAt)
    }

    @Test
    fun `an unconfirmed record is retried only when visible`() {
        val state = ShuttleAlarmState(records = listOf(record(tomorrow, t(5, 25), ShuttleAlarmOutcome.UNCONFIRMED)))
        val at = today.atTime(10, 0)

        assertTrue(decide(request(at, visible = false), state).fire.isEmpty())
        assertEquals(listOf(day(tomorrow, 5, 25)), decide(request(at, visible = true), state).fire)
    }

    @Test
    fun `force rewrites everything that can be written`() {
        val state = ShuttleAlarmState(records = listOf(record(tomorrow, t(5, 25))))

        val decision = decide(request(today.atTime(10, 0), force = true), state)

        assertEquals(listOf(day(tomorrow, 5, 25)), decision.fire)
    }

    @Test
    fun `a bus that disappears or falls below five turns the recorded alarms stale`() {
        val state = ShuttleAlarmState(records = listOf(record(tomorrow, t(5, 25))))
        val at = today.atTime(20, 0)

        val rest = decide(request(at), state, window(tomorrowBus = null))
        assertEquals(ShuttleAlarmPlan.alarmTimes(t(5, 25)).map { ShuttleAlarm(tomorrow, it) }, rest.newStale)

        val tooEarly = decide(request(at), state, window(tomorrowBus = day(tomorrow, 4, 50)))
        assertEquals(rest.newStale, tooEarly.newStale)
        assertTrue(tooEarly.fire.isEmpty())
    }

    @Test
    fun `only stale alarms still ahead are kept and known ones are not reported twice`() {
        val known = ShuttleAlarm(tomorrow, t(5, 0))
        val state = ShuttleAlarmState(
            records = listOf(record(tomorrow, t(5, 25))),
            stale = listOf(known, ShuttleAlarm(today, t(4, 0))),
        )

        val decision = decide(request(today.atTime(20, 0)), state, window(tomorrowBus = day(tomorrow, 5, 55)))

        val expected = listOf(t(5, 5), t(5, 10), t(5, 15), t(5, 20)).map { ShuttleAlarm(tomorrow, it) }
        assertEquals(expected, decision.newStale)
        assertEquals((listOf(known) + expected).sorted(), decision.state.stale)
    }

    @Test
    fun `disabling turns every future recorded alarm stale and cancels the wake`() {
        val state = ShuttleAlarmState(
            records = listOf(record(today, t(5, 55)), record(tomorrow, t(5, 25))),
            pendingVerifyAt = today.atTime(12, 0),
            verifyHops = 2,
        )

        val decision = decide(request(today.atTime(5, 40), enabled = false), state)

        val expected = listOf(t(5, 45), t(5, 50)).map { ShuttleAlarm(today, it) } +
            ShuttleAlarmPlan.alarmTimes(t(5, 25)).map { ShuttleAlarm(tomorrow, it) }
        assertEquals(expected, decision.newStale)
        assertTrue(decision.fire.isEmpty())
        assertNull(decision.nextWakeAt)
        assertNull(decision.state.pendingVerifyAt)
    }

    @Test
    fun `records older than yesterday are pruned and the pending verify becomes the wake`() {
        val state = ShuttleAlarmState(
            records = listOf(record(today.minusDays(2), t(5, 55)), record(today, t(5, 55)), record(tomorrow, t(5, 25))),
            pendingVerifyAt = today.atTime(10, 30),
        )

        val decision = decide(request(today.atTime(10, 0)), state)

        assertEquals(listOf(today, tomorrow), decision.state.records.map { it.date })
        assertEquals(today.atTime(10, 30), decision.nextWakeAt)
    }

    @Test
    fun `after midnight yesterday tomorrow is simply today`() {
        val state = ShuttleAlarmState(records = listOf(record(today, t(5, 55))))

        val decision = decide(request(today.atTime(0, 30)), state)

        assertTrue(decision.fire.none { it.date == today })
        assertEquals(today.atTime(5, 21), decision.nextWakeAt)
    }

    @Test
    fun `an unresolved group yields nothing to write and no wake`() {
        val decision = decide(request(today.atTime(10, 0)), window = List(8) { null })

        assertTrue(decision.fire.isEmpty())
        assertNull(decision.nextWakeAt)
    }

    @Test
    fun `judging the next system alarm`() {
        val first = tomorrow.atTime(5, 0)
        val records = listOf(record(today, t(12, 0)))
        val judge = { next: LocalDateTime?, hops: Int, reports: Boolean ->
            ShuttleAlarmVerification.judge(next, first, records, hops, reports)
        }

        assertEquals(ShuttleAlarmVerdict.Confirmed, judge(first.plusSeconds(30), 0, true))
        assertEquals(ShuttleAlarmVerdict.Blocked, judge(null, 0, true))
        assertEquals(ShuttleAlarmVerdict.Unconfirmed, judge(null, 0, false))
        assertEquals(ShuttleAlarmVerdict.Blocked, judge(first.plusHours(2), 0, true))
        // 前面挡着别的闹钟：两分钟后再看。
        assertEquals(ShuttleAlarmVerdict.Inconclusive(today.atTime(22, 2)), judge(today.atTime(22, 0), 0, true))
        // 挡着的是本 App 今天 11:00–11:50 的序列：直接跳到最后一响之后。
        assertEquals(ShuttleAlarmVerdict.Inconclusive(today.atTime(11, 52)), judge(today.atTime(11, 0), 0, true))
        val hops = ShuttleAlarmVerification.MAX_VERIFY_HOPS
        assertEquals(ShuttleAlarmVerdict.Unconfirmed, judge(today.atTime(22, 0), hops, true))
        // 再看一次也来不及了。
        assertEquals(ShuttleAlarmVerdict.Unconfirmed, judge(first.minusMinutes(2), 0, true))
    }

    @Test
    fun `merging keeps recorded times still in the series and counts attempts per series`() {
        val blocked = ShuttleAlarmOutcome.BLOCKED
        val confirmed = ShuttleAlarmOutcome.CONFIRMED
        val old = record(tomorrow, t(5, 25), blocked, times = listOf(t(5, 0), t(5, 5)), attempts = 2)
        val at = today.atTime(20, 0)

        val same = ShuttleAlarmVerification.merged(
            old, day(tomorrow, 5, 25), listOf(t(5, 10)), ShuttleAlarmWrite(confirmed, background = false, at),
        )
        assertEquals(listOf(t(5, 0), t(5, 5), t(5, 10)), same.times)
        assertEquals(3, same.attempts)
        assertEquals(confirmed, same.outcome)

        val changed = ShuttleAlarmVerification.merged(
            old, day(tomorrow, 5, 55), listOf(t(5, 25)), ShuttleAlarmWrite(confirmed, background = true, at),
        )
        assertEquals(listOf(t(5, 25)), changed.times)
        assertEquals(1, changed.attempts)
        assertEquals(t(5, 55), changed.departure)
    }
}
