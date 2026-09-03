package com.bradj.airshift.model.shift

import java.time.LocalDate

/** 排班日历列表中的一行。 */
data class ShiftCalendarRow(
    val day: ShiftDay,
    /** 休息日、以及交接班日不到岗时为 null。 */
    val bus: BusRecommendation?,
    val offDutyMinutes: Int?,
    val offDutySource: ShiftEstimateSource,
    val isToday: Boolean,
)

/**
 * 把班次计算、当日真实排班和班车规则拼成可直接渲染的列表。
 *
 * App 只保存一份当前排班，因此只有日期与排班自身日期相同的那一行能用真实航班时间；
 * 其余行退回历史规律推算，并在 UI 上标注为预估。
 */
object ShiftCalendarRows {

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
    ): List<ShiftCalendarRow> = schedule.daysFor(groupId, from, toInclusive).map { day ->
        val slot = day.slot
        val hasRoster = rosterDate != null && rosterDate == day.date
        if (slot == null || !day.attends) {
            return@map ShiftCalendarRow(
                day = day,
                bus = null,
                offDutyMinutes = null,
                offDutySource = ShiftEstimateSource.ESTIMATE,
                isToday = day.date == today,
            )
        }
        // 到位时间早于当日零点说明真实排班与本行对不上，宁可退回推算也不给出错误班车。
        val rosterReportBy = rosterReportByMinutes?.takeIf { hasRoster && it >= 0 }
        val rosterOffDuty = rosterLastTaskMinutes?.takeIf { hasRoster }
        ShiftCalendarRow(
            day = day,
            bus = ShiftBusPlan.recommend(day.kind, slot, rosterReportBy, marginMinutes),
            offDutyMinutes = rosterOffDuty ?: ShiftBusPlan.expectedOffDutyMinutes(day.kind, slot),
            offDutySource = if (rosterOffDuty != null) ShiftEstimateSource.ROSTER else ShiftEstimateSource.ESTIMATE,
            isToday = day.date == today,
        )
    }
}
