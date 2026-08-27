package com.bradj.airshift.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.bradj.airshift.model.RosterAssignment
import java.time.ZoneId

object ReminderScheduler {
    fun canScheduleExactAlarms(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun cancelAll(context: Context, assignments: List<RosterAssignment>) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        assignments.forEach { assignment ->
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                assignment.stableId.hashCode(),
                Intent(context, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pendingIntent != null) alarmManager.cancel(pendingIntent)
        }
    }

    fun scheduleAll(context: Context, assignments: List<RosterAssignment>): ScheduleSummary {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        var scheduled = 0
        var skippedPast = 0
        val exactAllowed = canScheduleExactAlarms(context)

        assignments.forEach { assignment ->
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                assignment.stableId.hashCode(),
                Intent(context, ReminderReceiver::class.java).apply {
                    putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, assignment.stableId.hashCode())
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.cancel(pendingIntent)
            val spec = ReminderPolicy.create(assignment) ?: return@forEach
            val triggerMillis = spec.triggerAt
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            if (triggerMillis <= System.currentTimeMillis()) {
                skippedPast++
                return@forEach
            }
            val scheduledIntent = PendingIntent.getBroadcast(
                context,
                assignment.stableId.hashCode(),
                Intent(context, ReminderReceiver::class.java).apply {
                    putExtra(ReminderReceiver.EXTRA_TITLE, spec.title)
                    putExtra(ReminderReceiver.EXTRA_MESSAGE, spec.message)
                    putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, assignment.stableId.hashCode())
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, scheduledIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, scheduledIntent)
            }
            scheduled++
        }
        return ScheduleSummary(scheduled, skippedPast, exactAllowed)
    }
}

data class ScheduleSummary(
    val scheduledCount: Int,
    val skippedPastCount: Int,
    val exactAlarmsAllowed: Boolean,
)
