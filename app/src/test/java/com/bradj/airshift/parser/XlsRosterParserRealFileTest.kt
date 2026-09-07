package com.bradj.airshift.parser

import com.bradj.airshift.model.shift.ShiftCalibration
import com.bradj.airshift.model.shift.ShiftCycle
import com.bradj.airshift.model.shift.ShiftSchedule
import com.bradj.airshift.model.shift.ShiftTeam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 真实 `.xls` 排班表回归。仓库不存放这些文件（含真实个人信息），
 * 需同时配置 `AIRSHIFT_XLS_FIXTURES_DIR` 与 `AIRSHIFT_XLS_TEST_NAME` 才会运行，否则跳过。
 */
class XlsRosterParserRealFileTest {
    private val expectedDates = mapOf(
        "mx_security_decrypted_prefix_8.24.xls" to LocalDate.of(2026, 8, 24),
        "mx_security_decrypted_prefix_8.25.xls" to LocalDate.of(2026, 8, 25),
        "mx_security_decrypted_prefix_8.29.xls" to LocalDate.of(2026, 8, 29),
        "mx_security_decrypted_prefix_8.30.xls" to LocalDate.of(2026, 8, 30),
        "mx_security_decrypted_prefix_8.31.xls" to LocalDate.of(2026, 8, 31),
        "mx_security_decrypted_prefix_9.1.xls" to LocalDate.of(2026, 9, 1),
    )

    private val clock = Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneId.of("Asia/Shanghai"))

    private fun fixtures(): Pair<File, String>? {
        val directory = System.getenv("AIRSHIFT_XLS_FIXTURES_DIR")?.let(::File)
        val testName = System.getenv("AIRSHIFT_XLS_TEST_NAME")
        if (directory?.isDirectory != true || testName.isNullOrBlank()) return null
        return directory to testName
    }

    @Test
    fun parsesConfiguredRealRosterFixtures() {
        val configured = fixtures()
        assumeTrue(configured != null)
        val (directory, testName) = configured!!

        expectedDates.forEach { (fileName, expectedDate) ->
            val file = directory.resolve(fileName)
            assertTrue("缺少测试文件：$fileName", file.isFile)
            val result = XlsRosterParser.parse(file, testName, clock)
            assertEquals(fileName, expectedDate, result.rosterDate)
            assertTrue("$fileName 未提取到指定人员的任务", result.assignments.isNotEmpty())
        }
    }

    /**
     * 排班日历算法的端到端判据：每份整班工作日表格右侧的“候机早班/中班/夜班”行，
     * 必须与 [ShiftSchedule] 纯计算出的班组顺序逐位一致。
     */
    @Test
    fun theBuiltInRotationMatchesEveryRealShiftLine() {
        val configured = fixtures()
        assumeTrue(configured != null)
        val (directory, testName) = configured!!
        val schedule = ShiftSchedule()
        var checked = 0

        expectedDates.forEach { (fileName, date) ->
            val file = directory.resolve(fileName)
            assertTrue("缺少测试文件：$fileName", file.isFile)
            val observed = XlsRosterParser.parse(file, testName, clock).observedShiftGroups
            if (!ShiftCycle.dayKind(date, ShiftTeam.FIRST).isFullWorkday) {
                // 交接班日的半天表格没有班次行。
                assertEquals("$fileName 不应含班次行", null, observed)
                return@forEach
            }
            val groups = requireNotNull(observed) { "$fileName 未解析到班次行" }
            assertTrue("$fileName 的班次行不完整", groups.isUsable)
            assertTrue("$fileName 带真实组号", !groups.hasSyntheticIds)
            assertEquals("$fileName 的班组顺序", groups.orderedGroupIds, schedule.orderFor(date))
            // 用该表自校正后，其余日期的顺序不应改变。
            val calibrated = ShiftSchedule(ShiftCalibration(date, groups))
            assertEquals(ShiftTeam.FIRST, calibrated.team)
            expectedDates.values.filter { ShiftCycle.dayKind(it, ShiftTeam.FIRST).isFullWorkday }.forEach { other ->
                assertEquals(
                    "以 $fileName 校正后 $other 的顺序",
                    schedule.orderFor(other),
                    calibrated.orderFor(other),
                )
            }
            checked++
        }

        assertTrue("没有任何整班工作日表格参与校验", checked > 0)
    }

    /**
     * 二组真实表（`9.7.xls`，2026-09-07 接班日）：小组没有编号、姓名连写、夜班写作“候机夜航”。
     * 夹具目录里没有该文件时跳过。
     */
    @Test
    fun parsesTheSecondTeamSheetIntoSyntheticGroups() {
        val configured = fixtures()
        assumeTrue(configured != null)
        val (directory, testName) = configured!!
        val file = directory.resolve("9.7.xls")
        assumeTrue(file.isFile)

        val result = XlsRosterParser.parse(file, testName, clock)
        assertEquals(LocalDate.of(2026, 9, 7), result.rosterDate)
        val observed = requireNotNull(result.observedShiftGroups) { "9.7.xls 未解析到班次行" }
        assertTrue(observed.isUsable)
        assertTrue(observed.hasSyntheticIds)
        assertEquals(listOf(3, 4, 3), listOf(observed.early.size, observed.mid.size, observed.night.size))
        assertEquals((1..10).toList(), observed.orderedGroupIds)
        observed.orderedGroupIds.forEach { id ->
            val members = observed.members[id].orEmpty()
            assertTrue("组 $id 没有成员", members.isNotEmpty())
            assertTrue("组 $id 成员未切开：$members", members.all { it.length in 2..4 })
        }

        val calibration = ShiftCalibration(result.rosterDate, observed)
        assertEquals(ShiftTeam.SECOND, calibration.team)
        val schedule = ShiftSchedule(calibration)
        assertEquals(ShiftTeam.SECOND, schedule.team)
        assertEquals(observed.orderedGroupIds, schedule.orderFor(LocalDate.of(2026, 9, 7)))
        assertTrue(schedule.labelOf(1).endsWith("组"))
    }
}
