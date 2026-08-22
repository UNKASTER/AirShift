package com.bradj.airshift.gateway

import kotlin.test.Test
import kotlin.test.assertEquals

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
              'ArrStandGate': '105', 'arr_bridge': '靠廊桥'}]
        """.trimIndent()

        val json = VariFlightPayloadParser.parse(payload, "fallback")

        assertEquals("ZZ1001", json.getString("flightNumber"))
        assertEquals("2026-08-20T10:40:00", json.getString("plannedDeparture"))
        assertEquals("2026-08-20T12:38:00", json.getString("estimatedArrival"))
        assertEquals("2026-08-20T12:59:49", json.getString("actualArrival"))
        assertEquals("105", json.getString("arrivalGate"))
        assertEquals("靠廊桥", json.getString("arrivalBridge"))
        assertEquals("LHW", json.getJSONObject("origin").getString("code"))
        assertEquals("PKX", json.getJSONObject("destination").getString("code"))
    }
}
