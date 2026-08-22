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
}
