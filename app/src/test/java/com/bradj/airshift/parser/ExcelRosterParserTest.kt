package com.bradj.airshift.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            userName = "张宇",
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
