package com.bradj.airshift.reminder

import com.bradj.airshift.model.shift.ShuttleAlarmDay
import com.bradj.airshift.model.shift.ShuttleAlarmPlan
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 班车闹铃在日历行、设置页与通知里的文案，纯函数。 */
object ShuttleAlarmText {
    const val NONE = "无闹铃"

    fun time(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

    /** 「05:25–05:50 · 6 响」；空序列给 [NONE]。 */
    fun range(day: ShuttleAlarmDay): String {
        val first = day.first
        val last = day.last
        return if (first == null || last == null) NONE else "${time(first)}–${time(last)} · ${day.times.size} 响"
    }

    /** 今天 / 明天 / M/d。 */
    fun dayWord(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> "${date.monthValue}/${date.dayOfMonth}"
    }

    /** 日历行第三行：「闹铃 05:25 起 · 6 响」，今明两天再带状态词；无序列给「无闹铃 · 班车早于 05:00」。 */
    fun rowLine(day: ShuttleAlarmDay?, record: ShuttleAlarmRecord?, now: LocalDateTime): String? {
        val base = baseLine(day) ?: return null
        val status = day?.let { statusWord(it, record, now.toLocalDate()) }
        return if (status == null) base else "$base · $status"
    }

    /** [rowLine] 去掉状态词的部分，界面把状态词单独做成灯。 */
    fun baseLine(day: ShuttleAlarmDay?): String? {
        if (day == null) return null
        val first = day.first
        return if (first == null) {
            "$NONE · 班车早于 ${time(ShuttleAlarmPlan.EARLIEST)}"
        } else {
            "闹铃 ${time(first)} 起 · ${day.times.size} 响"
        }
    }

    /** 只有今明两天有状态：已设 / 待设 / 未核对 / 后台被拦。 */
    fun statusWord(day: ShuttleAlarmDay, record: ShuttleAlarmRecord?, today: LocalDate): String? = when {
        day.date != today && day.date != today.plusDays(1) -> null
        day.isEmpty -> null
        record == null || day.times.none { it in record.times } -> "待设"
        record.outcome == ShuttleAlarmOutcome.CONFIRMED -> "已设"
        record.outcome == ShuttleAlarmOutcome.BLOCKED -> "后台被拦"
        else -> "未核对"
    }

    /**
     * 设置页「下一组」：还没响完的最近一天。「明天 05:25–05:50 · 6 响 · 已设」「明天班车 04:50 · 无闹铃（早于 05:00）」
     * 「明天休息」；班组解析不到（整窗 null）给「—」。
     */
    fun statusLine(window: List<ShuttleAlarmDay?>, state: ShuttleAlarmState, now: LocalDateTime): String {
        val today = now.toLocalDate()
        val next = window.filterNotNull()
            .firstOrNull { day -> day.isEmpty || day.times.any { day.date.atTime(it) > now } }
        return when {
            window.all { it == null } -> "—"
            next == null -> "近几天没有到岗日"
            next.isEmpty ->
                "${dayWord(next.date, today)} 班车 ${time(next.departure)} · $NONE（早于 ${time(ShuttleAlarmPlan.EARLIEST)}）"
            else -> listOfNotNull(
                "${dayWord(next.date, today)} ${range(next)}",
                statusWord(next, state.record(next.date), today),
            ).joinToString(" · ")
        }
    }

    /** 旧闹铃按天分组：「明天：07:00、07:10、07:20」。 */
    fun staleLines(stale: List<ShuttleAlarm>, now: LocalDateTime): List<String> {
        val today = now.toLocalDate()
        return stale.filter { it.at > now }
            .groupBy { it.date }
            .toSortedMap()
            .map { (date, alarms) -> "${dayWord(date, today)}：${alarms.sorted().joinToString("、") { time(it.time) }}" }
    }

    fun attemptLine(attempt: ShuttleAlarmAttempt?): String {
        if (attempt == null) return "—"
        val word = when (attempt.outcome) {
            ShuttleAlarmOutcome.CONFIRMED -> "已核对"
            ShuttleAlarmOutcome.UNCONFIRMED -> "未核对"
            ShuttleAlarmOutcome.BLOCKED -> "被拦截"
        }
        val at = attempt.at
        return "$word · ${at.monthValue}/${at.dayOfMonth} ${time(at.toLocalTime())}"
    }

    /** 通知与 Toast 用：「明天 05:25–05:50 共 6 响」。 */
    fun summary(day: ShuttleAlarmDay, today: LocalDate): String {
        val first = day.first
        val last = day.last
        val word = dayWord(day.date, today)
        return if (first == null || last == null) {
            "$word $NONE"
        } else {
            "$word ${time(first)}–${time(last)} 共 ${day.times.size} 响"
        }
    }
}
