package com.bradj.airshift.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime
import java.time.ZoneId

/** 班车闹铃唯一的后台唤醒：先取消再定，晚几分钟无害（就绪时刻本身离下一响还有一天）。 */
internal object ShuttleAlarmWake {
    const val ACTION_WAKE = "com.bradj.airshift.reminder.action.SHUTTLE_ALARM_WAKE"
    private const val REQUEST_CODE = 0x5A11

    /** [force] 只给调试用的"N 秒后后台重设"：让唤醒无视已有记录重写一遍，好观察后台路径。 */
    fun schedule(context: Context, at: LocalDateTime?, force: Boolean = false) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context, force)
        alarmManager.cancel(pending)
        if (at == null) return
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
    }

    private fun pendingIntent(context: Context, force: Boolean = false): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ShuttleAlarmReceiver::class.java).setAction(ACTION_WAKE).putExtra(EXTRA_FORCE, force),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    const val EXTRA_FORCE = "force"
}
