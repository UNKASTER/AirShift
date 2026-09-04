package com.bradj.airshift.specialservice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.time.LocalDate

class SpecialServiceStateTest {
    private val date = LocalDate.of(2026, 8, 26)
    private val flight = FlightReference("MU2473", date, 10_000L)

    @Test
    fun reducerKeepsLatestUpdateAndCancellationTombstone() {
        val initial = SpecialServiceReducer.apply(
            emptyList(),
            candidate(source = 100L, fingerprint = "first"),
            flight,
            ReviewStatus.CONFIRMED,
        ).records
        assertTrue(initial.single().active)
        assertEquals(1, initial.single().count)

        val cancelled = SpecialServiceReducer.apply(
            initial,
            candidate(source = 200L, fingerprint = "cancel", action = CandidateAction.CANCEL),
            flight,
            ReviewStatus.CONFIRMED,
        ).records
        assertFalse(cancelled.single().active)

        val oldSummary = SpecialServiceReducer.apply(
            cancelled,
            candidate(source = 150L, fingerprint = "old-summary"),
            flight,
            ReviewStatus.CONFIRMED,
        )
        assertFalse(oldSummary.applied)
        assertFalse(oldSummary.records.single().active)
        assertEquals("cancel", oldSummary.records.single().fingerprint)

        val reactivated = SpecialServiceReducer.apply(
            cancelled,
            candidate(source = 300L, fingerprint = "first"),
            flight,
            ReviewStatus.CONFIRMED,
        ).records.single()
        assertTrue(reactivated.active)
        assertEquals("first", reactivated.fingerprint)
    }

    @Test
    fun dedupeUsesStableMessageTimeButConservativelySuppressesFallbackSummaries() {
        val processed = listOf(ProcessedFingerprint("body", 100L, 1_000L))

        assertTrue(SpecialServiceDedupe.isDuplicate(processed, "body", 100L, true, 500L))
        assertFalse(SpecialServiceDedupe.isDuplicate(processed, "body", 200L, true, 500L))
        assertTrue(SpecialServiceDedupe.isDuplicate(processed, "body", 200L, false, 500L))
        assertFalse(SpecialServiceDedupe.isDuplicate(processed, "body", 100L, true, 1_000L))
    }

    @Test
    fun equalTimestampWithDifferentFingerprintNeedsReview() {
        val existing = SpecialServiceReducer.apply(
            emptyList(),
            candidate(source = 100L, fingerprint = "first"),
            flight,
            ReviewStatus.CONFIRMED,
        ).records

        val conflict = SpecialServiceReducer.apply(
            existing,
            candidate(source = 100L, fingerprint = "different"),
            flight,
            ReviewStatus.CONFIRMED,
        )

        assertTrue(conflict.conflict)
        assertFalse(conflict.applied)
        assertEquals("first", conflict.records.single().fingerprint)
    }

    @Test
    fun expiryRemovesPendingAtBoundaryAndRetainsFingerprintWithRecord() {
        val record = SpecialServiceReducer.apply(
            emptyList(),
            candidate(source = 100L, fingerprint = "record"),
            flight,
            ReviewStatus.CONFIRMED,
        ).records.single()
        val pending = candidate(source = 100L, fingerprint = "pending")
            .copy(expiresAtEpochMillis = 500L)
            .toPending()
        val state = SpecialServiceState(
            records = listOf(record),
            pendingReviews = listOf(pending),
            processedFingerprints = listOf(
                ProcessedFingerprint("record", 100L, 500L),
                ProcessedFingerprint("pending", 100L, 500L),
            ),
        )

        val pruned = SpecialServiceExpiry.prune(state, 500L)

        assertTrue(pruned.pendingReviews.isEmpty())
        assertEquals(listOf("record"), pruned.processedFingerprints.map { it.value })
        assertEquals(10_000L, pruned.processedFingerprints.single().expiresAtEpochMillis)
        assertTrue(SpecialServiceExpiry.prune(pruned, 10_000L).records.isEmpty())
    }

    @Test
    fun jsonRoundTripContainsOnlyRedactedStructuredState() {
        val record = SpecialServiceReducer.apply(
            emptyList(),
            candidate(source = 100L, fingerprint = "hmac-value"),
            flight,
            ReviewStatus.CONFIRMED,
        ).records.single()
        val state = SpecialServiceState(
            records = listOf(record),
            pendingReviews = listOf(candidate(200L, "pending-hmac").toPending()),
            gateChanges = listOf(
                GateChangeRecord("MU2473", date, "A5", 210L, 10_000L, "gate-hmac", previousGate = "A2"),
            ),
            pendingGateChanges = listOf(gateCandidate(220L, "pending-gate-hmac")),
            standChanges = listOf(
                StandChangeRecord("MU2473", date, "305", 215L, 10_000L, "stand-hmac"),
            ),
            pendingStandChanges = listOf(standCandidate(225L, "pending-stand-hmac")),
            flightCancellations = listOf(
                FlightCancellationRecord(
                    "MU2473",
                    date,
                    FlightCancellationScope.SPECIAL_SERVICES,
                    230L,
                    10_000L,
                    "cancel-hmac",
                ),
            ),
            pendingFlightCancellations = listOf(flightCancellation(240L, "pending-cancel-hmac")),
            processedFingerprints = listOf(ProcessedFingerprint("hmac-value", 100L, 10_000L)),
            lastSuccessfulRecognitionEpochMillis = 300L,
            lastProcessedEpochMillis = 301L,
            lastProcessingResult = "识别 1 条：自动关联 1 条，待确认 0 条",
        )

        val encoded = SpecialServiceJsonCodec.encode(state)
        val decoded = SpecialServiceJsonCodec.decode(encoded)

        assertEquals(state, decoded)
        listOf("张三", "13812345678", "7812345678901", "33J", "原始正文").forEach {
            assertFalse(encoded.contains(it))
        }
        assertFalse(encoded.contains("rawText"))
        assertFalse(encoded.contains("sender"))

        val previousVersion = JSONObject(encoded).apply {
            put("version", 2)
            remove("standChanges")
            remove("pendingStandChanges")
        }
        val previousDecoded = SpecialServiceJsonCodec.decode(previousVersion.toString())
        assertEquals(state.records, previousDecoded.records)
        assertEquals(state.gateChanges, previousDecoded.gateChanges)
        assertTrue(previousDecoded.standChanges.isEmpty())

        val legacy = JSONObject(previousVersion.toString()).apply {
            put("version", 1)
            remove("gateChanges")
            remove("pendingGateChanges")
            remove("flightCancellations")
            remove("pendingFlightCancellations")
        }
        val legacyDecoded = SpecialServiceJsonCodec.decode(legacy.toString())
        assertEquals(state.records, legacyDecoded.records)
        assertTrue(legacyDecoded.gateChanges.isEmpty())
    }

    @Test
    fun highConfidenceAutoMatchesNumericPartWhileLowConfidenceIsIgnored() {
        val high = MucMessageReducer.apply(
            SpecialServiceState(),
            ParsedMucMessage(serviceCandidates = listOf(candidate(100L, "high"))),
            listOf(flight),
        )
        assertEquals(1, high.specialServicesAutoMatched)
        assertTrue(high.state.pendingReviews.isEmpty())
        assertTrue(high.state.activeRecords(0L).single().active)

        val low = MucMessageReducer.apply(
            SpecialServiceState(),
            ParsedMucMessage(serviceCandidates = listOf(candidate(100L, "low").copy(confidence = Confidence.LOW))),
            listOf(flight),
        )
        assertEquals(1, low.manualReviews)
        assertEquals(0, low.specialServicesAutoMatched)
        assertTrue(low.state.pendingReviews.isEmpty())
        assertTrue(low.state.records.isEmpty())

        val unmatched = MucMessageReducer.apply(
            SpecialServiceState(),
            ParsedMucMessage(serviceCandidates = listOf(candidate(100L, "waiting").copy(flightToken = "9999"))),
            listOf(flight),
        )
        assertEquals(1, unmatched.awaitingRoster)
        assertEquals(0, unmatched.manualReviews)
        assertEquals(Confidence.HIGH, unmatched.state.pendingReviews.single().confidence)

        val reconciled = MucMessageReducer.reconcile(
            unmatched.state,
            listOf(FlightReference("MU9999", date, 10_000L)),
        )
        assertTrue(reconciled.pendingReviews.isEmpty())
        assertEquals("MU9999", reconciled.activeRecords(0L).single().flightNumber)
    }

    @Test
    fun gateAndStandChangesRespectMessageOrderAndTripCancellation() {
        val pendingGate = MucMessageReducer.apply(
            SpecialServiceState(),
            ParsedMucMessage(gateChanges = listOf(gateCandidate(90L, "pending-gate"))),
            emptyList(),
        ).state
        assertEquals(1, pendingGate.pendingGateChanges.size)
        val reconciledGate = MucMessageReducer.reconcile(pendingGate, listOf(flight))
        assertTrue(reconciledGate.pendingGateChanges.isEmpty())
        assertEquals("A5", reconciledGate.gateChanges.single().boardingGate)

        val firstGate = MucMessageReducer.apply(
            SpecialServiceState(),
            ParsedMucMessage(gateChanges = listOf(gateCandidate(100L, "gate-1", "A5"))),
            listOf(flight),
        ).state
        val newerGate = MucMessageReducer.apply(
            firstGate,
            ParsedMucMessage(gateChanges = listOf(gateCandidate(200L, "gate-2", "A8"))),
            listOf(flight),
        ).state
        val afterOldGate = MucMessageReducer.apply(
            newerGate,
            ParsedMucMessage(gateChanges = listOf(gateCandidate(150L, "gate-old", "A2"))),
            listOf(flight),
        ).state
        assertEquals("A8", afterOldGate.gateChanges.single().boardingGate)

        val pendingStand = MucMessageReducer.apply(
            SpecialServiceState(),
            ParsedMucMessage(standChanges = listOf(standCandidate(90L, "pending-stand"))),
            emptyList(),
        ).state
        assertEquals(1, pendingStand.pendingStandChanges.size)
        val reconciledStand = MucMessageReducer.reconcile(pendingStand, listOf(flight))
        assertTrue(reconciledStand.pendingStandChanges.isEmpty())
        assertEquals("305", reconciledStand.standChanges.single().stand)

        val firstStand = MucMessageReducer.apply(
            afterOldGate,
            ParsedMucMessage(standChanges = listOf(standCandidate(100L, "stand-1", "305"))),
            listOf(flight),
        )
        assertEquals(1, firstStand.standChangesApplied)
        val newerStand = MucMessageReducer.apply(
            firstStand.state,
            ParsedMucMessage(standChanges = listOf(standCandidate(200L, "stand-2", "A15"))),
            listOf(flight),
        ).state
        val afterOldStand = MucMessageReducer.apply(
            newerStand,
            ParsedMucMessage(standChanges = listOf(standCandidate(150L, "stand-old", "302"))),
            listOf(flight),
        ).state
        assertEquals("A15", afterOldStand.standChanges.single().stand)

        val pendingTrip = MucMessageReducer.apply(
            SpecialServiceState(),
            ParsedMucMessage(flightCancellations = listOf(flightCancellation(80L, "pending-trip"))),
            emptyList(),
        ).state
        val reconciledTrip = MucMessageReducer.reconcile(pendingTrip, listOf(flight))
        assertTrue(reconciledTrip.pendingFlightCancellations.isEmpty())
        assertEquals(FlightCancellationScope.TRIP, reconciledTrip.flightCancellations.single().scope)

        val withService = MucMessageReducer.apply(
            afterOldStand,
            ParsedMucMessage(serviceCandidates = listOf(candidate(100L, "service"))),
            listOf(flight),
        ).state
        val cancelled = MucMessageReducer.apply(
            withService,
            ParsedMucMessage(flightCancellations = listOf(flightCancellation(300L, "trip"))),
            listOf(flight),
        ).state
        assertFalse(cancelled.records.single().active)
        assertTrue(cancelled.gateChanges.isEmpty())
        assertTrue(cancelled.standChanges.isEmpty())
        assertEquals(FlightCancellationScope.TRIP, cancelled.flightCancellations.single().scope)

        val afterOldService = MucMessageReducer.apply(
            cancelled,
            ParsedMucMessage(serviceCandidates = listOf(candidate(250L, "old-service"))),
            listOf(flight),
        ).state
        assertFalse(afterOldService.records.single().active)

        val restored = MucMessageReducer.apply(
            afterOldService,
            ParsedMucMessage(serviceCandidates = listOf(candidate(400L, "restored"))),
            listOf(flight),
        ).state
        assertTrue(restored.records.single().active)
        assertTrue(restored.flightCancellations.isEmpty())
    }

    @Test
    fun gateChangeInheritsPreviousGateFromExistingRecordWhenAbsent() {
        val first = MucMessageReducer.apply(
            SpecialServiceState(),
            ParsedMucMessage(gateChanges = listOf(gateCandidate(100L, "gate-1", "A5").copy(previousGate = "A2"))),
            listOf(flight),
        ).state
        assertEquals("A5", first.gateChanges.single().boardingGate)
        assertEquals("A2", first.gateChanges.single().previousGate)

        // 新消息未携带原登机口（如「现为A8」）时，沿用既有记录的当前值，保持变更链完整
        val chained = MucMessageReducer.apply(
            first,
            ParsedMucMessage(gateChanges = listOf(gateCandidate(200L, "gate-2", "A8"))),
            listOf(flight),
        ).state
        assertEquals("A8", chained.gateChanges.single().boardingGate)
        assertEquals("A5", chained.gateChanges.single().previousGate)
    }

    @Test
    fun gateChangeJsonRoundTripsPreviousGateAndToleratesLegacyData() {
        val record = GateChangeRecord("MU2473", date, "A5", 210L, 10_000L, "gate-hmac", previousGate = "A2")
        val encoded = SpecialServiceJsonCodec.encode(SpecialServiceState(gateChanges = listOf(record)))
        assertEquals(record, SpecialServiceJsonCodec.decode(encoded).gateChanges.single())

        // 旧版本数据没有 previousGate 字段，解码为 null
        val legacy = JSONObject(encoded).apply {
            getJSONArray("gateChanges").getJSONObject(0).remove("previousGate")
        }
        assertNull(SpecialServiceJsonCodec.decode(legacy.toString()).gateChanges.single().previousGate)
    }

    @Test
    fun corruptEntryDoesNotDiscardOtherValidRecords() {
        val validRecord = SpecialServiceReducer.apply(
            emptyList(),
            candidate(source = 100L, fingerprint = "valid"),
            flight,
            ReviewStatus.CONFIRMED,
        ).records.single()
        val root = JSONObject(SpecialServiceJsonCodec.encode(SpecialServiceState(records = listOf(validRecord))))
        root.getJSONArray("records").put(JSONObject().put("flightNumber", "BROKEN"))

        val decoded = SpecialServiceJsonCodec.decode(root.toString())

        assertEquals(listOf(validRecord), decoded.records)
    }

    @Test
    fun olderUnreadableSummaryCannotRegressNewerProcessingStatus() {
        val recognized = SpecialServiceState(
            lastSuccessfulRecognitionEpochMillis = 300L,
            lastProcessedEpochMillis = 300L,
            lastProcessingResult = "识别 1 条：自动关联 0 条，待确认 1 条",
        )

        assertEquals(recognized, recognized.withUnreadableNotification(200L))

        val newerUnreadable = recognized.withUnreadableNotification(400L)
        assertEquals(400L, newerUnreadable.lastProcessedEpochMillis)
        assertEquals("MUC 通知没有提供可读正文；请用无个人信息测试消息核实通知样式", newerUnreadable.lastProcessingResult)

        val repaired = recognized.copy(
            lastProcessedEpochMillis = 200L,
            lastProcessingResult = "MUC 通知没有提供可读正文；请用无个人信息测试消息核实通知样式",
        ).withRepairedProcessingStatus()
        assertEquals(300L, repaired.lastProcessedEpochMillis)
        assertEquals("最近一次 MUC 通知已成功识别；较旧的不可读摘要已忽略", repaired.lastProcessingResult)
    }

    private fun candidate(
        source: Long,
        fingerprint: String,
        action: CandidateAction = CandidateAction.UPSERT,
    ) = ParsedServiceCandidate(
        fingerprint = fingerprint,
        flightToken = "2473",
        explicitDate = null,
        notificationDate = date,
        serviceType = ServiceType.UNACCOMPANIED_MINOR,
        wheelchairLevel = null,
        count = 1,
        confidence = Confidence.HIGH,
        action = action,
        sourceEpochMillis = source,
        expiresAtEpochMillis = 1_000L,
    )

    private fun gateCandidate(
        source: Long,
        fingerprint: String,
        gate: String = "A5",
    ) = ParsedGateChangeCandidate(
        fingerprint = fingerprint,
        flightToken = "2473",
        explicitDate = null,
        notificationDate = date,
        boardingGate = gate,
        sourceEpochMillis = source,
        expiresAtEpochMillis = 1_000L,
    )

    private fun standCandidate(
        source: Long,
        fingerprint: String,
        stand: String = "305",
    ) = ParsedStandChangeCandidate(
        fingerprint = fingerprint,
        flightToken = "2473",
        explicitDate = null,
        notificationDate = date,
        stand = stand,
        sourceEpochMillis = source,
        expiresAtEpochMillis = 1_000L,
    )

    private fun flightCancellation(
        source: Long,
        fingerprint: String,
    ) = ParsedFlightCancellationCandidate(
        fingerprint = fingerprint,
        flightToken = "2473",
        explicitDate = null,
        notificationDate = date,
        scope = FlightCancellationScope.TRIP,
        sourceEpochMillis = source,
        expiresAtEpochMillis = 1_000L,
    )

    private fun ParsedServiceCandidate.toPending() = PendingServiceReview(
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
}
