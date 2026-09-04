package com.bradj.airshift.ui.components

import com.bradj.airshift.model.DutyTimeline
import com.bradj.airshift.model.LegDirection
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.specialservice.FlightCancellationRecord
import com.bradj.airshift.specialservice.FlightServiceRecord
import com.bradj.airshift.specialservice.GateChangeRecord
import com.bradj.airshift.specialservice.StandChangeRecord
import java.time.LocalDateTime

/** 一张任务卡看到的 MUC 上下文：已按过期过滤的可见记录。 */
data class MucContext(
    val specialServiceRecords: List<FlightServiceRecord> = emptyList(),
    val gateChanges: List<GateChangeRecord> = emptyList(),
    val standChanges: List<StandChangeRecord> = emptyList(),
    val flightCancellations: List<FlightCancellationRecord> = emptyList(),
)

/**
 * 任务卡的详略：全部执勤页只用“变更”角标提示 MUC 变更、只用“特服”角标提示特服；
 * 当前执勤页展开原值 → 新值、MUC 更新时间、预计登机开始/关闭与特服明细。
 */
enum class DetailLevel { SUMMARY, FULL }

/** [FlightRow] 的输入：一段航段在卡片上要显示的全部内容，由 [legUiModels] 从排班与 MUC 上下文算出。 */
data class FlightLegUiModel(
    val direction: LegDirection,
    val flight: String,
    val fromCode: String?,
    val fromName: String?,
    val toCode: String?,
    val toName: String?,
    val planned: LocalDateTime?,
    val estimated: LocalDateTime?,
    val actual: LocalDateTime?,
    val hasSpecialServices: Boolean,
    val specialServices: List<FlightServiceRecord>,
    val flightCancellation: FlightCancellationRecord?,
    val originDetails: List<DetailEntry>,
    val destinationDetails: List<DetailEntry>,
    val details: List<DetailEntry>,
    val aircraftRegistration: String?,
    val aircraftType: String?,
    val offBlock: LocalDateTime?,
)

/** 进港段在前、出港段在后；机号/机型只挂在最后一段上。 */
fun RosterAssignment.legUiModels(muc: MucContext, level: DetailLevel): List<FlightLegUiModel> = buildList {
    inboundFlight?.let { flight -> add(inboundLeg(flight, muc, level, isLast = outboundFlight == null)) }
    outboundFlight?.let { flight -> add(outboundLeg(flight, muc, level)) }
}

private fun RosterAssignment.inboundLeg(
    flight: String,
    muc: MucContext,
    level: DetailLevel,
    isLast: Boolean,
): FlightLegUiModel {
    val operationDate = scheduledArrival?.toLocalDate()
    val gateChange = muc.gateChanges.gateForFlight(flight, operationDate)
    val standChange = muc.standChanges.standForFlight(flight, operationDate)
    val services = muc.specialServiceRecords.forFlight(flight, operationDate)
    val full = level == DetailLevel.FULL
    return FlightLegUiModel(
        direction = LegDirection.INBOUND,
        flight = flight,
        fromCode = originCode,
        fromName = origin,
        toCode = localAirportCode,
        toName = localAirportName,
        planned = scheduledArrival,
        estimated = estimatedArrival,
        actual = actualArrival,
        hasSpecialServices = services.isNotEmpty(),
        specialServices = if (full) services else emptyList(),
        flightCancellation = muc.flightCancellations.cancellationForFlight(flight, operationDate),
        originDetails = listOf(
            DetailEntry(
                kind = DetailKind.GATE,
                value = if (full && gateChange != null) {
                    gateChangeDisplayValue(inboundBoardingGate, gateChange)
                } else {
                    inboundBoardingGate ?: "--"
                },
                hasChange = gateChange != null,
            ),
            DetailEntry(kind = DetailKind.STAND, value = inboundDepartureStand ?: "--"),
        ),
        destinationDetails = listOf(
            DetailEntry(kind = DetailKind.GATE, value = "--"),
            DetailEntry(
                kind = DetailKind.STAND,
                value = if (full && standChange != null) "${arrivalStand ?: "--"} → ${standChange.stand}" else arrivalStand ?: "--",
                hasChange = standChange != null,
            ),
        ),
        details = buildList {
            if (full) {
                gateChange?.let { add(mucSource(DetailKind.GATE_CHANGE_SOURCE, it.updatedAtEpochMillis)) }
                standChange?.let { add(mucSource(DetailKind.STAND_CHANGE_SOURCE, it.updatedAtEpochMillis)) }
            }
            add(DetailEntry(kind = DetailKind.GATE_CLOSED, value = inboundGateClosedObservedAt.formatClock()))
            add(DetailEntry(kind = DetailKind.OFF_BLOCK, value = inboundActualOffBlock.formatClock()))
        },
        aircraftRegistration = if (isLast) aircraftRegistration else null,
        aircraftType = if (isLast) aircraftType ?: "--" else null,
        offBlock = inboundActualOffBlock,
    )
}

private fun RosterAssignment.outboundLeg(
    flight: String,
    muc: MucContext,
    level: DetailLevel,
): FlightLegUiModel {
    val operationDate = scheduledDeparture?.toLocalDate()
    val gateChange = muc.gateChanges.gateForFlight(flight, operationDate)
    val standChange = muc.standChanges.standForFlight(flight, operationDate)
    val services = muc.specialServiceRecords.forFlight(flight, operationDate)
    val full = level == DetailLevel.FULL
    return FlightLegUiModel(
        direction = LegDirection.OUTBOUND,
        flight = flight,
        fromCode = localAirportCode,
        fromName = localAirportName,
        toCode = destinationCode,
        toName = destination,
        planned = scheduledDeparture,
        estimated = estimatedDeparture,
        actual = actualDeparture,
        hasSpecialServices = services.isNotEmpty(),
        specialServices = if (full) services else emptyList(),
        flightCancellation = muc.flightCancellations.cancellationForFlight(flight, operationDate),
        originDetails = listOf(
            DetailEntry(
                kind = DetailKind.GATE,
                value = if (full && gateChange != null) gateChangeDisplayValue(boardingGate, gateChange) else boardingGate ?: "--",
                hasChange = gateChange != null,
            ),
            DetailEntry(
                kind = DetailKind.STAND,
                value = if (full && standChange != null) "${departureStand ?: "--"} → ${standChange.stand}" else departureStand ?: "--",
                hasChange = standChange != null,
            ),
        ),
        destinationDetails = listOf(
            DetailEntry(kind = DetailKind.GATE, value = "--"),
            DetailEntry(kind = DetailKind.STAND, value = outboundArrivalStand ?: "--"),
        ),
        details = buildList {
            if (full) {
                add(DetailEntry(kind = DetailKind.BOARDING_START, value = DutyTimeline.boardingStartTime(this@outboundLeg).formatClock()))
                add(DetailEntry(kind = DetailKind.BOARDING_END, value = DutyTimeline.gateCloseTime(this@outboundLeg).formatClock()))
                gateChange?.let { add(mucSource(DetailKind.GATE_CHANGE_SOURCE, it.updatedAtEpochMillis)) }
                standChange?.let { add(mucSource(DetailKind.STAND_CHANGE_SOURCE, it.updatedAtEpochMillis)) }
            }
            add(DetailEntry(kind = DetailKind.GATE_CLOSED, value = outboundGateClosedObservedAt.formatClock()))
            add(DetailEntry(kind = DetailKind.OFF_BLOCK, value = outboundActualOffBlock.formatClock()))
        },
        aircraftRegistration = aircraftRegistration,
        aircraftType = aircraftType ?: "--",
        offBlock = outboundActualOffBlock,
    )
}

private fun mucSource(kind: DetailKind, updatedAtEpochMillis: Long) =
    DetailEntry(kind = kind, value = "MUC 更新于 ${updatedAtEpochMillis.formatEpoch("HH:mm")}")
