package com.bradj.airshift.reminder

import com.bradj.airshift.model.shift.ShuttleAlarmDay
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** 写入后用系统"下一闹钟"核对的结论。 */
sealed interface ShuttleAlarmVerdict {
    data object Confirmed : ShuttleAlarmVerdict
    data object Blocked : ShuttleAlarmVerdict
    data object Unconfirmed : ShuttleAlarmVerdict
    data class Inconclusive(val retryAt: LocalDateTime) : ShuttleAlarmVerdict
}

/** 一次写入的结果，并入记录时用。 */
data class ShuttleAlarmWrite(val outcome: ShuttleAlarmOutcome, val background: Boolean, val at: LocalDateTime)

/** 写入之后的核对与记录合并，全部纯函数。 */
object ShuttleAlarmVerification {
    const val MAX_VERIFY_HOPS = 8
    private const val VERIFY_TOLERANCE_SECONDS = 60L
    private const val VERIFY_RETRY_DELAY_MINUTES = 2L

    val VERIFY_TOLERANCE: Duration = Duration.ofSeconds(VERIFY_TOLERANCE_SECONDS)
    val VERIFY_RETRY_DELAY: Duration = Duration.ofMinutes(VERIFY_RETRY_DELAY_MINUTES)

    /**
     * 系统"下一闹钟"落在本次第一响上就是成功；为空或晚于第一响说明没进时钟；
     * 早于第一响时挡在前面的是别的闹钟（或本 App 今天还没响完的序列），只能过一会再看。
     */
    fun judge(
        next: LocalDateTime?,
        expectedFirst: LocalDateTime,
        records: List<ShuttleAlarmRecord>,
        hops: Int,
        clockReportsNextAlarm: Boolean,
    ): ShuttleAlarmVerdict {
        val notSet = if (clockReportsNextAlarm) ShuttleAlarmVerdict.Blocked else ShuttleAlarmVerdict.Unconfirmed
        return when {
            next == null -> notSet
            Duration.between(next, expectedFirst).abs() <= VERIFY_TOLERANCE -> ShuttleAlarmVerdict.Confirmed
            next > expectedFirst -> notSet
            hops >= MAX_VERIFY_HOPS -> ShuttleAlarmVerdict.Unconfirmed
            else -> retryAfter(next, expectedFirst, records)
        }
    }

    /** 本次写入并入某天的记录：仍在序列里的旧时刻保留，换了班车的整体重来，尝试次数按同一序列累计。 */
    fun merged(
        record: ShuttleAlarmRecord?,
        plan: ShuttleAlarmDay,
        fired: List<LocalTime>,
        write: ShuttleAlarmWrite,
    ): ShuttleAlarmRecord {
        val sameSeries = record?.takeIf { it.departure == plan.departure }
        val kept = record?.times.orEmpty().filter { it in plan.times }
        return ShuttleAlarmRecord(
            date = plan.date,
            departure = plan.departure,
            times = (kept + fired).distinct().sorted(),
            setAt = write.at,
            outcome = write.outcome,
            background = write.background,
            attempts = (sameSeries?.attempts ?: 0) + 1,
        )
    }

    /** 挡在前面的若是本 App 自己记录的某天序列，直接跳到那天最后一响之后，少绕几跳。 */
    private fun retryAfter(
        next: LocalDateTime,
        expectedFirst: LocalDateTime,
        records: List<ShuttleAlarmRecord>,
    ): ShuttleAlarmVerdict {
        val ownSeries = records.firstOrNull { record ->
            record.alarms().any { Duration.between(it.at, next).abs() <= VERIFY_TOLERANCE }
        }
        val after = ownSeries?.alarms()?.maxOf { it.at } ?: next
        val retryAt = after.plus(VERIFY_RETRY_DELAY)
        return if (retryAt >= expectedFirst) {
            ShuttleAlarmVerdict.Unconfirmed
        } else {
            ShuttleAlarmVerdict.Inconclusive(retryAt)
        }
    }
}
