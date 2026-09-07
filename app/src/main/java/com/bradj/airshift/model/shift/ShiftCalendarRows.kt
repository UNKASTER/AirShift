package com.bradj.airshift.model.shift

import com.bradj.airshift.model.holiday.ChinaHolidays
import com.bradj.airshift.model.holiday.HolidayCalendar
import com.bradj.airshift.model.holiday.PublicHoliday
import java.time.LocalDate

/** 排班日历列表中的一行。 */
data class ShiftCalendarRow(
    val day: ShiftDay,
    /** 休息日、以及交接班日不到岗时为 null。 */
    val bus: BusRecommendation?,
    val offDutyMinutes: Int?,
    val offDutySource: ShiftEstimateSource,
    val isToday: Boolean,
    /** 国家法定节假日安排里这一天的记录（放假 / 调休上班）；普通日或未收录的年份为 null。只作标注，不影响班次与班车。 */
    val holiday: PublicHoliday? = null,
)

/**
 * 把班次计算、当日真实排班、本机实测记录和班车规则拼成可直接渲染的列表。
 *
 * App 只保存一份当前排班，因此只有日期与排班自身日期相同的那一行能用真实航班时间；
 * 其余行先看该槽位有没有攒够样本的实测聚合值（[learned]），没有再退回内置表，并在 UI 上标注来源。
 */
object ShiftCalendarRows {

    // 尾部全是可选输入，拆成参数对象只会把同样的字段搬到三个调用点。
    @Suppress("LongParameterList")
    fun build(
        schedule: ShiftSchedule,
        groupId: Int,
        from: LocalDate,
        toInclusive: LocalDate,
        today: LocalDate,
        rosterDate: LocalDate? = null,
        rosterReportByMinutes: Int? = null,
        rosterLastTaskMinutes: Int? = null,
        marginMinutes: Int = ShiftBusPlan.DEFAULT_REPORT_MARGIN_MINUTES,
        learned: LearnedTimes = LearnedTimes.NONE,
        holidays: HolidayCalendar = ChinaHolidays,
    ): List<ShiftCalendarRow> = schedule.daysFor(groupId, from, toInclusive).map { day ->
        val slot = day.slot
        val hasRoster = rosterDate != null && rosterDate == day.date
        val holiday = holidays.on(day.date)
        if (slot == null || !day.attends) {
            return@map ShiftCalendarRow(
                day = day,
                bus = null,
                offDutyMinutes = null,
                offDutySource = ShiftEstimateSource.ESTIMATE,
                isToday = day.date == today,
                holiday = holiday,
            )
        }
        // 到位时间早于当日零点说明真实排班与本行对不上，宁可退回推算也不给出错误班车。
        val rosterReportBy = rosterReportByMinutes?.takeIf { hasRoster && it >= 0 }
        val rosterOffDuty = rosterLastTaskMinutes?.takeIf { hasRoster }
        val learnedSlot = learned[day.kind, slot]
        val learnedOffDuty = learnedSlot?.offDutyMinutes
        ShiftCalendarRow(
            day = day,
            bus = ShiftBusPlan.recommend(day.kind, slot, rosterReportBy, marginMinutes, learnedSlot),
            offDutyMinutes = rosterOffDuty ?: learnedOffDuty ?: ShiftBusPlan.expectedOffDutyMinutes(day.kind, slot),
            offDutySource = when {
                rosterOffDuty != null -> ShiftEstimateSource.ROSTER
                learnedOffDuty != null -> ShiftEstimateSource.LEARNED
                else -> ShiftEstimateSource.ESTIMATE
            },
            isToday = day.date == today,
            holiday = holiday,
        )
    }
}
