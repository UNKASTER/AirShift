package com.bradj.airshift.gateway

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.Executors

private const val MCP_URL = "https://ai.variflight.com/servers/aviation/mcp"

fun main() {
    val apiKey = System.getenv("VariFlight")?.takeIf { it.isNotBlank() }
        ?: error("Missing VariFlight environment variable")
    val host = System.getenv("AIRSHIFT_GATEWAY_HOST")?.takeIf { it.isNotBlank() } ?: "127.0.0.1"
    val port = System.getenv("AIRSHIFT_GATEWAY_PORT")?.toIntOrNull() ?: 8787
    val service = VariFlightService(apiKey)
    val server = HttpServer.create(InetSocketAddress(host, port), 0).apply {
        executor = Executors.newFixedThreadPool(6)
        createContext("/health") { exchange ->
            if (exchange.requestMethod != "GET") exchange.respond(405, errorJson("method_not_allowed"))
            else exchange.respond(200, JSONObject().put("status", "ok").toString())
        }
        createContext("/v1/flights") { exchange -> handleFlight(exchange, service) }
        start()
    }
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(1) })
    println("AirShift gateway listening on http://$host:$port")
}

private fun handleFlight(exchange: HttpExchange, service: VariFlightService) {
    if (exchange.requestMethod != "GET") {
        exchange.respond(405, errorJson("method_not_allowed"))
        return
    }
    val flightNumber = exchange.requestURI.path.substringAfterLast('/').uppercase()
    val dateValue = exchange.requestURI.rawQuery
        ?.split('&')
        ?.mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 } }
        ?.firstOrNull { it[0] == "date" }
        ?.get(1)
    val date = runCatching { LocalDate.parse(dateValue) }.getOrNull()
    if (!Regex("[A-Z]{2,3}\\d{3,4}").matches(flightNumber) || date == null) {
        exchange.respond(400, errorJson("invalid_flight_or_date"))
        return
    }

    runCatching { service.search(flightNumber, date) }
        .onSuccess { exchange.respond(200, it.toString()) }
        .onFailure { error ->
            System.err.println("VariFlight request failed: ${error.javaClass.simpleName}: ${error.message}")
            exchange.respond(502, errorJson("upstream_unavailable"))
        }
}

internal class VariFlightService(
    private val apiKey: String,
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
) {
    fun search(flightNumber: String, date: LocalDate): JSONObject {
        val requestBody = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "tools/call")
            .put(
                "params",
                JSONObject()
                    .put("name", "searchFlightsByNumber")
                    .put(
                        "arguments",
                        JSONObject().put("fnum", flightNumber).put("date", date.toString()),
                    ),
            )
            .toString()
        val request = HttpRequest.newBuilder(URI(MCP_URL))
            .timeout(Duration.ofSeconds(25))
            .header("X-API-Key", apiKey)
            .header("Accept", "application/json, text/event-stream")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        check(response.statusCode() in 200..299) { "HTTP ${response.statusCode()}" }
        val rpc = JSONObject(response.body())
        check(!rpc.has("error")) { "MCP returned an error" }
        val payload = rpc.getJSONObject("result")
            .getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
        return VariFlightPayloadParser.parse(payload, flightNumber)
    }
}

internal object VariFlightPayloadParser {
    fun parse(payload: String, fallbackFlightNumber: String): JSONObject {
        val output = JSONObject()
            .put("flightNumber", stringField(payload, "FlightNo") ?: fallbackFlightNumber)
            .putNullable("plannedDeparture", dateTimeField(payload, "FlightDeptimePlanDate"))
            .putNullable(
                "estimatedDeparture",
                dateTimeField(payload, "VeryZhunReadyDeptimeDate")
                    ?: dateTimeField(payload, "FlightDeptimeReadyDate"),
            )
            .putNullable("actualDeparture", dateTimeField(payload, "FlightDeptimeDate"))
            .putNullable("plannedArrival", dateTimeField(payload, "FlightArrtimePlanDate"))
            .putNullable(
                "estimatedArrival",
                dateTimeField(payload, "VeryZhunReadyArrtimeDate")
                    ?: dateTimeField(payload, "FlightArrtimeReadyDate"),
            )
            .putNullable("actualArrival", dateTimeField(payload, "FlightArrtimeDate"))
            .putNullable("arrivalGate", stringField(payload, "ArrStandGate"))
            .putNullable("arrivalBridge", stringField(payload, "arr_bridge") ?: stringField(payload, "bridge"))

        airport(
            code = stringField(payload, "FlightDepcode"),
            name = stringField(payload, "FlightDepAirport"),
            latitude = numberField(payload, "DepAirportLat"),
            longitude = numberField(payload, "DepAirportLon"),
        )?.let { output.put("origin", it) } ?: output.put("origin", JSONObject.NULL)
        airport(
            code = stringField(payload, "FlightArrcode"),
            name = stringField(payload, "FlightArrAirport"),
            latitude = numberField(payload, "ArrAirportLat"),
            longitude = numberField(payload, "ArrAirportLon"),
        )?.let { output.put("destination", it) } ?: output.put("destination", JSONObject.NULL)
        return output
    }

    private fun stringField(payload: String, key: String): String? {
        val quoted = Regex("['\"]${Regex.escape(key)}['\"]\\s*:\\s*['\"]([^'\"]*)['\"]")
            .find(payload)
            ?.groupValues
            ?.get(1)
        return quoted?.takeIf { it.isNotBlank() }
    }

    private fun numberField(payload: String, key: String): Double? =
        Regex("['\"]${Regex.escape(key)}['\"]\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
            .find(payload)
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull()

    private fun dateTimeField(payload: String, key: String): String? {
        val valueStart = "['\"]${Regex.escape(key)}['\"]\\s*:\\s*"
        val datetime = Regex(
            valueStart + "datetime\\.datetime\\((\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+)(?:,\\s*(\\d+))?",
        ).find(payload)
        if (datetime != null) {
            val parts = datetime.groupValues.drop(1).map { it.toIntOrNull() ?: 0 }
            return "%04d-%02d-%02dT%02d:%02d:%02d".format(
                parts[0], parts[1], parts[2], parts[3], parts[4], parts[5],
            )
        }
        return Regex(valueStart + "['\"](\\d{4}-\\d{2}-\\d{2})[ T](\\d{2}:\\d{2}(?::\\d{2})?)['\"]")
            .find(payload)
            ?.let { match -> "${match.groupValues[1]}T${match.groupValues[2]}" }
    }

    private fun airport(code: String?, name: String?, latitude: Double?, longitude: Double?): JSONObject? {
        if (code == null || name == null || latitude == null || longitude == null) return null
        return JSONObject()
            .put("code", code)
            .put("name", name)
            .put("latitude", latitude)
            .put("longitude", longitude)
    }

    private fun JSONObject.putNullable(key: String, value: String?): JSONObject =
        put(key, value ?: JSONObject.NULL)
}

private fun HttpExchange.respond(status: Int, body: String) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    responseHeaders.set("Content-Type", "application/json; charset=utf-8")
    responseHeaders.set("Cache-Control", "no-store")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}

private fun errorJson(code: String): String = JSONObject().put("error", code).toString()
