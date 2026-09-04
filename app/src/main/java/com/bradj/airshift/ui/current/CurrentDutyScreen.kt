package com.bradj.airshift.ui.current

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import com.bradj.airshift.model.DutyTimeline
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.dutyWindowIndices
import com.bradj.airshift.specialservice.FlightCancellationRecord
import com.bradj.airshift.specialservice.FlightServiceRecord
import com.bradj.airshift.specialservice.GateChangeRecord
import com.bradj.airshift.specialservice.StandChangeRecord
import com.bradj.airshift.ui.components.AssignmentDetailCard
import com.bradj.airshift.ui.components.BoardingPassDivider
import com.bradj.airshift.ui.components.DetailLevel
import com.bradj.airshift.ui.components.LinearIcons
import com.bradj.airshift.ui.components.MucContext
import com.bradj.airshift.ui.components.RouteArcsDecoration
import com.bradj.airshift.ui.components.formatClock
import com.bradj.airshift.ui.theme.AirShiftMotion
import com.bradj.airshift.ui.theme.AirShiftRadius
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.CeaNavyGradient
import com.bradj.airshift.ui.theme.CeaRed
import com.bradj.airshift.ui.theme.NumericHero
import com.bradj.airshift.ui.theme.NumericUnit
import com.bradj.airshift.ui.theme.TextHint
import com.bradj.airshift.ui.theme.heroShadow
import java.time.Duration
import java.time.LocalDateTime

/** 当前执勤页：倒计时 hero + 当前任务详情 + 执勤完成推进。 */
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
) {
    val window = assignments.dutyWindowIndices(dutyIndex, now)
    when {
        assignments.isEmpty() -> CurrentDutyEmpty(modifier, onGoToAllDuty)
        window.isEmpty() -> CurrentDutyFinished(modifier, onGoToAllDuty)
        else -> {
            CurrentDutyContent(
                modifier = modifier,
                assignment = assignments[window.first()],
                nextAssignment = window.getOrNull(1)?.let(assignments::get),
                now = now,
                specialServiceRecords = specialServiceRecords,
                gateChanges = gateChanges,
                standChanges = standChanges,
                flightCancellations = flightCancellations,
                onDutyComplete = onDutyComplete,
            )
        }
    }
}

@Composable
private fun CurrentDutyEmpty(modifier: Modifier, onGoToAllDuty: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(AirShiftSpacing.L),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = LinearIcons.Plane,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = TextHint,
        )
        Spacer(Modifier.height(AirShiftSpacing.S))
        Text(
            "还没有排班",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(AirShiftSpacing.S))
        Text(
            "先到“全部执勤”导入排班图片或 Excel 文件，再开始今日执勤。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(AirShiftSpacing.L))
        Button(
            onClick = onGoToAllDuty,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(AirShiftRadius.Button),
            colors = ButtonDefaults.buttonColors(containerColor = CeaRed, contentColor = Color.White),
        ) {
            Text("去导入排班", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CurrentDutyFinished(modifier: Modifier, onGoToAllDuty: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(AirShiftSpacing.L),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "今日执勤全部完成",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(AirShiftSpacing.S))
        Text("今天的保障任务已全部执行完毕，辛苦了。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(AirShiftSpacing.L))
        Button(
            onClick = onGoToAllDuty,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(AirShiftRadius.Button),
            colors = ButtonDefaults.buttonColors(containerColor = CeaRed, contentColor = Color.White),
        ) {
            Text("返回全部执勤", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CurrentDutyContent(
    assignment: RosterAssignment,
    nextAssignment: RosterAssignment?,
    now: LocalDateTime,
    specialServiceRecords: List<FlightServiceRecord>,
    gateChanges: List<GateChangeRecord>,
    standChanges: List<StandChangeRecord>,
    flightCancellations: List<FlightCancellationRecord>,
    onDutyComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(all = AirShiftSpacing.M),
        verticalArrangement = Arrangement.spacedBy(AirShiftSpacing.M),
    ) {
        item {
            CountdownHero(
                assignment = assignment,
                nextAssignment = nextAssignment,
                now = now,
            )
        }
        item {
            val muc = remember(specialServiceRecords, gateChanges, standChanges, flightCancellations) {
                MucContext(specialServiceRecords, gateChanges, standChanges, flightCancellations)
            }
            AssignmentDetailCard(assignment = assignment, muc = muc, level = DetailLevel.FULL)
        }
        item {
            Button(
                onClick = onDutyComplete,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(AirShiftRadius.Button),
                colors = ButtonDefaults.buttonColors(containerColor = CeaRed, contentColor = Color.White),
            ) {
                Text("执勤完成", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        item { Spacer(Modifier.height(AirShiftSpacing.L)) }
    }
}

/**
 * 倒计时 hero 卡（最高优先级）：
 * 常态深藏青 135° 微渐变底 + 白色 60sp Heavy 等宽大数字 + 燕子弧线纹理；
 * 紧急态（应立即到位）切换为东航红底 + 白字 + 1.2s 呼吸脉冲光晕；
 * 到位时间与"下一任务预览"为卡内两行辅助信息；三级阴影。
 */
@Composable
private fun CountdownHero(
    assignment: RosterAssignment,
    nextAssignment: RosterAssignment?,
    now: LocalDateTime,
) {
    val gateArrival = DutyTimeline.gateArrivalTime(assignment)
    val remaining = gateArrival?.let { Duration.between(now, it) }
    val urgent = remaining != null && (remaining.isNegative || remaining.isZero)

    // 紧急态呼吸脉冲光晕：1.2s 循环
    val pulse by rememberInfiniteTransition(label = "heroPulse").animateFloat(
        initialValue = 0.12f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = AirShiftMotion.EaseOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "heroPulseAlpha",
    )

    val shape = RoundedCornerShape(AirShiftRadius.Card)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heroShadow(shape)
            .clip(shape)
            .then(
                if (urgent) Modifier.background(CeaRed) else Modifier.background(CeaNavyGradient),
            ),
    ) {
        RouteArcsDecoration(
            color = Color.White.copy(alpha = if (urgent) 0.12f else 0.08f),
            modifier = Modifier.matchParentSize(),
        )
        if (urgent) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = pulse),
                                    Color.Transparent,
                                ),
                                center = androidx.compose.ui.geometry.Offset(
                                    size.width / 2f,
                                    size.height * 0.35f,
                                ),
                                radius = size.maxDimension * 0.9f,
                            ),
                        )
                    },
            )
        }
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                "当前任务 · 到位倒计时",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(AirShiftSpacing.S))
            if (remaining == null) {
                Text(
                    "暂无到位时间信息",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                )
            } else if (urgent) {
                // 强制单行 + 字号随卡片宽度自适应（40–64sp），Heavy 字重，0.02em 字距
                Text(
                    "应立即到位",
                    modifier = Modifier.fillMaxWidth(),
                    style = NumericHero.copy(letterSpacing = 0.02.em),
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 40.sp, maxFontSize = 64.sp),
                )
            } else {
                CountdownNumbers(remaining)
            }
            // 辅助信息行 1：到位时间
            if (gateArrival != null) {
                Spacer(Modifier.height(AirShiftSpacing.S))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "到位时间",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.width(AirShiftSpacing.S))
                    Text(
                        gateArrival.formatClock(),
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
            // 辅助信息行 2：下一任务预览
            nextAssignment?.let { next ->
                Spacer(Modifier.height(AirShiftSpacing.M))
                BoardingPassDivider(color = Color.White.copy(alpha = 0.35f))
                Spacer(Modifier.height(10.dp))
                val nextFlight = next.inboundFlight ?: next.outboundFlight.orEmpty()
                val nextGateArrival = DutyTimeline.gateArrivalTime(next)
                val nextText = if (nextGateArrival == null) {
                    "下一任务 $nextFlight"
                } else {
                    val nextRemaining = Duration.between(now, nextGateArrival)
                    if (nextRemaining.isNegative || nextRemaining.isZero) {
                        "下一任务 $nextFlight：应立即到位"
                    } else {
                        "下一任务 $nextFlight：${nextGateArrival.formatClock()} 前到位（还有 ${formatRemaining(nextRemaining)}）"
                    }
                }
                Text(
                    nextText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** 剩余时间大数字："X 小时 X 分钟"，60sp 白色等宽 Heavy，单位缩小跟随。 */
@Composable
private fun CountdownNumbers(remaining: Duration) {
    val totalMinutes = remaining.toMinutes()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    Row(verticalAlignment = Alignment.Bottom) {
        if (hours > 0) {
            Text("$hours", style = NumericHero, color = Color.White)
            Text(
                "小时",
                style = NumericUnit,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(start = 4.dp, end = AirShiftSpacing.S, bottom = 10.dp),
            )
        }
        Text("$minutes", style = NumericHero, color = Color.White)
        Text(
            "分钟",
            style = NumericUnit,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )
    }
}

private fun formatRemaining(duration: Duration): String {
    val totalMinutes = duration.toMinutes()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours} 小时 ${minutes} 分钟"
        hours > 0 -> "${hours} 小时"
        else -> "${minutes} 分钟"
    }
}
