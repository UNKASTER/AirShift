package com.bradj.airshift.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class XlsRosterParserRealFileTest {
    @Test
    fun parsesConfiguredRealRosterFixtures() {
        val fixtureDirectory = System.getenv("AIRSHIFT_XLS_FIXTURES_DIR")?.let(::File)
        val testName = System.getenv("AIRSHIFT_XLS_TEST_NAME")
        assumeTrue(fixtureDirectory?.isDirectory == true && !testName.isNullOrBlank())
        val configuredDirectory = requireNotNull(fixtureDirectory)

        val expectedDates = mapOf(
            "mx_security_decrypted_prefix_8.23.xls" to LocalDate.of(2026, 8, 23),
            "mx_security_decrypted_prefix_8.24.xls" to LocalDate.of(2026, 8, 24),
            "mx_security_decrypted_prefix_8.25.xls" to LocalDate.of(2026, 8, 25),
            "mx_security_decrypted_prefix_8.26.xls" to LocalDate.of(2026, 8, 26),
        )
        val clock = Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneId.of("Asia/Shanghai"))

        expectedDates.forEach { (fileName, expectedDate) ->
            val file = configuredDirectory.resolve(fileName)
            assertTrue("缺少测试文件：$fileName", file.isFile)
            val result = XlsRosterParser.parse(file, testName.orEmpty(), clock)
            assertEquals(fileName, expectedDate, result.rosterDate)
            assertTrue("$fileName 未提取到指定人员的任务", result.assignments.isNotEmpty())
        }
    }
}
