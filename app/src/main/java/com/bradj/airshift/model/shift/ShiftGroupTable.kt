package com.bradj.airshift.model.shift

/** 一个班组：排班表中的组号（二组为合成序号）与成员姓名。 */
data class ShiftGroup(val id: Int, val members: List<String>)

/**
 * 某一大组的班组表与环形轮转顺序。
 *
 * [cycleOrder] 是固定的环形顺序，每个整班工作日整体左移 [ShiftCycle.ROTATION_STEP] 位；
 * 旋转后的位置经 [tierSizes] 映射为早/中/晚槽位。
 *
 * [DEFAULT] 只记录一组由 2026-08-24 至 2026-09-01 六份实测排班表反推的环形顺序与槽位分层，
 * 不含任何成员姓名：仓库公开，真实姓名只能来自用户自己导入的 Excel 班次行（见 [from]）。
 * 二组的小组没有编号，也没有内置表（见 [builtIn]），完全依赖校准。
 */
data class ShiftGroupTable(
    val team: ShiftTeam,
    val cycleOrder: List<Int>,
    val groups: Map<Int, ShiftGroup>,
    val tierSizes: ShiftTierSizes,
) {
    val size: Int get() = cycleOrder.size

    /** 班组在环形顺序中的下标；不在表内返回 null。 */
    fun cycleIndexOf(groupId: Int): Int? = cycleOrder.indexOf(groupId).takeIf { it >= 0 }

    fun membersOf(groupId: Int): List<String> = groups[groupId]?.members.orEmpty()

    /**
     * 界面上的班组称呼。一组按组号叫“第 N 组”；二组的小组没有编号，用首位成员命名，如“李江涛组”，
     * 没有成员名单时才退回组号。
     */
    fun labelOf(groupId: Int): String = when (team) {
        ShiftTeam.FIRST -> "第 $groupId 组"
        ShiftTeam.SECOND -> membersOf(groupId).firstOrNull()?.let { "${it}组" } ?: "第 $groupId 组"
    }

    /**
     * 按姓名查所属班组。去除空白与标点后要求整名精确相等，因此短姓名不会命中更长的姓名；
     * 同名落在多个班组时视为无法判定并返回 null。
     *
     * 只有当整名匹配落空时，才对“切不开的连写成员串”（长度至少 [MIN_UNSPLIT_MEMBER_LENGTH]，
     * 见解析层的姓名切分兜底）按包含关系判断，同样要求唯一。
     */
    fun findGroupIdForName(name: String): Int? {
        val target = normalize(name)
        if (target.isBlank()) return null
        val exact = cycleOrder.filter { id ->
            membersOf(id).any { normalize(it) == target }
        }
        if (exact.isNotEmpty()) return exact.singleOrNull()
        val containing = cycleOrder.filter { id ->
            membersOf(id).any { member ->
                val compact = normalize(member)
                compact.length >= MIN_UNSPLIT_MEMBER_LENGTH && compact.contains(target)
            }
        }
        return containing.singleOrNull()
    }

    companion object {
        private val NAME_NOISE = Regex("[\\s\\p{P}\\p{S}]")

        // 与 ExcelRosterParser 对人员栏的处理一致：括号备注（如“（关封）”）不属于姓名。
        private val PARENTHETICAL_NOTE = Regex("[（(][^）)]*[）)]")

        /** 一个人的姓名最多 4 字；至少 5 字的成员串只可能是没切开的多人连写。 */
        internal const val MIN_UNSPLIT_MEMBER_LENGTH = 5

        internal fun normalize(raw: String): String =
            raw.replace(PARENTHETICAL_NOTE, "").replace(NAME_NOISE, "")

        /** 一组的环形轮转顺序：1 → 5 → 11 → 8 → 9 → 2 → 6 → 4 → 10 → 3 → 回到 1。 */
        val DEFAULT_CYCLE_ORDER = listOf(1, 5, 11, 8, 9, 2, 6, 4, 10, 3)

        /**
         * 一组内置表：10 个在编班组（7、12 号空缺）的环形顺序与 3/4/3 槽位分层，成员名单为空。
         * 校准前 [findGroupIdForName] 恒为 null，排班日历依赖设置中的手动班组。
         */
        val DEFAULT = ShiftGroupTable(
            team = ShiftTeam.FIRST,
            cycleOrder = DEFAULT_CYCLE_ORDER,
            groups = DEFAULT_CYCLE_ORDER.associateWith { id -> ShiftGroup(id, emptyList()) },
            tierSizes = ShiftTierSizes.DEFAULT,
        )

        /**
         * 各大组的内置表。二组的小组没有编号，只有一份样本，因此内置表为空：
         * 校准前日历只能给出上班/交接/休息的日型，班次与班车要等导入一份带班次行的二组表。
         */
        fun builtIn(team: ShiftTeam): ShiftGroupTable = when (team) {
            ShiftTeam.FIRST -> DEFAULT
            ShiftTeam.SECOND -> ShiftGroupTable(
                team = ShiftTeam.SECOND,
                cycleOrder = emptyList(),
                groups = emptyMap(),
                tierSizes = ShiftTierSizes.DEFAULT,
            )
        }

        /**
         * 用一次观测到的分组行覆盖 [base]（默认为一组内置表；结果沿用 [base] 的大组）。
         *
         * 成员按“观测优先、未被任何观测行认领的基表成员保留”合并，这样病假缺席的人不会丢失归属，
         * 而真正换组的人也不会同时留在旧组里。观测行不完整或组号重复时原样返回 [base]。
         */
        fun from(observed: ObservedShiftGroups, base: ShiftGroupTable = DEFAULT): ShiftGroupTable {
            val order = observed.orderedGroupIds
            if (order.size < 2 || order.distinct().size != order.size) return base
            val claimed = observed.members.values.flatten().map(::normalize).toSet()
            val merged = order.associateWith { id ->
                val fresh = observed.members[id].orEmpty()
                val carried = base.membersOf(id).filter { normalize(it) !in claimed }
                ShiftGroup(id, (fresh + carried).distinctBy(::normalize))
            }
            return ShiftGroupTable(
                team = base.team,
                cycleOrder = order,
                groups = merged,
                tierSizes = ShiftTierSizes.of(observed.early.size, observed.mid.size, observed.night.size),
            )
        }
    }
}

/**
 * 从排班表“候机早班/中班/夜班（夜航）”三行原样读出的分组，早→中→晚顺序即当天旋转后的序列。
 *
 * [hasSyntheticIds] 为 true 表示表里的小组没有编号（二组写法），id 是解析时按位次给的 1..N 合成序号，
 * 换一天导入就会变；见 [ShiftCalibration.alignedWith]。
 */
data class ObservedShiftGroups(
    val early: List<Int>,
    val mid: List<Int>,
    val night: List<Int>,
    val members: Map<Int, List<String>>,
    val hasSyntheticIds: Boolean = false,
) {
    val orderedGroupIds: List<Int> get() = early + mid + night

    val isUsable: Boolean
        get() = early.isNotEmpty() && mid.isNotEmpty() && night.isNotEmpty() &&
            orderedGroupIds.distinct().size == orderedGroupIds.size
}
