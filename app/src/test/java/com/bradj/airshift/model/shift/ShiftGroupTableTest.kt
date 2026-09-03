package com.bradj.airshift.model.shift

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftGroupTableTest {
    private val table = ShiftGroupTable.DEFAULT

    @Test
    fun `every built in name resolves to exactly one group`() {
        val expected = mapOf(
            "甲子" to 1, "甲丑" to 1,
            "己子" to 2, "己丑" to 2,
            "癸子" to 3, "癸丑" to 3, "癸寅" to 3,
            "辛子" to 4, "辛丑" to 4,
            "乙子" to 5, "乙丑" to 5, "乙寅" to 5,
            "庚子" to 6, "庚丑" to 6,
            "丁子明" to 8, "丁丑明" to 8, "丁寅明" to 8,
            "戊子" to 9, "戊丑" to 9,
            "壬子" to 10, "壬丑" to 10, "壬寅" to 10,
            "丙子" to 11, "丙丑" to 11, "丙寅" to 11,
        )
        expected.forEach { (name, group) ->
            assertEquals(name, group, table.findGroupIdForName(name))
        }
        assertEquals(25, expected.size)
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
        assertNull(table.findGroupIdForName("朱"))
        assertNull(table.findGroupIdForName("李"))
        assertNull(table.findGroupIdForName("李华"))
    }

    @Test
    fun `unknown and blank names resolve to nothing`() {
        assertNull(table.findGroupIdForName("张三"))
        assertNull(table.findGroupIdForName(""))
        assertNull(table.findGroupIdForName("   "))
    }

    @Test
    fun `the cycle order covers every group exactly once`() {
        assertEquals(10, table.size)
        assertEquals(table.size, table.cycleOrder.distinct().size)
        assertEquals(table.cycleOrder.toSet(), table.groups.keys)
    }

    @Test
    fun `vacant group numbers are absent`() {
        assertNull(table.cycleIndexOf(7))
        assertNull(table.cycleIndexOf(12))
        assertTrue(table.membersOf(7).isEmpty())
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
    fun `a member absent from the observed sheet keeps the built in group`() {
        // 8.29 起癸子病假，班次行里只剩癸丑和癸寅，但癸子仍应留在组 3。
        val observed = ObservedShiftGroups(
            early = listOf(6, 4, 10),
            mid = listOf(3, 1, 5, 11),
            night = listOf(8, 9, 2),
            members = mapOf(3 to listOf("癸丑", "癸寅")),
        )
        val calibrated = ShiftGroupTable.from(observed)
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
        val calibrated = ShiftGroupTable.from(observed)
        assertEquals(5, calibrated.findGroupIdForName("癸子"))
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
