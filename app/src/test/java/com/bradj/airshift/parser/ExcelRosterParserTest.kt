package com.bradj.airshift.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExcelRosterParserTest {
    @Test
    fun parsesSemanticColumnsWithInsertedPhotoFieldsAndSharedStrings() {
        val rows = listOf(
            row(1, text("A", "2026/8/26")),
            row(
                2,
                text("A", "机号"),
                text("B", "机型"),
                text("C", "进港航班"),
                text("D", "前站"),
                text("E", "预落"),
                text("F", "进港到位照片"),
                text("G", "出港航班"),
                text("H", "到站"),
                text("I", "计离"),
                text("J", "接送机人员"),
                text("K", "出港到位照片"),
            ),
            row(
                3,
                text("A", "B6802"),
                text("B", "320"),
                text("C", "CES9977#&8"),
                text("D", "敦煌"),
                text("E", "0945"),
                text("G", "CES9977#&8"),
                text("H", "合肥"),
                number("I", "1040"),
                text("J", "测试甲 测试乙"),
            ),
            row(
                4,
                text("A", "B8392"),
                text("B", "320"),
                text("G", "MU6771"),
                text("H", "大连"),
                number("I", "710"),
                text("J", "丁寅明"),
            ),
            row(6, text("L", "VIP信息自查 严禁外泄")),
            row(7, text("L", "CES9977 贵宾")),
            row(8, text("L", "CIP")),
            row(9, text("L", "MU6771 CIP")),
        )

        val result = ExcelRosterParser.parse(
            input = workbook(rows, useSharedStrings = true),
            userName = "测试甲",
        )

        assertEquals(LocalDate.of(2026, 8, 26), result.rosterDate)
        assertEquals(1, result.assignments.size)
        with(result.assignments.single()) {
            assertEquals("B6802", aircraftRegistration)
            assertEquals("MU9977", inboundFlight)
            assertEquals("敦煌", origin)
            assertEquals(LocalDateTime.of(2026, 8, 26, 9, 45), scheduledArrival)
            assertEquals("MU9977", outboundFlight)
            assertEquals("合肥", destination)
            assertEquals(LocalDateTime.of(2026, 8, 26, 10, 40), scheduledDeparture)
            assertTrue(inboundHasVip)
            assertTrue(outboundHasVip)
        }
    }

    @Test
    fun parsesCompactTemplateNumericDateNumericTimeAndNextDayMarker() {
        val rosterDate = LocalDate.of(2026, 8, 23)
        val serialDate = ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 30), rosterDate)
        val rows = listOf(
            row(1, number("J", serialDate.toString())),
            compactHeader(2),
            row(
                3,
                text("A", "B327A"),
                text("B", "32N"),
                text("F", "MU6771&"),
                text("G", "大连"),
                number("H", (430.0 / 1440.0).toString()),
                text("I", "测试甲测试乙"),
            ),
            row(
                4,
                text("A", "B32KM"),
                text("B", "32N"),
                text("C", "MU6813&"),
                text("D", "虹桥"),
                text("E", "0040+"),
                text("I", "测试甲测试乙"),
            ),
        )

        val result = ExcelRosterParser.parse(
            input = workbook(rows, useSharedStrings = false),
            userName = "测试甲",
        )

        assertEquals(rosterDate, result.rosterDate)
        assertEquals(2, result.assignments.size)
        assertEquals(LocalDateTime.of(2026, 8, 23, 7, 10), result.assignments[0].scheduledDeparture)
        assertEquals(LocalDateTime.of(2026, 8, 24, 0, 40), result.assignments[1].scheduledArrival)
    }

    @Test
    fun doesNotTreatANameAsSubstringOfAnotherEmployee() {
        val rows = listOf(
            row(1, text("A", "2026-08-26")),
            compactHeader(2),
            row(
                3,
                text("A", "B8392"),
                text("B", "320"),
                text("F", "MU6771"),
                text("G", "大连"),
                text("H", "0710"),
                text("I", "丁寅明"),
            ),
        )

        val result = ExcelRosterParser.parse(
            input = workbook(rows, useSharedStrings = true),
            userName = "丁寅",
            clock = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneId.of("Asia/Shanghai")),
        )

        assertTrue(result.assignments.isEmpty())
        assertTrue(result.warnings.any { it.contains("没有找到姓名") })
    }

    @Test
    fun rejectsWorkbookWithoutARecognizableRosterHeader() {
        val error = runCatching {
            ExcelRosterParser.parse(
                input = workbook(
                    listOf(
                        row(1, text("A", "机号"), text("B", "进港到位照片"), text("C", "接送机人员")),
                    ),
                    useSharedStrings = false,
                ),
                userName = "测试甲",
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertFalse(error?.message.isNullOrBlank())
    }

    private fun compactHeader(index: Int): RowDef = row(
        index,
        text("A", "机号"),
        text("B", "机型"),
        text("C", "进港航班"),
        text("D", "前站"),
        text("E", "预落"),
        text("F", "出港航班"),
        text("G", "到站"),
        text("H", "计离"),
        text("I", "接送机人员"),
    )

    @Test
    fun readsTheThreeShiftLinesIntoAnOrderedGroupSequence() {
        val result = ExcelRosterParser.parse(
            input = workbook(
                rosterWithExtraRows(
                    row(21, text("L", "候机早班：1甲子 甲丑5乙子 乙丑 乙寅11丙子 丙丑 丙寅")),
                    row(22, text("L", "候机中班：8丁子明 丁丑明 丁寅明9戊子 戊丑2己子 己丑6庚子 庚丑")),
                    row(23, text("L", "候机夜班：4辛子 辛丑10壬子 壬丑3癸子 癸丑 癸寅")),
                ),
                useSharedStrings = false,
            ),
            userName = "辛子",
        )

        val observed = requireNotNull(result.observedShiftGroups)
        assertEquals(listOf(1, 5, 11), observed.early)
        assertEquals(listOf(8, 9, 2, 6), observed.mid)
        assertEquals(listOf(4, 10, 3), observed.night)
        assertEquals(listOf(1, 5, 11, 8, 9, 2, 6, 4, 10, 3), observed.orderedGroupIds)
        assertTrue(observed.isUsable)
        assertEquals(listOf("乙子", "乙丑", "乙寅"), observed.members[5])
        assertEquals(listOf("壬子", "壬丑"), observed.members[10])
    }

    @Test
    fun aSheetWithoutShiftLinesLeavesTheCalendarOnTheBuiltInTable() {
        val result = ExcelRosterParser.parse(
            input = workbook(rosterWithExtraRows(), useSharedStrings = false),
            userName = "辛子",
        )

        assertEquals(1, result.assignments.size)
        assertNull(result.observedShiftGroups)
    }

    @Test
    fun theNeighbouringDutyNotesAreNotMistakenForShiftLines() {
        val result = ExcelRosterParser.parse(
            input = workbook(
                rosterWithExtraRows(
                    row(24, text("L", "夜班拉不开依次留中班  上下班打卡！！！")),
                    row(25, text("L", "病假：癸卯   早班柜台：癸丑 5：30-7:00")),
                    row(26, text("L", "候机主任：甲寅 甲卯 代班：乙卯")),
                ),
                useSharedStrings = false,
            ),
            userName = "辛子",
        )

        assertNull(result.observedShiftGroups)
    }

    @Test
    fun aShiftBlockMissingTheNightLineIsDiscarded() {
        val result = ExcelRosterParser.parse(
            input = workbook(
                rosterWithExtraRows(
                    row(21, text("L", "候机早班：6庚子 庚丑4辛子 辛丑10壬子 壬丑 壬寅")),
                    row(22, text("L", "候机中班：3癸丑 癸寅1甲子 甲丑5乙子 乙丑 乙寅11丙子 丙丑 丙寅")),
                ),
                useSharedStrings = false,
            ),
            userName = "辛子",
        )

        assertNull(result.observedShiftGroups)
    }

    @Test
    fun aGroupShortenedBySickLeaveAndBracketNotesStillParses() {
        val result = ExcelRosterParser.parse(
            input = workbook(
                rosterWithExtraRows(
                    row(22, text("L", "候机早班：6庚子 庚丑4辛子 辛丑（关封）10壬子 壬丑 壬寅")),
                    row(23, text("L", "候机中班：3癸丑 癸寅1甲子 甲丑5乙子 乙丑 乙寅11丙子 丙丑 丙寅")),
                    row(24, text("L", "候机夜班：8丁子明 丁丑明 丁寅明9戊子 戊丑2己子 己丑")),
                ),
                useSharedStrings = false,
            ),
            userName = "辛子",
        )

        val observed = requireNotNull(result.observedShiftGroups)
        assertEquals(listOf(6, 4, 10, 3, 1, 5, 11, 8, 9, 2), observed.orderedGroupIds)
        // 癸子病假，当天组 3 只剩两人；括号备注不进入姓名。
        assertEquals(listOf("癸丑", "癸寅"), observed.members[3])
        assertEquals(listOf("辛子", "辛丑"), observed.members[4])
    }

    @Test
    fun readsSecondTeamShiftLinesWithTrailingNumbersIntoSyntheticGroups() {
        // 二组写法：姓名连写、数字跟在每个小组后面且只是分隔符，夜班叫“候机夜航”。
        val result = ExcelRosterParser.parse(
            input = workbook(
                secondTeamSheet(
                    secondTeamTask(3, "王甲子李乙丑周丙寅"),
                    row(21, text("L", "候机早班：王甲子李乙丑周丙寅4 吴丁卯郑戊辰4 冯己巳陈庚午 4")),
                    row(22, text("L", "候机中班：褚辛未卫壬申5 蒋癸酉沈甲戌韩乙亥5 杨丙子朱丁丑5 秦戊寅尤己卯5")),
                    row(23, text("L", "候机夜航：许庚辰何辛巳4 吕壬午施癸未4 张甲申孔乙酉4")),
                ),
                useSharedStrings = false,
            ),
            userName = "李乙丑",
            clock = secondTeamClock,
        )

        assertEquals(LocalDate.of(2026, 9, 7), result.rosterDate)
        assertEquals(1, result.assignments.size)
        val observed = requireNotNull(result.observedShiftGroups)
        assertTrue(observed.isUsable)
        assertTrue(observed.hasSyntheticIds)
        assertEquals(listOf(1, 2, 3), observed.early)
        assertEquals(listOf(4, 5, 6, 7), observed.mid)
        assertEquals(listOf(8, 9, 10), observed.night)
        assertEquals(listOf("王甲子", "李乙丑", "周丙寅"), observed.members[1])
        assertEquals(listOf("冯己巳", "陈庚午"), observed.members[3])
        assertEquals(listOf("蒋癸酉", "沈甲戌", "韩乙亥"), observed.members[5])
        assertEquals(listOf("张甲申", "孔乙酉"), observed.members[10])
        assertTrue(result.warnings.none { it.contains("核对表格日期") })
    }

    @Test
    fun aThreeCharacterNameIsFoundInAFiveCharacterUnbrokenCell() {
        // 二组两人小组连写只有 5 个字；旧规则要求栏长至少是姓名两倍，会漏掉三字姓名的人。
        val sheet = secondTeamSheet(
            row(3, text("A", "B8395"), text("B", "320"), text("F", "MU9977#&"), text("G", "合肥"), text("H", "1040"), text("I", "王甲李乙丑")),
            row(4, text("A", "B8558"), text("B", "320"), text("F", "MU9835"), text("G", "南京"), text("H", "1045"), text("I", "周丙寅吴丁")),
        )

        val threeCharacters = ExcelRosterParser.parse(workbook(sheet, useSharedStrings = false), "李乙丑", secondTeamClock)
        assertEquals(listOf("MU9977"), threeCharacters.assignments.map { it.outboundFlight })

        val twoCharacters = ExcelRosterParser.parse(workbook(sheet, useSharedStrings = false), "吴丁", secondTeamClock)
        assertEquals(listOf("MU9835"), twoCharacters.assignments.map { it.outboundFlight })

        // 切开后按整名比较：王甲 不是 王甲子。
        val prefixSheet = secondTeamSheet(secondTeamTask(3, "王甲子李乙丑"))
        val prefix = ExcelRosterParser.parse(workbook(prefixSheet, useSharedStrings = false), "王甲", secondTeamClock)
        assertTrue(prefix.assignments.isEmpty())
    }

    @Test
    fun mixedShiftLineStylesAreDiscarded() {
        val result = ExcelRosterParser.parse(
            input = workbook(
                rosterWithExtraRows(
                    row(21, text("L", "候机早班：1甲子 甲丑5乙子 乙丑 乙寅11丙子 丙丑 丙寅")),
                    row(22, text("L", "候机中班：褚辛未卫壬申5 蒋癸酉沈甲戌5")),
                    row(23, text("L", "候机夜班：4辛子 辛丑10壬子 壬丑3癸子 癸丑 癸寅")),
                ),
                useSharedStrings = false,
            ),
            userName = "辛子",
        )

        assertNull(result.observedShiftGroups)
    }

    @Test
    fun theVipBlockHeadedByYaoKeSkipsInlineCipLines() {
        val result = ExcelRosterParser.parse(
            input = workbook(
                secondTeamSheet(
                    row(
                        3,
                        text("A", "B1858"),
                        text("B", "325"),
                        text("C", "MU6807&"),
                        text("D", "虹桥"),
                        text("E", "1100"),
                        text("F", "MU6802&"),
                        text("G", "虹桥"),
                        text("H", "1200"),
                        text("I", "王甲子李乙丑周丙寅"),
                        text("L", "要客：要客信息自查"),
                    ),
                    row(4, text("A", "B8558"), text("B", "320"), text("C", "MU2448"), text("D", "南京"), text("E", "1345"), text("I", "王甲子李乙丑周丙寅")),
                    row(5, text("L", "MU6807 虹桥-兰州              王甲子")),
                    row(6, text("L", "MU2448 南京-兰州 CIP       李乙丑")),
                ),
                useSharedStrings = false,
            ),
            userName = "李乙丑",
            clock = secondTeamClock,
        )

        assertEquals(2, result.assignments.size)
        val transit = result.assignments.single { it.inboundFlight == "MU6807" }
        assertTrue(transit.inboundHasVip)
        assertFalse(transit.outboundHasVip)
        assertFalse(result.assignments.single { it.inboundFlight == "MU2448" }.inboundHasVip)
    }

    @Test
    fun secondTeamShiftLinesOnAFirstTeamWorkdayWarnAboutTheDate() {
        // 8/24 是一组的整班日，却出现二组写法的班次行：多半是表格日期写错，提示但仍解析。
        val result = ExcelRosterParser.parse(
            input = workbook(
                rosterWithExtraRows(
                    row(21, text("L", "候机早班：王甲子李乙丑周丙寅4 吴丁卯郑戊辰4 冯己巳陈庚午 4")),
                    row(22, text("L", "候机中班：褚辛未卫壬申5 蒋癸酉沈甲戌韩乙亥5 杨丙子朱丁丑5 秦戊寅尤己卯5")),
                    row(23, text("L", "候机夜航：许庚辰何辛巳4 吕壬午施癸未4 张甲申孔乙酉4")),
                ),
                useSharedStrings = false,
            ),
            userName = "辛子",
        )

        assertTrue(requireNotNull(result.observedShiftGroups).hasSyntheticIds)
        assertTrue(result.warnings.any { it.contains("核对表格日期") && it.contains("二组") && it.contains("一组") })
    }

    /** “9.7” 这种月日写法按离“今天”最近的一年解释，固定时钟让它落在 2026-09-07。 */
    private val secondTeamClock = Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneId.of("Asia/Shanghai"))

    /** 二组表：日期是表头右侧的 “9.7”，没有到位照片列，多一列到岗情况。 */
    private fun secondTeamSheet(vararg extra: RowDef): List<RowDef> = listOf(
        row(
            2,
            text("A", "机号"),
            text("B", "机型"),
            text("C", "进港航班"),
            text("D", "前站"),
            text("E", "预落"),
            text("F", "出港航班"),
            text("G", "到站"),
            text("H", "计离"),
            text("I", "接送机人员"),
            text("J", "到岗情况"),
            number("L", "9.7"),
        ),
    ) + extra

    private fun secondTeamTask(index: Int, assignees: String): RowDef = row(
        index,
        text("A", "B329P"),
        text("B", "32N"),
        text("F", "MU2130&"),
        text("G", "北京大兴"),
        text("H", "1040"),
        text("I", assignees),
    )

    /** 最小可识别排班（表头 + 一行任务），再拼上表格右侧的附加行。 */
    private fun rosterWithExtraRows(vararg extra: RowDef): List<RowDef> = listOf(
        row(1, text("A", "2026/8/24")),
        row(
            2,
            text("A", "机号"),
            text("B", "机型"),
            text("C", "进港航班"),
            text("D", "前站"),
            text("E", "预落"),
            text("G", "出港航班"),
            text("H", "到站"),
            text("I", "计离"),
            text("J", "接送机人员"),
        ),
        row(
            3,
            text("A", "B6560"),
            text("B", "320"),
            text("G", "MU6771"),
            text("H", "大连"),
            text("I", "0710"),
            text("J", "辛子 辛丑 壬丑"),
        ),
    ) + extra

    private fun workbook(rows: List<RowDef>, useSharedStrings: Boolean): ByteArrayInputStream {
        val textValues = rows.flatMap { row -> row.cells.filterNot(CellDef::numeric).map(CellDef::value) }
        val sharedStringIndexes = if (useSharedStrings) {
            textValues.distinct().withIndex().associate { (index, value) -> value to index }
        } else {
            emptyMap()
        }
        val sheetXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
            rows.forEach { row ->
                append("<row r=\"").append(row.index).append("\">")
                row.cells.forEach { cell ->
                    val reference = "${cell.column}${row.index}"
                    when {
                        cell.numeric -> append("<c r=\"").append(reference).append("\"><v>")
                            .append(escapeXml(cell.value)).append("</v></c>")
                        useSharedStrings -> append("<c r=\"").append(reference).append("\" t=\"s\"><v>")
                            .append(sharedStringIndexes.getValue(cell.value)).append("</v></c>")
                        else -> append("<c r=\"").append(reference).append("\" t=\"inlineStr\"><is><t>")
                            .append(escapeXml(cell.value)).append("</t></is></c>")
                    }
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.writeEntry(
                "[Content_Types].xml",
                "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>",
            )
            zip.writeEntry(
                "xl/workbook.xml",
                "<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><workbookPr date1904=\"0\"/></workbook>",
            )
            if (useSharedStrings) {
                val sharedStrings = buildString {
                    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                    append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                    sharedStringIndexes.entries.sortedBy(Map.Entry<String, Int>::value).forEach { (value, _) ->
                        append("<si><t>").append(escapeXml(value)).append("</t></si>")
                    }
                    append("</sst>")
                }
                zip.writeEntry("xl/sharedStrings.xml", sharedStrings)
            }
            zip.writeEntry("xl/worksheets/sheet1.xml", sheetXml)
        }
        return ByteArrayInputStream(output.toByteArray())
    }

    private fun ZipOutputStream.writeEntry(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun row(index: Int, vararg cells: CellDef): RowDef = RowDef(index, cells.toList())

    private fun text(column: String, value: String): CellDef = CellDef(column, value, numeric = false)

    private fun number(column: String, value: String): CellDef = CellDef(column, value, numeric = true)

    private data class RowDef(val index: Int, val cells: List<CellDef>)

    private data class CellDef(val column: String, val value: String, val numeric: Boolean)
}
