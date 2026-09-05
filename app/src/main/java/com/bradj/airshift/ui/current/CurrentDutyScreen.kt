package com.bradj.airshift.ui.current

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.bradj.airshift.model.DutyTimeline
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.dutyWindowIndices
import com.bradj.airshift.specialservice.FlightCancellationRecord
import com.bradj.airshift.specialservice.FlightServiceRecord
import com.bradj.airshift.specialservice.GateChangeRecord
import com.bradj.airshift.specialservice.StandChangeRecord
import com.bradj.airshift.ui.components.BayTitle
import com.bradj.airshift.ui.components.BoardHeader
import com.bradj.airshift.ui.components.DutyStrip
import com.bradj.airshift.ui.components.EmptyBay
import com.bradj.airshift.ui.components.LinearIcons
import com.bradj.airshift.ui.components.MucContext
import com.bradj.airshift.ui.components.OdometerText
import com.bradj.airshift.ui.components.PinnedActionBar
import com.bradj.airshift.ui.components.animateListItem
import com.bradj.airshift.ui.components.boardDateText
import com.bradj.airshift.ui.components.formatClock
import com.bradj.airshift.ui.components.remainingText
import com.bradj.airshift.ui.theme.AirShiftFonts
import com.bradj.airshift.ui.theme.AirShiftMotion
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.AirShiftTokens
import com.bradj.airshift.ui.theme.BoardNumeric
import com.bradj.airshift.ui.theme.BoardValue
import com.bradj.airshift.ui.theme.LocalReduceMotion
import java.time.Duration
import java.time.LocalDateTime

/** 当前执勤页：板面倒计时 + 当前条（展开）+ 下一条（折叠）+ 钉底"执勤完成"。 */
@Composable
fun CurrentDutyScreen(
    assignments: List<RosterAssignment>,
    dutyIndex: Int,
    specialServiceRecords: List<FlightServiceRecord>,
    gateChanges: List<GateChangeRecord>,
    standChanges: List<StandChangeRecord>,
    flightCancellations: List<FlightCancellationRecord>,
    onDutyComplete: () -> Unit,
    onGoToAllDuty: () -> Unit,
    modifier: Modifier = Modifier,
    now: LocalDateTime = LocalDateTime.now(),
    nextShiftText: String? = null,
) {
    val window = assignments.dutyWindowIndices(dutyIndex, now)
    val nextShiftBlock: (@Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit)? =
        nextShiftText?.let { text -> { NextShiftBlock(text) } }
    when {
        assignments.isEmpty() -> Column(modifier.fillMaxSize()) {
            BoardHeader(
                title = "当前执勤",
                now = now,
                dateText = now.toLocalDate().boardDateText(),
                content = nextShiftBlock,
            )
            EmptyBay(
                icon = LinearIcons.Plane,
                title = "还没有排班",
                hint = "先到“全部执勤”导入排班图片或 Excel 文件，再开始今日执勤。",
                actionText = "去导入排班",
                onAction = onGoToAllDuty,
            )
        }
        window.isEmpty() -> Column(modifier.fillMaxSize()) {
            BoardHeader(
                title = "当前执勤",
                subtitle = "${assignments.size} 项已完成",
                now = now,
                dateText = now.toLocalDate().boardDateText(),
                content = nextShiftBlock,
            )
            EmptyBay(
                icon = LinearIcons.PlaneTakeoff,
                title = "今日执勤全部完成",
                hint = "今天的保障任务已全部执行完毕，辛苦了。",
                actionText = "返回全部执勤",
                onAction = onGoToAllDuty,
            )
        }
        else -> CurrentDutyContent(
            modifier = modifier,
            assignment = assignments[window.first()],
            position = window.first() + 1,
            total = assignments.size,
            nextAssignment = window.getOrNull(1)?.let(assignments::get),
            now = now,
            muc = remember(specialServiceRecords, gateChanges, standChanges, flightCancellations) {
                MucContext(specialServiceRecords, gateChanges, standChanges, flightCancellations)
            },
            onDutyComplete = onDutyComplete,
        )
    }
}

@Composable
private fun CurrentDutyContent(
    assignment: RosterAssignment,
    position: Int,
    total: Int,
    nextAssignment: RosterAssignment?,
    now: LocalDateTime,
    muc: MucContext,
    onDutyComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val station = assignment.localAirportCode
    Column(modifier = modifier.fillMaxSize()) {
        BoardHeader(
            title = "当前执勤",
            subtitle = "第 $position / $total 项",
            now = now,
            dateText = listOfNotNull(now.toLocalDate().boardDateText(), station).joinToString(" · "),
            content = { CountdownBlock(assignment = assignment, now = now) },
            footer = nextAssignment?.let { next -> { NextTaskLine(next, now) } },
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = AirShiftSpacing.M,
                end = AirShiftSpacing.M,
                top = 12.dp,
                bottom = AirShiftSpacing.M,
            ),
            verticalArrangement = Arrangement.spacedBy(AirShiftSpacing.S),
        ) {
            item(key = "bay_current") {
                BayTitle("当前", modifier = animateListItem(), testTag = "bay_current")
            }
            item(key = assignment.stableId) {
                DutyStrip(
                    assignment = assignment,
                    muc = muc,
                    expanded = true,
                    modifier = animateListItem(),
                    emphasized = true,
                )
            }
            if (nextAssignment != null) {
                item(key = "bay_upcoming") {
                    BayTitle("接下来", modifier = animateListItem(), testTag = "bay_upcoming")
                }
                item(key = nextAssignment.stableId) {
                    DutyStrip(
                        assignment = nextAssignment,
                        muc = muc,
                        expanded = false,
                        modifier = animateListItem(),
                    )
                }
            }
        }
        PinnedActionBar(
            text = "执勤完成",
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onDutyComplete()
            },
            testTag = "current_complete",
        )
    }
}

/**
 * 板面主体：左侧"距到位 57 分"翻牌大数字，右侧到位时间。
 * 到位点已过时改为红灯"应立即到位"（光晕呼吸，系统关闭动画时静止）。
 */
@Composable
private fun CountdownBlock(assignment: RosterAssignment, now: LocalDateTime) {
    val c = AirShiftTokens.colors
    val gateArrival = DutyTimeline.gateArrivalTime(assignment)
    val remaining = gateArrival?.let { Duration.between(now, it) }
    val urgent = remaining != null && (remaining.isNegative || remaining.isZero)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("距到位", style = MaterialTheme.typography.bodyMedium, color = c.onBoardSecondary)
            Spacer(Modifier.size(2.dp))
            when {
                remaining == null -> Text(
                    "暂无到位时间信息",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.onBoardSecondary,
                )
                urgent -> UrgentLamp()
                else -> CountdownNumbers(remaining)
            }
        }
        if (gateArrival != null) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    if (urgent) "到位时间 · 已过 ${remaining!!.negated().remainingText()}" else "到位时间",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onBoardSecondary,
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    gateArrival.formatClock(),
                    style = BoardValue,
                    color = if (urgent) c.onBoardAlert else c.onBoard,
                )
            }
        }
    }
}

@Composable
private fun CountdownNumbers(remaining: Duration) {
    val c = AirShiftTokens.colors
    val totalMinutes = remaining.toMinutes()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    Row(verticalAlignment = Alignment.Bottom) {
        if (hours > 0) {
            OdometerText(text = hours.toString(), style = BoardNumeric, color = c.onBoard)
            CountdownUnit("小时")
            Spacer(Modifier.width(AirShiftSpacing.S))
        }
        OdometerText(text = minutes.toString(), style = BoardNumeric, color = c.onBoard)
        CountdownUnit("分")
    }
}

@Composable
private fun CountdownUnit(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleLarge.copy(fontFamily = AirShiftFonts.Text),
        color = AirShiftTokens.colors.onBoardSecondary,
    )
}

@Composable
private fun UrgentLamp() {
    val c = AirShiftTokens.colors
    // Compose 在系统比例为 0 时会把无限动画直接停在终值（0.45）；这里显式停在中间亮度，静止时不刺眼。
    val reduceMotion = LocalReduceMotion.current
    val halo by rememberInfiniteTransition(label = "urgentHalo").animateFloat(
        initialValue = if (reduceMotion) 0.25f else 0.15f,
        targetValue = if (reduceMotion) 0.25f else 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = AirShiftMotion.BreathMs, easing = AirShiftMotion.Standard),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "urgentHaloAlpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Canvas(modifier = Modifier.size(24.dp)) {
            drawCircle(color = c.onBoardAlert.copy(alpha = halo), radius = size.minDimension / 2f)
            drawCircle(color = c.onBoardAlert, radius = 6.dp.toPx())
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "应立即到位",
            style = MaterialTheme.typography.displaySmall,
            color = c.onBoardAlert,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 全部完成或没有排班时，板面仍回答"接下来"：下一次到岗的日期、班次与班车。 */
@Composable
private fun NextShiftBlock(text: String) {
    val c = AirShiftTokens.colors
    Column {
        Text("下一班", style = MaterialTheme.typography.bodyMedium, color = c.onBoardSecondary)
        Spacer(Modifier.size(2.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = c.onBoard)
    }
}

/** 板脚：下一任务一句话，保持为一个文本节点。 */
@Composable
private fun NextTaskLine(next: RosterAssignment, now: LocalDateTime) {
    val c = AirShiftTokens.colors
    val nextFlight = next.inboundFlight ?: next.outboundFlight.orEmpty()
    val nextGateArrival = DutyTimeline.gateArrivalTime(next)
    val tail = when {
        nextGateArrival == null -> ""
        else -> {
            val nextRemaining = Duration.between(now, nextGateArrival)
            if (nextRemaining.isNegative || nextRemaining.isZero) {
                "：应立即到位"
            } else {
                "：${nextGateArrival.formatClock()} 前到位（还有 ${nextRemaining.remainingText()}）"
            }
        }
    }
    val flightSpan = SpanStyle(color = c.onBoard, fontFamily = AirShiftFonts.Board, fontWeight = FontWeight.SemiBold)
    Text(
        buildAnnotatedString {
            append("下一任务 ")
            withStyle(flightSpan) { append(nextFlight) }
            append(tail)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = c.onBoardSecondary,
    )
}
