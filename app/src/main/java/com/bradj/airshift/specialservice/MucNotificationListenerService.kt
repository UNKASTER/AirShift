package com.bradj.airshift.specialservice

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MucNotificationListenerService : NotificationListenerService() {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "muc-special-service-parser")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.packageName != MUC_PACKAGE_NAME) return
        val payloads = NotificationTextExtractor.extract(sbn.notification, sbn.postTime)
        if (payloads.isEmpty()) {
            executor.execute {
                SpecialServiceRepository.get(applicationContext).noteUnreadableNotification(sbn.postTime)
            }
            return
        }
        payloads.forEach { payload ->
            executor.execute {
                SpecialServiceRepository.get(applicationContext)
                    .processNotification(
                        payload.texts,
                        payload.sourceEpochMillis,
                        payload.sourceTimeReliable,
                        sbn.postTime,
                    )
            }
        }
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val MUC_PACKAGE_NAME = "com.ceair.im.muc"
    }
}
