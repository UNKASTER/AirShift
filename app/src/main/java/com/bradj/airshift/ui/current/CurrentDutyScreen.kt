package com.bradj.airshift.ui.current

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bradj.airshift.model.AssignmentKind
import com.bradj.airshift.model.DutyTimeline
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.dutyWindowIndices
import com.bradj.airshift.specialservice.FlightCancellationRecord
import com.bradj.airshift.specialservice.FlightServiceRecord
import com.bradj.airshift.specialservice.GateChangeRecord
import com.bradj.airshift.specialservice.StandChangeRecord
import com.bradj.airshift.ui.components.AccentBar
import com.bradj.airshift.ui.components.DetailEntry
import com.bradj.airshift.ui.components.FlightRow
import com.bradj.airshift.ui.components.QuietCard
import com.bradj.airshift.ui.components.cancellationForFlight
import com.bradj.airshift.ui.components.forFlight
import com.bradj.airshift.ui.components.formatClock
import com.bradj.airshift.ui.components.formatEpoch
import com.bradj.airshift.ui.components.gateChangeDisplayValue
import com.bradj.airshift.ui.components.gateForFlight
import com.bradj.airshift.ui.components.standForFlight
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.BorderSoft
import com.bradj.airshift.ui.theme.CeaRed
import com.bradj.airshift.ui.theme.NumericHero
import com.bradj.airshift.ui.theme.NumericLarge
import com.bradj.airshift.ui.theme.NumericUnit
import com.bradj.airshift.ui.theme.OnVipAmberContainer
import com.bradj.airshift.ui.theme.TextHint
import com.bradj.airshift.ui.theme.TextPrimary
import com.bradj.airshift.ui.theme.TextSecondary
import com.bradj.airshift.ui.theme.VipAmberContainer
import java.time.Duration
import java.time.LocalDateTime

/** 当前执勤页：倒计时卡 + 当前任务详情 + 执勤完成推进。 */
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
        Text(
            "还没有排班",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Spacer(Modifier.height(AirShiftSpacing.S))
        Text(
            "先到“全部执勤”导入排班图片或 Excel 文件，再开始今日执勤。",
            color = TextSecondary,
        )
        Spacer(Modifier.height(AirShiftSpacing.L))
        Button(
            onClick = onGoToAllDuty,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = CircleShape,
        ) {
            Text("去导入排班")
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
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Spacer(Modifier.height(AirShiftSpacing.S))
        Text("今天的保障任务已全部执行完毕，辛苦了。", color = TextSecondary)
        Spacer(Modifier.height(AirShiftSpacing.L))
        Button(
            onClick = onGoToAllDuty,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = CircleShape,
        ) {
            Text("返回全部执勤")
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
            CountdownCard(
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
                shape = CircleShape,
            ) {
                Text("执勤完成", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        item { Spacer(Modifier.height(AirShiftSpacing.L)) }
    }
}

/**
 * 倒计时卡（最高优先级）：白卡 + 左侧 4dp 红竖条；
 * "X 小时 X 分钟"超大红色等宽数字；"到位时间"单独突出；下一任务降为灰色小字。
 */
@Composable
private fun CountdownCard(
    assignment: RosterAssignment,
    nextAssignment: RosterAssignment?,
    now: LocalDateTime,
) {
    val gateArrival = DutyTimeline.gateArrivalTime(assignment)
    QuietCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            AccentBar(color = CeaRed)
            Column(modifier = Modifier.fillMaxWidth().padding(AirShiftSpacing.M)) {
                Text(
                    "当前任务 · 到位倒计时",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(AirShiftSpacing.S))
                if (gateArrival == null) {
                    Text(
                        "暂无到位时间信息",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextHint,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    val remaining = Duration.between(now, gateArrival)
                    if (remaining.isNegative || remaining.isZero) {
                        Text(
                            "应立即到位",
                            style = NumericHero,
                            color = CeaRed,
                        )
                    } else {
                        CountdownNumbers(remaining)
                    }
                    Spacer(Modifier.height(AirShiftSpacing.S))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "到位时间",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.width(AirShiftSpacing.S))
                        Text(
                            gateArrival.formatClock(),
                            style = NumericLarge,
                            color = TextPrimary,
                        )
                    }
                }
                nextAssignment?.let { next ->
                    Spacer(Modifier.height(AirShiftSpacing.M))
                    HorizontalDivider(color = BorderSoft)
                    Spacer(Modifier.height(AirShiftSpacing.S))
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
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

/** 剩余时间大数字："X 小时 X 分钟"，数字用超大红色等宽粗体，单位缩小跟随。 */
@Composable
private fun CountdownNumbers(remaining: Duration) {
    val totalMinutes = remaining.toMinutes()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    Row(verticalAlignment = Alignment.Bottom) {
        if (hours > 0) {
            Text("$hours", style = NumericHero, color = CeaRed)
            Text(
                "小时",
                style = NumericUnit,
                color = TextPrimary,
                modifier = Modifier.padding(start = 4.dp, end = AirShiftSpacing.S, bottom = 4.dp),
            )
        }
        Text("$minutes", style = NumericHero, color = CeaRed)
        Text(
            "分钟",
            style = NumericUnit,
            color = TextPrimary,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
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
        Column(modifier = Modifier.padding(AirShiftSpacing.M)) {
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
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
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
            Text(
                buildList {
                    add("机号：${assignment.aircraftRegistration}")
                    add("机型：${assignment.aircraftType ?: "--"}")
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(AirShiftSpacing.M))
            assignment.inboundFlight?.let { flight ->
                val operationDate = assignment.scheduledArrival?.toLocalDate()
                val gateChange = gateChanges.gateForFlight(flight, operationDate)
                val standChange = standChanges.standForFlight(flight, operationDate)
                FlightRow(
                    direction = "进港",
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
                                label = "登机口",
                                value = gateChange?.let {
                                    gateChangeDisplayValue(assignment.inboundBoardingGate, it)
                                } ?: (assignment.inboundBoardingGate ?: "--"),
                                hasChange = gateChange != null,
                            ),
                        )
                        add(DetailEntry(label = "出发机位", value = assignment.inboundDepartureStand ?: "--"))
                    },
                    destinationDetails = buildList {
                        add(
                            DetailEntry(
                                label = "到达机位",
                                value = standChange?.let {
                                    "${assignment.arrivalStand ?: "--"} → ${it.stand}"
                                } ?: (assignment.arrivalStand ?: "--"),
                                hasChange = standChange != null,
                            ),
                        )
                    },
                    details = buildList {
                        gateChange?.let {
                            add(DetailEntry(label = "登机口变更", value = "MUC 更新于 ${it.updatedAtEpochMillis.formatEpoch("HH:mm")}"))
                        }
                        standChange?.let {
                            add(DetailEntry(label = "机位变更", value = "MUC 更新于 ${it.updatedAtEpochMillis.formatEpoch("HH:mm")}"))
                        }
                        add(DetailEntry(label = "登机口关闭", value = assignment.inboundGateClosedObservedAt.formatClock()))
                        add(DetailEntry(label = "实际离位", value = assignment.inboundActualOffBlock.formatClock()))
                    },
                )
            }
            if (assignment.inboundFlight != null && assignment.outboundFlight != null) {
                Spacer(Modifier.height(AirShiftSpacing.M))
                HorizontalDivider(color = BorderSoft)
                Spacer(Modifier.height(AirShiftSpacing.M))
            }
            assignment.outboundFlight?.let { flight ->
                val operationDate = assignment.scheduledDeparture?.toLocalDate()
                val gateChange = gateChanges.gateForFlight(flight, operationDate)
                val standChange = standChanges.standForFlight(flight, operationDate)
                FlightRow(
                    direction = "出港",
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
                                label = "登机口",
                                value = gateChange?.let {
                                    gateChangeDisplayValue(assignment.boardingGate, it)
                                } ?: (assignment.boardingGate ?: "--"),
                                hasChange = gateChange != null,
                            ),
                        )
                        add(
                            DetailEntry(
                                label = "出发机位",
                                value = standChange?.let {
                                    "${assignment.departureStand ?: "--"} → ${it.stand}"
                                } ?: (assignment.departureStand ?: "--"),
                                hasChange = standChange != null,
                            ),
                        )
                    },
                    destinationDetails = buildList {
                        add(DetailEntry(label = "到达机位", value = assignment.outboundArrivalStand ?: "--"))
                    },
                    details = buildList {
                        add(DetailEntry(label = "预计登机开始", value = DutyTimeline.boardingStartTime(assignment).formatClock()))
                        add(DetailEntry(label = "预计登机口关闭", value = DutyTimeline.gateCloseTime(assignment).formatClock()))
                        gateChange?.let {
                            add(DetailEntry(label = "登机口变更", value = "MUC 更新于 ${it.updatedAtEpochMillis.formatEpoch("HH:mm")}"))
                        }
                        standChange?.let {
                            add(DetailEntry(label = "机位变更", value = "MUC 更新于 ${it.updatedAtEpochMillis.formatEpoch("HH:mm")}"))
                        }
                        add(DetailEntry(label = "登机口关闭", value = assignment.outboundGateClosedObservedAt.formatClock()))
                        add(DetailEntry(label = "实际离位", value = assignment.outboundActualOffBlock.formatClock()))
                    },
                )
            }
        }
    }
}
