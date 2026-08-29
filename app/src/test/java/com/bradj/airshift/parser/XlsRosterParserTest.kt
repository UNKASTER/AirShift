package com.bradj.airshift.parser

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

class XlsRosterParserTest {
    @Test
    fun parsesBiffWorkbookWithTemplateVariants() {
        val file = File.createTempFile("airshift-roster-", ".xls")
        try {
            HSSFWorkbook().use { workbook ->
                val sheet = workbook.createSheet("排班")
                sheet.createRow(0).createCell(0).setCellValue("2026/8/26")

                val headers = listOf(
                    "机号",
                    "机型",
                    "进港航班",
                    "前站",
                    "预落",
                    "进港到位照片",
                    "出港航班",
                    "到站",
                    "计离",
                    "送机人员",
                    "出港到位照片",
                )
                val headerRow = sheet.createRow(1)
                headers.forEachIndexed { column, value ->
                    headerRow.createCell(column).setCellValue(value)
                }

                val dataRow = sheet.createRow(2)
                dataRow.createCell(0).setCellValue("B6802")
                dataRow.createCell(1).setCellValue("320")
                dataRow.createCell(2).setCellValue("CES9977#&8")
                dataRow.createCell(3).setCellValue("敦煌")
                dataRow.createCell(4).setCellValue("0945")
                dataRow.createCell(6).setCellValue("CES9977#&8")
                dataRow.createCell(7).setCellValue("合肥")
                dataRow.createCell(8).setCellValue(1040.0)
                dataRow.createCell(9).setCellValue("测试甲 测试乙")

                file.outputStream().use(workbook::write)
            }

            val result = XlsRosterParser.parse(file, "测试甲")

            assertEquals(LocalDate.of(2026, 8, 26), result.rosterDate)
            assertEquals(1, result.assignments.size)
            with(result.assignments.single()) {
                assertEquals("B6802", aircraftRegistration)
                assertEquals("MU9977", inboundFlight)
                assertEquals(LocalDateTime.of(2026, 8, 26, 9, 45), scheduledArrival)
                assertEquals("MU9977", outboundFlight)
                assertEquals(LocalDateTime.of(2026, 8, 26, 10, 40), scheduledDeparture)
            }
            assertTrue(result.warnings.isEmpty())
        } finally {
            file.delete()
        }
    }
}
