package com.bradj.airshift.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bradj.airshift.MainActivity
import com.bradj.airshift.R
import com.bradj.airshift.widget.DutyWidgetUpdater

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DutyWidgetUpdater.notifyRosterChanged(context)
        createChannel(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(intent.getStringExtra(EXTRA_TITLE) ?: context.getString(R.string.app_name))
            .setContentText(intent.getStringExtra(EXTRA_MESSAGE).orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(intent.getStringExtra(EXTRA_MESSAGE).orEmpty()))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        NotificationManagerCompat.from(context).notify(
            intent.getIntExtra(EXTRA_NOTIFICATION_ID, System.currentTimeMillis().toInt()),
            notification,
        )
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val CHANNEL_ID = "flight_operations"

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
