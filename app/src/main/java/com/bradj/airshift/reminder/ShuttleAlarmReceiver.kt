package com.bradj.airshift.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 班车闹铃的后台入口：就绪 / 核对唤醒，以及通知上的「已关掉」。 */
class ShuttleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ShuttleAlarmWake.ACTION_WAKE -> {
                // 写时钟与核对之间要等一两秒，goAsync 让进程活到回调（系统给约 10 秒）。
                val pending = goAsync()
                ShuttleAlarmSync.sync(
                    context,
                    ShuttleAlarmSync.Launch.BACKGROUND,
                    ShuttleAlarmReason.WAKE,
                    force = intent.getBooleanExtra(ShuttleAlarmWake.EXTRA_FORCE, false),
                ) { pending.finish() }
            }
            ShuttleAlarmNotifications.ACTION_ACK_STALE -> ShuttleAlarmSync.acknowledgeStale(context)
        }
    }
}
