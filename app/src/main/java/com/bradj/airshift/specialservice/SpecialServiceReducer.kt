package com.bradj.airshift.specialservice

import java.time.LocalDate

/** 一次归并的结果：新的记录列表、是否写入、是否与同一时刻的另一条消息冲突。 */
data class Reduction<T>(
    val records: List<T>,
    val applied: Boolean,
    val conflict: Boolean,
)

typealias SpecialServiceReduction = Reduction<FlightServiceRecord>
typealias GateChangeReduction = Reduction<GateChangeRecord>
typealias StandChangeReduction = Reduction<StandChangeRecord>
typealias FlightCancellationReduction = Reduction<FlightCancellationRecord>

/**
 * 四类记录（特服、登机口、机位、取消）共用的按 key 归并规则：
 * 比既有记录更早的候选被忽略；同一时刻但指纹不同视为冲突、不写入；否则替换同 key 的旧记录。
 */
internal fun <T : TimestampedRecord> List<T>.applyLatest(
    key: (T) -> String,
    candidateKey: String,
    candidateUpdatedAtEpochMillis: Long,
    candidateFingerprint: String,
    build: (existing: T?) -> T,
): Reduction<T> {
    val existing = firstOrNull { key(it) == candidateKey }
    fun replaced() = Reduction(
        records = filterNot { key(it) == candidateKey } + build(existing),
        applied = true,
        conflict = false,
    )
    return when {
        existing == null -> replaced()
        existing.updatedAtEpochMillis > candidateUpdatedAtEpochMillis ->
            Reduction(this, applied = false, conflict = false)
        existing.updatedAtEpochMillis == candidateUpdatedAtEpochMillis ->
            Reduction(this, applied = false, conflict = existing.fingerprint != candidateFingerprint)
        else -> replaced()
    }
}

object SpecialServiceReducer {
    fun apply(
        records: List<FlightServiceRecord>,
        candidate: ParsedServiceCandidate,
        flight: FlightReference,
        reviewStatus: ReviewStatus,
    ): SpecialServiceReduction = records.applyLatest(
        key = FlightServiceRecord::businessKey,
        candidateKey = recordKey(flight, candidate.serviceType, candidate.wheelchairLevel),
        candidateUpdatedAtEpochMillis = candidate.sourceEpochMillis,
        candidateFingerprint = candidate.fingerprint,
    ) {
        FlightServiceRecord(
            flightNumber = flight.flightNumber,
            operationDate = flight.operationDate,
            serviceType = candidate.serviceType,
            wheelchairLevel = candidate.wheelchairLevel,
            count = candidate.count,
            updatedAtEpochMillis = candidate.sourceEpochMillis,
            confidence = candidate.confidence,
            reviewStatus = reviewStatus,
            expiresAtEpochMillis = flight.expiresAtEpochMillis,
            fingerprint = candidate.fingerprint,
            active = candidate.action == CandidateAction.UPSERT,
        )
    }

    fun applyGateChange(
        records: List<GateChangeRecord>,
        candidate: ParsedGateChangeCandidate,
        flight: FlightReference,
    ): GateChangeReduction = records.applyLatest(
        key = GateChangeRecord::flightKey,
        candidateKey = flight.key,
        candidateUpdatedAtEpochMillis = candidate.sourceEpochMillis,
        candidateFingerprint = candidate.fingerprint,
    ) { existing ->
        GateChangeRecord(
            flightNumber = flight.flightNumber,
            operationDate = flight.operationDate,
            boardingGate = candidate.boardingGate,
            updatedAtEpochMillis = candidate.sourceEpochMillis,
            expiresAtEpochMillis = flight.expiresAtEpochMillis,
            fingerprint = candidate.fingerprint,
            // 候选未携带原登机口时（如「现为D66」），沿用既有记录的当前值，保持变更链完整。
            previousGate = candidate.previousGate ?: existing?.boardingGate,
        )
    }

    fun applyStandChange(
        records: List<StandChangeRecord>,
        candidate: ParsedStandChangeCandidate,
        flight: FlightReference,
    ): StandChangeReduction = records.applyLatest(
        key = StandChangeRecord::flightKey,
        candidateKey = flight.key,
        candidateUpdatedAtEpochMillis = candidate.sourceEpochMillis,
        candidateFingerprint = candidate.fingerprint,
    ) {
        StandChangeRecord(
            flightNumber = flight.flightNumber,
            operationDate = flight.operationDate,
            stand = candidate.stand,
            updatedAtEpochMillis = candidate.sourceEpochMillis,
            expiresAtEpochMillis = flight.expiresAtEpochMillis,
            fingerprint = candidate.fingerprint,
        )
    }

    fun applyFlightCancellation(
        records: List<FlightCancellationRecord>,
        candidate: ParsedFlightCancellationCandidate,
        flight: FlightReference,
    ): FlightCancellationReduction = records.applyLatest(
        key = FlightCancellationRecord::flightKey,
        candidateKey = flight.key,
        candidateUpdatedAtEpochMillis = candidate.sourceEpochMillis,
        candidateFingerprint = candidate.fingerprint,
    ) {
        FlightCancellationRecord(
            flightNumber = flight.flightNumber,
            operationDate = flight.operationDate,
            scope = candidate.scope,
            updatedAtEpochMillis = candidate.sourceEpochMillis,
            expiresAtEpochMillis = flight.expiresAtEpochMillis,
            fingerprint = candidate.fingerprint,
        )
    }
}

data class MucMessageReduction(
    val state: SpecialServiceState,
    val specialServicesAutoMatched: Int,
    val awaitingRoster: Int,
    val manualReviews: Int,
    val gateChangesApplied: Int,
    val standChangesApplied: Int,
    val cancellationsApplied: Int,
    val resolvedExpiryEpochMillis: Long,
)

/** 登机口 / 机位这类“一航班一记录”的候选归并状态。 */
private data class FacilityLane<C : MucCandidate, R>(
    val pending: List<C>,
    val records: List<R>,
    val applied: Int = 0,
    val awaitingRoster: Int = 0,
    val resolvedExpiryEpochMillis: Long = 0L,
)

/** 登机口与机位共用的候选归并：匹配不到排班进入等待列表；行程已取消且取消消息更新时不再更新。 */
private class FacilityReducer(
    private val flights: List<FlightReference>,
    private val cancellations: List<FlightCancellationRecord>,
) {
    fun <C : MucCandidate, R> reduce(
        candidates: List<C>,
        lane: FacilityLane<C, R>,
        apply: (List<R>, C, FlightReference) -> Reduction<R>,
    ): FacilityLane<C, R> = candidates.fold(lane) { current, candidate -> step(current, candidate, apply) }

    private fun <C : MucCandidate, R> step(
        lane: FacilityLane<C, R>,
        candidate: C,
        apply: (List<R>, C, FlightReference) -> Reduction<R>,
    ): FacilityLane<C, R> {
        val flight = candidate.matchAgainst(flights)
            ?: return lane.copy(
                pending = lane.pending.filterNot { it.id == candidate.id } + candidate,
                awaitingRoster = lane.awaitingRoster + 1,
            )
        val pending = lane.pending.filterNot { it.id == candidate.id }
        val expiry = maxOf(lane.resolvedExpiryEpochMillis, flight.expiresAtEpochMillis)
        val reduction = if (tripCancelledAfter(flight, candidate)) null else apply(lane.records, candidate, flight)
        return when {
            reduction == null || reduction.conflict -> lane.copy(pending = pending, resolvedExpiryEpochMillis = expiry)
            else -> lane.copy(
                pending = pending,
                records = reduction.records,
                applied = lane.applied + if (reduction.applied) 1 else 0,
                resolvedExpiryEpochMillis = expiry,
            )
        }
    }

    private fun tripCancelledAfter(flight: FlightReference, candidate: MucCandidate): Boolean {
        val cancellation = cancellations.firstOrNull { it.flightKey == flight.key } ?: return false
        return cancellation.scope == FlightCancellationScope.TRIP &&
            cancellation.updatedAtEpochMillis >= candidate.sourceEpochMillis
    }
}

object MucMessageReducer {
    fun apply(
        initial: SpecialServiceState,
        message: ParsedMucMessage,
        flights: List<FlightReference>,
    ): MucMessageReduction {
        var records = initial.records
        var pendingReviews = initial.pendingReviews
        var gateChanges = initial.gateChanges
        var pendingGateChanges = initial.pendingGateChanges
        var standChanges = initial.standChanges
        var pendingStandChanges = initial.pendingStandChanges
        var flightCancellations = initial.flightCancellations
        var pendingFlightCancellations = initial.pendingFlightCancellations
        var specialServicesAutoMatched = 0
        var awaitingRoster = 0
        var manualReviews = 0
        var cancellationsApplied = 0
        var resolvedExpiry = sequenceOf(
            message.serviceCandidates.maxOfOrNull(ParsedServiceCandidate::expiresAtEpochMillis),
            message.gateChanges.maxOfOrNull(ParsedGateChangeCandidate::expiresAtEpochMillis),
            message.standChanges.maxOfOrNull(ParsedStandChangeCandidate::expiresAtEpochMillis),
            message.flightCancellations.maxOfOrNull(ParsedFlightCancellationCandidate::expiresAtEpochMillis),
        ).filterNotNull().maxOrNull() ?: 0L

        // 1. 取消：先处理，因为它会级联作废同航班的特服、登机口与机位记录。
        message.flightCancellations.forEach { candidate ->
            val flight = candidate.matchAgainst(flights)
            if (flight == null) {
                pendingFlightCancellations = pendingFlightCancellations.filterNot { it.id == candidate.id } + candidate
                awaitingRoster++
                return@forEach
            }
            pendingFlightCancellations = pendingFlightCancellations.filterNot { it.id == candidate.id }
            val reduction = SpecialServiceReducer.applyFlightCancellation(flightCancellations, candidate, flight)
            if (!reduction.conflict) {
                flightCancellations = reduction.records
                if (reduction.applied) {
                    records = records.map { record ->
                        if (
                            record.flightNumber == flight.flightNumber &&
                            record.operationDate == flight.operationDate &&
                            record.updatedAtEpochMillis <= candidate.sourceEpochMillis
                        ) {
                            record.copy(
                                count = null,
                                updatedAtEpochMillis = candidate.sourceEpochMillis,
                                fingerprint = candidate.fingerprint,
                                active = false,
                            )
                        } else {
                            record
                        }
                    }
                    pendingReviews = pendingReviews.filterNot { review ->
                        review.sourceEpochMillis <= candidate.sourceEpochMillis && review.matches(flight)
                    }
                    if (candidate.scope == FlightCancellationScope.TRIP) {
                        gateChanges = gateChanges.filterNot { record ->
                            record.flightKey == flight.key && record.updatedAtEpochMillis <= candidate.sourceEpochMillis
                        }
                        pendingGateChanges = pendingGateChanges.filterNot { pending ->
                            pending.sourceEpochMillis <= candidate.sourceEpochMillis && pending.matches(flight)
                        }
                        standChanges = standChanges.filterNot { record ->
                            record.flightKey == flight.key && record.updatedAtEpochMillis <= candidate.sourceEpochMillis
                        }
                        pendingStandChanges = pendingStandChanges.filterNot { pending ->
                            pending.sourceEpochMillis <= candidate.sourceEpochMillis && pending.matches(flight)
                        }
                    }
                    cancellationsApplied++
                }
            }
            resolvedExpiry = maxOf(resolvedExpiry, flight.expiresAtEpochMillis)
        }

        // 2. 登机口与机位：同一套“行程已取消则不再更新”的规则。
        val facilities = FacilityReducer(flights, flightCancellations)
        val gateLane = facilities.reduce(
            message.gateChanges,
            FacilityLane(pendingGateChanges, gateChanges),
            SpecialServiceReducer::applyGateChange,
        )
        pendingGateChanges = gateLane.pending
        gateChanges = gateLane.records
        val standLane = facilities.reduce(
            message.standChanges,
            FacilityLane(pendingStandChanges, standChanges),
            SpecialServiceReducer::applyStandChange,
        )
        pendingStandChanges = standLane.pending
        standChanges = standLane.records
        awaitingRoster += gateLane.awaitingRoster + standLane.awaitingRoster
        resolvedExpiry = maxOf(resolvedExpiry, gateLane.resolvedExpiryEpochMillis, standLane.resolvedExpiryEpochMillis)

        // 3. 特服：低置信直接忽略；新于取消消息的 UPSERT 会撤销取消。
        message.serviceCandidates.forEach { candidate ->
            if (candidate.confidence == Confidence.LOW) {
                manualReviews++
                return@forEach
            }
            val flight = candidate.matchAgainst(flights)
            if (flight == null) {
                pendingReviews = pendingReviews.filterNot { it.id == candidate.id } + candidate.toPendingReview()
                awaitingRoster++
                return@forEach
            }
            pendingReviews = pendingReviews.filterNot { it.id == candidate.id }
            val cancellation = flightCancellations.firstOrNull { it.flightKey == flight.key }
            val cancelledByNewerMessage = candidate.action == CandidateAction.UPSERT &&
                cancellation != null && cancellation.updatedAtEpochMillis >= candidate.sourceEpochMillis
            if (!cancelledByNewerMessage) {
                if (
                    candidate.action == CandidateAction.UPSERT &&
                    cancellation != null && candidate.sourceEpochMillis > cancellation.updatedAtEpochMillis
                ) {
                    flightCancellations = flightCancellations.filterNot { it.flightKey == flight.key }
                }
                val reduction = SpecialServiceReducer.apply(records, candidate, flight, ReviewStatus.CONFIRMED)
                if (!reduction.conflict) {
                    records = reduction.records
                    if (reduction.applied) specialServicesAutoMatched++
                }
            }
            resolvedExpiry = maxOf(resolvedExpiry, flight.expiresAtEpochMillis)
        }

        return MucMessageReduction(
            state = initial.copy(
                records = records,
                pendingReviews = pendingReviews,
                gateChanges = gateChanges,
                pendingGateChanges = pendingGateChanges,
                standChanges = standChanges,
                pendingStandChanges = pendingStandChanges,
                flightCancellations = flightCancellations,
                pendingFlightCancellations = pendingFlightCancellations,
            ),
            specialServicesAutoMatched = specialServicesAutoMatched,
            awaitingRoster = awaitingRoster,
            manualReviews = manualReviews,
            gateChangesApplied = gateLane.applied,
            standChangesApplied = standLane.applied,
            cancellationsApplied = cancellationsApplied,
            resolvedExpiryEpochMillis = resolvedExpiry,
        )
    }

    fun reconcile(
        initial: SpecialServiceState,
        flights: List<FlightReference>,
    ): SpecialServiceState {
        fun refreshedExpiry(flightNumber: String, operationDate: LocalDate, fallback: Long): Long =
            flights.firstOrNull { it.flightNumber == flightNumber && it.operationDate == operationDate }
                ?.expiresAtEpochMillis ?: fallback

        val refreshed = initial.copy(
            records = initial.records.map { record ->
                record.copy(expiresAtEpochMillis = refreshedExpiry(record.flightNumber, record.operationDate, record.expiresAtEpochMillis))
            },
            pendingReviews = emptyList(),
            gateChanges = initial.gateChanges.map { record ->
                record.copy(expiresAtEpochMillis = refreshedExpiry(record.flightNumber, record.operationDate, record.expiresAtEpochMillis))
            },
            pendingGateChanges = emptyList(),
            standChanges = initial.standChanges.map { record ->
                record.copy(expiresAtEpochMillis = refreshedExpiry(record.flightNumber, record.operationDate, record.expiresAtEpochMillis))
            },
            pendingStandChanges = emptyList(),
            flightCancellations = initial.flightCancellations.map { record ->
                record.copy(expiresAtEpochMillis = refreshedExpiry(record.flightNumber, record.operationDate, record.expiresAtEpochMillis))
            },
            pendingFlightCancellations = emptyList(),
        )
        val pendingMessage = ParsedMucMessage(
            serviceCandidates = initial.pendingReviews.map(PendingServiceReview::toParsedCandidate),
            gateChanges = initial.pendingGateChanges,
            standChanges = initial.pendingStandChanges,
            flightCancellations = initial.pendingFlightCancellations,
        )
        return apply(refreshed, pendingMessage, flights).state
    }
}

object SpecialServiceExpiry {
    fun prune(state: SpecialServiceState, nowEpochMillis: Long): SpecialServiceState {
        fun <T : Fingerprinted> List<T>.alive(): List<T> = filter { it.expiresAtEpochMillis > nowEpochMillis }

        val records = state.records.alive()
        val pending = state.pendingReviews.alive()
        val gateChanges = state.gateChanges.alive()
        val pendingGateChanges = state.pendingGateChanges.alive()
        val standChanges = state.standChanges.alive()
        val pendingStandChanges = state.pendingStandChanges.alive()
        val flightCancellations = state.flightCancellations.alive()
        val pendingFlightCancellations = state.pendingFlightCancellations.alive()
        // 只要某条消息还有任何派生对象存活，它的指纹就要跟着保鲜，否则重复通知会被当成新消息。
        val referencedExpiry = (
            records + pending + gateChanges + pendingGateChanges +
                standChanges + pendingStandChanges + flightCancellations + pendingFlightCancellations
            )
            .groupBy(Fingerprinted::fingerprint)
            .mapValues { (_, items) -> items.maxOf(Fingerprinted::expiresAtEpochMillis) }
        val processed = state.processedFingerprints.map { fingerprint ->
            referencedExpiry[fingerprint.value]?.let { expiry ->
                fingerprint.copy(expiresAtEpochMillis = maxOf(fingerprint.expiresAtEpochMillis, expiry))
            } ?: fingerprint
        }.filter { it.expiresAtEpochMillis > nowEpochMillis }
        return state.copy(
            records = records,
            pendingReviews = pending,
            gateChanges = gateChanges,
            pendingGateChanges = pendingGateChanges,
            standChanges = standChanges,
            pendingStandChanges = pendingStandChanges,
            flightCancellations = flightCancellations,
            pendingFlightCancellations = pendingFlightCancellations,
            processedFingerprints = processed,
        )
    }
}

object SpecialServiceDedupe {
    fun isDuplicate(
        processedFingerprints: List<ProcessedFingerprint>,
        fingerprint: String,
        sourceEpochMillis: Long,
        sourceTimeReliable: Boolean,
        nowEpochMillis: Long,
    ): Boolean = processedFingerprints.any { processed ->
        processed.value == fingerprint &&
            processed.expiresAtEpochMillis > nowEpochMillis &&
            (!sourceTimeReliable || processed.sourceEpochMillis == sourceEpochMillis)
    }
}

private fun recordKey(
    flight: FlightReference,
    serviceType: ServiceType,
    wheelchairLevel: WheelchairLevel?,
): String = listOf(flight.flightNumber, flight.operationDate, serviceType, wheelchairLevel?.name.orEmpty()).joinToString("|")

internal fun ParsedServiceCandidate.toPendingReview() = PendingServiceReview(
    id = id,
    fingerprint = fingerprint,
    flightToken = flightToken,
    explicitDate = explicitDate,
    notificationDate = notificationDate,
    serviceType = serviceType,
    wheelchairLevel = wheelchairLevel,
    count = count,
    confidence = confidence,
    action = action,
    sourceEpochMillis = sourceEpochMillis,
    expiresAtEpochMillis = expiresAtEpochMillis,
)

internal fun PendingServiceReview.toParsedCandidate() = ParsedServiceCandidate(
    fingerprint = fingerprint,
    flightToken = flightToken,
    explicitDate = explicitDate,
    notificationDate = notificationDate,
    serviceType = serviceType,
    wheelchairLevel = wheelchairLevel,
    count = count,
    confidence = confidence,
    action = action,
    sourceEpochMillis = sourceEpochMillis,
    expiresAtEpochMillis = expiresAtEpochMillis,
)

private fun MucCandidate.matchAgainst(flights: List<FlightReference>): FlightReference? =
    RosterFlightMatcher.matchFlightToken(flightToken, explicitDate, notificationDate, flights).matched

private fun MucCandidate.matches(flight: FlightReference): Boolean = matchAgainst(listOf(flight)) != null
