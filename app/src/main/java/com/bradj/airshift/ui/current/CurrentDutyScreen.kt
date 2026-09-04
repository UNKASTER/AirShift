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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Surface
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
import com.bradj.airshift.model.AssignmentKind
import com.bradj.airshift.model.DutyTimeline
import com.bradj.airshift.model.LegDirection
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.dutyWindowIndices
import com.bradj.airshift.specialservice.FlightCancellationRecord
import com.bradj.airshift.specialservice.FlightServiceRecord
import com.bradj.airshift.specialservice.GateChangeRecord
import com.bradj.airshift.specialservice.StandChangeRecord
import com.bradj.airshift.ui.components.AccentBar
import com.bradj.airshift.ui.components.BoardingPassDivider
import com.bradj.airshift.ui.components.DetailEntry
import com.bradj.airshift.ui.components.DetailKind
import com.bradj.airshift.ui.components.FlightRow
import com.bradj.airshift.ui.components.LinearIcons
import com.bradj.airshift.ui.components.QuietCard
import com.bradj.airshift.ui.components.RouteArcsDecoration
import com.bradj.airshift.ui.components.cancellationForFlight
import com.bradj.airshift.ui.components.forFlight
import com.bradj.airshift.ui.components.formatClock
import com.bradj.airshift.ui.components.formatEpoch
import com.bradj.airshift.ui.components.gateChangeDisplayValue
import com.bradj.airshift.ui.components.gateForFlight
import com.bradj.airshift.ui.components.standForFlight
import com.bradj.airshift.ui.theme.AirShiftMotion
import com.bradj.airshift.ui.theme.AirShiftRadius
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.CeaNavyGradient
import com.bradj.airshift.ui.theme.CeaRed
import com.bradj.airshift.ui.theme.InboundBlue
import com.bradj.airshift.ui.theme.NumericHero
import com.bradj.airshift.ui.theme.NumericUnit
import com.bradj.airshift.ui.theme.OnVipAmberContainer
import com.bradj.airshift.ui.theme.TextHint
import com.bradj.airshift.ui.theme.VipAmberContainer
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
            CurrentAssignmentCard(
                assignment = assignment,
                specialServiceRecords = specialServiceRecords,
                gateChanges = gateChanges,
                standChanges = standChanges,
                flightCancellations = flightCancellations,
            )
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

/**
 * 当前任务详情卡：与列表页同一套卡片语言 ——
 * 左侧 4px 类型色条（出港红 / 进港蓝 / 接续上蓝下红）、接续段撕线虚线分隔、
 * 末段撕线 + meta 区（登机口关闭 / 实际离位 / 机号 / 机型）。
 */
@Composable
private fun CurrentAssignmentCard(
    assignment: RosterAssignment,
    specialServiceRecords: List<FlightServiceRecord>,
    gateChanges: List<GateChangeRecord>,
    standChanges: List<StandChangeRecord>,
    flightCancellations: List<FlightCancellationRecord>,
) {
    val vipBadgeText = when {
        assignment.inboundHasVip && assignment.outboundHasVip -> "VIP"
        assignment.inboundHasVip -> "进港 VIP"
        assignment.outboundHasVip -> "出港 VIP"
        else -> null
    }
    QuietCard(modifier = Modifier.fillMaxWidth(), vip = assignment.hasVip) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            when (assignment.kind) {
                AssignmentKind.ARRIVAL_ONLY -> AccentBar(color = InboundBlue)
                AssignmentKind.DEPARTURE_ONLY -> AccentBar(color = CeaRed)
                AssignmentKind.TURNAROUND -> Column(modifier = Modifier.fillMaxHeight()) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .weight(1f)
                            .background(InboundBlue),
                    )
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .weight(1f)
                            .background(CeaRed),
                    )
                }
            }
            // 卡片内边距：左右 16dp，上下 20dp（给时间块底边对齐留出空间）
            Column(modifier = Modifier.padding(horizontal = AirShiftSpacing.M, vertical = 20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when (assignment.kind) {
                            AssignmentKind.ARRIVAL_ONLY -> "进港保障"
                            AssignmentKind.DEPARTURE_ONLY -> "出港保障"
                            AssignmentKind.TURNAROUND -> "进港后接续出港"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    vipBadgeText?.let { label ->
                        Surface(
                            color = VipAmberContainer,
                            shape = CircleShape,
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = AirShiftSpacing.S, vertical = 4.dp),
                                color = OnVipAmberContainer,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                assignment.inboundFlight?.let { flight ->
                    val operationDate = assignment.scheduledArrival?.toLocalDate()
                    val gateChange = gateChanges.gateForFlight(flight, operationDate)
                    val standChange = standChanges.standForFlight(flight, operationDate)
                    FlightRow(
                        direction = LegDirection.INBOUND,
                        flight = flight,
                        fromCode = assignment.originCode,
                        fromName = assignment.origin,
                        toCode = assignment.localAirportCode,
                        toName = assignment.localAirportName,
                        planned = assignment.scheduledArrival,
                        estimated = assignment.estimatedArrival,
                        actual = assignment.actualArrival,
                        specialServices = specialServiceRecords.forFlight(flight, operationDate),
                        flightCancellation = flightCancellations.cancellationForFlight(flight, operationDate),
                        originDetails = buildList {
                            add(
                                DetailEntry(
                                    kind = DetailKind.GATE,
                                    value = gateChange?.let {
                                        gateChangeDisplayValue(assignment.inboundBoardingGate, it)
                                    } ?: (assignment.inboundBoardingGate ?: "--"),
                                    hasChange = gateChange != null,
                                ),
                            )
                            add(DetailEntry(kind = DetailKind.STAND, value = assignment.inboundDepartureStand ?: "--"))
                        },
                        destinationDetails = buildList {
                            add(DetailEntry(kind = DetailKind.GATE, value = "--"))
                            add(
                                DetailEntry(
                                    kind = DetailKind.STAND,
                                    value = standChange?.let {
                                        "${assignment.arrivalStand ?: "--"} → ${it.stand}"
                                    } ?: (assignment.arrivalStand ?: "--"),
                                    hasChange = standChange != null,
                                ),
                            )
                        },
                        details = buildList {
                            gateChange?.let {
                                add(DetailEntry(kind = DetailKind.GATE_CHANGE_SOURCE, value = "MUC 更新于 ${it.updatedAtEpochMillis.formatEpoch("HH:mm")}"))
                            }
                            standChange?.let {
                                add(DetailEntry(kind = DetailKind.STAND_CHANGE_SOURCE, value = "MUC 更新于 ${it.updatedAtEpochMillis.formatEpoch("HH:mm")}"))
                            }
                            add(DetailEntry(kind = DetailKind.GATE_CLOSED, value = assignment.inboundGateClosedObservedAt.formatClock()))
                            add(DetailEntry(kind = DetailKind.OFF_BLOCK, value = assignment.inboundActualOffBlock.formatClock()))
                        },
                        aircraftRegistration = if (assignment.outboundFlight == null) assignment.aircraftRegistration else null,
                        aircraftType = if (assignment.outboundFlight == null) assignment.aircraftType ?: "--" else null,
                        offBlock = assignment.inboundActualOffBlock,
                    )
                }
                if (assignment.inboundFlight != null && assignment.outboundFlight != null) {
                    Spacer(Modifier.height(AirShiftSpacing.M))
                    BoardingPassDivider()
                    Spacer(Modifier.height(AirShiftSpacing.M))
                }
                assignment.outboundFlight?.let { flight ->
                    val operationDate = assignment.scheduledDeparture?.toLocalDate()
                    val gateChange = gateChanges.gateForFlight(flight, operationDate)
                    val standChange = standChanges.standForFlight(flight, operationDate)
                    FlightRow(
                        direction = LegDirection.OUTBOUND,
                        flight = flight,
                        fromCode = assignment.localAirportCode,
                        fromName = assignment.localAirportName,
                        toCode = assignment.destinationCode,
                        toName = assignment.destination,
                        planned = assignment.scheduledDeparture,
                        estimated = assignment.estimatedDeparture,
                        actual = assignment.actualDeparture,
                        specialServices = specialServiceRecords.forFlight(flight, operationDate),
                        flightCancellation = flightCancellations.cancellationForFlight(flight, operationDate),
                        originDetails = buildList {
                            add(
                                DetailEntry(
                                    kind = DetailKind.GATE,
                                    value = gateChange?.let {
                                        gateChangeDisplayValue(assignment.boardingGate, it)
                                    } ?: (assignment.boardingGate ?: "--"),
                                    hasChange = gateChange != null,
                                ),
                            )
                            add(
                                DetailEntry(
                                    kind = DetailKind.STAND,
                                    value = standChange?.let {
                                        "${assignment.departureStand ?: "--"} → ${it.stand}"
                                    } ?: (assignment.departureStand ?: "--"),
                                    hasChange = standChange != null,
                                ),
                            )
                        },
                        destinationDetails = buildList {
                            add(DetailEntry(kind = DetailKind.GATE, value = "--"))
                            add(DetailEntry(kind = DetailKind.STAND, value = assignment.outboundArrivalStand ?: "--"))
                        },
                        details = buildList {
                            add(DetailEntry(kind = DetailKind.BOARDING_START, value = DutyTimeline.boardingStartTime(assignment).formatClock()))
                            add(DetailEntry(kind = DetailKind.BOARDING_END, value = DutyTimeline.gateCloseTime(assignment).formatClock()))
                            gateChange?.let {
                                add(DetailEntry(kind = DetailKind.GATE_CHANGE_SOURCE, value = "MUC 更新于 ${it.updatedAtEpochMillis.formatEpoch("HH:mm")}"))
                            }
                            standChange?.let {
                                add(DetailEntry(kind = DetailKind.STAND_CHANGE_SOURCE, value = "MUC 更新于 ${it.updatedAtEpochMillis.formatEpoch("HH:mm")}"))
                            }
                            add(DetailEntry(kind = DetailKind.GATE_CLOSED, value = assignment.outboundGateClosedObservedAt.formatClock()))
                            add(DetailEntry(kind = DetailKind.OFF_BLOCK, value = assignment.outboundActualOffBlock.formatClock()))
                        },
                        aircraftRegistration = assignment.aircraftRegistration,
                        aircraftType = assignment.aircraftType ?: "--",
                        offBlock = assignment.outboundActualOffBlock,
                    )
                }
            }
        }
    }
}
