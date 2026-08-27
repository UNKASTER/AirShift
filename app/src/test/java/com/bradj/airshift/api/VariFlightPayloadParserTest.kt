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

        val flight = VariFlightPayloadParser.parse(payload, "fallback")

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

        val flight = VariFlightPayloadParser.parse(payload, "MU1234")

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
    fun rejectsPayloadWithoutFlightData() {
        val error = assertThrows(VariFlightClientException::class.java) {
            VariFlightPayloadParser.parse("[]", "MU1234")
        }

        assertEquals("未查询到该航班的实时信息", error.message)
    }

    @Test
    fun parsesJsonRpcAndServerSentEventResponses() {
        val rpc = successfulRpc("[{'FlightNo': 'MU1234'}]")

        assertEquals("MU1234", VariFlightJsonRpcParser.parse(rpc, "fallback").flightNumber)
        assertEquals(
            "MU1234",
            VariFlightJsonRpcParser.parse("event: message\ndata: $rpc\n\n", "fallback").flightNumber,
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
