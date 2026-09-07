package com.bradj.airshift.model.shift

import java.time.LocalDate

/**
 * 一次真实排班表观测，用于校正班组表与轮转相位。
 * 只有整班工作日的表格才带“候机早班/中班/夜班”行，而任何一天恰有一个大组在整班，
 * 因此观测日期唯一确定它属于哪个大组（[team]）。
 */
data class ShiftCalibration(
    val date: LocalDate,
    val observed: ObservedShiftGroups,
) {
    val team: ShiftTeam get() = ShiftTeam.onFullWorkday(date)

    val isUsable: Boolean get() = observed.isUsable

    /**
     * 二组的小组没有编号，解析时按当天早→中→晚位次给了合成 id，换一天导入位次就变。
     * 把与上一份校准共享至少一个成员的组对齐到旧 id，其余分配双方都没用过的新 id，
     * 这样设置里手动指定的班组不会漂到别的组上。
     * 一组表带真实组号、没有上一份校准、或上一份属于另一大组时原样返回。
     */
    fun alignedWith(previous: ShiftCalibration?): ShiftCalibration {
        val old = previous?.observed
            ?.takeIf { observed.hasSyntheticIds && previous.isUsable && previous.team == team }
            ?: return this
        val mapping = mutableMapOf<Int, Int>()
        val taken = old.orderedGroupIds.toMutableSet()
        val matchedOld = mutableSetOf<Int>()
        for (id in observed.orderedGroupIds) {
            val names = observed.members[id].orEmpty().map(ShiftGroupTable::normalize).toSet()
            val match = old.orderedGroupIds.firstOrNull { oldId ->
                oldId !in matchedOld && old.members[oldId].orEmpty().any { ShiftGroupTable.normalize(it) in names }
            } ?: continue
            mapping[id] = match
            matchedOld += match
        }
        var next = 1
        for (id in observed.orderedGroupIds) {
            if (id in mapping) continue
            while (next in taken) next++
            mapping[id] = next
            taken += next
        }
        return copy(
            observed = ObservedShiftGroups(
                early = observed.early.map(mapping::getValue),
                mid = observed.mid.map(mapping::getValue),
                night = observed.night.map(mapping::getValue),
                members = observed.members.mapKeys { (id, _) -> mapping.getValue(id) },
                hasSyntheticIds = true,
            ),
        )
    }
}

/** 排班日历中的一天。 */
data class ShiftDay(
    val date: LocalDate,
    val kind: ShiftDayKind,
    /** 休息日为 null；交接班日继承前一个整班工作日的槽位。 */
    val slot: ShiftSlot?,
    /** 当天是否需要到岗。 */
    val attends: Boolean,
    /** 交接班日时为被继承槽位的那个整班工作日。 */
    val slotInheritedFrom: LocalDate? = null,
) {
    /** 前一天排到夜班，因此交接班日不到岗。 */
    val isHandoverExempt: Boolean
        get() = kind == ShiftDayKind.HANDOVER && !attends && slot != null
}

/**
 * 排班日历的计算入口。给定班组号与日期，得出日型、班次槽位与是否到岗。
 *
 * 大组由校准表的日期决定；没有可用校准时用 [fallbackTeam]（设置中的手动大组，缺省一组）。
 * 无校准数据时使用该大组的内置表与 [ShiftCycle] 的内置相位（二组内置表为空，只出日型）；
 * 有校准数据时，以观测当天的真实顺序为相位基准，其余日期相对它旋转。
 */
class ShiftSchedule(calibration: ShiftCalibration? = null, fallbackTeam: ShiftTeam = ShiftTeam.FIRST) {

    val team: ShiftTeam
    val table: ShiftGroupTable
    private val phaseReference: LocalDate?

    init {
        val usable = calibration?.takeIf { it.isUsable }
        team = usable?.team ?: fallbackTeam
        val base = ShiftGroupTable.builtIn(team)
        table = usable?.let { ShiftGroupTable.from(it.observed, base) } ?: base
        phaseReference = usable?.date
    }

    val isCalibrated: Boolean get() = phaseReference != null

    fun findGroupIdForName(name: String): Int? = table.findGroupIdForName(name)

    fun labelOf(groupId: Int): String = table.labelOf(groupId)

    fun dayKind(date: LocalDate): ShiftDayKind = ShiftCycle.dayKind(date, team)

    /** 当天旋转后的班组顺序（早→中→晚）；非整班工作日返回 null。 */
    fun orderFor(date: LocalDate): List<Int>? {
        val offset = rotationOffset(date) ?: return null
        return List(table.size) { table.cycleOrder[(offset + it) % table.size] }
    }

    /** 班组在当天旋转后序列中的位置；非整班工作日返回 null。 */
    fun positionOf(groupId: Int, date: LocalDate): Int? {
        val offset = rotationOffset(date) ?: return null
        val index = table.cycleIndexOf(groupId) ?: return null
        return Math.floorMod(index - offset, table.size)
    }

    /** 整班工作日的班次槽位；其他日型返回 null。 */
    fun slotOnFullWorkday(groupId: Int, date: LocalDate): ShiftSlot? =
        positionOf(groupId, date)?.let(table.tierSizes::slotAt)

    fun dayFor(groupId: Int, date: LocalDate): ShiftDay {
        val kind = dayKind(date)
        return when {
            kind.isRest -> ShiftDay(date, kind, slot = null, attends = false)
            kind.isFullWorkday -> {
                // 没有班组表（二组校准前）时只知道当天上班、不知道槽位：仍算到岗，槽位留空。
                val slot = slotOnFullWorkday(groupId, date)
                ShiftDay(date, kind, slot = slot, attends = slot != null || table.size == 0)
            }
            else -> {
                // 交接班日只上上午，班次沿用前一个整班工作日；那天排到夜班的组干到凌晨，不到岗。
                val source = ShiftCycle.previousFullWorkday(date, team)
                val slot = source?.let { slotOnFullWorkday(groupId, it) }
                ShiftDay(
                    date = date,
                    kind = kind,
                    slot = slot,
                    attends = slot?.let { it.tier != ShiftTier.NIGHT } ?: (table.size == 0),
                    slotInheritedFrom = source,
                )
            }
        }
    }

    fun daysFor(groupId: Int, from: LocalDate, toInclusive: LocalDate): List<ShiftDay> {
        if (toInclusive.isBefore(from)) return emptyList()
        return buildList {
            var cursor = from
            while (!cursor.isAfter(toInclusive)) {
                add(dayFor(groupId, cursor))
                cursor = cursor.plusDays(1)
            }
        }
    }

    private fun rotationOffset(date: LocalDate): Int? {
        val raw = ShiftCycle.rotationOffset(date, team, table.size) ?: return null
        val reference = phaseReference ?: return raw
        // 校准点当天的顺序就是观测顺序，故其偏移归零，其余日期相对它旋转。
        val referenceRaw = ShiftCycle.rotationOffset(reference, team, table.size) ?: return raw
        return Math.floorMod(raw - referenceRaw, table.size)
    }
}
