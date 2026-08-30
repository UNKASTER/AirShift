package com.bradj.airshift.api

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

private const val MCP_URL = "https://ai.variflight.com/servers/aviation/mcp"
private const val CONNECT_TIMEOUT_MILLIS = 5_000
private const val READ_TIMEOUT_MILLIS = 15_000
private const val CACHE_TTL_MILLIS = 120_000L
private const val RATE_LIMIT_PER_MINUTE = 30

class VariFlightClient(apiKey: String) {
    private val apiKey = apiKey.trim()

    init {
        require(this.apiKey.isNotEmpty()) { "飞常准 API Key 不能为空" }
    }

    fun fetchFlight(
        flightNumber: String,
        date: LocalDate,
        callback: (Result<List<FlightInfo>>) -> Unit,
    ) {
        executor.execute {
            val result = runCatching { fetchFlightBlocking(flightNumber, date) }
            mainHandler.post { callback(result) }
        }
    }

    fun testConnection(
        flightNumber: String,
        date: LocalDate,
        callback: (Result<Unit>) -> Unit,
    ) {
        executor.execute {
            val lookup = FlightLookup.of(flightNumber, date)
            val result = runCatching {
                ensureNotMainThread()
                sharedProtection.fetchUncached { requestFlightBlocking(lookup) }
                Unit
            }
            mainHandler.post { callback(result) }
        }
    }

    fun fetchFlightBlocking(flightNumber: String, date: LocalDate): List<FlightInfo> =
        fetchFlightBlocking(FlightLookup.of(flightNumber, date)) { true }

    internal fun fetchFlightBlocking(lookup: FlightLookup, isCurrent: () -> Boolean): List<FlightInfo> {
        ensureNotMainThread()
        return sharedProtection.fetch(lookup, isCurrent) { requestFlightBlocking(lookup) }
    }

    private fun requestFlightBlocking(lookup: FlightLookup): List<FlightInfo> {
        var connection: HttpURLConnection? = null
        return try {
            val activeConnection = (URI(MCP_URL).toURL().openConnection() as HttpURLConnection)
                .also { connection = it }
            val requestBytes = buildVariFlightRequestBody(lookup)
                .toByteArray(StandardCharsets.UTF_8)
            activeConnection.requestMethod = "POST"
            activeConnection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            activeConnection.readTimeout = READ_TIMEOUT_MILLIS
            activeConnection.doOutput = true
            activeConnection.setFixedLengthStreamingMode(requestBytes.size)
            activeConnection.setRequestProperty("X-API-Key", apiKey)
            activeConnection.setRequestProperty("Accept", "application/json, text/event-stream")
            activeConnection.setRequestProperty("Content-Type", "application/json")
            activeConnection.outputStream.use { output -> output.write(requestBytes) }

            val statusCode = activeConnection.responseCode
            if (statusCode !in 200..299) {
                activeConnection.errorStream?.close()
                throw httpFailure(statusCode)
            }
            val body = activeConnection.inputStream?.let { input ->
                InputStreamReader(input, StandardCharsets.UTF_8).buffered().use { it.readText() }
            }.orEmpty()
            VariFlightJsonRpcParser.parse(body, lookup.flightNumber)
        } catch (error: VariFlightClientException) {
            throw error
        } catch (_: SocketTimeoutException) {
            throw VariFlightClientException("连接飞常准超时，请稍后重试", retryable = true)
        } catch (_: IOException) {
            throw VariFlightClientException("无法连接飞常准，请检查网络后重试", retryable = true)
        } catch (_: SecurityException) {
            throw VariFlightClientException("无法建立安全网络连接")
        } finally {
            connection?.disconnect()
        }
    }

    private fun ensureNotMainThread() {
        check(Looper.myLooper() != Looper.getMainLooper()) { "实时航班网络请求不能在主线程执行" }
    }

    companion object {
        private val executor = Executors.newFixedThreadPool(3)
        private val mainHandler = Handler(Looper.getMainLooper())
        private val sharedProtection = VariFlightRequestProtection(
            rateLimiter = SlidingWindowRateLimiter(RATE_LIMIT_PER_MINUTE),
            cache = FlightResponseCache(CACHE_TTL_MILLIS),
        )

        fun clearCachedFlights() {
            sharedProtection.clearCache()
        }
    }
}

class VariFlightClientException(
    message: String,
    val retryable: Boolean = false,
) : IOException(message)

internal class FlightRefreshSkippedException : RuntimeException()

internal fun buildVariFlightRequestBody(lookup: FlightLookup): String = JSONObject()
    .put("jsonrpc", "2.0")
    .put("id", 1)
    .put("method", "tools/call")
    .put(
        "params",
        JSONObject()
            .put("name", "searchFlightsByNumber")
            .put(
                "arguments",
                JSONObject()
                    .put("fnum", lookup.flightNumber)
                    .put("date", lookup.date.toString()),
            ),
    )
    .toString()

internal fun httpFailure(statusCode: Int): VariFlightClientException = when (statusCode) {
    401, 403 -> VariFlightClientException("飞常准 API Key 无效或无权访问 Aviation MCP")
    408 -> VariFlightClientException("连接飞常准超时，请稍后重试", retryable = true)
    429 -> VariFlightClientException("飞常准请求过于频繁，请稍后重试", retryable = true)
    in 500..599 -> VariFlightClientException("飞常准服务暂时不可用，请稍后重试", retryable = true)
    else -> VariFlightClientException("飞常准请求失败（HTTP $statusCode）")
}

internal object VariFlightJsonRpcParser {
    fun parse(body: String, fallbackFlightNumber: String): List<FlightInfo> {
        if (body.isBlank()) throw VariFlightClientException("飞常准返回空响应", retryable = true)
        val rpc = parseRpcObject(body)
            ?: throw VariFlightClientException("飞常准响应格式异常", retryable = true)
        if (rpc.has("error") && !rpc.isNull("error")) {
            throw VariFlightClientException("飞常准接口返回错误")
        }
        val result = rpc.optJSONObject("result")
            ?: throw VariFlightClientException("飞常准响应格式异常", retryable = true)
        if (result.optBoolean("isError", false)) {
            throw VariFlightClientException("飞常准接口返回错误")
        }
        val content = result.optJSONArray("content")
            ?: throw VariFlightClientException("飞常准响应格式异常", retryable = true)
        // Each content item may hold one leg or a whole list of legs; read them all.
        val payloads = (0 until content.length()).mapNotNull { index ->
            val value = content.optJSONObject(index)?.opt("text")
            (value as? String)?.takeIf { it.isNotBlank() }
        }
        if (payloads.isEmpty()) throw VariFlightClientException("飞常准返回空航班数据")
        val legs = payloads.flatMap { VariFlightPayloadParser.parseLegs(it, fallbackFlightNumber) }
        if (legs.isEmpty()) throw VariFlightClientException("未查询到该航班的实时信息")
        return legs
    }

    private fun parseRpcObject(body: String): JSONObject? {
        val trimmed = body.trim()
        runCatching { JSONObject(trimmed) }.getOrNull()?.let { return it }
        return trimmed.lineSequence()
            .map(String::trim)
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .filter { it.isNotEmpty() && it != "[DONE]" }
            .firstNotNullOfOrNull { data -> runCatching { JSONObject(data) }.getOrNull() }
    }
}

internal object VariFlightPayloadParser {
    /**
     * The payload is a Python-dict-style response. Real responses wrap the flight list in
     * `Flight details: {'code': 200, ..., 'data': [...]}`; a stopover flight (e.g. DNH→LHW→PKX)
     * puts one element per leg into `data`, plus a whole-route summary record (StopFlag '1').
     */
    fun parseLegs(payload: String, fallbackFlightNumber: String): List<FlightInfo> {
        if (payload.isBlank()) throw VariFlightClientException("飞常准返回空航班数据")
        val parsed = splitLegElements(payload).mapNotNull { element ->
            parseLeg(element, fallbackFlightNumber)?.let { leg ->
                leg to (stringField(element, "StopFlag") == "1")
            }
        }
        if (parsed.size <= 1) return parsed.map { it.first }
        // Drop the whole-route summary records when per-leg records are available.
        val legsOnly = parsed.filterNot { it.second }.map { it.first }
        return legsOnly.ifEmpty { parsed.map { it.first } }
    }

    private fun parseLeg(element: String, fallbackFlightNumber: String): FlightInfo? {
        val flightNumber = stringField(element, "FlightNo")
        val plannedDeparture = dateTimeField(element, "FlightDeptimePlanDate")
        val estimatedDeparture = dateTimeField(element, "VeryZhunReadyDeptimeDate")
            ?: dateTimeField(element, "FlightDeptimeReadyDate")
        val actualDeparture = dateTimeField(element, "FlightDeptimeDate")
        val plannedArrival = dateTimeField(element, "FlightArrtimePlanDate")
        val estimatedArrival = dateTimeField(element, "VeryZhunReadyArrtimeDate")
            ?: dateTimeField(element, "FlightArrtimeReadyDate")
        val actualArrival = dateTimeField(element, "FlightArrtimeDate")
        val actualOffBlock = dateTimeField(element, "FlightOutgateTime")
        val gateClosedObservedAt = dateTimeField(element, "EstimateBoardingEndTime")
        val boardingGate = stringField(element, "BoardGate")
        val departureStand = stringField(element, "DepStandGate")
        val arrivalStand = stringField(element, "ArrStandGate")
        val arrivalBridge = stringField(element, "arr_bridge") ?: stringField(element, "bridge")
        val origin = airport(
            code = stringField(element, "FlightDepcode"),
            name = stringField(element, "FlightDepAirport"),
            latitude = numberField(element, "DepAirportLat"),
            longitude = numberField(element, "DepAirportLon"),
        )
        val destination = airport(
            code = stringField(element, "FlightArrcode"),
            name = stringField(element, "FlightArrAirport"),
            latitude = numberField(element, "ArrAirportLat"),
            longitude = numberField(element, "ArrAirportLon"),
        )
        val hasFlightData = listOf(
            flightNumber,
            plannedDeparture,
            estimatedDeparture,
            actualDeparture,
            plannedArrival,
            estimatedArrival,
            actualArrival,
            actualOffBlock,
            gateClosedObservedAt,
            boardingGate,
            departureStand,
            arrivalStand,
            arrivalBridge,
            origin,
            destination,
        ).any { it != null }
        if (!hasFlightData) return null

        return FlightInfo(
            flightNumber = flightNumber ?: fallbackFlightNumber,
            origin = origin,
            destination = destination,
            plannedDeparture = plannedDeparture,
            estimatedDeparture = estimatedDeparture,
            actualDeparture = actualDeparture,
            plannedArrival = plannedArrival,
            estimatedArrival = estimatedArrival,
            actualArrival = actualArrival,
            actualOffBlock = actualOffBlock,
            gateClosedObservedAt = gateClosedObservedAt,
            boardingGate = boardingGate,
            departureStand = departureStand,
            arrivalStand = arrivalStand,
            arrivalBridge = arrivalBridge,
        )
    }

    /**
     * Splits the leg elements of the `data` array when the response wraps it in
     * `{'code': ..., 'data': [...]}`; otherwise scans the whole payload for top-level dicts.
     */
    private fun splitLegElements(payload: String): List<String> =
        splitTopLevelDicts(dataArrayRegion(payload) ?: payload)

    /** Returns the region between the brackets of the `data` array, quotes/braces aware. */
    private fun dataArrayRegion(payload: String): String? {
        val match = Regex("['\"]data['\"]\\s*:\\s*\\[").find(payload) ?: return null
        var depth = 1
        var quote: Char? = null
        var index = match.range.last + 1
        while (index < payload.length) {
            val char = payload[index]
            val activeQuote = quote
            when {
                activeQuote != null -> when (char) {
                    '\\' -> index++ // skip the escaped character
                    activeQuote -> quote = null
                }
                char == '\'' || char == '"' -> quote = char
                char == '[' -> depth++
                char == ']' -> {
                    depth--
                    if (depth == 0) return payload.substring(match.range.last + 1, index)
                }
            }
            index++
        }
        return null
    }

    /** Splits top-level `{...}` elements, ignoring brackets and braces inside quoted strings. */
    private fun splitTopLevelDicts(payload: String): List<String> {
        val elements = mutableListOf<String>()
        var depth = 0
        var start = -1
        var quote: Char? = null
        var index = 0
        while (index < payload.length) {
            val char = payload[index]
            val activeQuote = quote
            when {
                activeQuote != null -> when (char) {
                    '\\' -> index++ // skip the escaped character
                    activeQuote -> quote = null
                }
                char == '\'' || char == '"' -> quote = char
                char == '{' -> {
                    if (depth == 0) start = index
                    depth++
                }
                char == '}' && depth > 0 -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        elements += payload.substring(start, index + 1)
                        start = -1
                    }
                }
            }
            index++
        }
        return elements
    }

    private fun stringField(payload: String, key: String): String? =
        Regex("['\"]${Regex.escape(key)}['\"]\\s*:\\s*['\"]([^'\"]*)['\"]")
            .find(payload)
            ?.groupValues
            ?.get(1)
            ?.takeIf { it.isNotBlank() }

    private fun numberField(payload: String, key: String): Double? =
        Regex("['\"]${Regex.escape(key)}['\"]\\s*:\\s*['\"]?(-?\\d+(?:\\.\\d+)?)['\"]?")
            .find(payload)
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull()

    private fun dateTimeField(payload: String, key: String): LocalDateTime? {
        val valueStart = "['\"]${Regex.escape(key)}['\"]\\s*:\\s*"
        val datetime = Regex(
            valueStart + "datetime\\.datetime\\((\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+)(?:,\\s*(\\d+))?",
        ).find(payload)
        if (datetime != null) {
            val parts = datetime.groupValues.drop(1).map { it.toIntOrNull() ?: 0 }
            return runCatching {
                LocalDateTime.of(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])
            }.getOrNull()
        }
        val text = Regex(valueStart + "['\"](\\d{4}-\\d{2}-\\d{2})[ T](\\d{2}:\\d{2}(?::\\d{2})?)['\"]")
            .find(payload)
            ?.let { match -> "${match.groupValues[1]}T${match.groupValues[2]}" }
            ?: return null
        return dateTimeFormatters.firstNotNullOfOrNull { formatter ->
            runCatching { LocalDateTime.parse(text, formatter) }.getOrNull()
        }
    }

    private fun airport(code: String?, name: String?, latitude: Double?, longitude: Double?): AirportPoint? {
        if (code == null) return null
        return AirportPoint(code, name, latitude, longitude)
    }

    private val dateTimeFormatters = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
    )
}

internal class FlightResponseCache(
    private val ttlMillis: Long,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private data class CacheEntry(val legs: List<FlightInfo>, val expiresAtMillis: Long)

    private val entries = ConcurrentHashMap<FlightLookup, CacheEntry>()

    init {
        require(ttlMillis >= 0) { "Cache TTL must not be negative" }
    }

    fun getOrFetch(lookup: FlightLookup, loader: () -> List<FlightInfo>): List<FlightInfo> {
        if (ttlMillis == 0L) return loader()
        val entry = entries.compute(lookup) { _, existing ->
            val now = nowMillis()
            if (existing != null && existing.expiresAtMillis > now) {
                existing
            } else {
                CacheEntry(loader(), now + ttlMillis)
            }
        } ?: error("Cache entry was not created")
        return entry.legs
    }

    fun clear() {
        entries.clear()
    }
}

internal class SlidingWindowRateLimiter(
    private val limit: Int,
    private val windowMillis: Long = 60_000L,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val acceptedAt = ArrayDeque<Long>()

    init {
        require(limit > 0) { "Rate limit must be positive" }
        require(windowMillis > 0) { "Rate-limit window must be positive" }
    }

    fun tryAcquire(): Boolean = synchronized(acceptedAt) {
        val now = nowMillis()
        val cutoff = now - windowMillis
        while (acceptedAt.isNotEmpty() && acceptedAt.first <= cutoff) acceptedAt.removeFirst()
        if (acceptedAt.size >= limit) {
            false
        } else {
            acceptedAt.addLast(now)
            true
        }
    }
}

internal class VariFlightRequestProtection(
    private val rateLimiter: SlidingWindowRateLimiter,
    private val cache: FlightResponseCache,
) {
    fun fetch(
        lookup: FlightLookup,
        isCurrent: () -> Boolean = { true },
        loader: () -> List<FlightInfo>,
    ): List<FlightInfo> {
        acquireCapacity()
        return cache.getOrFetch(lookup) {
            // Another refresh may have held this cache key while the duty window moved on.
            if (!isCurrent()) throw FlightRefreshSkippedException()
            loader()
        }
    }

    fun fetchUncached(loader: () -> List<FlightInfo>): List<FlightInfo> {
        acquireCapacity()
        return loader()
    }

    fun clearCache() {
        cache.clear()
    }

    private fun acquireCapacity() {
        if (!rateLimiter.tryAcquire()) {
            throw VariFlightClientException("本机实时航班查询已达到每分钟 30 次，请稍后重试", retryable = true)
        }
    }
}
