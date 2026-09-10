package com.bradj.airshift.data

import com.bradj.airshift.reminder.ShuttleAlarm
import com.bradj.airshift.reminder.ShuttleAlarmAttempt
import com.bradj.airshift.reminder.ShuttleAlarmOutcome
import com.bradj.airshift.reminder.ShuttleAlarmReason
import com.bradj.airshift.reminder.ShuttleAlarmRecord
import com.bradj.airshift.reminder.ShuttleAlarmState
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 班车闹铃状态（`shuttle_alarm_state`）的 JSON 编解码：
 * `{"records":[{date,departure,times[],setAt,outcome,background,attempts}],"stale":[{date,time}],
 *   "pendingVerifyAt","verifyHops","lastAttempt":{reason,at,outcome,background,summary}}`。
 * 时刻用 ISO 文本、枚举存名字；任何一处解析失败整体回空，与实测记录的处理一致。
 */
internal object ShuttleAlarmCodec {
    fun encode(state: ShuttleAlarmState): String = JSONObject().apply {
        put("records", JSONArray().apply { state.records.forEach { put(it.toJson()) } })
        put(
            "stale",
            JSONArray().apply {
                state.stale.forEach { alarm ->
                    put(JSONObject().put("date", alarm.date.toString()).put("time", alarm.time.toString()))
                }
            },
        )
        putNullable("pendingVerifyAt", state.pendingVerifyAt?.toString())
        put("verifyHops", state.verifyHops)
        putNullable("lastAttempt", state.lastAttempt?.toJson())
    }.toString()

    fun decode(raw: String): ShuttleAlarmState? = runCatching {
        val json = JSONObject(raw)
        ShuttleAlarmState(
            records = json.getJSONArray("records").objects { it.toRecord() },
            stale = json.getJSONArray("stale").objects {
                ShuttleAlarm(LocalDate.parse(it.getString("date")), LocalTime.parse(it.getString("time")))
            },
            pendingVerifyAt = json.nullableDateTime("pendingVerifyAt"),
            verifyHops = json.optInt("verifyHops", 0),
            lastAttempt = if (json.isNull("lastAttempt")) null else json.getJSONObject("lastAttempt").toAttempt(),
        )
    }.getOrNull()

    private fun ShuttleAlarmRecord.toJson() = JSONObject().apply {
        put("date", date.toString())
        putNullable("departure", departure?.toString())
        put("times", JSONArray().apply { times.forEach { put(it.toString()) } })
        put("setAt", setAt.toString())
        put("outcome", outcome.name)
        put("background", background)
        put("attempts", attempts)
    }

    private fun ShuttleAlarmAttempt.toJson() = JSONObject().apply {
        put("reason", reason.name)
        put("at", at.toString())
        put("outcome", outcome.name)
        put("background", background)
        put("summary", summary)
    }

    private fun JSONObject.toRecord(): ShuttleAlarmRecord {
        val times = getJSONArray("times")
        return ShuttleAlarmRecord(
            date = LocalDate.parse(getString("date")),
            departure = nullableString("departure")?.let(LocalTime::parse),
            times = List(times.length()) { LocalTime.parse(times.getString(it)) },
            setAt = LocalDateTime.parse(getString("setAt")),
            outcome = ShuttleAlarmOutcome.valueOf(getString("outcome")),
            background = getBoolean("background"),
            attempts = getInt("attempts"),
        )
    }

    private fun JSONObject.toAttempt() = ShuttleAlarmAttempt(
        reason = ShuttleAlarmReason.valueOf(getString("reason")),
        at = LocalDateTime.parse(getString("at")),
        outcome = ShuttleAlarmOutcome.valueOf(getString("outcome")),
        background = getBoolean("background"),
        summary = getString("summary"),
    )

    /** 与 [mapObjects] 不同：这里任何一个元素坏了就让整体解码失败。 */
    private inline fun <T> JSONArray.objects(transform: (JSONObject) -> T): List<T> =
        List(length()) { transform(getJSONObject(it)) }
}
