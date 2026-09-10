package com.bradj.airshift.reminder

import com.bradj.airshift.model.shift.ShuttleAlarmDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 序列什么时候可写、下一次后台该什么时候醒，纯函数。 */
object ShuttleAlarmWakes {
    /** 今天也响过的同一时刻条目：等它响完 / 稍后提醒结束再复用，否则会被时钟的"响后停用"清掉。 */
    const val SHARED_ENTRY_GRACE_MINUTES = 45L
    const val FRESH_ENTRY_GRACE_MINUTES = 1L

    /** 序列可写入的时刻：D−1 上最晚的同一时刻再加宽限；D−1 也响过的时刻宽限更长。 */
    fun readyAt(plan: ShuttleAlarmDay, ringsOnDayBefore: Set<LocalTime>): LocalDateTime {
        val dayBefore = plan.date.minusDays(1)
        return plan.times.maxOf { time ->
            val grace = if (time in ringsOnDayBefore) SHARED_ENTRY_GRACE_MINUTES else FRESH_ENTRY_GRACE_MINUTES
            dayBefore.atTime(time).plusMinutes(grace)
        }
    }

    /** D−1 响过的时刻：D−1 的推荐序列，加上 D−1 实际写过的记录（推荐后来变了也算）。 */
    fun ringsOnDayBefore(
        window: List<ShuttleAlarmDay?>,
        state: ShuttleAlarmState,
        offset: Int,
        today: LocalDate,
    ): Set<LocalTime> {
        val planned = window.getOrNull(offset - 1)?.times.orEmpty()
        val recorded = state.record(today.plusDays(offset - 1L))?.times.orEmpty()
        return (planned + recorded).toSet()
    }

    /** 下一次需要后台醒来的时刻：最早一个还没写全的序列的就绪时刻，或待核对时刻。 */
    fun nextWakeAt(now: LocalDateTime, window: List<ShuttleAlarmDay?>, state: ShuttleAlarmState): LocalDateTime? {
        val today = now.toLocalDate()
        val candidates = window.mapIndexedNotNull { offset, plan ->
            if (plan == null || plan.isEmpty || isWritten(state.record(plan.date), plan)) return@mapIndexedNotNull null
            readyAt(plan, ringsOnDayBefore(window, state, offset, today)).takeIf { it > now }
        }
        val verify = state.pendingVerifyAt?.takeIf { it > now }
        return (candidates + listOfNotNull(verify)).minOrNull()
    }

    private fun isWritten(record: ShuttleAlarmRecord?, plan: ShuttleAlarmDay): Boolean =
        record != null && record.outcome == ShuttleAlarmOutcome.CONFIRMED && plan.times.all { it in record.times }
}
