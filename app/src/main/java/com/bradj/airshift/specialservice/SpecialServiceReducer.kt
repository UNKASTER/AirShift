package com.bradj.airshift.specialservice

data class SpecialServiceReduction(
    val records: List<FlightServiceRecord>,
    val applied: Boolean,
    val conflict: Boolean,
)

data class GateChangeReduction(
    val records: List<GateChangeRecord>,
    val applied: Boolean,
    val conflict: Boolean,
)

data class StandChangeReduction(
    val records: List<StandChangeRecord>,
    val applied: Boolean,
    val conflict: Boolean,
)

data class FlightCancellationReduction(
    val records: List<FlightCancellationRecord>,
    val applied: Boolean,
    val conflict: Boolean,
)

object SpecialServiceReducer {
    fun apply(
        records: List<FlightServiceRecord>,
        candidate: ParsedServiceCandidate,
        flight: FlightReference,
        reviewStatus: ReviewStatus,
    ): SpecialServiceReduction {
        val key = recordKey(flight, candidate.serviceType, candidate.wheelchairLevel)
        val existing = records.firstOrNull { it.businessKey == key }
        if (existing != null) {
            if (existing.updatedAtEpochMillis > candidate.sourceEpochMillis) {
                return SpecialServiceReduction(records, applied = false, conflict = false)
            }
            if (existing.updatedAtEpochMillis == candidate.sourceEpochMillis) {
                return if (existing.fingerprint == candidate.fingerprint) {
                    SpecialServiceReduction(records, applied = false, conflict = false)
                } else {
                    SpecialServiceReduction(records, applied = false, conflict = true)
                }
            }
        }
        val updated = FlightServiceRecord(
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
        return SpecialServiceReduction(
            records = records.filterNot { it.businessKey == key } + updated,
            applied = true,
            conflict = false,
        )
    }

    fun applyGateChange(
        records: List<GateChangeRecord>,
        candidate: ParsedGateChangeCandidate,
        flight: FlightReference,
    ): GateChangeReduction {
        val existing = records.firstOrNull { it.flightKey == flight.key }
        if (existing != null) {
            if (existing.updatedAtEpochMillis > candidate.sourceEpochMillis) {
                return GateChangeReduction(records, applied = false, conflict = false)
            }
            if (existing.updatedAtEpochMillis == candidate.sourceEpochMillis) {
                return GateChangeReduction(
                    records,
                    applied = false,
                    conflict = existing.fingerprint != candidate.fingerprint,
                )
            }
        }
        val updated = GateChangeRecord(
            flightNumber = flight.flightNumber,
            operationDate = flight.operationDate,
            boardingGate = candidate.boardingGate,
            updatedAtEpochMillis = candidate.sourceEpochMillis,
            expiresAtEpochMillis = flight.expiresAtEpochMillis,
            fingerprint = candidate.fingerprint,
        )
        return GateChangeReduction(
            records = records.filterNot { it.flightKey == flight.key } + updated,
            applied = true,
            conflict = false,
        )
    }

    fun applyStandChange(
        records: List<StandChangeRecord>,
        candidate: ParsedStandChangeCandidate,
        flight: FlightReference,
    ): StandChangeReduction {
        val existing = records.firstOrNull { it.flightKey == flight.key }
        if (existing != null) {
            if (existing.updatedAtEpochMillis > candidate.sourceEpochMillis) {
                return StandChangeReduction(records, applied = false, conflict = false)
            }
            if (existing.updatedAtEpochMillis == candidate.sourceEpochMillis) {
                return StandChangeReduction(
                    records,
                    applied = false,
                    conflict = existing.fingerprint != candidate.fingerprint,
                )
            }
        }
        val updated = StandChangeRecord(
            flightNumber = flight.flightNumber,
            operationDate = flight.operationDate,
            stand = candidate.stand,
            updatedAtEpochMillis = candidate.sourceEpochMillis,
            expiresAtEpochMillis = flight.expiresAtEpochMillis,
            fingerprint = candidate.fingerprint,
        )
        return StandChangeReduction(
            records = records.filterNot { it.flightKey == flight.key } + updated,
            applied = true,
            conflict = false,
        )
    }

    fun applyFlightCancellation(
        records: List<FlightCancellationRecord>,
        candidate: ParsedFlightCancellationCandidate,
        flight: FlightReference,
    ): FlightCancellationReduction {
        val existing = records.firstOrNull { it.flightKey == flight.key }
        if (existing != null) {
            if (existing.updatedAtEpochMillis > candidate.sourceEpochMillis) {
                return FlightCancellationReduction(records, applied = false, conflict = false)
            }
            if (existing.updatedAtEpochMillis == candidate.sourceEpochMillis) {
                return FlightCancellationReduction(
                    records,
                    applied = false,
                    conflict = existing.fingerprint != candidate.fingerprint,
                )
            }
        }
        val updated = FlightCancellationRecord(
            flightNumber = flight.flightNumber,
            operationDate = flight.operationDate,
            scope = candidate.scope,
            updatedAtEpochMillis = candidate.sourceEpochMillis,
            expiresAtEpochMillis = flight.expiresAtEpochMillis,
            fingerprint = candidate.fingerprint,
        )
        return FlightCancellationReduction(
            records = records.filterNot { it.flightKey == flight.key } + updated,
            applied = true,
            conflict = false,
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
        var gateChangesApplied = 0
        var standChangesApplied = 0
        var cancellationsApplied = 0
        var resolvedExpiry = sequenceOf(
            message.serviceCandidates.maxOfOrNull(ParsedServiceCandidate::expiresAtEpochMillis),
            message.gateChanges.maxOfOrNull(ParsedGateChangeCandidate::expiresAtEpochMillis),
            message.standChanges.maxOfOrNull(ParsedStandChangeCandidate::expiresAtEpochMillis),
            message.flightCancellations.maxOfOrNull(ParsedFlightCancellationCandidate::expiresAtEpochMillis),
        ).filterNotNull().maxOrNull() ?: 0L

        message.flightCancellations.forEach { candidate ->
            val match = RosterFlightMatcher.matchFlightToken(
                candidate.flightToken,
                candidate.explicitDate,
                candidate.notificationDate,
                flights,
            )
            val flight = match.matched
            if (flight == null) {
                pendingFlightCancellations = pendingFlightCancellations.filterNot { it.id == candidate.id } + candidate
                awaitingRoster++
            } else {
                val reduction = SpecialServiceReducer.applyFlightCancellation(flightCancellations, candidate, flight)
                pendingFlightCancellations = pendingFlightCancellations.filterNot { it.id == candidate.id }
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
        }

        message.gateChanges.forEach { candidate ->
            val match = RosterFlightMatcher.matchFlightToken(
                candidate.flightToken,
                candidate.explicitDate,
                candidate.notificationDate,
                flights,
            )
            val flight = match.matched
            if (flight == null) {
                pendingGateChanges = pendingGateChanges.filterNot { it.id == candidate.id } + candidate
                awaitingRoster++
            } else {
                pendingGateChanges = pendingGateChanges.filterNot { it.id == candidate.id }
                val cancellation = flightCancellations.firstOrNull { it.flightKey == flight.key }
                if (
                    cancellation?.scope != FlightCancellationScope.TRIP ||
                    cancellation.updatedAtEpochMillis < candidate.sourceEpochMillis
                ) {
                    val reduction = SpecialServiceReducer.applyGateChange(gateChanges, candidate, flight)
                    if (!reduction.conflict) {
                        gateChanges = reduction.records
                        if (reduction.applied) gateChangesApplied++
                    }
                }
                resolvedExpiry = maxOf(resolvedExpiry, flight.expiresAtEpochMillis)
            }
        }

        message.standChanges.forEach { candidate ->
            val match = RosterFlightMatcher.matchFlightToken(
                candidate.flightToken,
                candidate.explicitDate,
                candidate.notificationDate,
                flights,
            )
            val flight = match.matched
            if (flight == null) {
                pendingStandChanges = pendingStandChanges.filterNot { it.id == candidate.id } + candidate
                awaitingRoster++
            } else {
                pendingStandChanges = pendingStandChanges.filterNot { it.id == candidate.id }
                val cancellation = flightCancellations.firstOrNull { it.flightKey == flight.key }
                if (
                    cancellation?.scope != FlightCancellationScope.TRIP ||
                    cancellation.updatedAtEpochMillis < candidate.sourceEpochMillis
                ) {
                    val reduction = SpecialServiceReducer.applyStandChange(standChanges, candidate, flight)
                    if (!reduction.conflict) {
                        standChanges = reduction.records
                        if (reduction.applied) standChangesApplied++
                    }
                }
                resolvedExpiry = maxOf(resolvedExpiry, flight.expiresAtEpochMillis)
            }
        }

        message.serviceCandidates.forEach { candidate ->
            val match = RosterFlightMatcher.match(candidate, flights)
            val flight = match.matched
            if (candidate.confidence == Confidence.LOW) {
                pendingReviews = pendingReviews.filterNot { it.id == candidate.id } + candidate.toPendingReview(match.suggestions)
                manualReviews++
                return@forEach
            }
            if (flight == null) {
                pendingReviews = pendingReviews.filterNot { it.id == candidate.id } + candidate.toPendingReview(emptyList())
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
            gateChangesApplied = gateChangesApplied,
            standChangesApplied = standChangesApplied,
            cancellationsApplied = cancellationsApplied,
            resolvedExpiryEpochMillis = resolvedExpiry,
        )
    }

    fun reconcile(
        initial: SpecialServiceState,
        flights: List<FlightReference>,
    ): SpecialServiceState {
        fun refreshedExpiry(flightNumber: String, operationDate: java.time.LocalDate, fallback: Long): Long =
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
        val records = state.records.filter { it.expiresAtEpochMillis > nowEpochMillis }
        val pending = state.pendingReviews.filter { it.expiresAtEpochMillis > nowEpochMillis }
        val gateChanges = state.gateChanges.filter { it.expiresAtEpochMillis > nowEpochMillis }
        val pendingGateChanges = state.pendingGateChanges.filter { it.expiresAtEpochMillis > nowEpochMillis }
        val standChanges = state.standChanges.filter { it.expiresAtEpochMillis > nowEpochMillis }
        val pendingStandChanges = state.pendingStandChanges.filter { it.expiresAtEpochMillis > nowEpochMillis }
        val flightCancellations = state.flightCancellations.filter { it.expiresAtEpochMillis > nowEpochMillis }
        val pendingFlightCancellations = state.pendingFlightCancellations.filter { it.expiresAtEpochMillis > nowEpochMillis }
        val referencedExpiry = buildMap<String, Long> {
            records.forEach { record -> put(record.fingerprint, maxOf(get(record.fingerprint) ?: 0L, record.expiresAtEpochMillis)) }
            pending.forEach { review -> put(review.fingerprint, maxOf(get(review.fingerprint) ?: 0L, review.expiresAtEpochMillis)) }
            gateChanges.forEach { record -> put(record.fingerprint, maxOf(get(record.fingerprint) ?: 0L, record.expiresAtEpochMillis)) }
            pendingGateChanges.forEach { candidate -> put(candidate.fingerprint, maxOf(get(candidate.fingerprint) ?: 0L, candidate.expiresAtEpochMillis)) }
            standChanges.forEach { record -> put(record.fingerprint, maxOf(get(record.fingerprint) ?: 0L, record.expiresAtEpochMillis)) }
            pendingStandChanges.forEach { candidate -> put(candidate.fingerprint, maxOf(get(candidate.fingerprint) ?: 0L, candidate.expiresAtEpochMillis)) }
            flightCancellations.forEach { record -> put(record.fingerprint, maxOf(get(record.fingerprint) ?: 0L, record.expiresAtEpochMillis)) }
            pendingFlightCancellations.forEach { candidate -> put(candidate.fingerprint, maxOf(get(candidate.fingerprint) ?: 0L, candidate.expiresAtEpochMillis)) }
        }
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
            (processed.ignored || !sourceTimeReliable || processed.sourceEpochMillis == sourceEpochMillis)
    }
}

private fun recordKey(
    flight: FlightReference,
    serviceType: ServiceType,
    wheelchairLevel: WheelchairLevel?,
): String = listOf(flight.flightNumber, flight.operationDate, serviceType, wheelchairLevel?.name.orEmpty()).joinToString("|")

internal fun ParsedServiceCandidate.toPendingReview(suggestions: List<FlightReference>) = PendingServiceReview(
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
    suggestedFlights = suggestions,
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

private fun PendingServiceReview.matches(flight: FlightReference): Boolean =
    RosterFlightMatcher.matchFlightToken(flightToken, explicitDate, notificationDate, listOf(flight)).matched != null

private fun ParsedGateChangeCandidate.matches(flight: FlightReference): Boolean =
    RosterFlightMatcher.matchFlightToken(flightToken, explicitDate, notificationDate, listOf(flight)).matched != null

private fun ParsedStandChangeCandidate.matches(flight: FlightReference): Boolean =
    RosterFlightMatcher.matchFlightToken(flightToken, explicitDate, notificationDate, listOf(flight)).matched != null
