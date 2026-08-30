package com.bradj.airshift.api

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class VariFlightPayloadParserTest {
    @Test
    fun parsesTheFieldsUsedByTheAndroidApp() {
        val payload = """
            [{'FlightNo': 'ZZ1001',
              'FlightDepcode': 'LHW', 'FlightDepAirport': '兰州中川',
              'DepAirportLat': 36.51, 'DepAirportLon': 103.62,
              'FlightArrcode': 'PKX', 'FlightArrAirport': '北京大兴',
              'ArrAirportLat': 39.51, 'ArrAirportLon': 116.41,
              'FlightDeptimePlanDate': datetime.datetime(2026, 8, 20, 10, 40),
              'VeryZhunReadyDeptimeDate': datetime.datetime(2026, 8, 20, 10, 47),
              'FlightDeptimeDate': datetime.datetime(2026, 8, 20, 10, 48),
              'FlightArrtimePlanDate': datetime.datetime(2026, 8, 20, 12, 55),
              'FlightArrtimeReadyDate': datetime.datetime(2026, 8, 20, 12, 38),
              'FlightArrtimeDate': datetime.datetime(2026, 8, 20, 12, 59, 49),
              'FlightOutgateTime': '2026-08-20 10:36:00',
              'BoardGate': 'D58', 'DepStandGate': '358', 'ArrStandGate': '105',
              'EstimateBoardingEndTime': '2026-08-20 10:25:00', 'arr_bridge': '靠廊桥'}]
        """.trimIndent()

        val flight = VariFlightPayloadParser.parseLegs(payload, "fallback").single()

        assertEquals("ZZ1001", flight.flightNumber)
        assertEquals(LocalDateTime.of(2026, 8, 20, 10, 40), flight.plannedDeparture)
        assertEquals(LocalDateTime.of(2026, 8, 20, 10, 47), flight.estimatedDeparture)
        assertEquals(LocalDateTime.of(2026, 8, 20, 10, 48), flight.actualDeparture)
        assertEquals(LocalDateTime.of(2026, 8, 20, 12, 55), flight.plannedArrival)
        assertEquals(LocalDateTime.of(2026, 8, 20, 12, 38), flight.estimatedArrival)
        assertEquals(LocalDateTime.of(2026, 8, 20, 12, 59, 49), flight.actualArrival)
        assertEquals(LocalDateTime.of(2026, 8, 20, 10, 36), flight.actualOffBlock)
        assertEquals(LocalDateTime.of(2026, 8, 20, 10, 25), flight.gateClosedObservedAt)
        assertEquals("D58", flight.boardingGate)
        assertEquals("358", flight.departureStand)
        assertEquals("105", flight.arrivalStand)
        assertEquals("靠廊桥", flight.arrivalBridge)
        assertEquals("LHW", flight.origin?.code)
        assertEquals("兰州中川", flight.origin?.name)
        assertEquals(36.51, flight.origin?.latitude ?: 0.0, 0.001)
        assertEquals("PKX", flight.destination?.code)
    }

    @Test
    fun usesFallbackFieldsAndFallbackFlightNumber() {
        val payload = """
            [{'FlightDeptimeReadyDate': '2026-08-20 10:47',
              'VeryZhunReadyArrtimeDate': '2026-08-20T12:37:00',
              'FlightArrtimeReadyDate': '2026-08-20 12:38:00',
              'FlightDepcode': 'LHW', 'FlightDepAirport': '兰州中川',
              'DepAirportLat': '36.51',
              'bridge': '远机位'}]
        """.trimIndent()

        val flight = VariFlightPayloadParser.parseLegs(payload, "MU1234").single()

        assertEquals("MU1234", flight.flightNumber)
        assertEquals(LocalDateTime.of(2026, 8, 20, 10, 47), flight.estimatedDeparture)
        assertEquals(LocalDateTime.of(2026, 8, 20, 12, 37), flight.estimatedArrival)
        assertEquals("远机位", flight.arrivalBridge)
        assertEquals("LHW", flight.origin?.code)
        assertEquals("兰州中川", flight.origin?.name)
        assertEquals(36.51, flight.origin?.latitude ?: 0.0, 0.001)
        assertEquals(null, flight.origin?.longitude)
    }

    @Test
    fun splitsAStopoverFlightIntoOneLegPerElement() {
        val payload = """
            [{'FlightNo': 'MU2415',
              'FlightDepcode': 'DNH', 'FlightDepAirport': '敦煌莫高',
              'FlightArrcode': 'LHW', 'FlightArrAirport': '兰州中川',
              'FlightDeptimePlanDate': datetime.datetime(2026, 8, 30, 12, 40),
              'FlightDeptimeDate': datetime.datetime(2026, 8, 30, 12, 47),
              'FlightArrtimePlanDate': datetime.datetime(2026, 8, 30, 14, 30),
              'BoardGate': '301', 'ArrStandGate': '105'},
             {'FlightNo': 'MU2415',
              'FlightDepcode': 'LHW', 'FlightDepAirport': '兰州中川',
              'FlightArrcode': 'PKX', 'FlightArrAirport': '北京大兴',
              'FlightDeptimePlanDate': datetime.datetime(2026, 8, 30, 15, 30),
              'FlightArrtimePlanDate': datetime.datetime(2026, 8, 30, 17, 35),
              'BoardGate': 'D58', 'DepStandGate': '358'}]
        """.trimIndent()

        val legs = VariFlightPayloadParser.parseLegs(payload, "fallback")

        assertEquals(2, legs.size)
        assertEquals("DNH", legs[0].origin?.code)
        assertEquals("LHW", legs[0].destination?.code)
        assertEquals(LocalDateTime.of(2026, 8, 30, 12, 40), legs[0].plannedDeparture)
        assertEquals(LocalDateTime.of(2026, 8, 30, 12, 47), legs[0].actualDeparture)
        assertEquals(LocalDateTime.of(2026, 8, 30, 14, 30), legs[0].plannedArrival)
        assertEquals("301", legs[0].boardingGate)
        assertEquals("105", legs[0].arrivalStand)
        assertEquals("LHW", legs[1].origin?.code)
        assertEquals("PKX", legs[1].destination?.code)
        assertEquals(LocalDateTime.of(2026, 8, 30, 15, 30), legs[1].plannedDeparture)
        assertEquals(LocalDateTime.of(2026, 8, 30, 17, 35), legs[1].plannedArrival)
        assertEquals("358", legs[1].departureStand)
    }

    @Test
    fun skipsElementsWithoutFlightData() {
        val payload = "[{}, {'FlightNo': 'MU1234', 'FlightDepcode': 'LHW'}]"

        val legs = VariFlightPayloadParser.parseLegs(payload, "fallback")

        assertEquals(1, legs.size)
        assertEquals("MU1234", legs[0].flightNumber)
    }

    @Test
    fun rejectsPayloadWithoutFlightData() {
        val rpc = successfulRpc("[]")

        val error = assertThrows(VariFlightClientException::class.java) {
            VariFlightJsonRpcParser.parse(rpc, "MU1234")
        }

        assertEquals("未查询到该航班的实时信息", error.message)
    }

    @Test
    fun unwrapsTheDataArrayAndDropsTheStopoverSummaryRecord() {
        // Real MU2415 response shape: a 'Flight details: ' prefix, a {'code', 'message', 'data'}
        // wrapper, and a whole-route summary record (StopFlag '1') before the two leg records.
        val payload = """
            Flight details: {'code': 200, 'message': 'Success', 'data': [
              {'FlightNo': 'MU2415', 'FlightDepcode': 'DNH', 'FlightDepAirport': '敦煌莫高',
               'FlightArrcode': 'PKX', 'FlightArrAirport': '北京大兴',
               'FlightDeptimePlanDate': '2026-08-30 12:45:00', 'FlightArrtimePlanDate': '2026-08-30 17:55:00',
               'BoardGate': '301', 'ArrStandGate': '105', 'StopFlag': '1', 'StopAirportCode': 'LHW'},
              {'FlightNo': 'MU2415', 'FlightDepcode': 'DNH', 'FlightDepAirport': '敦煌莫高',
               'FlightArrcode': 'LHW', 'FlightArrAirport': '兰州中川',
               'FlightDeptimePlanDate': '2026-08-30 12:45:00', 'FlightDeptimeDate': '2026-08-30 12:47:00',
               'FlightArrtimePlanDate': '2026-08-30 14:30:00', 'FlightArrtimeDate': '2026-08-30 14:03:47',
               'BoardGate': '301', 'ArrStandGate': '351', 'StopFlag': '0'},
              {'FlightNo': 'MU2415', 'FlightDepcode': 'LHW', 'FlightDepAirport': '兰州中川',
               'FlightArrcode': 'PKX', 'FlightArrAirport': '北京大兴',
               'FlightDeptimePlanDate': '2026-08-30 15:30:00', 'FlightDeptimeDate': '2026-08-30 15:31:05',
               'FlightArrtimePlanDate': '2026-08-30 17:55:00', 'FlightArrtimeDate': '2026-08-30 17:31:25',
               'BoardGate': 'C51', 'DepStandGate': '351', 'ArrStandGate': '105', 'StopFlag': '0'}]}
        """.trimIndent()

        val legs = VariFlightPayloadParser.parseLegs(payload, "fallback")

        assertEquals(2, legs.size)
        assertEquals("DNH", legs[0].origin?.code)
        assertEquals("LHW", legs[0].destination?.code)
        assertEquals("兰州中川", legs[0].destination?.name)
        assertEquals(LocalDateTime.of(2026, 8, 30, 14, 30), legs[0].plannedArrival)
        assertEquals(LocalDateTime.of(2026, 8, 30, 14, 3, 47), legs[0].actualArrival)
        assertEquals("351", legs[0].arrivalStand)
        assertEquals("LHW", legs[1].origin?.code)
        assertEquals("PKX", legs[1].destination?.code)
        assertEquals(LocalDateTime.of(2026, 8, 30, 15, 30), legs[1].plannedDeparture)
        assertEquals(LocalDateTime.of(2026, 8, 30, 15, 31, 5), legs[1].actualDeparture)
        assertEquals("C51", legs[1].boardingGate)
        assertEquals("105", legs[1].arrivalStand)
    }

    @Test
    fun combinesLegsFromMultipleContentItems() {
        val rpc = JSONObject()
            .put("jsonrpc", "2.0")
            .put(
                "result",
                JSONObject().put(
                    "content",
                    JSONArray()
                        .put(JSONObject().put("type", "text").put("text", "[{'FlightNo': 'MU2415', 'FlightDepcode': 'DNH', 'FlightArrcode': 'LHW'}]"))
                        .put(JSONObject().put("type", "text").put("text", "[{'FlightNo': 'MU2415', 'FlightDepcode': 'LHW', 'FlightArrcode': 'PKX'}]")),
                ),
            )
            .toString()

        val legs = VariFlightJsonRpcParser.parse(rpc, "fallback")

        assertEquals(2, legs.size)
        assertEquals("DNH", legs[0].origin?.code)
        assertEquals("PKX", legs[1].destination?.code)
    }

    @Test
    fun parsesJsonRpcAndServerSentEventResponses() {
        val rpc = successfulRpc("[{'FlightNo': 'MU1234'}]")

        assertEquals("MU1234", VariFlightJsonRpcParser.parse(rpc, "fallback").single().flightNumber)
        assertEquals(
            "MU1234",
            VariFlightJsonRpcParser.parse("event: message\ndata: $rpc\n\n", "fallback").single().flightNumber,
        )
    }

    @Test
    fun jsonRpcErrorsAreDesensitized() {
        val sensitiveResponse = "never-display-this-response"
        val rpc = JSONObject()
            .put("jsonrpc", "2.0")
            .put("error", JSONObject().put("code", -32000).put("message", sensitiveResponse))
            .toString()

        val error = assertThrows(VariFlightClientException::class.java) {
            VariFlightJsonRpcParser.parse(rpc, "MU1234")
        }

        assertEquals("飞常准接口返回错误", error.message)
        assertFalse(error.message.orEmpty().contains(sensitiveResponse))
    }

    @Test
    fun rejectsEmptyAndMalformedJsonRpcResponses() {
        assertEquals(
            "飞常准返回空响应",
            assertThrows(VariFlightClientException::class.java) {
                VariFlightJsonRpcParser.parse("", "MU1234")
            }.message,
        )
        assertEquals(
            "飞常准响应格式异常",
            assertThrows(VariFlightClientException::class.java) {
                VariFlightJsonRpcParser.parse("not-json", "MU1234")
            }.message,
        )
        val emptyContent = JSONObject()
            .put("jsonrpc", "2.0")
            .put("result", JSONObject().put("content", JSONArray()))
            .toString()
        assertEquals(
            "飞常准返回空航班数据",
            assertThrows(VariFlightClientException::class.java) {
                VariFlightJsonRpcParser.parse(emptyContent, "MU1234")
            }.message,
        )
    }

    @Test
    fun buildsTheExpectedAviationMcpRequest() {
        val request = JSONObject(buildVariFlightRequestBody(FlightLookup.of("mu1234", LocalDate.of(2026, 8, 23))))

        assertEquals("2.0", request.getString("jsonrpc"))
        assertEquals("tools/call", request.getString("method"))
        val params = request.getJSONObject("params")
        assertEquals("searchFlightsByNumber", params.getString("name"))
        assertEquals("MU1234", params.getJSONObject("arguments").getString("fnum"))
        assertEquals("2026-08-23", params.getJSONObject("arguments").getString("date"))
    }

    @Test
    fun mapsHttpFailuresToFixedSafeMessages() {
        assertEquals("飞常准 API Key 无效或无权访问 Aviation MCP", httpFailure(401).message)
        assertEquals("飞常准请求过于频繁，请稍后重试", httpFailure(429).message)
        assertEquals("飞常准服务暂时不可用，请稍后重试", httpFailure(503).message)
    }

    private fun successfulRpc(payload: String): String = JSONObject()
        .put("jsonrpc", "2.0")
        .put(
            "result",
            JSONObject().put(
                "content",
                JSONArray().put(JSONObject().put("type", "text").put("text", payload)),
            ),
        )
        .toString()
}
