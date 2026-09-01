package com.bradj.airshift.specialservice

import java.time.LocalDate

enum class ServiceType {
    DISABILITY,
    WHEELCHAIR,
    UNACCOMPANIED_MINOR,
    MAAS,
    CABIN_PET,
}

enum class WheelchairLevel {
    WCHR,
    WCHS,
    WCHC,
}

enum class Confidence {
    HIGH,
    LOW,
}

enum class ReviewStatus {
    CONFIRMED,
    NEEDS_REVIEW,
    IGNORED,
}

enum class CandidateAction {
    UPSERT,
    CANCEL,
}

enum class FlightCancellationScope {
    SPECIAL_SERVICES,
    TRIP,
}

data class FlightReference(
    val flightNumber: String,
    val operationDate: LocalDate,
    val expiresAtEpochMillis: Long,
) {
    val key: String
        get() = "$flightNumber|$operationDate"
}

data class ParsedServiceCandidate(
    val fingerprint: String,
    val flightToken: String,
    val explicitDate: LocalDate?,
    val notificationDate: LocalDate,
    val serviceType: ServiceType,
    val wheelchairLevel: WheelchairLevel?,
    val count: Int?,
    val confidence: Confidence,
    val action: CandidateAction,
    val sourceEpochMillis: Long,
    val expiresAtEpochMillis: Long,
) {
    val id: String
        get() = listOf(fingerprint, flightToken, serviceType.name, wheelchairLevel?.name.orEmpty()).joinToString("|")
}

data class ParsedGateChangeCandidate(
    val fingerprint: String,
    val flightToken: String,
    val explicitDate: LocalDate?,
    val notificationDate: LocalDate,
    val boardingGate: String,
    val sourceEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val previousGate: String? = null,
) {
    val id: String
        get() = listOf(fingerprint, flightToken, boardingGate).joinToString("|")
}

data class GateChangeRecord(
    val flightNumber: String,
    val operationDate: LocalDate,
    val boardingGate: String,
    val updatedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val fingerprint: String,
    val previousGate: String? = null,
) {
    val flightKey: String
        get() = "$flightNumber|$operationDate"
}

/** 登机口/机位编号归一化：大写、去空格、数字部分去前导零（A08 与 A8 视为同一登机口）。 */
fun normalizeGateCode(value: String): String {
    val compact = value.trim().uppercase().replace(" ", "")
    val match = Regex("([A-Z]*)(\\d+)([A-Z]*)").matchEntire(compact) ?: return compact
    val digits = match.groupValues[2].trimStart('0')
    return "${match.groupValues[1]}${digits.ifEmpty { "0" }}${match.groupValues[3]}"
}

data class ParsedStandChangeCandidate(
    val fingerprint: String,
    val flightToken: String,
    val explicitDate: LocalDate?,
    val notificationDate: LocalDate,
    val stand: String,
    val sourceEpochMillis: Long,
    val expiresAtEpochMillis: Long,
) {
    val id: String
        get() = listOf(fingerprint, flightToken, stand).joinToString("|")
}

data class StandChangeRecord(
    val flightNumber: String,
    val operationDate: LocalDate,
    val stand: String,
    val updatedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val fingerprint: String,
) {
    val flightKey: String
        get() = "$flightNumber|$operationDate"
}

data class ParsedFlightCancellationCandidate(
    val fingerprint: String,
    val flightToken: String,
    val explicitDate: LocalDate?,
    val notificationDate: LocalDate,
    val scope: FlightCancellationScope,
    val sourceEpochMillis: Long,
    val expiresAtEpochMillis: Long,
) {
    val id: String
        get() = listOf(fingerprint, flightToken, scope.name).joinToString("|")
}

data class FlightCancellationRecord(
    val flightNumber: String,
    val operationDate: LocalDate,
    val scope: FlightCancellationScope,
    val updatedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val fingerprint: String,
) {
    val flightKey: String
        get() = "$flightNumber|$operationDate"
}

data class FlightServiceRecord(
    val flightNumber: String,
    val operationDate: LocalDate,
    val serviceType: ServiceType,
    val wheelchairLevel: WheelchairLevel?,
    val count: Int?,
    val updatedAtEpochMillis: Long,
    val confidence: Confidence,
    val reviewStatus: ReviewStatus,
    val expiresAtEpochMillis: Long,
    val fingerprint: String,
    val active: Boolean = true,
) {
    val businessKey: String
        get() = listOf(flightNumber, operationDate, serviceType, wheelchairLevel.orEmptyKey()).joinToString("|")
}

data class PendingServiceReview(
    val id: String,
    val fingerprint: String,
    val flightToken: String,
    val explicitDate: LocalDate?,
    val notificationDate: LocalDate,
    val serviceType: ServiceType,
    val wheelchairLevel: WheelchairLevel?,
    val count: Int?,
    val confidence: Confidence,
    val action: CandidateAction,
    val sourceEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val suggestedFlights: List<FlightReference>,
    val reviewStatus: ReviewStatus = ReviewStatus.NEEDS_REVIEW,
)

data class ProcessedFingerprint(
    val value: String,
    val sourceEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val ignored: Boolean = false,
)

data class SpecialServiceState(
    val records: List<FlightServiceRecord> = emptyList(),
    val pendingReviews: List<PendingServiceReview> = emptyList(),
    val gateChanges: List<GateChangeRecord> = emptyList(),
    val pendingGateChanges: List<ParsedGateChangeCandidate> = emptyList(),
    val standChanges: List<StandChangeRecord> = emptyList(),
    val pendingStandChanges: List<ParsedStandChangeCandidate> = emptyList(),
    val flightCancellations: List<FlightCancellationRecord> = emptyList(),
    val pendingFlightCancellations: List<ParsedFlightCancellationCandidate> = emptyList(),
    val processedFingerprints: List<ProcessedFingerprint> = emptyList(),
    val lastSuccessfulRecognitionEpochMillis: Long? = null,
    val lastProcessedEpochMillis: Long? = null,
    val lastProcessingResult: String? = null,
) {
    fun activeRecords(nowEpochMillis: Long = System.currentTimeMillis()): List<FlightServiceRecord> =
        records.filter { it.active && it.expiresAtEpochMillis > nowEpochMillis }

    fun activeGateChanges(nowEpochMillis: Long = System.currentTimeMillis()): List<GateChangeRecord> =
        gateChanges.filter { it.expiresAtEpochMillis > nowEpochMillis }

    fun activeStandChanges(nowEpochMillis: Long = System.currentTimeMillis()): List<StandChangeRecord> =
        standChanges.filter { it.expiresAtEpochMillis > nowEpochMillis }

    fun activeFlightCancellations(nowEpochMillis: Long = System.currentTimeMillis()): List<FlightCancellationRecord> =
        flightCancellations.filter { it.expiresAtEpochMillis > nowEpochMillis }
}

private fun WheelchairLevel?.orEmptyKey(): String = this?.name.orEmpty()
