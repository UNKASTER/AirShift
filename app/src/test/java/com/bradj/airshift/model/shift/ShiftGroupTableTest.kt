package com.bradj.airshift.model.shift

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftGroupTableTest {
    /** 合成姓名（天干地支），与真实人员无关；组 8 用三字名以覆盖前缀不命中的情况。 */
    private val members = mapOf(
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

    /** 与内置表同一环形顺序，但带合成成员，模拟一次 Excel 校准之后的班组表。 */
    private val table = ShiftGroupTable(
        cycleOrder = ShiftGroupTable.DEFAULT_CYCLE_ORDER,
        groups = members.mapValues { (id, names) -> ShiftGroup(id, names) },
        tierSizes = ShiftTierSizes.DEFAULT,
    )

    @Test
    fun `the built in table carries no member names`() {
        // 真实姓名不得进入公开仓库；成员只能来自导入 Excel 的班次行校准。
        assertTrue(ShiftGroupTable.DEFAULT.groups.values.all { it.members.isEmpty() })
        assertNull(ShiftGroupTable.DEFAULT.findGroupIdForName("甲子"))
    }

    @Test
    fun `the built in cycle order covers every group exactly once`() {
        val builtIn = ShiftGroupTable.DEFAULT
        assertEquals(10, builtIn.size)
        assertEquals(builtIn.size, builtIn.cycleOrder.distinct().size)
        assertEquals(builtIn.cycleOrder.toSet(), builtIn.groups.keys)
    }

    @Test
    fun `vacant group numbers are absent`() {
        assertNull(ShiftGroupTable.DEFAULT.cycleIndexOf(7))
        assertNull(ShiftGroupTable.DEFAULT.cycleIndexOf(12))
        assertTrue(ShiftGroupTable.DEFAULT.membersOf(7).isEmpty())
    }

    @Test
    fun `every member resolves to exactly one group`() {
        var checked = 0
        members.forEach { (group, names) ->
            names.forEach { name ->
                assertEquals(name, group, table.findGroupIdForName(name))
                checked++
            }
        }
        assertEquals(25, checked)
    }

    @Test
    fun `no name is claimed by two groups`() {
        val all = table.cycleOrder.flatMap(table::membersOf)
        assertEquals(all.size, all.distinct().size)
    }

    @Test
    fun `surrounding whitespace and punctuation are ignored`() {
        assertEquals(1, table.findGroupIdForName("  甲子 "))
        assertEquals(3, table.findGroupIdForName("癸寅（关封）"))
    }

    @Test
    fun `a partial name does not match a longer colleague`() {
        assertNull(table.findGroupIdForName("甲"))
        assertNull(table.findGroupIdForName("丁寅"))
        assertEquals(8, table.findGroupIdForName("丁寅明"))
    }

    @Test
    fun `unknown and blank names resolve to nothing`() {
        assertNull(table.findGroupIdForName("张三"))
        assertNull(table.findGroupIdForName(""))
        assertNull(table.findGroupIdForName("   "))
    }

    @Test
    fun `an observed sheet becomes the new cycle order`() {
        val observed = ObservedShiftGroups(
            early = listOf(8, 9, 2),
            mid = listOf(6, 4, 10, 3),
            night = listOf(1, 5, 11),
            members = mapOf(3 to listOf("癸丑", "癸寅")),
        )
        val calibrated = ShiftGroupTable.from(observed)
        assertEquals(listOf(8, 9, 2, 6, 4, 10, 3, 1, 5, 11), calibrated.cycleOrder)
        assertEquals(ShiftTierSizes(3, 4, 3), calibrated.tierSizes)
    }

    @Test
    fun `a member absent from the observed sheet keeps the group from the base table`() {
        // 某人病假，班次行里组 3 只剩两人，但此人仍应留在组 3。
        val observed = ObservedShiftGroups(
            early = listOf(6, 4, 10),
            mid = listOf(3, 1, 5, 11),
            night = listOf(8, 9, 2),
            members = mapOf(3 to listOf("癸丑", "癸寅")),
        )
        val calibrated = ShiftGroupTable.from(observed, base = table)
        assertEquals(3, calibrated.findGroupIdForName("癸子"))
        assertTrue(calibrated.membersOf(3).containsAll(listOf("癸丑", "癸寅", "癸子")))
    }

    @Test
    fun `a member who moved groups does not stay in the old one`() {
        val observed = ObservedShiftGroups(
            early = listOf(6, 4, 10),
            mid = listOf(3, 1, 5, 11),
            night = listOf(8, 9, 2),
            members = mapOf(
                3 to listOf("癸丑", "癸寅"),
                5 to listOf("乙子", "乙丑", "乙寅", "癸子"),
            ),
        )
        val calibrated = ShiftGroupTable.from(observed, base = table)
        assertEquals(5, calibrated.findGroupIdForName("癸子"))
    }

    @Test
    fun `without a base table only observed members are known`() {
        val observed = ObservedShiftGroups(
            early = listOf(6, 4, 10),
            mid = listOf(3, 1, 5, 11),
            night = listOf(8, 9, 2),
            members = mapOf(3 to listOf("癸丑", "癸寅")),
        )
        val calibrated = ShiftGroupTable.from(observed)
        assertEquals(listOf("癸丑", "癸寅"), calibrated.membersOf(3))
        assertNull(calibrated.findGroupIdForName("癸子"))
    }

    @Test
    fun `a twelve group sheet yields four slots per tier`() {
        val observed = ObservedShiftGroups(
            early = listOf(1, 5, 11, 7),
            mid = listOf(8, 9, 2, 6),
            night = listOf(4, 10, 3, 12),
            members = emptyMap(),
        )
        val calibrated = ShiftGroupTable.from(observed)
        assertEquals(12, calibrated.size)
        assertEquals(ShiftTierSizes(4, 4, 4), calibrated.tierSizes)
    }

    @Test
    fun `a sheet with duplicate group numbers is rejected`() {
        val observed = ObservedShiftGroups(
            early = listOf(1, 1, 11),
            mid = listOf(8, 9, 2, 6),
            night = listOf(4, 10, 3),
            members = emptyMap(),
        )
        assertEquals(ShiftGroupTable.DEFAULT.cycleOrder, ShiftGroupTable.from(observed).cycleOrder)
        assertEquals(table, ShiftGroupTable.from(observed, base = table))
    }

    @Test
    fun `usability requires all three tiers`() {
        assertTrue(
            ObservedShiftGroups(listOf(1), listOf(2), listOf(3), emptyMap()).isUsable,
        )
        assertTrue(
            !ObservedShiftGroups(listOf(1), emptyList(), listOf(3), emptyMap()).isUsable,
        )
        assertTrue(
            !ObservedShiftGroups(listOf(1), listOf(1), listOf(3), emptyMap()).isUsable,
        )
    }
}
