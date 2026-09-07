package com.bradj.airshift.model.shift

import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.parser.ExcelRosterParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ShiftTimeObserverTest {
    /** 与 ExcelRosterParserTest 同款的合成班次行：08-24 早 [1,5,11] 中 [8,9,2,6] 晚 [4,10,3]。 */
    private val members = mapOf(
        1 to listOf("甲子", "甲丑"),
        5 to listOf("乙子", "乙丑", "乙寅"),
        11 to listOf("丙子", "丙丑", "丙寅"),
        8 to listOf("丁子明", "丁丑明", "丁寅明"),
        9 to listOf("戊子", "戊丑"),
        2 to listOf("己子", "己丑"),
        6 to listOf("庚子", "庚丑"),
        4 to listOf("辛子", "辛丑"),
        10 to listOf("壬子", "壬丑"),
        3 to listOf("癸子", "癸丑", "癸寅"),
    )
    private val observed = ObservedShiftGroups(
        early = listOf(1, 5, 11),
        mid = listOf(8, 9, 2, 6),
        night = listOf(4, 10, 3),
        members = members,
    )
    private val workday = LocalDate.of(2026, 8, 24)
    private val calibrated = ShiftSchedule(ShiftCalibration(workday, observed))
    private val matches: (String, String) -> Boolean = ExcelRosterParser::containsAssignee

    private fun at(date: LocalDate, hour: Int, minute: Int): LocalDateTime = date.atTime(hour, minute)

    private fun task(
        assignees: String,
        outbound: String? = null,
        departure: LocalDateTime? = null,
        inbound: String? = null,
        arrival: LocalDateTime? = null,
    ) = RosterAssignment(
        aircraftRegistration = "B6560",
        aircraftType = "320",
        inboundFlight = inbound,
        origin = null,
        scheduledArrival = arrival,
        outboundFlight = outbound,
        destination = null,
        scheduledDeparture = departure,
        assignees = assignees,
    )

    private fun workdayRows(date: LocalDate = workday): List<RosterAssignment> = listOf(
        task("甲子 甲丑", outbound = "MU6771", departure = at(date, 7, 20)),
        task("甲丑", outbound = "MU2249", departure = at(date, 9, 0)),
        task("甲子", inbound = "MU5000", arrival = at(date, 17, 15)),
        task("乙子 乙丑", outbound = "MU2100", departure = at(date, 7, 30)),
        task("丙子", outbound = "MU2101", departure = at(date, 7, 50)),
        task("丁子明 丁丑明", outbound = "MU2102", departure = at(date, 7, 50)),
        task(
            "戊子 戊丑",
            inbound = "MU9977",
            arrival = at(date, 12, 50),
            outbound = "MU9977",
            departure = at(date, 13, 40),
        ),
        task("己子", inbound = "MU2103", arrival = at(date, 12, 50)),
        task(
            "庚子 庚丑",
            inbound = "MU2104",
            arrival = at(date, 13, 30),
            outbound = "MU2104",
            departure = at(date, 14, 20),
        ),
        task("辛子 辛丑", outbound = "MU2105", departure = at(date, 7, 10)),
        task("辛子", inbound = "MU6813", arrival = at(date.plusDays(1), 0, 40)),
        task("壬子 壬丑", outbound = "MU2106", departure = at(date, 7, 10)),
    )

    private fun observations(
        rows: List<RosterAssignment>,
        date: LocalDate = workday,
        schedule: ShiftSchedule = calibrated,
    ) = ShiftTimeObserver.observeGroups(schedule, date, rows, matches).associateBy { it.slot }

    @Test
    fun `a full workday sheet yields one observation per attending group with the earliest task and its direction`() {
        val bySlot = observations(workdayRows())
        // 癸 组没有任何行，其余 9 组各一条。
        assertEquals(9, bySlot.size)
        assertFalse(ShiftSlot(ShiftTier.NIGHT, 3) in bySlot)
        val early1 = bySlot.getValue(ShiftSlot(ShiftTier.EARLY, 1))
        assertEquals(workday, early1.date)
        assertEquals(ShiftTeam.FIRST, early1.team)
        assertEquals(ShiftDayKind.WORK_SECOND, early1.kind)
        assertEquals(ShiftClock.of(7, 20), early1.firstTaskMinutes)
        assertFalse(early1.inbound)
        assertEquals(ShiftClock.of(17, 15), early1.lastTaskMinutes)
        assertTrue(bySlot.values.all { it.isPlausible })
    }

    @Test
    fun `a group whose first row is inbound records the arrival time and inbound`() {
        val mid2 = observations(workdayRows()).getValue(ShiftSlot(ShiftTier.MID, 2))
        assertEquals(ShiftClock.of(12, 50), mid2.firstTaskMinutes)
        assertTrue(mid2.inbound)
        assertEquals(ShiftClock.of(12, 35), mid2.reportByMinutes)
        assertEquals(ShiftClock.of(13, 40), mid2.lastTaskMinutes)
    }

    @Test
    fun `rows after midnight push the last task past 1440`() {
        val night1 = observations(workdayRows()).getValue(ShiftSlot(ShiftTier.NIGHT, 1))
        assertEquals(ShiftClock.of(7, 10), night1.firstTaskMinutes)
        assertEquals(ShiftClock.of(0, 40, nextDay = true), night1.lastTaskMinutes)
    }

    @Test
    fun `a row staffed by two groups counts for both`() {
        val rows = workdayRows() + task("甲子 壬子", outbound = "MU2000", departure = at(workday, 6, 50))
        val bySlot = observations(rows)
        assertEquals(ShiftClock.of(6, 50), bySlot.getValue(ShiftSlot(ShiftTier.EARLY, 1)).firstTaskMinutes)
        assertEquals(ShiftClock.of(6, 50), bySlot.getValue(ShiftSlot(ShiftTier.NIGHT, 2)).firstTaskMinutes)
    }

    @Test
    fun `groups without members or without timed rows are skipped`() {
        // 内置表没有成员名单，整表匹配不到任何组。
        assertTrue(ShiftTimeObserver.observeGroups(ShiftSchedule(), workday, workdayRows(), matches).isEmpty())
        // 没有任何行时也不会凭空生成记录。
        assertTrue(ShiftTimeObserver.observeGroups(calibrated, workday, emptyList(), matches).isEmpty())
        // 只有没有时间的行的组不算。
        val timeless = listOf(task("癸子", outbound = "MU3000"))
        assertFalse(ShiftSlot(ShiftTier.NIGHT, 3) in observations(workdayRows() + timeless))
    }

    @Test
    fun `the handover sheet labels slots from the previous full workday and skips the night groups`() {
        // 08-31 是一组第 3 天，09-01 交接班：白班组沿用 08-31 的槽位到岗，夜班组不到岗。
        val schedule = ShiftSchedule(ShiftCalibration(LocalDate.of(2026, 8, 31), observed))
        val handover = LocalDate.of(2026, 9, 1)
        val rows = listOf(
            task("甲子", outbound = "MU6771", departure = at(handover, 7, 10)),
            task("丁子明", outbound = "MU2102", departure = at(handover, 7, 50)),
            task("辛子", outbound = "MU2105", departure = at(handover, 7, 30)),
        )
        val bySlot = observations(rows, date = handover, schedule = schedule)
        assertEquals(setOf(ShiftSlot(ShiftTier.EARLY, 1), ShiftSlot(ShiftTier.MID, 1)), bySlot.keys)
        assertTrue(bySlot.values.all { it.kind == ShiftDayKind.HANDOVER })
        assertEquals(ShiftClock.of(7, 10), bySlot.getValue(ShiftSlot(ShiftTier.EARLY, 1)).firstTaskMinutes)
    }

    @Test
    fun `a rest day records nothing`() {
        val rest = LocalDate.of(2026, 9, 2)
        assertEquals(ShiftDayKind.REST, calibrated.dayKind(rest))
        assertTrue(ShiftTimeObserver.observeGroups(calibrated, rest, workdayRows(rest), matches).isEmpty())
        assertTrue(ShiftTimeObserver.observeOwn(calibrated, rest, workdayRows(rest), ownGroupId = 1).isEmpty())
    }

    @Test
    fun `the own slot fallback records one observation for the users group`() {
        // 内置表：组 1 在 8-30 是早二。
        val date = LocalDate.of(2026, 8, 30)
        val own = listOf(
            task("辛子 辛丑", outbound = "MU2100", departure = at(date, 7, 30)),
            task("辛子", inbound = "MU2103", arrival = at(date, 17, 30)),
        )
        val observation = ShiftTimeObserver.observeOwn(ShiftSchedule(), date, own, ownGroupId = 1).single()
        assertEquals(ShiftSlot(ShiftTier.EARLY, 2), observation.slot)
        assertEquals(ShiftDayKind.WORK_SECOND, observation.kind)
        assertEquals(ShiftTeam.FIRST, observation.team)
        assertEquals(ShiftClock.of(7, 30), observation.firstTaskMinutes)
        assertFalse(observation.inbound)
        assertEquals(ShiftClock.of(17, 30), observation.lastTaskMinutes)
    }

    @Test
    fun `the own slot fallback records nothing without a group or without rows`() {
        val date = LocalDate.of(2026, 8, 30)
        val own = listOf(task("辛子", outbound = "MU2100", departure = at(date, 7, 30)))
        assertTrue(ShiftTimeObserver.observeOwn(ShiftSchedule(), date, own, ownGroupId = null).isEmpty())
        assertTrue(ShiftTimeObserver.observeOwn(ShiftSchedule(), date, emptyList(), ownGroupId = 1).isEmpty())
    }
}
