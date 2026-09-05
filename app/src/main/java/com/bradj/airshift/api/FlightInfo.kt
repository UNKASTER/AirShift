package com.bradj.airshift.api

import com.bradj.airshift.model.FlightOperation
import com.bradj.airshift.model.RosterAssignment
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

data class AirportPoint(
    val code: String,
    val name: String?,
    val latitude: Double?,
    val longitude: Double?,
)

data class FlightInfo(
    val flightNumber: String,
    val origin: AirportPoint?,
    val destination: AirportPoint?,
    val plannedDeparture: LocalDateTime?,
    val estimatedDeparture: LocalDateTime?,
    val actualDeparture: LocalDateTime?,
    val plannedArrival: LocalDateTime?,
    val estimatedArrival: LocalDateTime?,
    val actualArrival: LocalDateTime?,
    val actualOffBlock: LocalDateTime?,
    val gateClosedObservedAt: LocalDateTime?,
    val boardingGate: String?,
    val departureStand: String?,
    val arrivalStand: String?,
    val arrivalBridge: String?,
)

internal data class FlightLookup(
    val flightNumber: String,
    val date: LocalDate,
) {
    companion object {
        private val FLIGHT_NUMBER_FORMAT = Regex("[A-Z]{2,3}\\d{3,4}")

        fun of(flightNumber: String, date: LocalDate): FlightLookup {
            val normalized = flightNumber.trim().uppercase(Locale.ROOT)
            require(FLIGHT_NUMBER_FORMAT.matches(normalized)) { "航班号格式无效" }
            return FlightLookup(normalized, date)
        }
    }
}

/**
 * 进港航段的查询日期。飞常准的 `date` 是出发日，因此按计划到达时间取运行日
 * （[FlightOperation.operationDateOfArrival]：06:00 前到达的夜班航班算前一天）；
 * 缺失时用同一任务的计划出发日，再退回 [fallback]（排班日）。
 */
internal fun RosterAssignment.inboundLookupDate(fallback: LocalDate): LocalDate =
    scheduledArrival?.let(FlightOperation::operationDateOfArrival)
        ?: scheduledDeparture?.toLocalDate()
        ?: fallback

/** 出港航段的查询日期：计划出发日；缺失时用同一任务的计划到达日，再退回 [fallback]（排班日）。 */
internal fun RosterAssignment.outboundLookupDate(fallback: LocalDate): LocalDate =
    (scheduledDeparture ?: scheduledArrival)?.toLocalDate() ?: fallback

internal fun RosterAssignment.withLiveInfo(
    live: Map<FlightLookup, List<FlightInfo>>,
    fallbackDate: LocalDate,
): RosterAssignment {
    val inboundLookup = inboundFlight?.let { flight -> FlightLookup.of(flight, inboundLookupDate(fallbackDate)) }
    val outboundLookup = outboundFlight?.let { flight -> FlightLookup.of(flight, outboundLookupDate(fallbackDate)) }
    // Same flight number on both sides means the flight passes through our station.
    val throughFlight = inboundLookup != null && inboundLookup == outboundLookup
    val inbound = selectInboundLeg(
        legs = inboundLookup?.let { lookup -> live[lookup]?.ownLegs(lookup, scheduledArrival, ::inboundTime) },
        scheduledArrival = scheduledArrival,
        localAirportCode = localAirportCode,
        throughFlight = throughFlight,
    )
    val outbound = selectOutboundLeg(
        legs = outboundLookup?.let { lookup -> live[lookup]?.ownLegs(lookup, scheduledDeparture, ::outboundTime) },
        scheduledDeparture = scheduledDeparture,
        localAirportCode = localAirportCode,
        throughFlight = throughFlight,
    )
    val localAirport = inbound?.destination ?: outbound?.origin
    return copy(
        origin = inbound?.origin?.name ?: origin,
        destination = outbound?.destination?.name ?: destination,
        originCode = inbound?.origin?.code ?: originCode,
        destinationCode = outbound?.destination?.code ?: destinationCode,
        localAirportCode = localAirport?.code ?: localAirportCode,
        localAirportName = localAirport?.name ?: localAirportName,
        scheduledArrival = scheduledArrival ?: inbound?.plannedArrival,
        scheduledDeparture = scheduledDeparture ?: outbound?.plannedDeparture,
        estimatedArrival = inbound?.estimatedArrival ?: estimatedArrival,
        actualArrival = inbound?.actualArrival ?: actualArrival,
        estimatedDeparture = outbound?.estimatedDeparture ?: estimatedDeparture,
        actualDeparture = outbound?.actualDeparture ?: actualDeparture,
        inboundBoardingGate = inbound?.boardingGate ?: inboundBoardingGate,
        inboundDepartureStand = inbound?.departureStand ?: inboundDepartureStand,
        boardingGate = outbound?.boardingGate ?: boardingGate,
        departureStand = outbound?.departureStand ?: departureStand,
        arrivalStand = inbound?.arrivalStand ?: arrivalStand,
        inboundGateClosedObservedAt = inbound?.gateClosedObservedAt ?: inboundGateClosedObservedAt,
        outboundGateClosedObservedAt = outbound?.gateClosedObservedAt ?: outboundGateClosedObservedAt,
        inboundActualOffBlock = inbound?.actualOffBlock ?: inboundActualOffBlock,
        outboundActualOffBlock = outbound?.actualOffBlock ?: outboundActualOffBlock,
        outboundArrivalStand = outbound?.arrivalStand ?: outboundArrivalStand,
        arrivalBridge = inbound?.arrivalBridge ?: arrivalBridge,
    )
}

/** 进港侧看到达时间：计划优先，其次预计、实际；航段选择与归属判断共用。 */
private fun inboundTime(leg: FlightInfo): LocalDateTime? =
    leg.plannedArrival ?: leg.estimatedArrival ?: leg.actualArrival

/** 出港侧看出发时间：计划优先，其次预计、实际。 */
private fun outboundTime(leg: FlightInfo): LocalDateTime? =
    leg.plannedDeparture ?: leg.estimatedDeparture ?: leg.actualDeparture

/**
 * 同一航班号每天都飞，只有属于排班这一班的航段才能合并进来。
 *
 * 有计划时间时按 [FlightOperation] 比对；没有计划时间时只能按查询日期粗判（允许跨零点的相邻一天）；
 * 航段自身没有任何时间时无从判断，照旧接受——它带不来会漂移的时间。
 * 别的日子的同号航班被整段丢弃，任务保持原值，而不是把它的预计/实际时间写进来。
 */
private fun List<FlightInfo>.ownLegs(
    lookup: FlightLookup,
    scheduled: LocalDateTime?,
    time: (FlightInfo) -> LocalDateTime?,
): List<FlightInfo> = filter { leg ->
    val reference = time(leg)
    when {
        reference == null -> true
        scheduled != null -> FlightOperation.isSameOperation(scheduled, reference)
        else -> abs(ChronoUnit.DAYS.between(lookup.date, reference.toLocalDate())) <= 1
    }
}

// A stopover flight (e.g. DNH→LHW→PKX) yields one FlightInfo per leg; the roster's
// schedule identifies the leg serving our station. Prefer schedule matching over the
// stored local airport, which may have been derived from a wrong whole-route leg.
private fun selectInboundLeg(
    legs: List<FlightInfo>?,
    scheduledArrival: LocalDateTime?,
    localAirportCode: String?,
    throughFlight: Boolean,
): FlightInfo? {
    if (legs.isNullOrEmpty()) return null
    if (legs.size == 1) return legs.first()
    closestByTime(legs, scheduledArrival, ::inboundTime)?.let { return it }
    localAirportCode?.let { code ->
        legs.firstOrNull { it.destination?.code == code }?.let { return it }
    }
    if (throughFlight) {
        // The inbound leg ends at the stopover where the outbound leg continues.
        legs.lastOrNull { leg ->
            val code = leg.destination?.code
            code != null && legs.any { it !== leg && it.origin?.code == code }
        }?.let { return it }
    }
    return legs.last()
}

private fun selectOutboundLeg(
    legs: List<FlightInfo>?,
    scheduledDeparture: LocalDateTime?,
    localAirportCode: String?,
    throughFlight: Boolean,
): FlightInfo? {
    if (legs.isNullOrEmpty()) return null
    if (legs.size == 1) return legs.first()
    closestByTime(legs, scheduledDeparture, ::outboundTime)?.let { return it }
    localAirportCode?.let { code ->
        legs.firstOrNull { it.origin?.code == code }?.let { return it }
    }
    if (throughFlight) {
        // The outbound leg starts at the stopover where the inbound leg arrived.
        legs.firstOrNull { leg ->
            val code = leg.origin?.code
            code != null && legs.any { it !== leg && it.destination?.code == code }
        }?.let { return it }
    }
    return legs.first()
}

private fun closestByTime(
    legs: List<FlightInfo>,
    scheduled: LocalDateTime?,
    time: (FlightInfo) -> LocalDateTime?,
): FlightInfo? {
    if (scheduled == null) return null
    return legs
        .mapNotNull { leg -> time(leg)?.let { leg to it } }
        .minByOrNull { (_, legTime) -> abs(Duration.between(scheduled, legTime).toMinutes()) }
        ?.first
}
