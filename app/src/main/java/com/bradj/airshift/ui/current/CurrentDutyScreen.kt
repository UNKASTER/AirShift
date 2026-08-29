package com.bradj.airshift.ui.current

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bradj.airshift.model.AssignmentKind
import com.bradj.airshift.model.DutyTimeline
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.specialservice.FlightCancellationRecord
import com.bradj.airshift.specialservice.FlightServiceRecord
import com.bradj.airshift.specialservice.GateChangeRecord
import com.bradj.airshift.specialservice.StandChangeRecord
import com.bradj.airshift.ui.components.DetailEntry
import com.bradj.airshift.ui.components.FlightRow
import com.bradj.airshift.ui.components.cancellationForFlight
import com.bradj.airshift.ui.components.forFlight
import com.bradj.airshift.ui.components.formatClock
import com.bradj.airshift.ui.components.formatEpoch
import com.bradj.airshift.ui.components.gateForFlight
import com.bradj.airshift.ui.components.standForFlight
import com.bradj.airshift.ui.theme.OnVipAmberContainer
import com.bradj.airshift.ui.theme.VipAmber
import com.bradj.airshift.ui.theme.VipAmberContainer
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime

private const val COUNTDOWN_TICK_MILLIS = 60 * 1000L

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
) {
    when {
        assignments.isEmpty() -> CurrentDutyEmpty(modifier, onGoToAllDuty)
        dutyIndex >= assignments.size -> CurrentDutyFinished(modifier, onGoToAllDuty)
        else -> {
            val index = dutyIndex.coerceAtMost(assignments.lastIndex)
            CurrentDutyContent(
                modifier = modifier,
                assignments = assignments,
                index = index,
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
        modifier = modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("还没有排班", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "先到“全部执勤”导入排班图片或 Excel 文件，再开始今日执勤。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGoToAllDuty, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("去导入排班")
        }
    }
}

@Composable
private fun CurrentDutyFinished(modifier: Modifier, onGoToAllDuty: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("今日执勤全部完成", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("今天的保障任务已全部执行完毕，辛苦了。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGoToAllDuty, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("返回全部执勤")
        }
    }
}

@Composable
private fun CurrentDutyContent(
    assignments: List<RosterAssignment>,
    index: Int,
    specialServiceRecords: List<FlightServiceRecord>,
    gateChanges: List<GateChangeRecord>,
    standChanges: List<StandChangeRecord>,
    flightCancellations: List<FlightCancellationRecord>,
    onDutyComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val assignment = assignments[index]
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(assignment.stableId) {
        while (true) {
            now = LocalDateTime.now()
            delay(COUNTDOWN_TICK_MILLIS)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            CountdownCard(
                assignment = assignment,
                nextAssignment = assignments.getOrNull(index + 1),
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
            ) {
                Text("执勤完成", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun CountdownCard(
    assignment: RosterAssignment,
    nextAssignment: RosterAssignment?,
    now: LocalDateTime,
) {
    val gateArrival = DutyTimeline.gateArrivalTime(assignment)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                "当前任务到位倒计时",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(6.dp))
            if (gateArrival == null) {
                Text(
                    "暂无到位时间信息",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                val remaining = Duration.between(now, gateArrival)
                if (remaining.isNegative || remaining.isZero) {
                    Text(
                        "应立即到位",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Text(
                        "须在 ${formatRemaining(remaining)} 后到达登机口",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "到位时间：${gateArrival.formatClock()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                )
            }
            nextAssignment?.let { next ->
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
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
            }
        }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = if (assignment.hasVip) BorderStroke(2.dp, VipAmber) else null,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
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
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                vipBadgeText?.let { label ->
                    Surface(
                        color = VipAmberContainer,
                        shape = RoundedCornerShape(7.dp),
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            color = OnVipAmberContainer,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Text(
                buildList {
                    add("机号：${assignment.aircraftRegistration}")
                    assignment.aircraftType?.let { add("机型：$it") }
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
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
                    details = buildList {
                        add(
                            DetailEntry(
                                gateChange?.let {
                                    "登机口：${assignment.inboundBoardingGate ?: "--"} → ${it.boardingGate}（MUC 更新于 ${it.updatedAtEpochMillis.formatEpoch("HH:mm")}）"
                                } ?: "登机口：${assignment.inboundBoardingGate ?: "--"}",
                            ),
                        )
                        add(DetailEntry("登机口关闭：${assignment.inboundGateClosedObservedAt.formatClock()}"))
                        add(DetailEntry("实际离位：${assignment.inboundActualOffBlock.formatClock()}"))
                        add(
                            DetailEntry(
                                standChange?.let {
                                    "到达机位：${assignment.arrivalStand ?: "--"} → ${it.stand}（MUC 更新于 ${it.updatedAtEpochMillis.formatEpoch("HH:mm")}）"
                                } ?: "到达机位：${assignment.arrivalStand ?: "--"}",
                            ),
                        )
                    },
                )
            }
            if (assignment.inboundFlight != null && assignment.outboundFlight != null) {
                Spacer(Modifier.height(12.dp))
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
                    details = buildList {
                        add(DetailEntry("预计登机开始：${DutyTimeline.boardingStartTime(assignment).formatClock()}"))
                        add(DetailEntry("预计登机口关闭：${DutyTimeline.gateCloseTime(assignment).formatClock()}"))
                        add(
                            DetailEntry(
                                gateChange?.let {
                                    "登机口：${assignment.boardingGate ?: "--"} → ${it.boardingGate}（MUC 更新于 ${it.updatedAtEpochMillis.formatEpoch("HH:mm")}）"
                                } ?: "登机口：${assignment.boardingGate ?: "--"}",
                            ),
                        )
                        add(
                            DetailEntry(
                                standChange?.let {
                                    "出发机位：${assignment.departureStand ?: "--"} → ${it.stand}（MUC 更新于 ${it.updatedAtEpochMillis.formatEpoch("HH:mm")}）"
                                } ?: "出发机位：${assignment.departureStand ?: "--"}",
                            ),
                        )
                        add(DetailEntry("登机口关闭：${assignment.outboundGateClosedObservedAt.formatClock()}"))
                        add(DetailEntry("实际离位：${assignment.outboundActualOffBlock.formatClock()}"))
                    },
                )
            }
        }
    }
}
