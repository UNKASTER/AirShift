package com.bradj.airshift

import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.parser.OcrToken
import com.bradj.airshift.parser.RosterTableParser
import com.bradj.airshift.reminder.ReminderPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ProjectSmokeTest {
    @Test
    fun parserFindsOnlyNamedEmployeesRowsAndNormalizesFlights() {
        val headers = listOf("机号", "机型", "进港航班", "前站", "预落", "出港航班", "到站", "计离", "接送机人员")
        val templateCenters = listOf(0.045, 0.125, 0.2265, 0.3435, 0.4315, 0.5545, 0.6815, 0.7505, 0.889)
        fun x(column: Int) = (20 + 600 * templateCenters[column]).toInt()
        fun token(text: String, column: Int, y: Int) = OcrToken(text, x(column) - 10, y - 5, x(column) + 10, y + 5)

        val tokens = buildList {
            add(OcrToken("8.20", 800, 5, 840, 15))
            headers.forEachIndexed { index, header -> add(token(header, index, 30)) }
            listOf("B0001", "32N", "CES1001&", "北京大兴", "1040", "CES1002#", "台北", "1200", "测试甲测试乙")
                .forEachIndexed { index, value -> add(token(value, index, 60)) }
            add(token("B0002", 0, 90))
            add(token("73V", 1, 90))
            add(token("QZ4001", 5, 90))
            add(token("北京大兴", 6, 90))
            add(token("1055", 7, 90))
            add(token("测试甲", 8, 90))
            listOf("B0003", "321", "ZZ2001", "虹桥", "1100", "ZZ2002", "虹桥", "1200", "示例人员")
                .forEachIndexed { index, value -> add(token(value, index, 120)) }
            listOf("B0004", "32N", "ZZ3001", "武汉", "1850", "ZZ3002", "昆明1", "940", "测试丙测试乙测试丁")
                .forEachIndexed { index, value -> add(token(value, index, 150)) }
            listOf("B0005", "32N", "ZZ3001", "武汉", "1850", "ZZ3002", "昆明1", "940", "测试丙测试甲测试丁")
                .forEachIndexed { index, value -> add(token(value, index, 180)) }
        }

        val clock = Clock.fixed(Instant.parse("2026-08-22T00:00:00Z"), ZoneId.of("Asia/Shanghai"))
        val result = RosterTableParser.parse(tokens, 1000, "测试甲", clock)

        assertEquals(LocalDate.of(2026, 8, 20), result.rosterDate)
        assertEquals(3, result.assignments.size)
        assertEquals("MU1001", result.assignments[0].inboundFlight)
        assertEquals("MU1002", result.assignments[0].outboundFlight)
        assertNull(result.assignments[1].inboundFlight)
        assertEquals("QZ4001", result.assignments[1].outboundFlight)
        assertTrue(result.assignments.none { it.aircraftRegistration == "B0004" })
        assertEquals("ZZ3001", result.assignments[2].inboundFlight)
        assertEquals("昆明", result.assignments[2].destination)
        assertEquals(LocalDateTime.of(2026, 8, 20, 19, 40), result.assignments[2].scheduledDeparture)
    }

    @Test
    fun parserUsesRegistrationRowsAndDoesNotCarryCrewAcrossRows() {
        val headers = listOf("机号", "机型", "进港航班", "前站", "预落", "出港航班", "到站", "计离", "接送机人员")
        val centers = listOf(100, 185, 300, 410, 500, 800, 900, 980, 1160)
        fun token(text: String, column: Int, y: Int) =
            OcrToken(text, centers[column] - 12, y - 5, centers[column] + 12, y + 5)

        val tokens = buildList {
            add(OcrToken("2026/8/26", 620, 5, 700, 15))
            headers.forEachIndexed { index, header -> add(token(header, index, 30)) }

            add(OcrToken("3", 64, 55, 76, 65))
            listOf("B8392", "320", "", "", "", "MU6771", "大连", "0710")
                .forEachIndexed { index, value -> if (value.isNotEmpty()) add(token(value, index, 60)) }
            add(token("戊子 戊丑 丁寅明", 8, 66))

            listOf("B6880", "320", "", "", "", "MU6199", "哈尔滨", "0825", "戊子 戊丑")
                .forEachIndexed { index, value -> if (value.isNotEmpty()) add(token(value, index, 90)) }
            listOf("B1609", "320", "", "", "", "MU9831", "南昌", "0830", "己子 己丑")
                .forEachIndexed { index, value -> if (value.isNotEmpty()) add(token(value, index, 120)) }
        }

        val result = RosterTableParser.parse(tokens, 1400, "丁寅明")

        assertEquals(1, result.assignments.size)
        assertEquals("B8392", result.assignments.single().aircraftRegistration)
        assertEquals("MU6771", result.assignments.single().outboundFlight)
    }

    @Test
    fun plusTimeMovesToNextDay() {
        assertEquals(
            LocalDateTime.of(2026, 8, 21, 0, 30),
            RosterTableParser.parseRosterTime("0030+", LocalDate.of(2026, 8, 20)),
        )
    }

    @Test
    fun parserMarksAssignedVipFlightFromHorizontalSection() {
        val tokens = baseRosterTokens() + listOf(
            OcrToken("VIP信息", 640, 20, 700, 40),
            OcrToken("早班", 730, 20, 770, 40),
            OcrToken("中班", 820, 20, 860, 40),
            OcrToken("晚班", 910, 20, 950, 40),
            OcrToken("ZZ1002 贵宾", 630, 55, 705, 70),
            OcrToken("测试甲 测试乙", 715, 55, 790, 70),
            OcrToken("测试丙", 815, 55, 865, 70),
            OcrToken("测试丁", 905, 55, 955, 70),
        )

        val result = RosterTableParser.parse(tokens, 1000, "测试甲")

        assertFalse(result.assignments.single().inboundHasVip)
        assertTrue(result.assignments.single().outboundHasVip)
    }

    @Test
    fun parserMarksAssignedVipFlightFromMultipleVerticalRows() {
        val tokens = baseRosterTokens() + listOf(
            OcrToken("VIP信息自查严禁外泄", 700, 20, 900, 35),
            OcrToken("ZZ9999 其他航班", 700, 42, 850, 57),
            OcrToken("ZZ1001 贵宾", 700, 64, 820, 79),
            OcrToken("CIP", 700, 86, 740, 101),
            OcrToken("早班", 700, 120, 750, 135),
        )

        val result = RosterTableParser.parse(tokens, 1000, "测试甲")

        assertTrue(result.assignments.single().inboundHasVip)
        assertFalse(result.assignments.single().outboundHasVip)
    }

    @Test
    fun parserDoesNotMarkUnassignedVipFlights() {
        val tokens = baseRosterTokens() + listOf(
            OcrToken("VIP信息自查严禁外泄", 700, 200, 900, 218),
            OcrToken("ZZ9001 其他航班", 700, 230, 850, 248),
        )

        val result = RosterTableParser.parse(tokens, 1000, "测试甲")

        assertFalse(result.assignments.single().hasVip)
    }

    @Test
    fun turnaroundOnlyGetsArrivalReminder() {
        val assignment = assignment(
            inbound = "ZZ1001",
            outbound = "ZZ1002",
            arrival = LocalDateTime.of(2026, 8, 20, 12, 0),
            departure = LocalDateTime.of(2026, 8, 20, 13, 0),
        )
        val reminder = ReminderPolicy.create(assignment)
        assertEquals(LocalDateTime.of(2026, 8, 20, 11, 45), reminder?.triggerAt)
        assertEquals("ZZ1001 即将进港", reminder?.title)
    }

    @Test
    fun departureOnlyGetsSeventyMinuteReminder() {
        val assignment = assignment(
            inbound = null,
            outbound = "QZ4001",
            arrival = null,
            departure = LocalDateTime.of(2026, 8, 20, 12, 0),
        )
        assertEquals(
            LocalDateTime.of(2026, 8, 20, 10, 50),
            ReminderPolicy.create(assignment)?.triggerAt,
        )
    }

    @Test
    fun stableIdCanBeCreatedForReminderScheduling() {
        val assignment = assignment(
            inbound = "ZZ1001",
            outbound = null,
            arrival = LocalDateTime.of(2026, 8, 20, 12, 0),
            departure = null,
        )

        assertEquals("B0001-ZZ1001--2026-08-20", assignment.stableId)
    }

    private fun assignment(
        inbound: String?,
        outbound: String?,
        arrival: LocalDateTime?,
        departure: LocalDateTime?,
    ) = RosterAssignment(
        aircraftRegistration = "B0001",
        aircraftType = "320",
        inboundFlight = inbound,
        origin = "北京",
        scheduledArrival = arrival,
        outboundFlight = outbound,
        destination = "上海",
        scheduledDeparture = departure,
        assignees = "测试甲",
    )

    private fun baseRosterTokens(): List<OcrToken> {
        val headers = listOf("机号", "机型", "进港航班", "前站", "预落", "出港航班", "到站", "计离", "接送机人员")
        val templateCenters = listOf(0.045, 0.125, 0.2265, 0.3435, 0.4315, 0.5545, 0.6815, 0.7505, 0.889)
        fun x(column: Int) = (20 + 600 * templateCenters[column]).toInt()
        fun token(text: String, column: Int, y: Int) = OcrToken(text, x(column) - 10, y - 5, x(column) + 10, y + 5)
        return buildList {
            add(OcrToken("8.20", 800, 5, 840, 15))
            headers.forEachIndexed { index, header -> add(token(header, index, 30)) }
            listOf("B0001", "32N", "ZZ1001", "北京大兴", "1040", "ZZ1002", "台北", "1200", "测试甲")
                .forEachIndexed { index, value -> add(token(value, index, 60)) }
        }
    }
}
