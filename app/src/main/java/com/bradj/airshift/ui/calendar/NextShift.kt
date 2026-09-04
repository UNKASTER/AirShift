package com.bradj.airshift.ui.calendar

import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.shift.ShiftCalendarRows
import com.bradj.airshift.model.shift.ShiftClock
import com.bradj.airshift.model.shift.ShiftRosterBridge
import com.bradj.airshift.model.shift.ShiftSchedule
import java.time.LocalDate

private const val LOOKAHEAD_DAYS = 7L
private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 当前执勤全部完成（或还没有排班）时，板面仍要回答"接下来是什么"：给出下一次到岗的班次与班车。 */
object NextShift {
    fun text(
        schedule: ShiftSchedule,
        groupId: Int?,
        assignments: List<RosterAssignment>,
        marginMinutes: Int,
        today: LocalDate,
    ): String? = groupId
        ?.let { group ->
            ShiftCalendarRows.build(
                schedule = schedule,
                groupId = group,
                from = today.plusDays(1),
                toInclusive = today.plusDays(LOOKAHEAD_DAYS),
                today = today,
                rosterDate = ShiftRosterBridge.rosterDate(assignments),
                rosterReportByMinutes = ShiftRosterBridge.reportByMinutes(assignments),
                rosterLastTaskMinutes = ShiftRosterBridge.lastTaskMinutes(assignments),
                marginMinutes = marginMinutes,
            ).firstOrNull { it.day.attends }
        }
        ?.let { next ->
            val date = next.day.date
            buildString {
                append("${date.monthValue}/${date.dayOfMonth} 周${WEEKDAYS[date.dayOfWeek.value - 1]}")
                next.day.slot?.label?.let { append(" $it") }
                next.bus?.let { bus ->
                    append(" · 班车 %02d:%02d".format(bus.departure.hour, bus.departure.minute))
                    append(" · 到位 ${ShiftClock.format(bus.reportByMinutes)}")
                }
            }
        }
}
