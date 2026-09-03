package com.bradj.airshift.model.shift

import com.bradj.airshift.model.RosterAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ShiftCalendarRowsTest {
    private val schedule = ShiftSchedule()
    private val today = LocalDate.of(2026, 9, 4)

    /** 组 1（甲子）：8-30 是早二，8-31 是晚二，9-1 交接班日因此不到岗。 */
    private fun rows(
        from: LocalDate = LocalDate.of(2026, 8, 29),
        to: LocalDate = LocalDate.of(2026, 9, 3),
        rosterDate: LocalDate? = null,
        reportBy: Int? = null,
        lastTask: Int? = null,
        margin: Int = ShiftBusPlan.DEFAULT_REPORT_MARGIN_MINUTES,
    ) = ShiftCalendarRows.build(
        schedule = schedule,
        groupId = 1,
        from = from,
        toInclusive = to,
        today = today,
        rosterDate = rosterDate,
        rosterReportByMinutes = reportBy,
        rosterLastTaskMinutes = lastTask,
        marginMinutes = margin,
    ).associateBy { it.day.date }

    @Test
    fun `a full cycle yields one row per day`() {
        assertEquals(6, rows().size)
    }

    @Test
    fun `a working row carries slot bus and off duty`() {
        val row = rows()[LocalDate.of(2026, 8, 30)]!!
        assertEquals(ShiftSlot(ShiftTier.EARLY, 2), row.day.slot)
        assertTrue(row.day.attends)
        assertEquals(LocalTime.of(5, 55), row.bus?.departure)
        assertEquals(ShiftEstimateSource.ESTIMATE, row.bus?.source)
        assertEquals(ShiftClock.of(17, 30), row.offDutyMinutes)
        assertEquals(ShiftEstimateSource.ESTIMATE, row.offDutySource)
    }

    @Test
    fun `rest rows carry no slot bus or off duty`() {
        listOf(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 3)).forEach { date ->
            val row = rows()[date]!!
            assertEquals(ShiftDayKind.REST, row.day.kind)
            assertNull(row.day.slot)
            assertNull(row.bus)
            assertNull(row.offDutyMinutes)
        }
    }

    @Test
    fun `a night group gets no bus on the handover morning it sits out`() {
        val row = rows()[LocalDate.of(2026, 9, 1)]!!
        assertEquals(ShiftDayKind.HANDOVER, row.day.kind)
        assertEquals(ShiftSlot(ShiftTier.NIGHT, 2), row.day.slot)
        assertFalse(row.day.attends)
        assertTrue(row.day.isHandoverExempt)
        assertNull(row.bus)
        assertNull(row.offDutyMinutes)
    }

    @Test
    fun `an attending group gets a bus on the handover morning`() {
        val attending = ShiftCalendarRows.build(
            schedule = schedule,
            groupId = 11,
            from = LocalDate.of(2026, 9, 1),
            toInclusive = LocalDate.of(2026, 9, 1),
            today = today,
        ).single()
        assertEquals(ShiftSlot(ShiftTier.EARLY, 1), attending.day.slot)
        assertTrue(attending.day.attends)
        assertEquals(LocalTime.of(5, 25), attending.bus?.departure)
        assertEquals(ShiftClock.of(10, 0), attending.offDutyMinutes)
    }

    @Test
    fun `today is flagged on exactly one row`() {
        val all = rows(from = today.minusDays(3), to = today.plusDays(3))
        assertEquals(1, all.values.count { it.isToday })
        assertTrue(all[today]!!.isToday)
    }

    @Test
    fun `a roster for the same day replaces the estimate`() {
        val date = LocalDate.of(2026, 8, 30)
        val row = rows(
            rosterDate = date,
            reportBy = ShiftClock.of(10, 0),
            lastTask = ShiftClock.of(18, 5),
        )[date]!!
        assertEquals(LocalTime.of(8, 0), row.bus?.departure)
        assertEquals(ShiftEstimateSource.ROSTER, row.bus?.source)
        assertEquals(ShiftClock.of(18, 5), row.offDutyMinutes)
        assertEquals(ShiftEstimateSource.ROSTER, row.offDutySource)
    }

    @Test
    fun `a roster for another day leaves every row on the estimate`() {
        val row = rows(
            rosterDate = LocalDate.of(2026, 8, 29),
            reportBy = ShiftClock.of(10, 0),
            lastTask = ShiftClock.of(18, 5),
        )[LocalDate.of(2026, 8, 30)]!!
        assertEquals(LocalTime.of(5, 55), row.bus?.departure)
        assertEquals(ShiftEstimateSource.ESTIMATE, row.bus?.source)
        assertEquals(ShiftClock.of(17, 30), row.offDutyMinutes)
    }

    @Test
    fun `a negative roster report time falls back to the estimate`() {
        val date = LocalDate.of(2026, 8, 30)
        val row = rows(rosterDate = date, reportBy = -30)[date]!!
        assertEquals(LocalTime.of(5, 55), row.bus?.departure)
        assertEquals(ShiftEstimateSource.ESTIMATE, row.bus?.source)
    }

    @Test
    fun `the margin setting moves the recommended bus`() {
        // 8-31 组 1 是晚二，首个任务 07:10 出港 → 最晚 06:10 到位，正好卡在两班之间。
        val date = LocalDate.of(2026, 8, 31)
        assertEquals(ShiftSlot(ShiftTier.NIGHT, 2), rows()[date]!!.day.slot)
        assertEquals(LocalTime.of(5, 55), rows(margin = 0)[date]!!.bus?.departure)
        assertEquals(LocalTime.of(5, 25), rows(margin = 15)[date]!!.bus?.departure)
        assertEquals(LocalTime.of(5, 25), rows(margin = 30)[date]!!.bus?.departure)
    }

    @Test
    fun `a slot with more slack keeps the same bus at every offered margin`() {
        // 8-30 组 1 是早二，首个任务 07:30 出港，三档余量都还能赶上 05:55。
        val date = LocalDate.of(2026, 8, 30)
        ShiftBusPlan.REPORT_MARGIN_OPTIONS.forEach { margin ->
            assertEquals("余量 $margin", LocalTime.of(5, 55), rows(margin = margin)[date]!!.bus?.departure)
        }
    }

    @Test
    fun `the bridge and the rows agree on a real looking roster`() {
        val date = LocalDate.of(2026, 8, 30)
        val assignments = listOf(
            RosterAssignment(
                aircraftRegistration = "B6560",
                aircraftType = "320",
                inboundFlight = null,
                origin = null,
                scheduledArrival = null,
                outboundFlight = "MU6771",
                destination = "大连",
                scheduledDeparture = LocalDateTime.of(date, LocalTime.of(11, 0)),
                assignees = "甲子 甲丑",
            ),
        )
        val row = rows(
            rosterDate = ShiftRosterBridge.rosterDate(assignments),
            reportBy = ShiftRosterBridge.reportByMinutes(assignments),
            lastTask = ShiftRosterBridge.lastTaskMinutes(assignments),
        )[date]!!
        // 11:00 出港 → 最晚 10:00 到位 → 留 15 分钟余量 → 08:00 班车。
        assertEquals(ShiftClock.of(10, 0), row.bus?.reportByMinutes)
        assertEquals(LocalTime.of(8, 0), row.bus?.departure)
        assertEquals(ShiftEstimateSource.ROSTER, row.bus?.source)
    }
}
