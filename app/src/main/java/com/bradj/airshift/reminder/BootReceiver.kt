package com.bradj.airshift.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bradj.airshift.data.RosterStore
import com.bradj.airshift.widget.DutyWidgetUpdater

/** 开机与应用升级：AlarmManager 里的闹钟都没了，重排提醒与班车闹铃的唤醒；时钟里的闹钟本身不受影响。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        ReminderReceiver.createChannel(context)
        ReminderScheduler.scheduleAll(context, RosterStore(context).loadAssignments())
        DutyWidgetUpdater.notifyRosterChanged(context)
        val pending = goAsync()
        ShuttleAlarmSync.sync(context, ShuttleAlarmSync.Launch.BACKGROUND, ShuttleAlarmReason.BOOT) { pending.finish() }
    }
}
