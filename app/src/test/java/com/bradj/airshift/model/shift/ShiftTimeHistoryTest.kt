package com.bradj.airshift.model.shift

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShiftTimeHistoryTest {
    private val early1 = ShiftSlot(ShiftTier.EARLY, 1)
    private val early2 = ShiftSlot(ShiftTier.EARLY, 2)
    private val mid3 = ShiftSlot(ShiftTier.MID, 3)
    private val handover = ShiftDayKind.HANDOVER

    private fun day(offset: Int): LocalDate = LocalDate.of(2026, 8, 24).plusDays(offset.toLong())

    /** 一组第 2 天早一的一条实测；其余字段用 `copy` 改。 */
    private fun observation(
        first: Int,
        date: LocalDate = day(0),
        inbound: Boolean = false,
        last: Int = first + ShiftClock.of(10, 0),
    ) = ShiftTimeObservation(
        date = date,
        team = ShiftTeam.FIRST,
        kind = ShiftDayKind.WORK_SECOND,
        slot = early1,
        firstTaskMinutes = first,
        inbound = inbound,
        lastTaskMinutes = last,
    )

    private fun history(vararg observations: ShiftTimeObservation) = ShiftTimeHistory().record(observations.toList())

    // ---------- 门槛与中位数 ----------

    @Test
    fun `fewer than three samples yield no estimate`() {
        val learned = history(
            observation(ShiftClock.of(7, 20), date = day(0)),
            observation(ShiftClock.of(7, 30), date = day(1)),
        ).learnedTimes(ShiftTeam.FIRST)
        assertNull(learned[ShiftDayKind.WORK_SECOND, early1])
        assertEquals(2, learned.dateCount)
        assertFalse(learned.isEmpty)
    }

    @Test
    fun `three samples yield the lower median report time and the majority direction`() {
        val learned = history(
            observation(ShiftClock.of(7, 20), date = day(0)),
            observation(ShiftClock.of(7, 30), date = day(1)),
            observation(ShiftClock.of(12, 50), date = day(6), inbound = true),
        ).learnedTimes(ShiftTeam.FIRST)[ShiftDayKind.WORK_SECOND, early1]!!
        // 到位时间 06:10 / 06:20 / 12:35 → 中位 06:20；两次出港占多数 → 首个任务 06:20 + 70 = 07:30 出港。
        assertEquals(ShiftClock.of(6, 20), learned.reportByMinutes)
        assertEquals(ExpectedFirstTask(ShiftClock.of(7, 30), inbound = false), learned.firstTask)
        assertEquals(3, learned.sampleCount)
    }

    @Test
    fun `an even count picks the earlier middle report time and the later middle off duty`() {
        val learned = history(
            observation(ShiftClock.of(7, 0), last = ShiftClock.of(17, 0)).copy(kind = ShiftDayKind.WORK_THIRD),
            observation(ShiftClock.of(7, 10), date = day(1), last = ShiftClock.of(17, 10)),
            observation(ShiftClock.of(7, 20), date = day(6), last = ShiftClock.of(17, 20)),
            observation(ShiftClock.of(7, 30), date = day(7), last = ShiftClock.of(17, 30)),
        ).learnedTimes(ShiftTeam.FIRST)[ShiftDayKind.WORK_THIRD, early1]!!
        assertEquals(ShiftClock.of(6, 0), learned.reportByMinutes)
        assertEquals(ShiftClock.of(7, 10), learned.firstTask.minutes)
        assertEquals(ShiftClock.of(17, 20), learned.offDutyMinutes)
        assertEquals(4, learned.sampleCount)
    }

    @Test
    fun `a direction tie resolves to outbound`() {
        val learned = history(
            observation(ShiftClock.of(12, 50), date = day(0), inbound = true).copy(slot = mid3),
            observation(ShiftClock.of(12, 50), date = day(1), inbound = true).copy(slot = mid3),
            observation(ShiftClock.of(13, 30), date = day(6)).copy(slot = mid3),
            observation(ShiftClock.of(13, 30), date = day(7)).copy(slot = mid3),
        ).learnedTimes(ShiftTeam.FIRST)[ShiftDayKind.WORK_SECOND, mid3]!!
        // 到位 12:35 / 12:35 / 12:20 / 12:20 → 下中位 12:20；平手按出港 → 首个任务 13:30 出港。
        assertFalse(learned.firstTask.inbound)
        assertEquals(ShiftClock.of(12, 20), learned.reportByMinutes)
        assertEquals(ShiftClock.of(13, 30), learned.firstTask.minutes)
    }

    @Test
    fun `the second and third workday share one bucket`() {
        val learned = history(
            observation(ShiftClock.of(7, 20), date = day(0)),
            observation(ShiftClock.of(7, 20), date = day(1)).copy(kind = ShiftDayKind.WORK_THIRD),
            observation(ShiftClock.of(7, 20), date = day(6)),
        ).learnedTimes(ShiftTeam.FIRST)
        assertEquals(3, learned[ShiftDayKind.WORK_SECOND, early1]?.sampleCount)
        assertEquals(3, learned[ShiftDayKind.WORK_THIRD, early1]?.sampleCount)
        assertNull(learned[ShiftDayKind.WORK_FIRST, early1])
        assertNull(learned[handover, early1])
    }

    @Test
    fun `handover off duty is never learned`() {
        val learned = history(
            observation(ShiftClock.of(7, 10), date = day(2), last = ShiftClock.of(9, 40)).copy(kind = handover),
            observation(ShiftClock.of(7, 20), date = day(8), last = ShiftClock.of(9, 50)).copy(kind = handover),
            observation(ShiftClock.of(7, 30), date = day(14), last = ShiftClock.of(9, 30)).copy(kind = handover),
        ).learnedTimes(ShiftTeam.FIRST)[handover, early1]!!
        assertEquals(ShiftClock.of(7, 20), learned.firstTask.minutes)
        assertNull(learned.offDutyMinutes)
    }

    // ---------- 记录、替换与窗口 ----------

    @Test
    fun `re-recording a date replaces that date's observations for the same slot only`() {
        val first = history(
            observation(ShiftClock.of(7, 20)),
            observation(ShiftClock.of(7, 30)).copy(slot = early2),
        )
        val again = first.record(listOf(observation(ShiftClock.of(7, 40))))
        assertEquals(2, again.observations.size)
        assertEquals(ShiftClock.of(7, 40), again.observations.single { it.slot == early1 }.firstTaskMinutes)
        assertEquals(ShiftClock.of(7, 30), again.observations.single { it.slot == early2 }.firstTaskMinutes)
    }

    @Test
    fun `only the eight most recent dates per key are kept`() {
        val dates = (0 until 10).map { day(it * 6) }
        val history = history(*dates.map { observation(ShiftClock.of(7, 20), date = it) }.toTypedArray())
        assertEquals(ShiftTimeHistory.MAX_DATES_PER_KEY, history.observations.size)
        assertEquals(dates.drop(2), history.observations.map { it.date })
        val learned = history.learnedTimes(ShiftTeam.FIRST)[ShiftDayKind.WORK_SECOND, early1]
        assertEquals(ShiftTimeHistory.MAX_DATES_PER_KEY, learned?.sampleCount)
    }

    @Test
    fun `recording nothing plausible leaves the history untouched`() {
        val history = history(observation(ShiftClock.of(7, 20)))
        assertTrue(history.record(emptyList()) === history)
        assertTrue(ShiftTimeHistory.EMPTY.isEmpty)
        assertTrue(ShiftTimeHistory.EMPTY.learnedTimes(ShiftTeam.FIRST).isEmpty)
    }

    @Test
    fun `implausible observations are dropped`() {
        val dropped = listOf(
            observation(-10),
            observation(ShiftClock.of(0, 30)),
            observation(ShiftClock.of(1, 0, nextDay = true)),
            observation(ShiftClock.of(7, 20), last = ShiftClock.of(7, 0)),
            observation(ShiftClock.of(7, 20), last = 2 * ShiftClock.MINUTES_PER_DAY),
            observation(ShiftClock.of(7, 20)).copy(kind = ShiftDayKind.REST),
        )
        dropped.forEach { assertFalse(it.toString(), it.isPlausible) }
        assertTrue(history(*dropped.toTypedArray()).isEmpty)
        assertTrue(observation(ShiftClock.of(0, 20), inbound = true).isPlausible)
        assertTrue(observation(ShiftClock.of(7, 20), last = ShiftClock.of(1, 35, nextDay = true)).isPlausible)
    }

    // ---------- 按大组隔离与汇总 ----------

    @Test
    fun `history is keyed by team so the other team's records do not leak`() {
        val history = history(
            observation(ShiftClock.of(7, 20), date = day(2)).copy(team = ShiftTeam.SECOND),
            observation(ShiftClock.of(7, 20), date = day(3)).copy(team = ShiftTeam.SECOND),
            observation(ShiftClock.of(7, 20), date = day(8)).copy(team = ShiftTeam.SECOND),
        )
        assertTrue(history.learnedTimes(ShiftTeam.FIRST).isEmpty)
        assertEquals(3, history.learnedTimes(ShiftTeam.SECOND)[ShiftDayKind.WORK_SECOND, early1]?.sampleCount)
    }

    @Test
    fun `the summary counts distinct dates and the latest date per team`() {
        val history = history(
            observation(ShiftClock.of(7, 20), date = day(0)),
            observation(ShiftClock.of(7, 30), date = day(0)).copy(slot = early2),
            observation(ShiftClock.of(7, 20), date = day(1)),
            observation(ShiftClock.of(7, 20), date = day(6)),
            observation(ShiftClock.of(7, 20), date = day(3)).copy(team = ShiftTeam.SECOND),
        )
        val first = history.learnedTimes(ShiftTeam.FIRST)
        assertEquals(3, first.dateCount)
        assertEquals(day(6), first.latestDate)
        val second = history.learnedTimes(ShiftTeam.SECOND)
        assertEquals(1, second.dateCount)
        assertEquals(day(3), second.latestDate)
    }
}
