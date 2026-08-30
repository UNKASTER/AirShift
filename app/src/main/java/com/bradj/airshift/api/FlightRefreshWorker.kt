package com.bradj.airshift.api

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.bradj.airshift.data.RosterStore
import com.bradj.airshift.model.allDutiesComplete
import com.bradj.airshift.reminder.ReminderScheduler
import com.bradj.airshift.specialservice.SpecialServiceRepository
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

private const val ROSTER_GENERATION_INPUT_KEY = "roster_generation"

class FlightRefreshWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val store = RosterStore(applicationContext)
        val apiKey = store.variFlightApiKey ?: return Result.success()
        val snapshot = store.loadSnapshot()
        if (inputData.getLong(ROSTER_GENERATION_INPUT_KEY, 0L) != snapshot.generation) {
            cancelThisWork()
            return Result.success()
        }
        val assignments = snapshot.assignments
        if (assignments.isEmpty()) return Result.success()
        val now = LocalDateTime.now()
        if (assignments.allDutiesComplete(now, snapshot.manuallyCompletedCount)) {
            cancelThisWork()
            return Result.success()
        }
        val relevantFlights = assignments
            .flatMap { assignment ->
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
            }
            .filter { request ->
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
            if (isStopped || store.rosterGeneration != snapshot.generation) return Result.success()
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
        if (isStopped || store.rosterGeneration != snapshot.generation) return Result.success()
        if (live.isNotEmpty()) {
            val enriched = assignments.map { it.withLiveInfo(live, now.toLocalDate()) }
            if (!store.saveAssignmentsIfGeneration(enriched, snapshot.generation, System.currentTimeMillis())) {
                return Result.success()
            }
            store.runIfGenerationCurrent(snapshot.generation) {
                SpecialServiceRepository.get(applicationContext).onRosterChanged(enriched)
                ReminderScheduler.scheduleAll(applicationContext, enriched)
            }
            val current = store.loadSnapshot()
            val complete = current.assignments.allDutiesComplete(LocalDateTime.now(), current.manuallyCompletedCount)
            if (current.generation == snapshot.generation && complete) {
                cancelThisWork()
            }
        }
        return if (failed == relevantFlights.size && retryableFailures > 0) Result.retry() else Result.success()
    }

    private fun cancelThisWork() {
        WorkManager.getInstance(applicationContext).cancelWorkById(id)
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
            .setInputData(Data.Builder().putLong(ROSTER_GENERATION_INPUT_KEY, RosterStore(context).rosterGeneration).build())
            .build()
        // A retiring worker must never share its ID with a newly scheduled roster refresh.
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }
}
