package com.bradj.airshift.model.holiday

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** 2026 年的表逐条对照国办发明电〔2025〕7 号。 */
class ChinaHolidaysTest {
    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)

    @Test
    fun `national day 2026 is off from the 1st to the 7th with two adjusted work days`() {
        (1..7).forEach { day ->
            val record = ChinaHolidays.on(d(10, day))
            assertEquals("国庆节", record?.name)
            assertTrue(record!!.isOff)
        }
        assertEquals(PublicHoliday(d(9, 20), "国庆节", PublicHoliday.Kind.WORK), ChinaHolidays.on(d(9, 20)))
        assertEquals(PublicHoliday(d(10, 10), "国庆节", PublicHoliday.Kind.WORK), ChinaHolidays.on(d(10, 10)))
        assertNull(ChinaHolidays.on(d(10, 8)))
    }

    @Test
    fun `spring festival 2026 runs nine days from the 15th to the 23rd of February`() {
        assertNull(ChinaHolidays.on(d(2, 13)))
        assertEquals(PublicHoliday.Kind.WORK, ChinaHolidays.on(d(2, 14))?.kind)
        (15..23).forEach { day -> assertEquals("春节", ChinaHolidays.on(d(2, day))?.name) }
        assertNull(ChinaHolidays.on(d(2, 24)))
        assertEquals(PublicHoliday.Kind.WORK, ChinaHolidays.on(d(2, 28))?.kind)
    }

    @Test
    fun `ordinary days and uncovered years have no record`() {
        assertNull(ChinaHolidays.on(d(9, 7)))
        assertNull(ChinaHolidays.on(LocalDate.of(2025, 10, 1)))
        assertTrue(ChinaHolidays.covers(2026))
        assertFalse(ChinaHolidays.covers(2025))
        assertFalse(ChinaHolidays.covers(2027))
    }

    @Test
    fun `2026 totals match the notice`() {
        val records = ChinaHolidays.year(2026)
        val off = records.filter { it.isOff }
        val work = records.filterNot { it.isOff }
        // 3 + 9 + 3 + 5 + 3 + 3 + 7 天放假；1/4、2/14、2/28、5/9、9/20、10/10 上班。
        assertEquals(33, off.size)
        assertEquals(6, work.size)
        assertEquals(
            listOf("元旦", "春节", "清明节", "劳动节", "端午节", "中秋节", "国庆节"),
            off.map { it.name }.distinct(),
        )
        assertEquals(mapOf("元旦" to 3, "春节" to 9, "清明节" to 3, "劳动节" to 5, "端午节" to 3, "中秋节" to 3, "国庆节" to 7),
            off.groupingBy { it.name }.eachCount())
        // 调休上班日一定落在周末，且不会与放假日重叠。
        work.forEach { assertTrue(it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY) }
        assertEquals(records.size, records.map { it.date }.distinct().size)
    }
}
