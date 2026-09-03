package com.bradj.airshift.model.shift

/** 一个班组：排班表中的组号与成员姓名。 */
data class ShiftGroup(val id: Int, val members: List<String>)

/**
 * 班组表与环形轮转顺序。
 *
 * [cycleOrder] 是固定的环形顺序，每个整班工作日整体左移 [ShiftCycle.ROTATION_STEP] 位；
 * 旋转后的位置经 [tierSizes] 映射为早/中/晚槽位。
 *
 * [DEFAULT] 由 2026-08-24 至 2026-09-01 的六份真实排班表反推，作为内置默认值；
 * 导入带“候机早班/中班/夜班”行的 Excel 时会被 [ShiftCalibration] 校正。
 */
data class ShiftGroupTable(
    val cycleOrder: List<Int>,
    val groups: Map<Int, ShiftGroup>,
    val tierSizes: ShiftTierSizes,
) {
    val size: Int get() = cycleOrder.size

    /** 班组在环形顺序中的下标；不在表内返回 null。 */
    fun cycleIndexOf(groupId: Int): Int? = cycleOrder.indexOf(groupId).takeIf { it >= 0 }

    fun membersOf(groupId: Int): List<String> = groups[groupId]?.members.orEmpty()

    /**
     * 按姓名查所属班组。去除空白与标点后要求整名精确相等，因此短姓名不会命中更长的姓名；
     * 同名落在多个班组时视为无法判定并返回 null。
     */
    fun findGroupIdForName(name: String): Int? {
        val target = normalize(name)
        if (target.isBlank()) return null
        val matches = cycleOrder.filter { id ->
            membersOf(id).any { normalize(it) == target }
        }
        return matches.singleOrNull()
    }

    companion object {
        private val NAME_NOISE = Regex("[\\s\\p{P}\\p{S}]")

        // 与 ExcelRosterParser 对人员栏的处理一致：括号备注（如“（关封）”）不属于姓名。
        private val PARENTHETICAL_NOTE = Regex("[（(][^）)]*[）)]")

        internal fun normalize(raw: String): String =
            raw.replace(PARENTHETICAL_NOTE, "").replace(NAME_NOISE, "")

        /** 环形轮转顺序：1 → 5 → 11 → 8 → 9 → 2 → 6 → 4 → 10 → 3 → 回到 1。 */
        val DEFAULT_CYCLE_ORDER = listOf(1, 5, 11, 8, 9, 2, 6, 4, 10, 3)

        private val DEFAULT_MEMBERS = mapOf(
            1 to listOf("甲子", "甲丑"),
            2 to listOf("己子", "己丑"),
            3 to listOf("癸子", "癸丑", "癸寅"),
            4 to listOf("辛子", "辛丑"),
            5 to listOf("乙子", "乙丑", "乙寅"),
            6 to listOf("庚子", "庚丑"),
            8 to listOf("丁子明", "丁丑明", "丁寅明"),
            9 to listOf("戊子", "戊丑"),
            10 to listOf("壬子", "壬丑", "壬寅"),
            11 to listOf("丙子", "丙丑", "丙寅"),
        )

        val DEFAULT = ShiftGroupTable(
            cycleOrder = DEFAULT_CYCLE_ORDER,
            groups = DEFAULT_MEMBERS.mapValues { (id, members) -> ShiftGroup(id, members) },
            tierSizes = ShiftTierSizes.DEFAULT,
        )

        /**
         * 用一次观测到的分组行覆盖内置表。
         *
         * 成员按“观测优先、未被任何观测行认领的内置成员保留”合并，这样病假缺席的人不会丢失归属，
         * 而真正换组的人也不会同时留在旧组里。
         */
        fun from(observed: ObservedShiftGroups): ShiftGroupTable {
            val order = observed.orderedGroupIds
            if (order.size < 2 || order.distinct().size != order.size) return DEFAULT
            val claimed = observed.members.values.flatten().map(::normalize).toSet()
            val merged = order.associateWith { id ->
                val fresh = observed.members[id].orEmpty()
                val carried = DEFAULT_MEMBERS[id].orEmpty().filter { normalize(it) !in claimed }
                ShiftGroup(id, (fresh + carried).distinctBy(::normalize))
            }
            return ShiftGroupTable(
                cycleOrder = order,
                groups = merged,
                tierSizes = ShiftTierSizes.of(observed.early.size, observed.mid.size, observed.night.size),
            )
        }
    }
}

/**
 * 从排班表“候机早班/中班/夜班”三行原样读出的分组，早→中→晚顺序即当天旋转后的序列。
 */
data class ObservedShiftGroups(
    val early: List<Int>,
    val mid: List<Int>,
    val night: List<Int>,
    val members: Map<Int, List<String>>,
) {
    val orderedGroupIds: List<Int> get() = early + mid + night

    val isUsable: Boolean
        get() = early.isNotEmpty() && mid.isNotEmpty() && night.isNotEmpty() &&
            orderedGroupIds.distinct().size == orderedGroupIds.size
}
