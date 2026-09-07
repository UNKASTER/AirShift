package com.bradj.airshift.parser

import com.bradj.airshift.model.shift.LearnedSlotTimes
import com.bradj.airshift.model.shift.ShiftBusPlan
import com.bradj.airshift.model.shift.ShiftCalibration
import com.bradj.airshift.model.shift.ShiftClock
import com.bradj.airshift.model.shift.ShiftCycle
import com.bradj.airshift.model.shift.ShiftDayKind
import com.bradj.airshift.model.shift.ShiftSchedule
import com.bradj.airshift.model.shift.ShiftSlot
import com.bradj.airshift.model.shift.ShiftTeam
import com.bradj.airshift.model.shift.ShiftTier
import com.bradj.airshift.model.shift.ShiftTimeBucket
import com.bradj.airshift.model.shift.ShiftTimeHistory
import com.bradj.airshift.model.shift.ShiftTimeObservation
import com.bradj.airshift.model.shift.ShiftTimeObserver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

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
            assertTrue("$fileName 没有任务行", result.staffAssignments.isNotEmpty())
            // 配置的姓名可能属于另一大组（用户 0.14 时在二组，这六份是一组表）：只在表上有此人时要求提取到任务。
            val onSheet = result.staffAssignments.any { ExcelRosterParser.containsAssignee(it.assignees, testName) }
            assertEquals("$fileName 指定人员的任务", onSheet, result.assignments.isNotEmpty())
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
     * 实测自学习的端到端判据：内置表就是由这六份表人工归纳的，因此整表按班组成员归组读出的各槽位到位时间，
     * 与内置表相差超过 30 分钟的观测不得超过一成（实跑 57 条中 50 条完全一致、5 条差 10–15 分钟、2 条离群）；
     * 四份整班日表聚合后的中位数与内置到位时间相差不超过 15 分钟（实跑 10 个槽位全部为 0），且按实测推荐的班车不会迟到。
     * 逐槽位对照表打印到测试输出，供人工核对。
     */
    @Test
    fun theGroupLevelObservationsReproduceTheBuiltInTables() {
        val configured = fixtures()
        assumeTrue(configured != null)
        val (directory, testName) = configured!!

        val observations = observeRealSheets(directory, testName)
        val learned = ShiftTimeHistory().record(observations).learnedTimes(ShiftTeam.FIRST)
        assertEquals(expectedDates.size, learned.dateCount)
        val fullDay = learned.entries.filterKeys { (bucket, _) -> bucket == ShiftTimeBucket.FULL_DAY }
        assertEquals("整班档应有 10 个槽位攒够样本", 10, fullDay.size)
        fullDay.forEach { (key, slotTimes) -> println(describeFullDay(key.second, slotTimes)) }
        fullDay.forEach { (key, slotTimes) -> assertFullDaySlotMatchesTheBuiltInTable(key.second, slotTimes) }
        // 接班日与交接班日各只有一份表，不够 3 次，仍走内置表。
        assertNull(learned[ShiftDayKind.WORK_FIRST, ShiftSlot(ShiftTier.EARLY, 1)])
        assertNull(learned[ShiftDayKind.HANDOVER, ShiftSlot(ShiftTier.EARLY, 1)])
    }

    /**
     * 按日期顺序过六份表，像 DutyViewModel.finishImport 一样把校准向前带；先把逐槽位对照表整张打印出来，再逐条断言，
     * 这样一条失败也能看到全貌。对照的是到位时间而不是首任务时间：观测按最早到位的行取方向，
     * 一个 13:30 的纯出港（提前 70 分钟）会比 12:50 的进港（提前 15 分钟）更早到位，这正是班车该看的口径。
     */
    private fun observeRealSheets(directory: File, testName: String): List<ShiftTimeObservation> {
        var calibration: ShiftCalibration? = null
        val observations = mutableListOf<Pair<String, ShiftTimeObservation>>()
        val report = StringBuilder("date       kind         slot 实测(向)     内置(向)     Δ到位  末项        Δ下班\n")
        expectedDates.entries.sortedBy { it.value }.forEach { (fileName, date) ->
            val file = directory.resolve(fileName)
            assertTrue("缺少测试文件：$fileName", file.isFile)
            val result = XlsRosterParser.parse(file, testName, clock)
            assertTrue("$fileName 未识别到日期", result.rosterDateRecognized)
            result.observedShiftGroups?.let { calibration = ShiftCalibration(date, it).alignedWith(calibration) }
            val schedule = ShiftSchedule(calibration)
            assertTrue("$fileName 之前没有可用的校准表", schedule.isCalibrated)
            val observed = ShiftTimeObserver.observeGroups(
                schedule,
                date,
                result.staffAssignments,
                ExcelRosterParser::containsAssignee,
            )
            val kind = ShiftCycle.dayKind(date, ShiftTeam.FIRST)
            assertEquals("$fileName 的实测条数", if (kind.isFullWorkday) 10 else 7, observed.size)
            assertEquals("$fileName 的槽位应各不相同", observed.size, observed.map { it.slot }.distinct().size)
            observed.forEach { report.append(describe(it)) }
            observations += observed.map { fileName to it }
        }
        println(report)
        // 单日离群是真实存在的（如 08-25 中三 有一行 07:10 的出港），靠中位数压住；这里只要求离群不超过一成。
        val outliers = observations.filter { (_, observation) -> abs(reportByDelta(observation)) > 30 }
        outliers.forEach { (fileName, observation) ->
            println("离群：$fileName ${observation.slot.label} Δ到位 ${reportByDelta(observation)} 分钟")
        }
        assertTrue(
            "到位时间偏离内置表超过 30 分钟的观测过多：${outliers.size}/${observations.size}",
            outliers.size <= observations.size / 10,
        )
        return observations.map { it.second }
    }

    private fun reportByDelta(observation: ShiftTimeObservation): Int {
        val builtIn = requireNotNull(ShiftBusPlan.expectedFirstTask(observation.kind, observation.slot))
        return observation.reportByMinutes - ShiftBusPlan.reportByMinutes(builtIn)
    }

    private fun direction(inbound: Boolean): String = if (inbound) "进" else "出"

    private fun describe(observation: ShiftTimeObservation): String {
        val builtIn = requireNotNull(ShiftBusPlan.expectedFirstTask(observation.kind, observation.slot))
        val builtInOff = ShiftBusPlan.expectedOffDutyMinutes(observation.kind, observation.slot)
        return "%s %-12s %-4s %s(%s)  %s(%s)  %+5d  %-11s %s\n".format(
            observation.date,
            observation.kind,
            observation.slot.label,
            ShiftClock.format(observation.firstTaskMinutes),
            direction(observation.inbound),
            ShiftClock.format(builtIn.minutes),
            direction(builtIn.inbound),
            reportByDelta(observation),
            ShiftClock.format(observation.lastTaskMinutes),
            builtInOff?.let { "%+d".format(observation.lastTaskMinutes - it) } ?: "-",
        )
    }

    private fun describeFullDay(slot: ShiftSlot, slotTimes: LearnedSlotTimes): String {
        val builtIn = requireNotNull(ShiftBusPlan.expectedFirstTask(ShiftDayKind.WORK_SECOND, slot))
        val deltaReportBy = slotTimes.reportByMinutes - ShiftBusPlan.reportByMinutes(builtIn)
        val offDuty = slotTimes.offDutyMinutes?.let(ShiftClock::format)
        val learnedAt = "${ShiftClock.format(slotTimes.reportByMinutes)}(${direction(slotTimes.firstTask.inbound)})"
        val builtInAt = "${ShiftClock.format(ShiftBusPlan.reportByMinutes(builtIn))}(${direction(builtIn.inbound)})"
        return "FULL_DAY ${slot.label}: 样本 ${slotTimes.sampleCount}，到位 $learnedAt，内置 $builtInAt，" +
            "相差 $deltaReportBy 分钟，下班 $offDuty"
    }

    private fun assertFullDaySlotMatchesTheBuiltInTable(slot: ShiftSlot, slotTimes: LearnedSlotTimes) {
        val builtIn = requireNotNull(ShiftBusPlan.expectedFirstTask(ShiftDayKind.WORK_SECOND, slot))
        val deltaReportBy = slotTimes.reportByMinutes - ShiftBusPlan.reportByMinutes(builtIn)
        assertTrue("整班 ${slot.label} 的到位中位数偏离内置表 $deltaReportBy 分钟", abs(deltaReportBy) <= 15)
        ShiftBusPlan.REPORT_MARGIN_OPTIONS.forEach { margin ->
            val bus =
                ShiftBusPlan.recommend(ShiftDayKind.WORK_SECOND, slot, marginMinutes = margin, learned = slotTimes)
            assertTrue("整班 ${slot.label} 余量 $margin 没有班车", bus != null)
            assertTrue("整班 ${slot.label} 余量 $margin 迟到", bus!!.spareMinutes >= 0 || bus.isFixedByRule)
        }
    }

    /** 二组表按成员归组也能得到接班日全部 10 个槽位的实测；只打印，不参与内置表对照。 */
    @Test
    fun theSecondTeamSheetYieldsAnObservationPerGroup() {
        val configured = fixtures()
        assumeTrue(configured != null)
        val (directory, testName) = configured!!
        val file = directory.resolve("9.7.xls")
        assumeTrue(file.isFile)

        val result = XlsRosterParser.parse(file, testName, clock)
        val calibration = ShiftCalibration(result.rosterDate, requireNotNull(result.observedShiftGroups))
        val observed = ShiftTimeObserver.observeGroups(
            ShiftSchedule(calibration),
            result.rosterDate,
            result.staffAssignments,
            ExcelRosterParser::containsAssignee,
        )
        observed.forEach {
            println("9.7 ${it.slot.label} ${ShiftClock.format(it.firstTaskMinutes)}(${if (it.inbound) "进" else "出"}) " +
                ShiftClock.format(it.lastTaskMinutes))
        }
        assertEquals(10, observed.size)
        assertTrue(observed.all { it.team == ShiftTeam.SECOND && it.kind == ShiftDayKind.WORK_FIRST })
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
