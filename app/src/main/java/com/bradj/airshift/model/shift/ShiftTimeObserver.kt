package com.bradj.airshift.model.shift

import com.bradj.airshift.model.DutyTimeline
import com.bradj.airshift.model.RosterAssignment
import java.time.LocalDate

/**
 * 从一次导入的排班表里读出各槽位当天的实测首末任务，供 [ShiftTimeHistory] 累积。
 *
 * 整张表的每一行都带人员栏，按校准得到的成员名单把行归到各班组，再由 [ShiftSchedule] 得出该班组当天的
 * 日型与槽位，一份整班日表就能给全部槽位各一条记录；交接班日的半天表按上一份校准继承槽位。
 * 首个任务与到位时间的口径和 [DutyTimeline.gateArrivalTime] 一致：有进港航班号的行按进港算（提前 15 分钟），
 * 纯出港按出港算（提前 70 分钟）。记录里只有时间与方向，不含姓名。
 */
object ShiftTimeObserver {

    /**
     * 整张表按班组成员匹配行：每个到岗且有槽位的班组一条记录。
     * 没有成员名单的组（一组未校准的内置表、二组校准前的空表）自然跳过，[matches] 由解析器提供人员栏匹配规则。
     */
    fun observeGroups(
        schedule: ShiftSchedule,
        rosterDate: LocalDate,
        staffAssignments: List<RosterAssignment>,
        matches: (assignees: String, member: String) -> Boolean,
    ): List<ShiftTimeObservation> {
        if (staffAssignments.isEmpty()) return emptyList()
        return schedule.table.cycleOrder.mapNotNull { groupId ->
            val members = schedule.table.membersOf(groupId)
            val rows = staffAssignments.filter { row -> members.any { member -> matches(row.assignees, member) } }
            observe(schedule, rosterDate, groupId, rows)
        }
    }

    /** 兜底：只用用户自己的行给自己的槽位一条记录；没有班组、休息日或不到岗时为空。 */
    fun observeOwn(
        schedule: ShiftSchedule,
        rosterDate: LocalDate,
        ownAssignments: List<RosterAssignment>,
        ownGroupId: Int?,
    ): List<ShiftTimeObservation> =
        listOfNotNull(ownGroupId?.let { observe(schedule, rosterDate, it, ownAssignments) })

    /** 某班组当天的一条实测；不到岗、没有槽位、没有带时间的任务或数值不合理时为 null。 */
    private fun observe(
        schedule: ShiftSchedule,
        rosterDate: LocalDate,
        groupId: Int,
        rows: List<RosterAssignment>,
    ): ShiftTimeObservation? {
        val day = schedule.dayFor(groupId, rosterDate)
        val slot = day.slot?.takeIf { day.attends } ?: return null
        val first = rows
            .mapNotNull { row -> DutyTimeline.gateArrivalTime(row)?.let { gateArrival -> gateArrival to row } }
            .minByOrNull { (gateArrival, _) -> gateArrival }
        val lastTask = rows.mapNotNull(ShiftRosterBridge::scheduledEnd).maxOrNull()
        return if (first == null || lastTask == null) {
            null
        } else {
            val (gateArrival, row) = first
            val inbound = row.inboundFlight != null
            ShiftTimeObservation(
                date = rosterDate,
                team = schedule.team,
                kind = day.kind,
                slot = slot,
                firstTaskMinutes = ShiftRosterBridge.minutesFrom(rosterDate, gateArrival) +
                    ShiftBusPlan.reportLeadMinutes(inbound),
                inbound = inbound,
                lastTaskMinutes = ShiftRosterBridge.minutesFrom(rosterDate, lastTask),
            ).takeIf { it.isPlausible }
        }
    }
}
