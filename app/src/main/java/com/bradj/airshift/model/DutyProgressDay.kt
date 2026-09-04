package com.bradj.airshift.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 人工完成进度所属的“执勤日”。
 *
 * 排班一天从清晨排到次日凌晨（晚班约 01:35 下班，见 ShiftBusPlan.OFF_DUTY），
 * 因此执勤日不能在自然日零点切换，否则夜班用户在 00:00 之后会看到已完成的任务重新变为当前任务。
 * 这里以 [ROLLOVER_HOUR] 为界：凌晨仍属前一天；最早班车 04:50、首个任务 07:10 都落在新的一天。
 */
object DutyProgressDay {
    const val ROLLOVER_HOUR = 6

    fun of(now: LocalDateTime): LocalDate = now.minusHours(ROLLOVER_HOUR.toLong()).toLocalDate()
}
