package com.bradj.airshift.api

import java.time.LocalDateTime

data class AirportPoint(
    val code: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
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
    val arrivalGate: String?,
    val arrivalBridge: String?,
)
