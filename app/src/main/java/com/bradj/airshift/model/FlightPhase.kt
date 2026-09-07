package com.bradj.airshift.model

import java.time.LocalDateTime

/**
 * 一段航班的进程：未起飞 → 已起飞 → 已落地。进港、出港航段各自独立判定——
 * 进港段看"前站起飞、本站落地"，出港段看"本站起飞、后站落地"。
 */
enum class FlightPhase {
    NOT_DEPARTED,
    DEPARTED,
    LANDED,
    ;

    companion object {
        /** 飞常准 `FlightState` 里表示已落地的状态原文。 */
        private val LANDED_STATES = setOf("到达")

        /** 飞常准 `FlightState` 里表示已在空中的状态原文。 */
        private val AIRBORNE_STATES = setOf("起飞", "即将到达")

        /**
         * 任一实际信号即算数：落地看实际到达时间或状态"到达"；起飞看实际起飞、实际离位或状态"起飞"。
         * 落地蕴含起飞，所以先判落地。计划 / 预计时间不参与——时间过点不等于飞机动了。
         */
        fun of(
            actualDeparture: LocalDateTime?,
            actualArrival: LocalDateTime?,
            actualOffBlock: LocalDateTime?,
            flightState: String?,
        ): FlightPhase {
            val state = flightState?.trim().orEmpty()
            return when {
                actualArrival != null || state in LANDED_STATES -> LANDED
                actualDeparture != null || actualOffBlock != null || state in AIRBORNE_STATES -> DEPARTED
                else -> NOT_DEPARTED
            }
        }
    }
}

/** 进港航段：前站实际起飞 / 前站实际离位 → 本站实际到达。 */
fun RosterAssignment.inboundPhase(): FlightPhase = FlightPhase.of(
    actualDeparture = inboundActualDeparture,
    actualArrival = actualArrival,
    actualOffBlock = inboundActualOffBlock,
    flightState = inboundFlightState,
)

/** 出港航段：本站实际起飞 / 实际离位 → 后站实际到达。 */
fun RosterAssignment.outboundPhase(): FlightPhase = FlightPhase.of(
    actualDeparture = actualDeparture,
    actualArrival = outboundActualArrival,
    actualOffBlock = outboundActualOffBlock,
    flightState = outboundFlightState,
)
