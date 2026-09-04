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

/** 所有 MUC 派生对象共有的消息指纹与过期时间；过期清理与指纹保鲜只依赖这两项。 */
interface Fingerprinted {
    val fingerprint: String
    val expiresAtEpochMillis: Long
}

/** 从一条通知解析出、尚未落到具体航班上的候选（含等待排班的待匹配项）。 */
interface MucCandidate : Fingerprinted {
    val id: String
    val flightToken: String
    val explicitDate: LocalDate?
    val notificationDate: LocalDate
    val sourceEpochMillis: Long
}

/** 已落到具体航班上的记录；同 key 的记录按 [updatedAtEpochMillis] 归并（见 `applyLatest`）。 */
interface TimestampedRecord : Fingerprinted {
    val updatedAtEpochMillis: Long
}

data class ParsedServiceCandidate(
    override val fingerprint: String,
    override val flightToken: String,
    override val explicitDate: LocalDate?,
    override val notificationDate: LocalDate,
    val serviceType: ServiceType,
    val wheelchairLevel: WheelchairLevel?,
    val count: Int?,
    val confidence: Confidence,
    val action: CandidateAction,
    override val sourceEpochMillis: Long,
    override val expiresAtEpochMillis: Long,
) : MucCandidate {
    override val id: String
        get() = listOf(fingerprint, flightToken, serviceType.name, wheelchairLevel?.name.orEmpty()).joinToString("|")
}

data class ParsedGateChangeCandidate(
    override val fingerprint: String,
    override val flightToken: String,
    override val explicitDate: LocalDate?,
    override val notificationDate: LocalDate,
    val boardingGate: String,
    override val sourceEpochMillis: Long,
    override val expiresAtEpochMillis: Long,
    val previousGate: String? = null,
) : MucCandidate {
    override val id: String
        get() = listOf(fingerprint, flightToken, boardingGate).joinToString("|")
}

data class GateChangeRecord(
    val flightNumber: String,
    val operationDate: LocalDate,
    val boardingGate: String,
    override val updatedAtEpochMillis: Long,
    override val expiresAtEpochMillis: Long,
    override val fingerprint: String,
    val previousGate: String? = null,
) : TimestampedRecord {
    val flightKey: String
        get() = "$flightNumber|$operationDate"
}

private val gateCodeRegex = Regex("([A-Z]*)(\\d+)([A-Z]*)")

/** 登机口/机位编号归一化：大写、去空格、数字部分去前导零（A08 与 A8 视为同一登机口）。 */
fun normalizeGateCode(value: String): String {
    val compact = value.trim().uppercase().replace(" ", "")
    val match = gateCodeRegex.matchEntire(compact) ?: return compact
    val digits = match.groupValues[2].trimStart('0')
    return "${match.groupValues[1]}${digits.ifEmpty { "0" }}${match.groupValues[3]}"
}

data class ParsedStandChangeCandidate(
    override val fingerprint: String,
    override val flightToken: String,
    override val explicitDate: LocalDate?,
    override val notificationDate: LocalDate,
    val stand: String,
    override val sourceEpochMillis: Long,
    override val expiresAtEpochMillis: Long,
) : MucCandidate {
    override val id: String
        get() = listOf(fingerprint, flightToken, stand).joinToString("|")
}

data class StandChangeRecord(
    val flightNumber: String,
    val operationDate: LocalDate,
    val stand: String,
    override val updatedAtEpochMillis: Long,
    override val expiresAtEpochMillis: Long,
    override val fingerprint: String,
) : TimestampedRecord {
    val flightKey: String
        get() = "$flightNumber|$operationDate"
}

data class ParsedFlightCancellationCandidate(
    override val fingerprint: String,
    override val flightToken: String,
    override val explicitDate: LocalDate?,
    override val notificationDate: LocalDate,
    val scope: FlightCancellationScope,
    override val sourceEpochMillis: Long,
    override val expiresAtEpochMillis: Long,
) : MucCandidate {
    override val id: String
        get() = listOf(fingerprint, flightToken, scope.name).joinToString("|")
}

data class FlightCancellationRecord(
    val flightNumber: String,
    val operationDate: LocalDate,
    val scope: FlightCancellationScope,
    override val updatedAtEpochMillis: Long,
    override val expiresAtEpochMillis: Long,
    override val fingerprint: String,
) : TimestampedRecord {
    val flightKey: String
        get() = "$flightNumber|$operationDate"
}

data class FlightServiceRecord(
    val flightNumber: String,
    val operationDate: LocalDate,
    val serviceType: ServiceType,
    val wheelchairLevel: WheelchairLevel?,
    val count: Int?,
    override val updatedAtEpochMillis: Long,
    val confidence: Confidence,
    val reviewStatus: ReviewStatus,
    override val expiresAtEpochMillis: Long,
    override val fingerprint: String,
    val active: Boolean = true,
) : TimestampedRecord {
    val businessKey: String
        get() = listOf(flightNumber, operationDate, serviceType, wheelchairLevel.orEmptyKey()).joinToString("|")
}

/** 尚未匹配到排班航班的特服候选；排班变化时重新匹配。 */
data class PendingServiceReview(
    override val id: String,
    override val fingerprint: String,
    override val flightToken: String,
    override val explicitDate: LocalDate?,
    override val notificationDate: LocalDate,
    val serviceType: ServiceType,
    val wheelchairLevel: WheelchairLevel?,
    val count: Int?,
    val confidence: Confidence,
    val action: CandidateAction,
    override val sourceEpochMillis: Long,
    override val expiresAtEpochMillis: Long,
    val reviewStatus: ReviewStatus = ReviewStatus.NEEDS_REVIEW,
) : MucCandidate

data class ProcessedFingerprint(
    val value: String,
    val sourceEpochMillis: Long,
    val expiresAtEpochMillis: Long,
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
