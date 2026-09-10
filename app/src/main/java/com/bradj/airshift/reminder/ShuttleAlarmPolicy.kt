package com.bradj.airshift.reminder

import com.bradj.airshift.model.shift.ShuttleAlarmDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 一次同步的输入：现在、开关、是否可见（前台）、是否无视记录重写。 */
data class ShuttleAlarmRequest(
    val now: LocalDateTime,
    val enabled: Boolean,
    val visible: Boolean,
    val force: Boolean = false,
)

/** 一次同步要做的事：写哪些天的哪些时刻、新发现的旧闹铃、下一次后台唤醒、剪枝后的状态。 */
data class ShuttleAlarmDecision(
    val fire: List<ShuttleAlarmDay>,
    val newStale: List<ShuttleAlarm>,
    val nextWakeAt: LocalDateTime?,
    val state: ShuttleAlarmState,
)

/**
 * 班车闹铃的决策，全部纯函数。系统时钟没有日期参数：某时刻 T 只能被设成"下一次出现"，
 * 所以某天 D 的序列只有在 D−1 的同一时刻过去之后才能写（[ShuttleAlarmWakes.readyAt]），并且写的都是 D 上还没到的时刻。
 */
object ShuttleAlarmPolicy {
    /** 太贴近当前时刻的一响不设，避免时钟把它排到明天。 */
    const val SAFETY_LEAD_MINUTES = 1L

    /** 上三休三：7 天内必有到岗日，下一次唤醒永远算得出。 */
    const val HORIZON_DAYS = 7
    const val MAX_ATTEMPTS = 3
    private const val FIRE_DAYS = 2

    private class DayDecision(val fire: ShuttleAlarmDay?, val stale: List<ShuttleAlarm>)

    /**
     * [window] 下标 0 是今天，之后逐日；只有今天与明天会被写，更远的日子只用来算唤醒。
     * 不可见时（后台唤醒）不重试无法核对的记录——结果只会一样。
     */
    fun decide(
        request: ShuttleAlarmRequest,
        window: List<ShuttleAlarmDay?>,
        state: ShuttleAlarmState,
    ): ShuttleAlarmDecision {
        val today = request.now.toLocalDate()
        val pruned = prune(state, today, request.now)
        if (!request.enabled) return disabled(request.now, today, pruned)
        val days = (0 until FIRE_DAYS).map { offset -> dayDecision(request, window, pruned, offset) }
        val newStale = days.flatMap { it.stale }.filter { it !in pruned.stale }
        val merged = pruned.copy(stale = (pruned.stale + newStale).sorted())
        return ShuttleAlarmDecision(
            fire = days.mapNotNull { it.fire },
            newStale = newStale,
            nextWakeAt = ShuttleAlarmWakes.nextWakeAt(request.now, window, merged),
            state = merged,
        )
    }

    private fun dayDecision(
        request: ShuttleAlarmRequest,
        window: List<ShuttleAlarmDay?>,
        state: ShuttleAlarmState,
        offset: Int,
    ): DayDecision {
        val today = request.now.toLocalDate()
        val date = today.plusDays(offset.toLong())
        val plan = window.getOrNull(offset)
        val record = state.record(date)
        if (isFrozen(request.now, date, record)) return DayDecision(null, emptyList())
        val rings = ShuttleAlarmWakes.ringsOnDayBefore(window, state, offset, today)
        val fire = plan
            ?.takeIf { !it.isEmpty && request.now >= ShuttleAlarmWakes.readyAt(it, rings) }
            ?.let { ready ->
                dueTimes(request, ready, record).takeIf { it.isNotEmpty() }?.let { due -> ready.copy(times = due) }
            }
        return DayDecision(fire, staleOf(request.now, date, record, plan))
    }

    private fun disabled(now: LocalDateTime, today: LocalDate, pruned: ShuttleAlarmState): ShuttleAlarmDecision {
        val future = pruned.records.filter { it.date >= today }.flatMap { it.alarms() }.filter { it.at > now }
        val newStale = future.filter { it !in pruned.stale }
        val merged = pruned.copy(stale = (pruned.stale + newStale).sorted(), pendingVerifyAt = null, verifyHops = 0)
        return ShuttleAlarmDecision(emptyList(), newStale, null, merged)
    }

    private fun prune(state: ShuttleAlarmState, today: LocalDate, now: LocalDateTime): ShuttleAlarmState =
        state.copy(
            records = state.records.filter { it.date >= today.minusDays(1) },
            stale = state.stale.filter { it.at > now },
        )

    /** 今天的序列一旦开始响就不再改：进行中的实时刷新 / 晚导入不会把它改来改去。 */
    private fun isFrozen(now: LocalDateTime, date: LocalDate, record: ShuttleAlarmRecord?): Boolean {
        val first = record?.times?.minOrNull() ?: return false
        return date == now.toLocalDate() && now >= date.atTime(first)
    }

    private fun staleOf(
        now: LocalDateTime,
        date: LocalDate,
        record: ShuttleAlarmRecord?,
        plan: ShuttleAlarmDay?,
    ): List<ShuttleAlarm> {
        val planned = plan?.times.orEmpty()
        return record?.times.orEmpty()
            .filter { it !in planned }
            .map { ShuttleAlarm(date, it) }
            .filter { it.at > now }
    }

    private fun dueTimes(
        request: ShuttleAlarmRequest,
        plan: ShuttleAlarmDay,
        record: ShuttleAlarmRecord?,
    ): List<LocalTime> =
        plan.times
            .filter { plan.date.atTime(it) > request.now.plusMinutes(SAFETY_LEAD_MINUTES) }
            .filter { request.force || shouldWrite(record, it, request.visible) }

    private fun shouldWrite(record: ShuttleAlarmRecord?, time: LocalTime, visible: Boolean): Boolean = when {
        record == null || time !in record.times -> true
        record.outcome == ShuttleAlarmOutcome.CONFIRMED || record.attempts >= MAX_ATTEMPTS -> false
        else -> visible || record.outcome == ShuttleAlarmOutcome.BLOCKED
    }
}
