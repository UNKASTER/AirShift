package com.bradj.airshift.api

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

class FlightGatewayClient(private val baseUrl: String) {
    fun fetchFlight(
        flightNumber: String,
        date: LocalDate,
        callback: (Result<FlightInfo>) -> Unit,
    ) {
        executor.execute {
            val result = runCatching { fetchFlightBlocking(flightNumber, date) }
            mainHandler.post { callback(result) }
        }
    }

    fun fetchFlightBlocking(flightNumber: String, date: LocalDate): FlightInfo {
        validateBaseUrl(baseUrl)
        val encodedFlight = URLEncoder.encode(flightNumber, StandardCharsets.UTF_8)
        val encodedDate = URLEncoder.encode(date.toString(), StandardCharsets.UTF_8)
        val connection = URI("${baseUrl.trimEnd('/')}/v1/flights/$encodedFlight?date=$encodedDate")
            .toURL()
            .openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            check(connection.responseCode in 200..299) {
                "实时航班服务返回 HTTP ${connection.responseCode}"
            }
            parse(JSONObject(body))
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(json: JSONObject): FlightInfo = FlightInfo(
        flightNumber = json.getString("flightNumber"),
        origin = json.optJSONObject("origin")?.toAirportPoint(),
        destination = json.optJSONObject("destination")?.toAirportPoint(),
        plannedDeparture = json.dateTime("plannedDeparture"),
        estimatedDeparture = json.dateTime("estimatedDeparture"),
        actualDeparture = json.dateTime("actualDeparture"),
        plannedArrival = json.dateTime("plannedArrival"),
        estimatedArrival = json.dateTime("estimatedArrival"),
        actualArrival = json.dateTime("actualArrival"),
        arrivalGate = json.stringOrNull("arrivalGate"),
        arrivalBridge = json.stringOrNull("arrivalBridge"),
    )

    private fun JSONObject.toAirportPoint() = AirportPoint(
        code = getString("code"),
        name = getString("name"),
        latitude = getDouble("latitude"),
        longitude = getDouble("longitude"),
    )

    private fun JSONObject.dateTime(key: String): LocalDateTime? =
        stringOrNull(key)?.let { value ->
            dateTimeFormatters.firstNotNullOfOrNull { formatter ->
                runCatching { LocalDateTime.parse(value, formatter) }.getOrNull()
            }
        }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

    private fun validateBaseUrl(value: String) {
        val uri = URI(value)
        val isPrivateDevHost = uri.scheme == "http" && (
            uri.host == "10.0.2.2" || uri.host == "localhost" || uri.host?.startsWith("10.") == true
        )
        require(uri.scheme == "https" || isPrivateDevHost) { "实时航班服务必须使用 HTTPS" }
        require(!uri.host.isNullOrBlank()) { "实时航班服务地址无效" }
    }

    companion object {
        private val executor = Executors.newFixedThreadPool(3)
        private val mainHandler = Handler(Looper.getMainLooper())
        private val dateTimeFormatters = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        )
    }
}
