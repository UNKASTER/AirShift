package com.bradj.airshift.model.holiday

import java.time.LocalDate

/** 按日期查法定节假日安排；普通日、以及安排尚未收录的年份都返回 null。 */
fun interface HolidayCalendar {
    fun on(date: LocalDate): PublicHoliday?

    companion object {
        /** 什么都不标：测试与不需要节假日的调用方用。 */
        val NONE: HolidayCalendar = HolidayCalendar { null }
    }
}
