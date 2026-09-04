package com.bradj.airshift.ui.components

import com.bradj.airshift.specialservice.Confidence
import com.bradj.airshift.specialservice.FlightCancellationRecord
import com.bradj.airshift.specialservice.FlightServiceRecord
import com.bradj.airshift.specialservice.GateChangeRecord
import com.bradj.airshift.specialservice.RosterFlightMatcher
import com.bradj.airshift.specialservice.ServiceType
import com.bradj.airshift.specialservice.StandChangeRecord
import com.bradj.airshift.specialservice.normalizeGateCode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 航段详情条目类型。文案与“是否归到卡片底部的时钟 meta 区”都由类型决定，
 * 不再按中文标签字符串分派。
 */
enum class DetailKind(val label: String, val clockMeta: Boolean = false) {
    STAND("机位"),
    GATE_CLOSED("登机口关闭", clockMeta = true),
    OFF_BLOCK("实际离位", clockMeta = true),
    GATE_CHANGE("登机口变更"),
    STAND_CHANGE_SOURCE("机位变更"),
    BOARDING_START("预计登机开始"),
    BOARDING_END("预计登机口关闭"),
}

/** 航段详情行：灰字小标签 + 深色值；hasChange 为 true 时附加"变更"提醒。 */
data class DetailEntry(val kind: DetailKind, val value: String, val hasChange: Boolean = false) {
    val label: String get() = kind.label
}

internal fun LocalDateTime?.formatClock(): String =
    this?.format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)) ?: "--:--"

internal fun Long.formatEpoch(pattern: String): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern(pattern, Locale.CHINA))

internal fun List<FlightServiceRecord>.forFlight(
    flight: String,
    operationDate: LocalDate?,
): List<FlightServiceRecord> {
    if (operationDate == null) return emptyList()
    val normalizedFlight = RosterFlightMatcher.normalizeFlight(flight)
    return filter { it.flightNumber == normalizedFlight && it.operationDate == operationDate }
}

internal fun List<GateChangeRecord>.gateForFlight(
    flight: String,
    operationDate: LocalDate?,
): GateChangeRecord? {
    if (operationDate == null) return null
    val normalizedFlight = RosterFlightMatcher.normalizeFlight(flight)
    return firstOrNull { it.flightNumber == normalizedFlight && it.operationDate == operationDate }
}

/**
 * 登机口变更的显示值：原登机口优先取 MUC 消息中的记录；缺失时回退实时值，
 * 但实时值与新值归一化后相同（如 A08 与 A8）时不显示原值，避免出现 "D65 → D65"。
 */
internal fun gateChangeDisplayValue(currentGate: String?, change: GateChangeRecord): String {
    val from = change.previousGate
        ?: currentGate?.takeIf { normalizeGateCode(it) != normalizeGateCode(change.boardingGate) }
    return listOfNotNull(from, change.boardingGate).joinToString(" → ")
}

internal fun List<StandChangeRecord>.standForFlight(
    flight: String,
    operationDate: LocalDate?,
): StandChangeRecord? {
    if (operationDate == null) return null
    val normalizedFlight = RosterFlightMatcher.normalizeFlight(flight)
    return firstOrNull { it.flightNumber == normalizedFlight && it.operationDate == operationDate }
}

internal fun List<FlightCancellationRecord>.cancellationForFlight(
    flight: String,
    operationDate: LocalDate?,
): FlightCancellationRecord? {
    if (operationDate == null) return null
    val normalizedFlight = RosterFlightMatcher.normalizeFlight(flight)
    return firstOrNull { it.flightNumber == normalizedFlight && it.operationDate == operationDate }
}

internal fun FlightServiceRecord.badgeLabel(): String =
    "${when (serviceType) {
        ServiceType.DISABILITY -> "障残"
        ServiceType.WHEELCHAIR -> wheelchairLevel?.name ?: "轮椅"
        ServiceType.UNACCOMPANIED_MINOR -> "UM"
        ServiceType.MAAS -> "MAAS"
        ServiceType.CABIN_PET -> "客舱宠物"
    }}${count?.let { " ×$it" }.orEmpty()}"

internal fun FlightServiceRecord.typeLabel(): String = when (serviceType) {
    ServiceType.WHEELCHAIR -> "轮椅旅客${wheelchairLevel?.let { "（${it.name}）" }.orEmpty()}"
    else -> serviceType.label()
}

internal fun ServiceType.label(): String = when (this) {
    ServiceType.DISABILITY -> "残障旅客"
    ServiceType.WHEELCHAIR -> "轮椅旅客"
    ServiceType.UNACCOMPANIED_MINOR -> "UM 无陪伴儿童"
    ServiceType.MAAS -> "MAAS 全流程陪伴"
    ServiceType.CABIN_PET -> "客舱宠物"
}

internal fun Confidence.label(): String = when (this) {
    Confidence.HIGH -> "高置信"
    Confidence.LOW -> "低置信"
}
