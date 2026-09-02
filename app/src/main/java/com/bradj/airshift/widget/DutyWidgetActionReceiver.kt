package com.bradj.airshift.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bradj.airshift.api.FlightRefreshScheduler
import com.bradj.airshift.data.RosterStore
import com.bradj.airshift.model.allDutiesComplete
import com.bradj.airshift.reminder.ReminderScheduler
import com.bradj.airshift.specialservice.SpecialServiceRepository
import java.time.LocalDateTime

/** Handles explicit actions from the widget without opening the app. */
class DutyWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COMPLETE_DUTY) return
        val generation = intent.getLongExtra(EXTRA_ROSTER_GENERATION, INVALID_GENERATION)
        val dutyIndex = intent.getIntExtra(EXTRA_DUTY_INDEX, INVALID_DUTY_INDEX)
        if (generation == INVALID_GENERATION || dutyIndex == INVALID_DUTY_INDEX) return

        val appContext = context.applicationContext
        val store = RosterStore(appContext)
        val completion = store.completeCurrentDuty(generation, dutyIndex)
        if (completion == null) {
            DutyWidgetUpdater.notifyRosterChanged(appContext)
            return
        }

        val snapshot = completion.snapshot
        DutyWidgetUpdater.notifyRosterChanged(appContext)
        SpecialServiceRepository.get(appContext).onRosterChanged(snapshot.assignments)
        ReminderScheduler.scheduleAll(appContext, snapshot.assignments)
        val refreshEnabled = store.hasVariFlightApiKey && snapshot.assignments.isNotEmpty() &&
            !snapshot.assignments.allDutiesComplete(
                now = LocalDateTime.now(),
                manuallyCompletedCount = snapshot.manuallyCompletedCount,
            )
        FlightRefreshScheduler.configure(appContext, refreshEnabled)
        if (refreshEnabled && completion.newlyTrackedFlights.isNotEmpty()) {
            FlightRefreshScheduler.refreshNow(
                appContext,
                snapshot.generation,
                completion.newlyTrackedFlights,
            )
        }
    }

    companion object {
        internal const val ACTION_COMPLETE_DUTY = "com.bradj.airshift.widget.action.COMPLETE_DUTY"
        internal const val EXTRA_ROSTER_GENERATION = "roster_generation"
        internal const val EXTRA_DUTY_INDEX = "duty_index"
        private const val INVALID_GENERATION = Long.MIN_VALUE
        private const val INVALID_DUTY_INDEX = Int.MIN_VALUE
    }
}
