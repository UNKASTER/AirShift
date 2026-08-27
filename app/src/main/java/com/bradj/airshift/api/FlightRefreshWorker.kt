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
import com.bradj.airshift.reminder.ReminderScheduler
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class FlightRefreshWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val store = RosterStore(applicationContext)
        val apiKey = store.variFlightApiKey ?: return Result.success()
        val assignments = store.loadAssignments()
        if (assignments.isEmpty()) return Result.success()
        val now = LocalDateTime.now()
        val relevantFlights = assignments.flatMap { assignment ->
            buildList {
                assignment.inboundFlight?.let { flight ->
                    add(
                        FlightRequest(
                            FlightLookup.of(flight, assignment.scheduledArrival?.toLocalDate() ?: now.toLocalDate()),
                            assignment.scheduledArrival,
                        ),
                    )
                }
                assignment.outboundFlight?.let { flight ->
                    add(
                        FlightRequest(
                            FlightLookup.of(flight, assignment.scheduledDeparture?.toLocalDate() ?: now.toLocalDate()),
                            assignment.scheduledDeparture,
                        ),
                    )
                }
            }
        }.filter { request ->
            val scheduled = request.scheduled ?: return@filter false
            val minutes = Duration.between(now, scheduled).toMinutes()
            minutes in -60..240
        }.distinctBy(FlightRequest::lookup)
        if (relevantFlights.isEmpty()) return Result.success()

        val client = VariFlightClient(apiKey)
        val live = mutableMapOf<FlightLookup, FlightInfo>()
        var failed = 0
        var retryableFailures = 0
        relevantFlights.forEach { request ->
            runCatching {
                client.fetchFlightBlocking(
                    request.lookup.flightNumber,
                    request.lookup.date,
                )
            }.onSuccess { live[request.lookup] = it }
                .onFailure { error ->
                    failed++
                    if (error !is VariFlightClientException || error.retryable) retryableFailures++
                }
        }
        if (live.isNotEmpty()) {
            val enriched = assignments.map { it.withLiveInfo(live, now.toLocalDate()) }
            store.saveAssignments(enriched)
            store.lastLiveRefreshEpochMillis = System.currentTimeMillis()
            ReminderScheduler.scheduleAll(applicationContext, enriched)
        }
        return if (failed == relevantFlights.size && retryableFailures > 0) Result.retry() else Result.success()
    }

    private data class FlightRequest(val lookup: FlightLookup, val scheduled: LocalDateTime?)
}

object FlightRefreshScheduler {
    private const val WORK_NAME = "flight-live-refresh"

    fun configure(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<FlightRefreshWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
