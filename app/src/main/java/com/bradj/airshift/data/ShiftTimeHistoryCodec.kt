package com.bradj.airshift.data

import com.bradj.airshift.model.shift.ShiftDayKind
import com.bradj.airshift.model.shift.ShiftSlot
import com.bradj.airshift.model.shift.ShiftTeam
import com.bradj.airshift.model.shift.ShiftTier
import com.bradj.airshift.model.shift.ShiftTimeHistory
import com.bradj.airshift.model.shift.ShiftTimeObservation
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 实测记录（`shift_time_history`）的 JSON 编解码。数组元素
 * `{"date","team","kind","tier","number","firstTask","inbound","lastTask"}`，枚举存名字；
 * 任何一条解析失败整体回空，与校准表的处理一致。记录里没有姓名。
 */
internal object ShiftTimeHistoryCodec {
    fun encode(history: ShiftTimeHistory): String = JSONArray().apply {
        history.observations.forEach { observation ->
            put(
                JSONObject().apply {
                    put("date", observation.date.toString())
                    put("team", observation.team.name)
                    put("kind", observation.kind.name)
                    put("tier", observation.slot.tier.name)
                    put("number", observation.slot.number)
                    put("firstTask", observation.firstTaskMinutes)
                    put("inbound", observation.inbound)
                    put("lastTask", observation.lastTaskMinutes)
                },
            )
        }
    }.toString()

    fun decode(raw: String): ShiftTimeHistory? = runCatching {
        val array = JSONArray(raw)
        ShiftTimeHistory(List(array.length()) { index -> array.getJSONObject(index).toObservation() })
    }.getOrNull()

    private fun JSONObject.toObservation() = ShiftTimeObservation(
        date = LocalDate.parse(getString("date")),
        team = ShiftTeam.valueOf(getString("team")),
        kind = ShiftDayKind.valueOf(getString("kind")),
        slot = ShiftSlot(ShiftTier.valueOf(getString("tier")), getInt("number")),
        firstTaskMinutes = getInt("firstTask"),
        inbound = getBoolean("inbound"),
        lastTaskMinutes = getInt("lastTask"),
    )
}
