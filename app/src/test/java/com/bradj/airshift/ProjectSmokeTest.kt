package com.bradj.airshift

import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.parser.OcrToken
import com.bradj.airshift.parser.RosterTableParser
import com.bradj.airshift.reminder.ReminderPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ProjectSmokeTest {
    @Test
    fun parserFindsOnlyNamedEmployeesRowsAndRemovesSymbols() {
        val headers = listOf("机号", "机型", "进港航班", "前站", "预落", "出港航班", "到站", "计离", "接送机人员")
        val templateCenters = listOf(0.045, 0.125, 0.2265, 0.3435, 0.4315, 0.5545, 0.6815, 0.7505, 0.889)
        fun x(column: Int) = (20 + 600 * templateCenters[column]).toInt()
        fun token(text: String, column: Int, y: Int) = OcrToken(text, x(column) - 10, y - 5, x(column) + 10, y + 5)

        val tokens = buildList {
            add(OcrToken("8.20", 800, 5, 840, 15))
            headers.forEachIndexed { index, header -> add(token(header, index, 30)) }
            listOf("B0001", "32N", "ZZ1001&", "北京大兴", "1040", "ZZ1002#", "台北", "1200", "测试甲测试乙")
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
        }

        val clock = Clock.fixed(Instant.parse("2026-08-22T00:00:00Z"), ZoneId.of("Asia/Shanghai"))
        val result = RosterTableParser.parse(tokens, 1000, "测试甲", clock)

        assertEquals(LocalDate.of(2026, 8, 20), result.rosterDate)
        assertEquals(3, result.assignments.size)
        assertEquals("ZZ1001", result.assignments[0].inboundFlight)
        assertEquals("ZZ1002", result.assignments[0].outboundFlight)
        assertNull(result.assignments[1].inboundFlight)
        assertEquals("QZ4001", result.assignments[1].outboundFlight)
        assertEquals("ZZ3001", result.assignments[2].inboundFlight)
        assertEquals("昆明", result.assignments[2].destination)
        assertEquals(LocalDateTime.of(2026, 8, 20, 19, 40), result.assignments[2].scheduledDeparture)
    }

    @Test
    fun plusTimeMovesToNextDay() {
        assertEquals(
            LocalDateTime.of(2026, 8, 21, 0, 30),
            RosterTableParser.parseRosterTime("0030+", LocalDate.of(2026, 8, 20)),
        )
    }

    @Test
    fun parserReadsHorizontalRightSideSectionsByHeaderAnchors() {
        val tokens = baseRosterTokens() + listOf(
            OcrToken("要客信息", 640, 20, 700, 40),
            OcrToken("早班", 730, 20, 770, 40),
            OcrToken("中班", 820, 20, 860, 40),
            OcrToken("晚班", 910, 20, 950, 40),
            OcrToken("VIP01 张先生", 630, 55, 705, 70),
            OcrToken("测试甲 测试乙", 715, 55, 790, 70),
            OcrToken("测试丙", 815, 55, 865, 70),
            OcrToken("测试丁", 905, 55, 955, 70),
        )

        val result = RosterTableParser.parse(tokens, 1000, "测试甲")

        assertEquals(listOf("VIP01 张先生"), result.supplement.vipInfo)
        assertEquals(listOf("测试甲 测试乙"), result.supplement.earlyShift)
        assertEquals(listOf("测试丙"), result.supplement.middleShift)
        assertEquals(listOf("测试丁"), result.supplement.lateShift)
    }

    @Test
    fun parserReadsVerticalRightSideSectionsByHeaderAnchors() {
        val tokens = baseRosterTokens() + listOf(
            OcrToken("要客信息", 700, 20, 770, 35),
            OcrToken("VIP02 李女士", 700, 42, 790, 57),
            OcrToken("早班", 700, 70, 750, 85),
            OcrToken("测试甲", 700, 92, 760, 107),
            OcrToken("中班", 700, 120, 750, 135),
            OcrToken("测试乙", 700, 142, 760, 157),
            OcrToken("晚班", 700, 170, 750, 185),
            OcrToken("测试丙", 700, 192, 760, 207),
        )

        val result = RosterTableParser.parse(tokens, 1000, "测试甲")

        assertEquals(listOf("VIP02 李女士"), result.supplement.vipInfo)
        assertEquals(listOf("测试甲"), result.supplement.earlyShift)
        assertEquals(listOf("测试乙"), result.supplement.middleShift)
        assertEquals(listOf("测试丙"), result.supplement.lateShift)
    }

    @Test
    fun parserReadsActualRightSideLabelsWithoutIncludingUnrelatedRows() {
        val tokens = baseRosterTokens() + listOf(
            OcrToken("要客：今日暂无要客", 700, 40, 880, 58),
            OcrToken("候机室卫生：李江涛 万兆丹 周兴佳", 700, 110, 980, 128),
            OcrToken("整理单据 对讲机充电 桌面、地面卫生", 700, 135, 1000, 153),
            OcrToken("候机早班：早班甲 早班乙4", 700, 210, 900, 228),
            OcrToken("候机中班：中班甲 中班乙5", 700, 235, 900, 253),
            OcrToken("候机夜航：夜航甲 夜航乙4", 700, 260, 900, 278),
            OcrToken("值班主任：主任甲 主任乙", 700, 350, 900, 368),
            OcrToken("病假：病假甲", 700, 420, 820, 438),
        )

        val result = RosterTableParser.parse(tokens, 1000, "测试甲")

        assertEquals(listOf("今日暂无要客"), result.supplement.vipInfo)
        assertEquals(listOf("早班甲早班乙4"), result.supplement.earlyShift)
        assertEquals(listOf("中班甲中班乙5"), result.supplement.middleShift)
        assertEquals(listOf("夜航甲夜航乙4"), result.supplement.lateShift)
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
        assertEquals(LocalDateTime.of(2026, 8, 20, 11, 50), reminder?.triggerAt)
        assertEquals("ZZ1001 即将进港", reminder?.title)
    }

    @Test
    fun departureOnlyGetsOneHourReminder() {
        val assignment = assignment(
            inbound = null,
            outbound = "QZ4001",
            arrival = null,
            departure = LocalDateTime.of(2026, 8, 20, 12, 0),
        )
        assertEquals(
            LocalDateTime.of(2026, 8, 20, 11, 0),
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
