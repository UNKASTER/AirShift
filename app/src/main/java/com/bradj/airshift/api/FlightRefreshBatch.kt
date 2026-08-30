package com.bradj.airshift.api

import java.util.concurrent.CancellationException

internal data class FlightRefreshBatchResult(
    val live: Map<FlightLookup, List<FlightInfo>>,
    val errors: List<String>,
    val attemptedCount: Int,
    val retryableFailures: Int,
)

internal fun refreshFlightBatch(
    targets: Set<FlightLookup>,
    isCurrent: (FlightLookup) -> Boolean,
    fetch: (FlightLookup) -> List<FlightInfo>,
): FlightRefreshBatchResult {
    val live = mutableMapOf<FlightLookup, List<FlightInfo>>()
    val errors = mutableListOf<String>()
    var attemptedCount = 0
    var retryableFailures = 0
    targets.forEach { lookup ->
        // Read current progress just before each request so queued flights can retire without a query.
        if (!isCurrent(lookup)) return@forEach
        attemptedCount++
        try {
            live[lookup] = fetch(lookup)
        } catch (_: FlightRefreshSkippedException) {
            attemptedCount--
        } catch (error: CancellationException) {
            throw error
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        } catch (error: Exception) {
            val safeMessage = if (error is VariFlightClientException) {
                if (error.retryable) retryableFailures++
                error.message ?: "实时航班更新失败"
            } else {
                retryableFailures++
                "实时航班更新失败"
            }
            errors += "${lookup.flightNumber}：$safeMessage"
        }
    }
    return FlightRefreshBatchResult(live.toMap(), errors.toList(), attemptedCount, retryableFailures)
}
