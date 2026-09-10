package com.bradj.airshift.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bradj.airshift.MainActivity
import com.bradj.airshift.R
import com.bradj.airshift.model.shift.ShuttleAlarmDay
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 班车闹铃的三条通知：后台被拦（点一下即设）、后台未核对（静默）、旧闹铃要手动关（点开时钟列表）。
 * 固定 id，重复发只更新。
 */
internal object ShuttleAlarmNotifications {
    const val ACTION_ACK_STALE = "com.bradj.airshift.reminder.action.SHUTTLE_ALARM_ACK_STALE"
    private const val CHANNEL_ALERTS = "shuttle_alarm"
    private const val CHANNEL_STATUS = "shuttle_alarm_status"
    private const val ID_SET_PROMPT = 0x5A12
    private const val ID_STALE = 0x5A13

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.shuttle_alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.shuttle_alarm_channel_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.shuttle_alarm_status_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.shuttle_alarm_status_channel_description) },
        )
    }

    /** 后台启动被拦：高优先级，点击进蹦床 Activity 在前台重写。 */
    fun showBlocked(context: Context, days: List<ShuttleAlarmDay>, today: LocalDate) {
        val summary = days.joinToString("；") { ShuttleAlarmText.summary(it, today) }
        post(
            context,
            ID_SET_PROMPT,
            builder(context, CHANNEL_ALERTS)
                .setContentTitle("班车闹铃还没设好")
                .setContentText("$summary，点这里一键设置")
                .setStyle(NotificationCompat.BigTextStyle().bigText("$summary，点这里一键设置。"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(setPromptIntent(context))
                .build(),
        )
    }

    /** 后台写入但核对不了：静默，只留一条可点的记录。 */
    fun showUnconfirmed(context: Context, days: List<ShuttleAlarmDay>, today: LocalDate) {
        val summary = days.joinToString("；") { ShuttleAlarmText.summary(it, today) }
        post(
            context,
            ID_SET_PROMPT,
            builder(context, CHANNEL_STATUS)
                .setContentTitle("班车闹铃已设置（未核对）")
                .setContentText("$summary。若时钟里没有，点这里重设")
                .setStyle(NotificationCompat.BigTextStyle().bigText("$summary。若时钟里没有，点这里重设。"))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(setPromptIntent(context))
                .build(),
        )
    }

    fun cancelSetPrompt(context: Context) = NotificationManagerCompat.from(context).cancel(ID_SET_PROMPT)

    /** 推荐变了，旧时刻还会响：列出来，点击直接打开时钟的闹钟列表；「已关掉」清掉提示。 */
    fun showStale(context: Context, stale: List<ShuttleAlarm>, now: LocalDateTime) {
        val lines = ShuttleAlarmText.staleLines(stale, now)
        if (lines.isEmpty()) return
        val ack = PendingIntent.getBroadcast(
            context,
            ID_STALE,
            Intent(context, ShuttleAlarmReceiver::class.java).setAction(ACTION_ACK_STALE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(
            context,
            ID_STALE,
            builder(context, CHANNEL_ALERTS)
                .setContentTitle("班车时间变了，请关掉旧闹铃")
                .setContentText(lines.joinToString("；"))
                .setStyle(NotificationCompat.BigTextStyle().bigText("请在时钟里关掉这些旧闹铃：\n" + lines.joinToString("\n")))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(showAlarmsIntent(context))
                .addAction(0, "已关掉", ack)
                .build(),
        )
    }

    fun cancelStale(context: Context) = NotificationManagerCompat.from(context).cancel(ID_STALE)

    private fun builder(context: Context, channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setAutoCancel(true)

    private fun setPromptIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        ID_SET_PROMPT,
        ShuttleAlarmSetActivity.intent(context, ShuttleAlarmReason.NOTIFICATION, force = true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 时钟不接 SHOW_ALARMS 时退回打开本应用。 */
    private fun showAlarmsIntent(context: Context): PendingIntent {
        val showAlarms = ShuttleAlarmClock(context).showAlarmsIntent()
        val target = if (showAlarms.resolveActivity(context.packageManager) != null) {
            showAlarms
        } else {
            Intent(context, MainActivity::class.java)
        }
        return PendingIntent.getActivity(
            context,
            ID_STALE,
            target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun post(context: Context, id: Int, notification: Notification) {
        createChannels(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
