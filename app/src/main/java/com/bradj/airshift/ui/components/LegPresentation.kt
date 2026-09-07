package com.bradj.airshift.ui.components

import com.bradj.airshift.model.AssignmentKind
import com.bradj.airshift.model.FlightPhase
import com.bradj.airshift.model.LegDirection
import java.time.Duration

/** 缺失值的显示：不用灰色骨架，只给一个短横。 */
internal const val MISSING = "—"

private const val DELAY_LAMP_MIN_MINUTES = 16L
private const val DELAY_LAMP_MAX_MINUTES = 720L

/** 任务类型的标题文案。 */
fun AssignmentKind.title(): String = when (this) {
    AssignmentKind.ARRIVAL_ONLY -> "进港保障"
    AssignmentKind.DEPARTURE_ONLY -> "出港保障"
    AssignmentKind.TURNAROUND -> "进港后接续出港"
}

/** 展开条 meta 行里的短标签。 */
internal fun DetailKind.shortLabel(): String = when (this) {
    DetailKind.BOARDING_START -> "预计登机"
    DetailKind.BOARDING_END -> "预计登机口关闭"
    else -> label
}

internal data class LegStatus(val text: String, val kind: LampKind, val dot: Boolean)

/**
 * 航段状态灯。同一个小方块走完一段航班的三步：未起飞 → 已起飞 → 已落地（[FlightPhase]，进港、出港段各自判定）。
 * 已取消压过一切；已完成只在没有任何实际动态时兜底（人工完成、过点自动完成），有动态就照实亮起飞 / 落地；
 * 晚 N 分只在起飞前有意义；完全没有时间信息时不给灯。
 */
internal fun legStatus(leg: FlightLegUiModel, completed: Boolean): LegStatus? {
    val delay = leg.delayMinutes()
    return when {
        leg.flightCancellation != null -> LegStatus("已取消", LampKind.Alert, dot = true)
        leg.phase == FlightPhase.LANDED -> LegStatus("已落地", LampKind.Ok, dot = true)
        leg.phase == FlightPhase.DEPARTED -> LegStatus("已起飞", LampKind.Ok, dot = true)
        completed -> LegStatus("已完成", LampKind.Ok, dot = true)
        delay.isLate() -> LegStatus("晚 $delay 分", LampKind.Estimate, dot = true)
        leg.planned == null && leg.estimated == null -> null
        else -> LegStatus("未起飞", LampKind.Neutral, dot = false)
    }
}

/** 预计比计划晚多少分钟；两者缺一为 null。 */
internal fun FlightLegUiModel.delayMinutes(): Long? {
    val planned = planned
    val live = live
    return if (planned == null || live == null) null else Duration.between(planned, live).toMinutes()
}

/** 15 分钟以内算正点；≥12 小时是跨午夜的日期差，不可比。 */
internal fun Long?.isLate(): Boolean =
    this != null && this in DELAY_LAMP_MIN_MINUTES until DELAY_LAMP_MAX_MINUTES

/** "--" / "--:--" 是旧模型层的缺失哨兵。 */
internal fun String.isPresent(): Boolean = this != "--" && this != "--:--" && isNotBlank()

/** 本站机位：进港看到达侧，出港看出发侧。缺失返回 null。 */
internal fun FlightLegUiModel.localStand(): DetailEntry? {
    val side = if (direction == LegDirection.INBOUND) destinationDetails else originDetails
    return side.firstOrNull { it.kind == DetailKind.STAND && it.value.isPresent() }
}

/** 对方机位：进港看前站出发机位，出港看后站到达机位。 */
internal fun FlightLegUiModel.remoteStand(): DetailEntry? {
    val side = if (direction == LegDirection.INBOUND) originDetails else destinationDetails
    return side.firstOrNull { it.kind == DetailKind.STAND && it.value.isPresent() }
}
