package com.bradj.airshift.reminder

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 时钟里的一响：日期只在 App 内有意义，系统时钟只认时刻（"下一次出现"）。 */
data class ShuttleAlarm(val date: LocalDate, val time: LocalTime) : Comparable<ShuttleAlarm> {
    val at: LocalDateTime get() = date.atTime(time)

    override fun compareTo(other: ShuttleAlarm): Int = at.compareTo(other.at)
}

/**
 * 一次写入之后 App 对时钟状态的判断：CONFIRMED 核对到第一响已进时钟；BLOCKED 明确没进（后台启动被拦或时钟没收）；
 * UNCONFIRMED 无法核对（前面挡着别的闹钟、时钟不上报下一闹钟）。
 */
enum class ShuttleAlarmOutcome { CONFIRMED, UNCONFIRMED, BLOCKED }

/** 某一天已经向时钟写过的序列；[times] 是累计写过的时刻，不是时钟实况。 */
data class ShuttleAlarmRecord(
    val date: LocalDate,
    val departure: LocalTime?,
    val times: List<LocalTime>,
    val setAt: LocalDateTime,
    val outcome: ShuttleAlarmOutcome,
    val background: Boolean,
    val attempts: Int,
) {
    fun alarms(): List<ShuttleAlarm> = times.map { ShuttleAlarm(date, it) }
}

/** 触发一次同步的原因；只用于记录与文案。 */
enum class ShuttleAlarmReason { FOREGROUND, IMPORT, SETTINGS, TOGGLE, WAKE, VERIFY, BOOT, NOTIFICATION, MANUAL }

/** 最近一次真正向时钟写入的结果，给设置页"上次后台设置"。 */
data class ShuttleAlarmAttempt(
    val reason: ShuttleAlarmReason,
    val at: LocalDateTime,
    val outcome: ShuttleAlarmOutcome,
    val background: Boolean,
    val summary: String,
)

/** 持久化的班车闹铃状态（`shuttle_alarm_state`）。 */
data class ShuttleAlarmState(
    val records: List<ShuttleAlarmRecord> = emptyList(),
    /** 写过、但已不在推荐序列里、还没到点的时刻：时钟里它们仍会响，只能提示用户手动关。 */
    val stale: List<ShuttleAlarm> = emptyList(),
    /** 核对无结论时的下一次核对时刻。 */
    val pendingVerifyAt: LocalDateTime? = null,
    val verifyHops: Int = 0,
    val lastAttempt: ShuttleAlarmAttempt? = null,
) {
    val isEmpty: Boolean
        get() = records.isEmpty() && stale.isEmpty() && pendingVerifyAt == null && lastAttempt == null

    fun record(date: LocalDate): ShuttleAlarmRecord? = records.firstOrNull { it.date == date }

    fun withRecord(record: ShuttleAlarmRecord): ShuttleAlarmState =
        copy(records = (records.filter { it.date != record.date } + record).sortedBy { it.date })

    companion object {
        val EMPTY = ShuttleAlarmState()
    }
}
