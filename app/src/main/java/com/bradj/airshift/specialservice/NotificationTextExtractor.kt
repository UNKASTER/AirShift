package com.bradj.airshift.specialservice

import android.app.Notification
import android.os.Parcelable

data class ExtractedNotificationPayload(
    val texts: List<String>,
    val sourceEpochMillis: Long,
    val sourceTimeReliable: Boolean,
)

object NotificationTextExtractor {
    fun extract(notification: Notification, notificationPostTime: Long): List<ExtractedNotificationPayload> {
        val extras = notification.extras
        val messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java),
        ).mapNotNull { message ->
            message.text?.toString()?.trim()?.takeIf(String::isNotBlank)?.let { text ->
                ExtractedNotificationPayload(
                    texts = listOf(text),
                    sourceEpochMillis = message.timestamp.takeIf { it > 0L } ?: notificationPostTime,
                    sourceTimeReliable = message.timestamp > 0L,
                )
            }
        }.distinctBy { it.texts.single() to it.sourceEpochMillis }
        if (messages.isNotEmpty()) return messages

        val fallbackTexts = buildList {
            addText(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { addText(it) }
            addText(extras.getCharSequence(Notification.EXTRA_TEXT))
            addText(extras.getCharSequence(Notification.EXTRA_TITLE))
            addText(extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE))
        }.distinct()
        return if (fallbackTexts.isEmpty()) {
            emptyList()
        } else {
            listOf(ExtractedNotificationPayload(fallbackTexts, notificationPostTime, sourceTimeReliable = false))
        }
    }

    private fun MutableList<String>.addText(value: CharSequence?) {
        value?.toString()?.trim()?.takeIf(String::isNotBlank)?.let(::add)
    }
}
