package com.bradj.airshift.model.shift

import java.time.LocalDate

/**
 * 一次真实排班表观测，用于校正内置班组表与轮转相位。
 * 只有整班工作日的表格才带“候机早班/中班/夜班”行，因此只有这类日期能作为校准点。
 */
data class ShiftCalibration(
    val date: LocalDate,
    val observed: ObservedShiftGroups,
) {
    val isUsable: Boolean
        get() = observed.isUsable && ShiftCycle.dayKind(date).isFullWorkday
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
 * 无校准数据时使用 [ShiftGroupTable.DEFAULT] 与 [ShiftCycle] 的内置相位；
 * 有校准数据时，以观测当天的真实顺序为相位基准，其余日期相对它旋转。
 */
class ShiftSchedule(calibration: ShiftCalibration? = null) {

    val table: ShiftGroupTable
    private val phaseReference: LocalDate?

    init {
        val usable = calibration?.takeIf { it.isUsable }
        table = usable?.let { ShiftGroupTable.from(it.observed) } ?: ShiftGroupTable.DEFAULT
        phaseReference = usable?.date
    }

    val isCalibrated: Boolean get() = phaseReference != null

    fun findGroupIdForName(name: String): Int? = table.findGroupIdForName(name)

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
        val kind = ShiftCycle.dayKind(date)
        return when {
            kind.isRest -> ShiftDay(date, kind, slot = null, attends = false)
            kind.isFullWorkday -> {
                val slot = slotOnFullWorkday(groupId, date)
                ShiftDay(date, kind, slot = slot, attends = slot != null)
            }
            else -> {
                // 交接班日只上上午，班次沿用前一个整班工作日；那天排到夜班的组干到凌晨，不到岗。
                val source = ShiftCycle.previousFullWorkday(date)
                val slot = source?.let { slotOnFullWorkday(groupId, it) }
                ShiftDay(
                    date = date,
                    kind = kind,
                    slot = slot,
                    attends = slot != null && slot.tier != ShiftTier.NIGHT,
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
        val raw = ShiftCycle.rotationOffset(date, table.size) ?: return null
        val reference = phaseReference ?: return raw
        // 校准点当天的顺序就是观测顺序，故其偏移归零，其余日期相对它旋转。
        val referenceRaw = ShiftCycle.rotationOffset(reference, table.size) ?: return raw
        return Math.floorMod(raw - referenceRaw, table.size)
    }
}
