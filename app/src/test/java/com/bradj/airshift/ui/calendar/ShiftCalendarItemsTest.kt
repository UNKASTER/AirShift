package com.bradj.airshift.ui.calendar

import com.bradj.airshift.model.shift.ShiftBusPlan
import com.bradj.airshift.model.shift.ShiftCalendarRows
import com.bradj.airshift.model.shift.ShiftSchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 日历列表的扁平化：月份标题只在换月时插一条，今天的下标把它们算进去。 */
class ShiftCalendarItemsTest {
    private val schedule = ShiftSchedule()

    private fun rows(today: LocalDate) = ShiftCalendarRows.build(
        schedule = schedule,
        groupId = 1,
        from = LocalDate.of(2026, 8, 29),
        toInclusive = LocalDate.of(2026, 9, 3),
        today = today,
        rosterDate = null,
        rosterReportByMinutes = null,
        rosterLastTaskMinutes = null,
        marginMinutes = ShiftBusPlan.DEFAULT_REPORT_MARGIN_MINUTES,
    )

    @Test
    fun `a month title precedes the first row of each month`() {
        val keys = rows(LocalDate.of(2026, 9, 2)).toCalendarItems().map { it.key }
        assertEquals(
            listOf(
                "month-2026-8", "2026-08-29", "2026-08-30", "2026-08-31",
                "month-2026-9", "2026-09-01", "2026-09-02", "2026-09-03",
            ),
            keys,
        )
    }

    @Test
    fun `today index counts the month titles before it`() {
        assertEquals(6, rows(LocalDate.of(2026, 9, 2)).toCalendarItems().todayIndex())
        assertEquals(1, rows(LocalDate.of(2026, 8, 29)).toCalendarItems().todayIndex())
    }

    @Test
    fun `without a today row the list starts at the top`() {
        assertEquals(0, rows(LocalDate.of(2026, 10, 1)).toCalendarItems().todayIndex())
        assertEquals(0, emptyList<ShiftCalendarItem>().todayIndex())
    }
}
