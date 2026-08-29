package com.bradj.airshift.specialservice

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import com.bradj.airshift.data.RosterStore
import com.bradj.airshift.model.RosterAssignment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom

class SpecialServiceRepository private constructor(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val fingerprintKey = loadOrCreateFingerprintKey()
    private val mutableState = MutableStateFlow(loadState())

    val state: StateFlow<SpecialServiceState> = mutableState.asStateFlow()

    fun processNotification(
        texts: List<String>,
        sourceEpochMillis: Long,
        sourceTimeReliable: Boolean = false,
        notificationEpochMillis: Long = sourceEpochMillis,
    ) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            var current = SpecialServiceExpiry.prune(mutableState.value, now)
            val parsed = SpecialServiceParser.parseMessage(
                texts = texts,
                sourceEpochMillis = sourceEpochMillis,
                fingerprintKey = fingerprintKey,
                notificationEpochMillis = notificationEpochMillis,
            ).let { message ->
                message.copy(
                    serviceCandidates = message.serviceCandidates.map { it.copy(expiresAtEpochMillis = now + PENDING_TTL_MILLIS) },
                    gateChanges = message.gateChanges.map { it.copy(expiresAtEpochMillis = now + PENDING_TTL_MILLIS) },
                    standChanges = message.standChanges.map { it.copy(expiresAtEpochMillis = now + PENDING_TTL_MILLIS) },
                    flightCancellations = message.flightCancellations.map { it.copy(expiresAtEpochMillis = now + PENDING_TTL_MILLIS) },
                )
            }
            val recognizedCount = parsed.serviceCandidates.size + parsed.gateChanges.size +
                parsed.standChanges.size + parsed.flightCancellations.size
            if (recognizedCount == 0) {
                val onlySummary = texts.any { text ->
                    Regex("新消息|NEW MESSAGE|收到.{0,4}消息", RegexOption.IGNORE_CASE).containsMatchIn(text)
                }
                publish(
                    current.copy(
                        lastProcessedEpochMillis = now,
                        lastProcessingResult = if (onlySummary) {
                            "MUC 通知只提供“新消息”摘要，平台未暴露可读正文；已停止识别且不会启用高风险替代方案"
                        } else {
                            "MUC 通知未包含可匹配的特服、登机口/机位变更或取消信息"
                        },
                    ),
                )
                return
            }

            val fingerprint = sequenceOf(
                parsed.serviceCandidates.firstOrNull()?.fingerprint,
                parsed.gateChanges.firstOrNull()?.fingerprint,
                parsed.standChanges.firstOrNull()?.fingerprint,
                parsed.flightCancellations.firstOrNull()?.fingerprint,
            ).filterNotNull().first()
            val alreadyProcessed = SpecialServiceDedupe.isDuplicate(
                processedFingerprints = current.processedFingerprints,
                fingerprint = fingerprint,
                sourceEpochMillis = sourceEpochMillis,
                sourceTimeReliable = sourceTimeReliable,
                nowEpochMillis = now,
            )
            if (alreadyProcessed) {
                publish(
                    current.copy(
                        lastProcessedEpochMillis = now,
                        lastProcessingResult = "重复的 MUC 通知摘要已忽略",
                    ),
                )
                return
            }

            val flights = RosterFlightMatcher.index(RosterStore(applicationContext).loadAssignments())
            val reduction = MucMessageReducer.apply(current, parsed, flights)
            val fingerprintExpiry = maxOf(now + PENDING_TTL_MILLIS, reduction.resolvedExpiryEpochMillis)

            val processed = current.processedFingerprints.filterNot {
                it.value == fingerprint && it.sourceEpochMillis == sourceEpochMillis
            } +
                ProcessedFingerprint(
                    value = fingerprint,
                    sourceEpochMillis = sourceEpochMillis,
                    expiresAtEpochMillis = fingerprintExpiry,
                )
            current = reduction.state.copy(
                processedFingerprints = processed,
                lastSuccessfulRecognitionEpochMillis = now,
                lastProcessedEpochMillis = now,
                lastProcessingResult = buildString {
                    append("识别 $recognizedCount 条：特服自动关联 ${reduction.specialServicesAutoMatched} 条")
                    append("，登机口更新 ${reduction.gateChangesApplied} 条")
                    append("，机位更新 ${reduction.standChangesApplied} 条")
                    append("，取消 ${reduction.cancellationsApplied} 条")
                    if (reduction.awaitingRoster > 0) append("，等待排班 ${reduction.awaitingRoster} 条")
                    if (reduction.manualReviews > 0) append("，低置信已忽略 ${reduction.manualReviews} 条")
                },
            )
            publish(SpecialServiceExpiry.prune(current, now))
        }
    }

    fun noteUnreadableNotification(sourceEpochMillis: Long) {
        synchronized(lock) {
            val current = SpecialServiceExpiry.prune(mutableState.value, System.currentTimeMillis())
            publish(current.withUnreadableNotification(sourceEpochMillis))
        }
    }

    fun onRosterChanged(assignments: List<RosterAssignment>) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val flights = RosterFlightMatcher.index(assignments)
            publish(
                SpecialServiceExpiry.prune(
                    MucMessageReducer.reconcile(mutableState.value, flights),
                    now,
                ),
            )
        }
    }

    private fun publish(updated: SpecialServiceState) {
        mutableState.value = updated
        preferences.edit { putString(KEY_STATE, SpecialServiceJsonCodec.encode(updated)) }
    }

    private fun loadState(): SpecialServiceState = preferences.getString(KEY_STATE, null)
        ?.let { encoded -> runCatching { SpecialServiceJsonCodec.decode(encoded) }.getOrNull() }
        ?.withRepairedProcessingStatus()
        ?: SpecialServiceState()

    private fun loadOrCreateFingerprintKey(): ByteArray {
        preferences.getString(KEY_FINGERPRINT_KEY, null)?.let { encoded ->
            return runCatching { Base64.decode(encoded, Base64.NO_WRAP) }.getOrNull()
                ?.takeIf { it.size >= 32 }
                ?: createFingerprintKey()
        }
        return createFingerprintKey()
    }

    private fun createFingerprintKey(): ByteArray = ByteArray(32).also { key ->
        SecureRandom().nextBytes(key)
        preferences.edit(commit = true) {
            putString(KEY_FINGERPRINT_KEY, Base64.encodeToString(key, Base64.NO_WRAP))
        }
    }

    companion object {
        private const val FILE_NAME = "air_shift_special_services"
        private const val KEY_STATE = "state"
        private const val KEY_FINGERPRINT_KEY = "fingerprint_key"
        private const val PENDING_TTL_MILLIS = 24L * 60L * 60L * 1000L

        @Volatile
        private var instance: SpecialServiceRepository? = null

        fun get(context: Context): SpecialServiceRepository = instance ?: synchronized(this) {
            instance ?: SpecialServiceRepository(context).also { instance = it }
        }
    }
}

internal fun SpecialServiceState.withUnreadableNotification(sourceEpochMillis: Long): SpecialServiceState {
    val latestProcessed = lastProcessedEpochMillis
    if (latestProcessed != null && sourceEpochMillis < latestProcessed) return this
    return copy(
        lastProcessedEpochMillis = sourceEpochMillis,
        lastProcessingResult = "MUC 通知没有提供可读正文；请用无个人信息测试消息核实通知样式",
    )
}

internal fun SpecialServiceState.withRepairedProcessingStatus(): SpecialServiceState {
    val latestSuccess = lastSuccessfulRecognitionEpochMillis ?: return this
    val latestProcessed = lastProcessedEpochMillis
    if (latestProcessed != null && latestProcessed >= latestSuccess) return this
    return copy(
        lastProcessedEpochMillis = latestSuccess,
        lastProcessingResult = "最近一次 MUC 通知已成功识别；较旧的不可读摘要已忽略",
    )
}
