package com.bradj.airshift.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bradj.airshift.data.RosterStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ReminderReceiver.createChannel(context)
        ReminderScheduler.scheduleAll(context, RosterStore(context).loadAssignments())
    }
}
