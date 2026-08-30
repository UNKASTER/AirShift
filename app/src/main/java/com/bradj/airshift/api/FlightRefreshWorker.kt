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
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.Future
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
        val relevantFlights = assignments.dutyWindowLookups(snapshot.manuallyCompletedCount, now)
        if (relevantFlights.isEmpty()) return Result.success()

        val client = VariFlightClient(apiKey)
        val isCurrent: (FlightLookup) -> Boolean = { lookup ->
            val current = store.loadSnapshot()
            !isStopped && current.generation == snapshot.generation && store.variFlightApiKey == apiKey &&
                lookup in current.assignments.dutyWindowLookups(current.manuallyCompletedCount)
        }
        val refresh = refreshFlightBatch(
            targets = relevantFlights,
            isCurrent = isCurrent,
            fetch = { lookup -> client.fetchFlightBlocking(lookup) { isCurrent(lookup) } },
        )
        if (isStopped || store.rosterGeneration != snapshot.generation || store.variFlightApiKey != apiKey) {
            return Result.success()
        }
        if (refresh.live.isNotEmpty()) {
            if (store.mergeLiveInfoIfGeneration(
                    refresh.live,
                    snapshot.generation,
                    now.toLocalDate(),
                    System.currentTimeMillis(),
                ) == null
            ) {
                return Result.success()
            }
            store.runIfGenerationCurrent(snapshot.generation) {
                val latest = store.loadSnapshot().assignments
                SpecialServiceRepository.get(applicationContext).onRosterChanged(latest)
                ReminderScheduler.scheduleAll(applicationContext, latest)
            }
            val current = store.loadSnapshot()
            val complete = current.assignments.allDutiesComplete(LocalDateTime.now(), current.manuallyCompletedCount)
            if (current.generation == snapshot.generation && complete) {
                cancelThisWork()
            }
        }
        return if (refresh.attemptedCount > 0 && refresh.errors.size == refresh.attemptedCount &&
            refresh.retryableFailures > 0
        ) Result.retry() else Result.success()
    }

    private fun cancelThisWork() {
        WorkManager.getInstance(applicationContext).cancelWorkById(id)
    }
}

object FlightRefreshScheduler {
    private const val WORK_NAME = "flight-live-refresh"
    internal const val WORK_TAG = "flight-live-refresh-window"
    private val executor = Executors.newSingleThreadExecutor()

    fun configure(context: Context, enabled: Boolean): Future<*> {
        val applicationContext = context.applicationContext
        val store = RosterStore(applicationContext)
        val generation = store.rosterGeneration
        return executor.submit {
            val manager = WorkManager.getInstance(applicationContext)
            if (store.rosterGeneration != generation) return@submit
            // Retire the fixed-name worker created by earlier app versions.
            manager.cancelUniqueWork(WORK_NAME).result.get()
            val existing = manager.getWorkInfosByTag(WORK_TAG).get()
            val current = store.loadSnapshot()
            if (current.generation != generation) return@submit
            val eligible = store.hasVariFlightApiKey && current.assignments.isNotEmpty() &&
                !current.assignments.allDutiesComplete(manuallyCompletedCount = current.manuallyCompletedCount)
            if (!enabled && eligible) return@submit
            val workName = "$WORK_NAME-$generation"
            existing.filter { !it.state.isFinished && (!eligible || workName !in it.tags) }.forEach {
                // Cancel captured IDs only: a stale configuration cannot cancel a newer roster's work.
                manager.cancelWorkById(it.id).result.get()
            }
            if (!eligible || store.rosterGeneration != generation) return@submit
            val request = PeriodicWorkRequestBuilder<FlightRefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(Data.Builder().putLong(ROSTER_GENERATION_INPUT_KEY, generation).build())
                .setInitialDelay(15, TimeUnit.MINUTES)
                .addTag(WORK_TAG)
                .addTag(workName)
                .build()
            manager.enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.KEEP, request).result.get()
        }
    }
}
