package com.bradj.airshift.widget

import com.bradj.airshift.model.AssignmentKind
import com.bradj.airshift.model.DutyTimeline
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.isDutyComplete
import com.bradj.airshift.model.nextIncompleteDutyIndex
import com.bradj.airshift.ui.components.formatClock
import java.time.Duration
import java.time.LocalDateTime

/** 小组件页面：[Message] 为整页提示（无排班/全部完成），[Duty] 为单个执勤。 */
sealed interface WidgetPage {
    data class Message(val title: String, val detail: String) : WidgetPage

    data class Duty(
        val header: String,
        val status: WidgetDutyStatus,
        val countdownTarget: LocalDateTime?,
        val gateArrivalClock: String,
        val hasVip: Boolean,
        val legs: List<WidgetFlightLeg>,
    ) : WidgetPage
}

enum class WidgetDutyStatus {
    /** 当前执勤之前的执勤（或已过点完成的执勤）：显示"已完成"。 */
    COMPLETED,

    /** 当前及之后的执勤：显示到位倒计时。 */
    COUNTDOWN,

    /** 到位时间已过：显示"应立即到位"。 */
    OVERDUE,

    /** 无任何时间信息：显示"暂无到位时间信息"。 */
    NO_TIME,
}

data class WidgetFlightLeg(
    val directionLabel: String,
    val flight: String,
    val place: String,
    val gate: String,
)

/** 固定小组件只选择当前未完成执勤；无排班或全部完成时返回对应提示页。 */
fun List<RosterAssignment>.toCurrentWidgetPage(
    manuallyCompletedCount: Int,
    now: LocalDateTime = LocalDateTime.now(),
): WidgetPage {
    val currentIndex = nextIncompleteDutyIndex(manuallyCompletedCount, now)
    val pages = toWidgetPages(manuallyCompletedCount, now)
    return pages.getOrElse(currentIndex) { pages.single() }
}

/** 构建完整执勤页面模型；固定小组件从中选择当前未完成执勤。 */
fun List<RosterAssignment>.toWidgetPages(
    manuallyCompletedCount: Int,
    now: LocalDateTime = LocalDateTime.now(),
): List<WidgetPage> {
    if (isEmpty()) {
        return listOf(WidgetPage.Message("还没有排班", "打开航勤智排导入排班后，这里会显示当前执勤。"))
    }
    val currentIndex = nextIncompleteDutyIndex(manuallyCompletedCount, now)
    if (currentIndex >= size) {
        return listOf(WidgetPage.Message("今日执勤全部完成", "今天的保障任务已全部执行完毕，辛苦了。"))
    }
    return mapIndexed { index, assignment -> assignment.toWidgetPage(index, size, currentIndex, now) }
}

private fun RosterAssignment.toWidgetPage(
    index: Int,
    total: Int,
    currentIndex: Int,
    now: LocalDateTime,
): WidgetPage.Duty {
    val gateArrival = DutyTimeline.gateArrivalTime(this)
    val remaining = gateArrival?.let { Duration.between(now, it) }
    val status = when {
        index < currentIndex || isDutyComplete(now) -> WidgetDutyStatus.COMPLETED
        gateArrival == null -> WidgetDutyStatus.NO_TIME
        remaining == null || remaining.isNegative || remaining.isZero -> WidgetDutyStatus.OVERDUE
        else -> WidgetDutyStatus.COUNTDOWN
    }
    val kindLabel = when (kind) {
        AssignmentKind.ARRIVAL_ONLY -> "进港保障"
        AssignmentKind.DEPARTURE_ONLY -> "出港保障"
        AssignmentKind.TURNAROUND -> "进港后接续出港"
    }
    // 标题行从尾部省略：机型最靠后，空间不足时最先被省略。
    val header = buildString {
        append("执勤 ${index + 1}/$total · $kindLabel · $aircraftRegistration")
        aircraftType?.let { append(" · $it") }
    }
    return WidgetPage.Duty(
        header = header,
        status = status,
        countdownTarget = gateArrival.takeIf { status == WidgetDutyStatus.COUNTDOWN },
        gateArrivalClock = gateArrival.formatClock(),
        hasVip = hasVip,
        legs = buildList {
            inboundFlight?.let { flight ->
                add(
                    WidgetFlightLeg(
                        directionLabel = "进港",
                        flight = flight,
                        place = airportDisplay(origin, originCode),
                        // 进港行尾显示到达站（本站）机位；到达侧无登机口字段。
                        gate = arrivalStand?.let { "机位 $it" } ?: "--",
                    ),
                )
            }
            outboundFlight?.let { flight ->
                add(
                    WidgetFlightLeg(
                        directionLabel = "出港",
                        flight = flight,
                        place = airportDisplay(destination, destinationCode),
                        // 出港行尾显示出发地（本站）登机口，缺省回退出发机位。
                        gate = boardingGate ?: departureStand?.let { "机位 $it" } ?: "--",
                    ),
                )
            }
        },
    )
}

/** 航段机场显示：中文名 + 三字码并列，缺一则降级显示现有项。 */
private fun airportDisplay(name: String?, code: String?): String = when {
    name != null && code != null -> "$name $code"
    name != null -> name
    else -> code ?: "--"
}
