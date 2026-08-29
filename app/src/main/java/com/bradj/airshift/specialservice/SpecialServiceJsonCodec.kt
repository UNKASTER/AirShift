package com.bradj.airshift.specialservice

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object SpecialServiceJsonCodec {
    fun encode(state: SpecialServiceState): String = JSONObject().apply {
        put("version", 3)
        put("records", JSONArray().apply { state.records.forEach { put(it.toJson()) } })
        put("pendingReviews", JSONArray().apply { state.pendingReviews.forEach { put(it.toJson()) } })
        put("gateChanges", JSONArray().apply { state.gateChanges.forEach { put(it.toJson()) } })
        put("pendingGateChanges", JSONArray().apply { state.pendingGateChanges.forEach { put(it.toJson()) } })
        put("standChanges", JSONArray().apply { state.standChanges.forEach { put(it.toJson()) } })
        put("pendingStandChanges", JSONArray().apply { state.pendingStandChanges.forEach { put(it.toJson()) } })
        put("flightCancellations", JSONArray().apply { state.flightCancellations.forEach { put(it.toJson()) } })
        put("pendingFlightCancellations", JSONArray().apply { state.pendingFlightCancellations.forEach { put(it.toJson()) } })
        put("processedFingerprints", JSONArray().apply { state.processedFingerprints.forEach { put(it.toJson()) } })
        putNullable("lastSuccessfulRecognitionEpochMillis", state.lastSuccessfulRecognitionEpochMillis)
        putNullable("lastProcessedEpochMillis", state.lastProcessedEpochMillis)
        putNullable("lastProcessingResult", state.lastProcessingResult)
    }.toString()

    fun decode(value: String): SpecialServiceState {
        val root = JSONObject(value)
        require(root.optInt("version", 1) in 1..3) { "Unsupported special-service data version" }
        return SpecialServiceState(
            records = root.optJSONArray("records").mapObjects { it.toRecord() },
            pendingReviews = root.optJSONArray("pendingReviews").mapObjects { it.toPendingReview() },
            gateChanges = root.optJSONArray("gateChanges").mapObjects { it.toGateChangeRecord() },
            pendingGateChanges = root.optJSONArray("pendingGateChanges").mapObjects { it.toParsedGateChange() },
            standChanges = root.optJSONArray("standChanges").mapObjects { it.toStandChangeRecord() },
            pendingStandChanges = root.optJSONArray("pendingStandChanges").mapObjects { it.toParsedStandChange() },
            flightCancellations = root.optJSONArray("flightCancellations").mapObjects { it.toFlightCancellationRecord() },
            pendingFlightCancellations = root.optJSONArray("pendingFlightCancellations").mapObjects { it.toParsedFlightCancellation() },
            processedFingerprints = root.optJSONArray("processedFingerprints").mapObjects { it.toProcessedFingerprint() },
            lastSuccessfulRecognitionEpochMillis = root.nullableLong("lastSuccessfulRecognitionEpochMillis"),
            lastProcessedEpochMillis = root.nullableLong("lastProcessedEpochMillis"),
            lastProcessingResult = root.nullableString("lastProcessingResult"),
        )
    }

    private fun FlightServiceRecord.toJson() = JSONObject().apply {
        put("flightNumber", flightNumber)
        put("operationDate", operationDate.toString())
        put("serviceType", serviceType.name)
        putNullable("wheelchairLevel", wheelchairLevel?.name)
        putNullable("count", count)
        put("updatedAtEpochMillis", updatedAtEpochMillis)
        put("confidence", confidence.name)
        put("reviewStatus", reviewStatus.name)
        put("expiresAtEpochMillis", expiresAtEpochMillis)
        put("fingerprint", fingerprint)
        put("active", active)
    }

    private fun PendingServiceReview.toJson() = JSONObject().apply {
        put("id", id)
        put("fingerprint", fingerprint)
        put("flightToken", flightToken)
        putNullable("explicitDate", explicitDate?.toString())
        put("notificationDate", notificationDate.toString())
        put("serviceType", serviceType.name)
        putNullable("wheelchairLevel", wheelchairLevel?.name)
        putNullable("count", count)
        put("confidence", confidence.name)
        put("action", action.name)
        put("sourceEpochMillis", sourceEpochMillis)
        put("expiresAtEpochMillis", expiresAtEpochMillis)
        put("suggestedFlights", JSONArray().apply { suggestedFlights.forEach { put(it.toJson()) } })
        put("reviewStatus", reviewStatus.name)
    }

    private fun GateChangeRecord.toJson() = JSONObject().apply {
        put("flightNumber", flightNumber)
        put("operationDate", operationDate.toString())
        put("boardingGate", boardingGate)
        put("updatedAtEpochMillis", updatedAtEpochMillis)
        put("expiresAtEpochMillis", expiresAtEpochMillis)
        put("fingerprint", fingerprint)
    }

    private fun ParsedGateChangeCandidate.toJson() = JSONObject().apply {
        put("fingerprint", fingerprint)
        put("flightToken", flightToken)
        putNullable("explicitDate", explicitDate?.toString())
        put("notificationDate", notificationDate.toString())
        put("boardingGate", boardingGate)
        put("sourceEpochMillis", sourceEpochMillis)
        put("expiresAtEpochMillis", expiresAtEpochMillis)
    }

    private fun StandChangeRecord.toJson() = JSONObject().apply {
        put("flightNumber", flightNumber)
        put("operationDate", operationDate.toString())
        put("stand", stand)
        put("updatedAtEpochMillis", updatedAtEpochMillis)
        put("expiresAtEpochMillis", expiresAtEpochMillis)
        put("fingerprint", fingerprint)
    }

    private fun ParsedStandChangeCandidate.toJson() = JSONObject().apply {
        put("fingerprint", fingerprint)
        put("flightToken", flightToken)
        putNullable("explicitDate", explicitDate?.toString())
        put("notificationDate", notificationDate.toString())
        put("stand", stand)
        put("sourceEpochMillis", sourceEpochMillis)
        put("expiresAtEpochMillis", expiresAtEpochMillis)
    }

    private fun FlightCancellationRecord.toJson() = JSONObject().apply {
        put("flightNumber", flightNumber)
        put("operationDate", operationDate.toString())
        put("scope", scope.name)
        put("updatedAtEpochMillis", updatedAtEpochMillis)
        put("expiresAtEpochMillis", expiresAtEpochMillis)
        put("fingerprint", fingerprint)
    }

    private fun ParsedFlightCancellationCandidate.toJson() = JSONObject().apply {
        put("fingerprint", fingerprint)
        put("flightToken", flightToken)
        putNullable("explicitDate", explicitDate?.toString())
        put("notificationDate", notificationDate.toString())
        put("scope", scope.name)
        put("sourceEpochMillis", sourceEpochMillis)
        put("expiresAtEpochMillis", expiresAtEpochMillis)
    }

    private fun FlightReference.toJson() = JSONObject().apply {
        put("flightNumber", flightNumber)
        put("operationDate", operationDate.toString())
        put("expiresAtEpochMillis", expiresAtEpochMillis)
    }

    private fun ProcessedFingerprint.toJson() = JSONObject().apply {
        put("value", value)
        put("sourceEpochMillis", sourceEpochMillis)
        put("expiresAtEpochMillis", expiresAtEpochMillis)
        put("ignored", ignored)
    }

    private fun JSONObject.toRecord() = FlightServiceRecord(
        flightNumber = getString("flightNumber"),
        operationDate = LocalDate.parse(getString("operationDate")),
        serviceType = enumValueOf(getString("serviceType")),
        wheelchairLevel = nullableString("wheelchairLevel")?.let(::enumValueOf),
        count = nullableInt("count"),
        updatedAtEpochMillis = getLong("updatedAtEpochMillis"),
        confidence = enumValueOf(getString("confidence")),
        reviewStatus = enumValueOf(getString("reviewStatus")),
        expiresAtEpochMillis = getLong("expiresAtEpochMillis"),
        fingerprint = getString("fingerprint"),
        active = optBoolean("active", true),
    )

    private fun JSONObject.toPendingReview() = PendingServiceReview(
        id = getString("id"),
        fingerprint = getString("fingerprint"),
        flightToken = getString("flightToken"),
        explicitDate = nullableString("explicitDate")?.let(LocalDate::parse),
        notificationDate = LocalDate.parse(getString("notificationDate")),
        serviceType = enumValueOf(getString("serviceType")),
        wheelchairLevel = nullableString("wheelchairLevel")?.let(::enumValueOf),
        count = nullableInt("count"),
        confidence = enumValueOf(getString("confidence")),
        action = enumValueOf(getString("action")),
        sourceEpochMillis = getLong("sourceEpochMillis"),
        expiresAtEpochMillis = getLong("expiresAtEpochMillis"),
        suggestedFlights = optJSONArray("suggestedFlights").mapObjects { it.toFlightReference() },
        reviewStatus = enumValueOf(optString("reviewStatus", ReviewStatus.NEEDS_REVIEW.name)),
    )

    private fun JSONObject.toGateChangeRecord() = GateChangeRecord(
        flightNumber = getString("flightNumber"),
        operationDate = LocalDate.parse(getString("operationDate")),
        boardingGate = getString("boardingGate"),
        updatedAtEpochMillis = getLong("updatedAtEpochMillis"),
        expiresAtEpochMillis = getLong("expiresAtEpochMillis"),
        fingerprint = getString("fingerprint"),
    )

    private fun JSONObject.toParsedGateChange() = ParsedGateChangeCandidate(
        fingerprint = getString("fingerprint"),
        flightToken = getString("flightToken"),
        explicitDate = nullableString("explicitDate")?.let(LocalDate::parse),
        notificationDate = LocalDate.parse(getString("notificationDate")),
        boardingGate = getString("boardingGate"),
        sourceEpochMillis = getLong("sourceEpochMillis"),
        expiresAtEpochMillis = getLong("expiresAtEpochMillis"),
    )

    private fun JSONObject.toStandChangeRecord() = StandChangeRecord(
        flightNumber = getString("flightNumber"),
        operationDate = LocalDate.parse(getString("operationDate")),
        stand = getString("stand"),
        updatedAtEpochMillis = getLong("updatedAtEpochMillis"),
        expiresAtEpochMillis = getLong("expiresAtEpochMillis"),
        fingerprint = getString("fingerprint"),
    )

    private fun JSONObject.toParsedStandChange() = ParsedStandChangeCandidate(
        fingerprint = getString("fingerprint"),
        flightToken = getString("flightToken"),
        explicitDate = nullableString("explicitDate")?.let(LocalDate::parse),
        notificationDate = LocalDate.parse(getString("notificationDate")),
        stand = getString("stand"),
        sourceEpochMillis = getLong("sourceEpochMillis"),
        expiresAtEpochMillis = getLong("expiresAtEpochMillis"),
    )

    private fun JSONObject.toFlightCancellationRecord() = FlightCancellationRecord(
        flightNumber = getString("flightNumber"),
        operationDate = LocalDate.parse(getString("operationDate")),
        scope = enumValueOf(getString("scope")),
        updatedAtEpochMillis = getLong("updatedAtEpochMillis"),
        expiresAtEpochMillis = getLong("expiresAtEpochMillis"),
        fingerprint = getString("fingerprint"),
    )

    private fun JSONObject.toParsedFlightCancellation() = ParsedFlightCancellationCandidate(
        fingerprint = getString("fingerprint"),
        flightToken = getString("flightToken"),
        explicitDate = nullableString("explicitDate")?.let(LocalDate::parse),
        notificationDate = LocalDate.parse(getString("notificationDate")),
        scope = enumValueOf(getString("scope")),
        sourceEpochMillis = getLong("sourceEpochMillis"),
        expiresAtEpochMillis = getLong("expiresAtEpochMillis"),
    )

    private fun JSONObject.toFlightReference() = FlightReference(
        flightNumber = getString("flightNumber"),
        operationDate = LocalDate.parse(getString("operationDate")),
        expiresAtEpochMillis = getLong("expiresAtEpochMillis"),
    )

    private fun JSONObject.toProcessedFingerprint() = ProcessedFingerprint(
        value = getString("value"),
        sourceEpochMillis = getLong("sourceEpochMillis"),
        expiresAtEpochMillis = getLong("expiresAtEpochMillis"),
        ignored = optBoolean("ignored", false),
    )

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private fun JSONObject.nullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else getInt(key)

    private fun JSONObject.nullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else getLong(key)

    private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                runCatching { transform(getJSONObject(index)) }.getOrNull()?.let(::add)
            }
        }
    }
}
