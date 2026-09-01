package com.bradj.airshift.specialservice

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

data class ParsedMucMessage(
    val serviceCandidates: List<ParsedServiceCandidate> = emptyList(),
    val gateChanges: List<ParsedGateChangeCandidate> = emptyList(),
    val standChanges: List<ParsedStandChangeCandidate> = emptyList(),
    val flightCancellations: List<ParsedFlightCancellationCandidate> = emptyList(),
)

object SpecialServiceParser {
    private const val COUNT_TOKEN = "(?:\\d{1,2}|[零〇一二两三四五六七八九十百]{1,3})"
    private val flightRegex = Regex("(?<![A-Z0-9])(?:([A-Z]{2,3})\\s*)?(\\d{3,4})(?![A-Z0-9])")
    private val fullDateRegex = Regex("(?<!\\d)(20\\d{2})[-/.年](\\d{1,2})[-/.月](\\d{1,2})日?(?!\\d)")
    private val monthDayRegex = Regex("(?<!\\d)(\\d{1,2})(?:[-/.]|月)(\\d{1,2})日?(?!\\d)")
    private val cancelRegex = Regex("取消|撤销|无需|不再需要|不需要|放弃")
    private val correctionRegex = Regex("更正为|更正|改为")
    private val gateChangeRegex = Regex(
        "登机口.{0,18}?(?:(?:变更|更改|调整|改|换|变)(?:为|至|到)?|现为)\\s*[:：]?\\s*([A-Z]?\\d{1,3}[A-Z]?)",
    )
    // 链式写法：「登机口[由|从]X改Y[ 改Z…]」，可还原原登机口 X，且最新值为链尾（如「C57改C55 改C54」→ C54）。
    private val gateTokenRegex = Regex("[A-Z]?\\d{1,3}[A-Z]?")
    private val gateChangeChainRegex = Regex(
        "登机口\\s*(?:由|从)?\\s*(${gateTokenRegex.pattern})" +
            "((?:[\\s,，、]*(?:变更|更改|调整|改|换|变)(?:为|至|到)?\\s*[:：]?\\s*(${gateTokenRegex.pattern}))+)",
    )
    private val standChangeRegex = Regex(
        "机位.{0,18}?(?:(?:变更|更改|调整|改|换|变)(?:为|至|到)?|现为)\\s*[:：]?\\s*([A-Z]?\\d{1,4}[A-Z]?)",
    )
    private val tripCancellationRegex = Regex(
        "取消(?:后续)?行程|行程(?:已)?取消|取消出行|取消(?:乘机|乘坐)|放弃(?:后续)?行程|终止行程|不再成行|不成行|不再乘机|不乘机|退票(?:完成|成功|已办|已办理)?",
    )
    private val allSpecialServiceCancellationRegex = Regex(
        "(?:全部|所有)?特服.{0,6}?(?:取消|撤销|放弃)(?:服务)?|(?:取消|撤销|放弃)(?:全部|所有)?特服(?:服务)?",
    )

    fun parse(
        texts: List<String>,
        sourceEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        fingerprintKey: ByteArray? = null,
        notificationEpochMillis: Long = sourceEpochMillis,
    ): List<ParsedServiceCandidate> = parseMessage(
        texts = texts,
        sourceEpochMillis = sourceEpochMillis,
        zoneId = zoneId,
        fingerprintKey = fingerprintKey,
        notificationEpochMillis = notificationEpochMillis,
    ).serviceCandidates

    fun parseMessage(
        texts: List<String>,
        sourceEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        fingerprintKey: ByteArray? = null,
        notificationEpochMillis: Long = sourceEpochMillis,
    ): ParsedMucMessage {
        val normalizedParts = texts.map(::normalize).filter(String::isNotBlank).distinct()
        if (normalizedParts.isEmpty()) return ParsedMucMessage()
        val combined = normalizedParts.joinToString("\n")
        val notificationDate = Instant.ofEpochMilli(notificationEpochMillis).atZone(zoneId).toLocalDate()
        val explicitDate = extractDate(combined, notificationDate)
        val fingerprint = fingerprint(combined, fingerprintKey)
        val expiresAt = sourceEpochMillis + ChronoUnit.HOURS.duration.multipliedBy(24).toMillis()
        val segments = combined.split(Regex("[\\n；;。！？!?]+"))
            .map(String::trim)
            .filter(String::isNotBlank)
        val serviceCandidates = buildList {
            segments.forEach { segment ->
                addAll(
                    parseSegment(
                        segment = segment,
                        fallbackText = combined,
                        fingerprint = fingerprint,
                        explicitDate = extractDate(segment, notificationDate) ?: explicitDate,
                        notificationDate = notificationDate,
                        sourceEpochMillis = sourceEpochMillis,
                        expiresAtEpochMillis = expiresAt,
                    ),
                )
            }
        }.distinctBy(ParsedServiceCandidate::id)
        val gateChanges = segments.flatMap { segment ->
            parseGateChangeSegment(
                segment = segment,
                fallbackText = combined,
                fingerprint = fingerprint,
                explicitDate = extractDate(segment, notificationDate) ?: explicitDate,
                notificationDate = notificationDate,
                sourceEpochMillis = sourceEpochMillis,
                expiresAtEpochMillis = expiresAt,
            )
        }.distinctBy(ParsedGateChangeCandidate::id)
        val standChanges = segments.flatMap { segment ->
            parseStandChangeSegment(
                segment = segment,
                fallbackText = combined,
                fingerprint = fingerprint,
                explicitDate = extractDate(segment, notificationDate) ?: explicitDate,
                notificationDate = notificationDate,
                sourceEpochMillis = sourceEpochMillis,
                expiresAtEpochMillis = expiresAt,
            )
        }.distinctBy(ParsedStandChangeCandidate::id)
        val flightCancellations = segments.flatMap { segment ->
            parseFlightCancellationSegment(
                segment = segment,
                fallbackText = combined,
                fingerprint = fingerprint,
                explicitDate = extractDate(segment, notificationDate) ?: explicitDate,
                notificationDate = notificationDate,
                sourceEpochMillis = sourceEpochMillis,
                expiresAtEpochMillis = expiresAt,
            )
        }.distinctBy(ParsedFlightCancellationCandidate::id)
        return ParsedMucMessage(
            serviceCandidates = serviceCandidates,
            gateChanges = gateChanges,
            standChanges = standChanges,
            flightCancellations = flightCancellations,
        )
    }

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .uppercase()
        .replace('\u00a0', ' ')
        .replace(Regex("[\\t\\r ]+"), " ")
        .trim()

    private fun parseSegment(
        segment: String,
        fallbackText: String,
        fingerprint: String,
        explicitDate: LocalDate?,
        notificationDate: LocalDate,
        sourceEpochMillis: Long,
        expiresAtEpochMillis: Long,
    ): List<ParsedServiceCandidate> {
        val mentions = findServiceMentions(segment)
        if (mentions.isEmpty()) return emptyList()
        val localFlights = findFlightTokens(segment)
        val flightTokens = if (localFlights.isNotEmpty()) localFlights else findFlightTokens(fallbackText)
        if (flightTokens.isEmpty()) return emptyList()
        val correction = correctionRegex.find(segment)
        val servicesAfterCorrection = correction?.let { marker ->
            mentions.filter { it.start > marker.range.last }.map { it.type to it.level }.toSet()
        }.orEmpty()

        return buildList {
            mentions.distinctBy { it.type to it.level }.forEach { mention ->
                val action = when {
                    cancellationApplies(segment, mention) -> CandidateAction.CANCEL
                    correction != null &&
                        mention.start < correction.range.first &&
                        servicesAfterCorrection.any { it != (mention.type to mention.level) } -> CandidateAction.CANCEL
                    else -> CandidateAction.UPSERT
                }
                val count = if (action == CandidateAction.CANCEL) null else extractCount(segment, mention)
                flightTokens.forEach { flightToken ->
                    add(
                        ParsedServiceCandidate(
                            fingerprint = fingerprint,
                            flightToken = flightToken,
                            explicitDate = explicitDate,
                            notificationDate = notificationDate,
                            serviceType = mention.type,
                            wheelchairLevel = mention.level,
                            count = count,
                            confidence = if (action == CandidateAction.CANCEL) Confidence.HIGH else mention.confidence,
                            action = action,
                            sourceEpochMillis = sourceEpochMillis,
                            expiresAtEpochMillis = expiresAtEpochMillis,
                        ),
                    )
                }
            }
        }
    }

    private fun parseGateChangeSegment(
        segment: String,
        fallbackText: String,
        fingerprint: String,
        explicitDate: LocalDate?,
        notificationDate: LocalDate,
        sourceEpochMillis: Long,
        expiresAtEpochMillis: Long,
    ): List<ParsedGateChangeCandidate> {
        val closedGuard = Regex("登机口(?:关闭|开放|开启|截止|时间)")
        val boardingGate: String
        val previousGate: String?
        val chain = gateChangeChainRegex.find(segment)?.takeIf { !closedGuard.containsMatchIn(it.value) }
        if (chain != null) {
            previousGate = chain.groupValues[1].trim().uppercase()
            boardingGate = gateTokenRegex.findAll(chain.groupValues[2]).lastOrNull()?.value
                ?.trim()?.uppercase() ?: return emptyList()
        } else {
            val gateChange = gateChangeRegex.find(segment) ?: return emptyList()
            if (closedGuard.containsMatchIn(gateChange.value)) return emptyList()
            boardingGate = gateChange.groupValues[1].trim().uppercase()
            previousGate = null
        }
        // 归一化后相同（如 A08改A8）不算变更。
        if (previousGate != null && normalizeGateCode(previousGate) == normalizeGateCode(boardingGate)) {
            return emptyList()
        }
        val localFlights = findFlightTokens(segment)
        val flightTokens = if (localFlights.isNotEmpty()) localFlights else findFlightTokens(fallbackText)
        return flightTokens.map { flightToken ->
            ParsedGateChangeCandidate(
                fingerprint = fingerprint,
                flightToken = flightToken,
                explicitDate = explicitDate,
                notificationDate = notificationDate,
                boardingGate = boardingGate,
                sourceEpochMillis = sourceEpochMillis,
                expiresAtEpochMillis = expiresAtEpochMillis,
                previousGate = previousGate,
            )
        }
    }

    private fun parseFlightCancellationSegment(
        segment: String,
        fallbackText: String,
        fingerprint: String,
        explicitDate: LocalDate?,
        notificationDate: LocalDate,
        sourceEpochMillis: Long,
        expiresAtEpochMillis: Long,
    ): List<ParsedFlightCancellationCandidate> {
        val scope = when {
            tripCancellationRegex.containsMatchIn(segment) -> FlightCancellationScope.TRIP
            allSpecialServiceCancellationRegex.containsMatchIn(segment) -> FlightCancellationScope.SPECIAL_SERVICES
            else -> return emptyList()
        }
        val localFlights = findFlightTokens(segment)
        val flightTokens = if (localFlights.isNotEmpty()) localFlights else findFlightTokens(fallbackText)
        return flightTokens.map { flightToken ->
            ParsedFlightCancellationCandidate(
                fingerprint = fingerprint,
                flightToken = flightToken,
                explicitDate = explicitDate,
                notificationDate = notificationDate,
                scope = scope,
                sourceEpochMillis = sourceEpochMillis,
                expiresAtEpochMillis = expiresAtEpochMillis,
            )
        }
    }

    private fun parseStandChangeSegment(
        segment: String,
        fallbackText: String,
        fingerprint: String,
        explicitDate: LocalDate?,
        notificationDate: LocalDate,
        sourceEpochMillis: Long,
        expiresAtEpochMillis: Long,
    ): List<ParsedStandChangeCandidate> {
        val standChange = standChangeRegex.find(segment) ?: return emptyList()
        if (Regex("机位(?:关闭|开放|开启|截止|时间)").containsMatchIn(standChange.value)) return emptyList()
        val stand = standChange.groupValues[1].trim().uppercase()
        val localFlights = findFlightTokens(segment)
        val flightTokens = if (localFlights.isNotEmpty()) localFlights else findFlightTokens(fallbackText)
        return flightTokens.map { flightToken ->
            ParsedStandChangeCandidate(
                fingerprint = fingerprint,
                flightToken = flightToken,
                explicitDate = explicitDate,
                notificationDate = notificationDate,
                stand = stand,
                sourceEpochMillis = sourceEpochMillis,
                expiresAtEpochMillis = expiresAtEpochMillis,
            )
        }
    }

    private fun findFlightTokens(text: String): List<String> {
        val matches = flightRegex.findAll(text)
            .filterNot { match -> isLikelyNonFlightNumber(text, match.range, match.groupValues[1]) }
            .toList()
        val preferred = matches.takeIf { values -> values.none { it.groupValues[1].isNotBlank() } }
            ?: matches.filter { it.groupValues[1].isNotBlank() }
        return preferred.map { match ->
            val prefix = match.groupValues[1]
            val digits = match.groupValues[2]
            when (prefix) {
                "" -> digits
                "CES" -> "MU$digits"
                else -> "$prefix$digits"
            }
        }.distinct()
    }

    private fun isLikelyNonFlightNumber(text: String, range: IntRange, carrier: String): Boolean {
        if (carrier.isNotBlank()) return false
        val dateRanges = fullDateRegex.findAll(text).map(MatchResult::range) + monthDayRegex.findAll(text).map(MatchResult::range)
        if (dateRanges.any { dateRange -> range.first >= dateRange.first && range.last <= dateRange.last }) return true
        val facilityChangeRanges = gateChangeChainRegex.findAll(text).map(MatchResult::range) +
            gateChangeRegex.findAll(text).map(MatchResult::range) +
            standChangeRegex.findAll(text).map(MatchResult::range)
        if (facilityChangeRanges.any { changeRange -> range.first >= changeRange.first && range.last <= changeRange.last }) return true
        val before = text.substring(maxOf(0, range.first - 5), range.first)
        val after = text.substring(range.last + 1, minOf(text.length, range.last + 6))
        if (Regex("(?:时间|时刻|日期|电话|手机|票号|座位|登机口)$").containsMatchIn(before)) return true
        if (Regex("^(?:点|时|分|分钟|号|年|月|日)").containsMatchIn(after)) return true
        val digits = text.substring(range)
        return digits.length == 4 && digits.take(2).toIntOrNull() in 0..23 &&
            digits.takeLast(2).toIntOrNull() in 0..59 &&
            Regex("(?:计划|预计|实际|到达|起飞|落地|时间)").containsMatchIn(before)
    }

    private fun findServiceMentions(text: String): List<ServiceMention> = buildList {
        val wheelchairCodes = Regex("WCHR|WCHS|WCHC").findAll(text).toList()
        wheelchairCodes.forEach { match ->
            val level = when (match.value) {
                "WCHR" -> WheelchairLevel.WCHR
                "WCHS" -> WheelchairLevel.WCHS
                "WCHC" -> WheelchairLevel.WCHC
                else -> null
            }
            add(ServiceMention(ServiceType.WHEELCHAIR, level, Confidence.HIGH, match.range.first, match.range.last))
        }
        if (wheelchairCodes.isEmpty()) {
            Regex("轮椅").findAll(text).forEach { match ->
                add(ServiceMention(ServiceType.WHEELCHAIR, null, Confidence.LOW, match.range.first, match.range.last))
            }
        }
        Regex("(?<![A-Z])UM(?![A-Z])|无陪伴儿童|无成人陪伴儿童|无人陪儿童").findAll(text).forEach { match ->
            add(ServiceMention(ServiceType.UNACCOMPANIED_MINOR, null, Confidence.HIGH, match.range.first, match.range.last))
        }
        Regex("(?<![A-Z])MAAS(?![A-Z])|全流程陪伴").findAll(text).forEach { match ->
            add(ServiceMention(ServiceType.MAAS, null, Confidence.HIGH, match.range.first, match.range.last))
        }
        Regex("客舱宠物|宠物(?:进入|进)客舱|小动物(?:进入|进)客舱").findAll(text).forEach { match ->
            add(ServiceMention(ServiceType.CABIN_PET, null, Confidence.HIGH, match.range.first, match.range.last))
        }
        Regex("残障|残疾|行动不便").findAll(text).forEach { match ->
            add(ServiceMention(ServiceType.DISABILITY, null, Confidence.HIGH, match.range.first, match.range.last))
        }
        if (none { it.type == ServiceType.UNACCOMPANIED_MINOR }) {
            Regex("无随行").findAll(text).forEach { match ->
                add(ServiceMention(ServiceType.UNACCOMPANIED_MINOR, null, Confidence.LOW, match.range.first, match.range.last))
            }
        }
    }

    private fun cancellationApplies(text: String, mention: ServiceMention): Boolean {
        val matches = cancelRegex.findAll(text).toList()
        if (matches.isEmpty()) return false
        val preceding = matches.any { it.range.last < mention.start && mention.start - it.range.last <= 8 }
        val onlyServiceType = findServiceMentions(text).distinctBy(ServiceMention::type).size == 1
        return preceding || onlyServiceType
    }

    private fun extractCount(text: String, mention: ServiceMention): Int? {
        correctionRegex.findAll(text).lastOrNull()?.let { marker ->
            val tail = text.substring(marker.range.last + 1)
            Regex("($COUNT_TOKEN)\\s*(?:位|名|个|人|只)")
                .find(tail)?.groupValues?.get(1)?.let(::parseCount)?.let { return it }
        }
        val prefix = text.substring(maxOf(0, mention.start - 12), mention.start)
        Regex("($COUNT_TOKEN)\\s*(?:位|名|个|人|只)\\s*$")
            .find(prefix)?.groupValues?.get(1)?.let(::parseCount)?.let { count ->
                if (count in 1..99) return count
            }
        val suffix = text.substring(mention.end + 1, minOf(text.length, mention.end + 18))
        Regex("^[,:，：\\s]*(?:儿童|旅客|乘客|人员)?\\s*(?:[X×*]\\s*)?($COUNT_TOKEN)\\s*(?:位|名|个|人|只)")
            .find(suffix)?.groupValues?.get(1)?.let(::parseCount)?.let { return it }
        if (mention.type == ServiceType.WHEELCHAIR && Regex("旅客.{0,4}(?:轮椅|WCHR|WCHS|WCHC)").containsMatchIn(text)) {
            return 1
        }
        return null
    }

    private fun parseCount(raw: String): Int? {
        raw.toIntOrNull()?.let { return it.takeIf { value -> value in 1..99 } }
        val normalized = raw.replace('两', '二').replace('〇', '零')
        val digits = mapOf('零' to 0, '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        if (normalized == "十") return 10
        if ('十' in normalized) {
            val parts = normalized.split('十')
            val tens = if (parts.first().isEmpty()) 1 else digits[parts.first().singleOrNull()] ?: return null
            val ones = if (parts.getOrNull(1).isNullOrEmpty()) 0 else digits[parts[1].singleOrNull()] ?: return null
            return (tens * 10 + ones).takeIf { it in 1..99 }
        }
        return normalized.map { digits[it] ?: return null }
            .joinToString("")
            .toIntOrNull()
            ?.takeIf { it in 1..99 }
    }

    private fun extractDate(text: String, notificationDate: LocalDate): LocalDate? {
        fullDateRegex.find(text)?.let { match ->
            return runCatching {
                LocalDate.of(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
            }.getOrNull()
        }
        monthDayRegex.find(text)?.let { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            return listOf(notificationDate.year - 1, notificationDate.year, notificationDate.year + 1)
                .mapNotNull { year -> runCatching { LocalDate.of(year, month, day) }.getOrNull() }
                .minByOrNull { candidate -> abs(ChronoUnit.DAYS.between(notificationDate, candidate)) }
        }
        return null
    }

    private fun fingerprint(value: String, key: ByteArray?): String {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        val digest = if (key == null) {
            MessageDigest.getInstance("SHA-256").digest(bytes)
        } else {
            Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(key, "HmacSHA256"))
                doFinal(bytes)
            }
        }
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private data class ServiceMention(
        val type: ServiceType,
        val level: WheelchairLevel?,
        val confidence: Confidence,
        val start: Int,
        val end: Int,
    )
}
