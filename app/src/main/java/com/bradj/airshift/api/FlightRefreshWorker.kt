package com.bradj.airshift.api

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.bradj.airshift.data.RosterStore
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.reminder.ReminderScheduler
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class FlightRefreshWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val store = RosterStore(applicationContext)
        val gateway = store.gatewayBaseUrl ?: return Result.success()
        val assignments = store.loadAssignments()
        if (assignments.isEmpty()) return Result.success()
        val now = LocalDateTime.now()
        val relevantFlights = assignments.flatMap { assignment ->
            buildList {
                assignment.inboundFlight?.let { add(FlightRequest(it, assignment.scheduledArrival)) }
                assignment.outboundFlight?.let { add(FlightRequest(it, assignment.scheduledDeparture)) }
            }
        }.filter { request ->
            val scheduled = request.scheduled ?: return@filter false
            val minutes = Duration.between(now, scheduled).toMinutes()
            minutes in -60..240
        }.distinctBy { it.flightNumber to it.scheduled?.toLocalDate() }
        if (relevantFlights.isEmpty()) return Result.success()

        val client = FlightGatewayClient(gateway)
        val live = mutableMapOf<String, FlightInfo>()
        var failed = 0
        relevantFlights.forEach { request ->
            runCatching {
                client.fetchFlightBlocking(
                    request.flightNumber,
                    request.scheduled?.toLocalDate() ?: LocalDate.now(),
                )
            }.onSuccess { live[request.flightNumber] = it }
                .onFailure { failed++ }
        }
        if (live.isNotEmpty()) {
            val enriched = assignments.map { it.withLiveInfo(live) }
            store.saveAssignments(enriched)
            ReminderScheduler.scheduleAll(applicationContext, enriched)
        }
        return if (failed == relevantFlights.size) Result.retry() else Result.success()
    }

    private fun RosterAssignment.withLiveInfo(live: Map<String, FlightInfo>): RosterAssignment {
        val inbound = inboundFlight?.let(live::get)
        val outbound = outboundFlight?.let(live::get)
        return copy(
            origin = inbound?.origin?.name ?: origin,
            destination = outbound?.destination?.name ?: destination,
            estimatedArrival = inbound?.estimatedArrival ?: estimatedArrival,
            actualArrival = inbound?.actualArrival ?: actualArrival,
            estimatedDeparture = outbound?.estimatedDeparture ?: estimatedDeparture,
            actualDeparture = outbound?.actualDeparture ?: actualDeparture,
            arrivalGate = inbound?.arrivalGate ?: arrivalGate,
            arrivalBridge = inbound?.arrivalBridge ?: arrivalBridge,
        )
    }

    private data class FlightRequest(val flightNumber: String, val scheduled: LocalDateTime?)
}

object FlightRefreshScheduler {
    private const val WORK_NAME = "flight-live-refresh"

    fun configure(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<FlightRefreshWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
