package com.bradj.airshift.specialservice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SpecialServiceParserTest {
    private val sourceTime = Instant.parse("2026-08-26T00:00:00Z").toEpochMilli()
    private val zoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun parsesPlanSamplesWithoutTakingSeatOrBaggageNumbers() {
        val um = parse("2473一位um").single()
        assertEquals("2473", um.flightToken)
        assertEquals(ServiceType.UNACCOMPANIED_MINOR, um.serviceType)
        assertEquals(1, um.count)

        val baggage = parse("9831一个UM，33J，托运1件，随身2件").single()
        assertEquals(1, baggage.count)

        val maas = parse("2447一位MAAS全流程陪伴服务").single()
        assertEquals(ServiceType.MAAS, maas.serviceType)
        assertEquals(1, maas.count)

        val wheelchair = parse("9670旅客临时轮椅服务：32C，WCHS").single()
        assertEquals(WheelchairLevel.WCHS, wheelchair.wheelchairLevel)
        assertEquals(1, wheelchair.count)
    }

    @Test
    fun detectsCabinPetsAndNeverPersistsMessageTextInCandidate() {
        val candidates = parse("MU857 客舱宠物；MU6802 宠物进客舱")

        assertEquals(listOf("MU857", "MU6802"), candidates.map { it.flightToken })
        assertTrue(candidates.all { it.serviceType == ServiceType.CABIN_PET })
        assertTrue(candidates.all { it.count == null })
        assertTrue(candidates.all { it.fingerprint.length == 64 })
    }

    @Test
    fun weakAliasNeedsReviewAndIsNotCancellation() {
        val candidate = parse("无随行，6940值机已办理完毕，特服单据").single()

        assertEquals(ServiceType.UNACCOMPANIED_MINOR, candidate.serviceType)
        assertEquals(Confidence.LOW, candidate.confidence)
        assertEquals(CandidateAction.UPSERT, candidate.action)
    }

    @Test
    fun ignoresOrdinaryFlightConversation() {
        assertTrue(parse("MU6700 有没有充电宝").isEmpty())
        assertTrue(parse("明天有特服吗").isEmpty())
    }

    @Test
    fun supportsFullWidthCaseDatesAndAllServiceTypes() {
        val candidates = parse("２０２６／８／２７ ＭＵ２４７３：um、MAAS、WCHR、残障旅客、客舱宠物")

        assertEquals(LocalDate.of(2026, 8, 27), candidates.first().explicitDate)
        assertTrue(candidates.all { it.flightToken == "MU2473" })
        assertEquals(
            setOf(
                ServiceType.UNACCOMPANIED_MINOR,
                ServiceType.MAAS,
                ServiceType.WHEELCHAIR,
                ServiceType.DISABILITY,
                ServiceType.CABIN_PET,
            ),
            candidates.map { it.serviceType }.toSet(),
        )
    }

    @Test
    fun genericWheelchairNeedsReviewButExplicitCodeIsHighConfidence() {
        assertEquals(Confidence.LOW, parse("MU9670需要轮椅").single().confidence)
        val coded = parse("MU9670需要轮椅 WCHC").single()
        assertEquals(Confidence.HIGH, coded.confidence)
        assertEquals(WheelchairLevel.WCHC, coded.wheelchairLevel)
    }

    @Test
    fun parsesMucWheelchairShorthandLetters() {
        val cLevel = parse("MU9670 C轮").single()
        assertEquals(WheelchairLevel.WCHC, cLevel.wheelchairLevel)
        assertEquals(Confidence.HIGH, cLevel.confidence)

        val rLevel = parse("2473一位r轮").single()
        assertEquals(WheelchairLevel.WCHR, rLevel.wheelchairLevel)
        assertEquals(1, rLevel.count)

        val sLevel = parse("MU2473旅客S 轮，33J").single()
        assertEquals(WheelchairLevel.WCHS, sLevel.wheelchairLevel)
        assertEquals(1, sLevel.count)

        // 代码与简称可在同一句混用
        val changed = parse("MU2473 WCHR改为S轮").associateBy { it.wheelchairLevel }
        assertEquals(CandidateAction.CANCEL, changed.getValue(WheelchairLevel.WCHR).action)
        assertEquals(CandidateAction.UPSERT, changed.getValue(WheelchairLevel.WCHS).action)

        // 座位号「32C」后接“轮椅”不是 C 轮；代码「WCHS」后接“轮”不会再识别出一条 S 轮
        val seat = parse("MU9670 座位32C轮椅").single()
        assertNull(seat.wheelchairLevel)
        assertEquals(Confidence.LOW, seat.confidence)
        assertEquals(WheelchairLevel.WCHS, parse("MU9670 WCHS轮椅").single().wheelchairLevel)
    }

    @Test
    fun handlesCancellationCorrectionAndTypeChange() {
        assertEquals(CandidateAction.CANCEL, parse("MU2473取消UM服务").single().action)

        val correction = parse("MU2473 UM 1人更正为2人").single()
        assertEquals(CandidateAction.UPSERT, correction.action)
        assertEquals(2, correction.count)

        val changed = parse("MU2473 UM改为MAAS").associateBy { it.serviceType }
        assertEquals(CandidateAction.CANCEL, changed.getValue(ServiceType.UNACCOMPANIED_MINOR).action)
        assertEquals(CandidateAction.UPSERT, changed.getValue(ServiceType.MAAS).action)

        val scopedCancellation = parse("MU2473 UM无需轮椅").associateBy { it.serviceType }
        assertEquals(CandidateAction.UPSERT, scopedCancellation.getValue(ServiceType.UNACCOMPANIED_MINOR).action)
        assertEquals(CandidateAction.CANCEL, scopedCancellation.getValue(ServiceType.WHEELCHAIR).action)

        val wheelchairChange = parse("MU2473 WCHR改为WCHS").associateBy { it.wheelchairLevel }
        assertEquals(CandidateAction.CANCEL, wheelchairChange.getValue(WheelchairLevel.WCHR).action)
        assertEquals(CandidateAction.UPSERT, wheelchairChange.getValue(WheelchairLevel.WCHS).action)
    }

    @Test
    fun parsesBoardingGateChangesWithoutTreatingGateAsFlightNumber() {
        val changed = parseMessage("MU719登机口由A2变更为A5").gateChanges.single()
        assertEquals("MU719", changed.flightToken)
        assertEquals("A5", changed.boardingGate)

        assertEquals("H18", parseMessage("719登机口从A12调整至H18").gateChanges.single().boardingGate)
        assertEquals("205", parseMessage("MU719登机口更改：205").gateChanges.single().boardingGate)
        assertEquals(
            "A5",
            parseMessage("MU719登机口变更为A5，登机口关闭时间不变").gateChanges.single().boardingGate,
        )
        assertTrue(parseMessage("MU719登机口关闭时间调整为12:30").gateChanges.isEmpty())
    }

    @Test
    fun parsesMucStyleGateChangesWithPreviousGate() {
        val simple = parseMessage("2233登机口D64改D65").gateChanges.single()
        assertEquals("2233", simple.flightToken)
        assertEquals("D65", simple.boardingGate)
        assertEquals("D64", simple.previousGate)

        val mentioned = parseMessage("6828登机口C53改C55@甲子 @甲丑").gateChanges.single()
        assertEquals("C55", mentioned.boardingGate)
        assertEquals("C53", mentioned.previousGate)

        // 一条消息内的连续变更：最新值取链尾，原值取链头
        val chained = parseMessage("2416登机口C57改C55 改C54").gateChanges.single()
        assertEquals("C54", chained.boardingGate)
        assertEquals("C57", chained.previousGate)

        assertEquals("A2", parseMessage("MU719登机口由A2变更为A5").gateChanges.single().previousGate)
        assertNull(parseMessage("MU719登机口更改：205").gateChanges.single().previousGate)

        // 归一化后相同（A08 与 A8）不算变更
        assertTrue(parseMessage("2233登机口A08改A8").gateChanges.isEmpty())
    }

    @Test
    fun normalizesGateCodesWithLeadingZeros() {
        assertEquals("A8", normalizeGateCode("A08"))
        assertEquals("A8", normalizeGateCode("a8"))
        assertEquals("A8", normalizeGateCode("A8"))
        assertEquals("D65", normalizeGateCode("D65"))
        assertEquals("365R", normalizeGateCode("365R"))
        assertEquals("205", normalizeGateCode("205"))
    }

    @Test
    fun parsesStandChangesWithoutTreatingStandAsFlightNumber() {
        val numeric = parseMessage("719机位由302变更为305").standChanges.single()
        assertEquals("719", numeric.flightToken)
        assertEquals("305", numeric.stand)

        assertEquals("A15", parseMessage("MU719出发机位从A12调整至A15").standChanges.single().stand)
        assertEquals("205", parseMessage("MU719到达机位更改：205").standChanges.single().stand)
        assertTrue(parseMessage("MU719机位关闭时间调整为1230").standChanges.isEmpty())
    }

    @Test
    fun parsesAbandonedServiceAndCancelledTrip() {
        val abandonedUm = parse("MU2473 UM旅客放弃服务").single()
        assertEquals(CandidateAction.CANCEL, abandonedUm.action)
        assertEquals(Confidence.HIGH, abandonedUm.confidence)

        val abandonedWheelchair = parse("MU9670旅客放弃轮椅服务").single()
        assertEquals(CandidateAction.CANCEL, abandonedWheelchair.action)
        assertEquals(Confidence.HIGH, abandonedWheelchair.confidence)

        val cancelledTrip = parseMessage("719旅客取消行程").flightCancellations.single()
        assertEquals("719", cancelledTrip.flightToken)
        assertEquals(FlightCancellationScope.TRIP, cancelledTrip.scope)

        val cancelledServices = parseMessage("719全部特服放弃").flightCancellations.single()
        assertEquals(FlightCancellationScope.SPECIAL_SERVICES, cancelledServices.scope)
        assertEquals(
            FlightCancellationScope.SPECIAL_SERVICES,
            parseMessage("719特服旅客放弃服务").flightCancellations.single().scope,
        )
        assertEquals(
            FlightCancellationScope.TRIP,
            parseMessage("719旅客取消后续行程").flightCancellations.single().scope,
        )
        assertTrue(parseMessage("MU719航班取消，等待后续通知").flightCancellations.isEmpty())
    }

    @Test
    fun doesNotUsePhoneTicketSeatOrWeightAsQuantity() {
        val candidate = parse("MU857 客舱宠物，电话13812345678，票号7812345678901，座位33J，重量5KG").single()
        assertNull(candidate.count)
        assertFalse(candidate.flightToken.contains("138"))
    }

    @Test
    fun keyedFingerprintIsStableButDifferentFromBareDigest() {
        val bare = SpecialServiceParser.parse(listOf("MU2473一位UM"), sourceTime, zoneId).single().fingerprint
        val keyed = SpecialServiceParser.parse(listOf("MU2473一位UM"), sourceTime, zoneId, ByteArray(32) { 7 }).single().fingerprint

        assertEquals(keyed, SpecialServiceParser.parse(listOf("MU2473一位UM"), sourceTime, zoneId, ByteArray(32) { 7 }).single().fingerprint)
        assertFalse(bare == keyed)
    }

    private fun parse(text: String): List<ParsedServiceCandidate> =
        SpecialServiceParser.parse(listOf(text), sourceTime, zoneId)

    private fun parseMessage(text: String): ParsedMucMessage =
        SpecialServiceParser.parseMessage(listOf(text), sourceTime, zoneId)
}
