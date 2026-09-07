package com.bradj.airshift.model.holiday

import java.time.LocalDate

/**
 * 中国国家法定节假日安排。国务院办公厅每年年末发布下一年的《关于 XXXX 年部分节假日安排的通知》，
 * 这里逐年照抄通知原文：放假的连续日期段与为它调休的上班日。
 *
 * 只收录已正式发布的年份，未收录的年份查不到任何记录——日历不显示，而不是按惯例猜；下一年的通知发布后在
 * [ARRANGEMENTS] 里照原文加一段即可。数据内置、不联网、不可配置，与排班日历"完全由本机计算"的定位一致。
 */
@Suppress("MagicNumber") // 节假日表：日期数字本身就是内容。
object ChinaHolidays : HolidayCalendar {

    /** 一个节日的安排：放假段 [offFrom]..[offTo]（含两端）与为它调休的上班日 [work]。 */
    private class Arrangement(
        val name: String,
        val offFrom: LocalDate,
        val offTo: LocalDate,
        val work: List<LocalDate> = emptyList(),
    )

    private fun d(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)

    private val ARRANGEMENTS: List<Arrangement> = listOf(
        // ---- 2026：国办发明电〔2025〕7 号（2025-11-04 发布）----
        Arrangement("元旦", d(2026, 1, 1), d(2026, 1, 3), work = listOf(d(2026, 1, 4))),
        Arrangement("春节", d(2026, 2, 15), d(2026, 2, 23), work = listOf(d(2026, 2, 14), d(2026, 2, 28))),
        Arrangement("清明节", d(2026, 4, 4), d(2026, 4, 6)),
        Arrangement("劳动节", d(2026, 5, 1), d(2026, 5, 5), work = listOf(d(2026, 5, 9))),
        Arrangement("端午节", d(2026, 6, 19), d(2026, 6, 21)),
        Arrangement("中秋节", d(2026, 9, 25), d(2026, 9, 27)),
        Arrangement("国庆节", d(2026, 10, 1), d(2026, 10, 7), work = listOf(d(2026, 9, 20), d(2026, 10, 10))),
    )

    private val byDate: Map<LocalDate, PublicHoliday> = buildMap {
        ARRANGEMENTS.forEach { arrangement ->
            var date = arrangement.offFrom
            while (!date.isAfter(arrangement.offTo)) {
                put(date, PublicHoliday(date, arrangement.name, PublicHoliday.Kind.OFF))
                date = date.plusDays(1)
            }
            arrangement.work.forEach { put(it, PublicHoliday(it, arrangement.name, PublicHoliday.Kind.WORK)) }
        }
    }

    /** 已收录安排的年份。 */
    val coveredYears: Set<Int> = ARRANGEMENTS.map { it.offFrom.year }.toSet()

    fun covers(year: Int): Boolean = year in coveredYears

    override fun on(date: LocalDate): PublicHoliday? = byDate[date]

    /** 某一年的全部记录（放假日与调休上班日），按日期排序。 */
    fun year(year: Int): List<PublicHoliday> = byDate.values.filter { it.date.year == year }.sortedBy { it.date }
}
