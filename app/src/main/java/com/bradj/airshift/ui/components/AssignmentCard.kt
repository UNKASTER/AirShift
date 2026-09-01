package com.bradj.airshift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bradj.airshift.model.AssignmentKind
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.specialservice.FlightCancellationRecord
import com.bradj.airshift.specialservice.FlightServiceRecord
import com.bradj.airshift.specialservice.GateChangeRecord
import com.bradj.airshift.specialservice.StandChangeRecord
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.CeaRed
import com.bradj.airshift.ui.theme.InboundBlue
import com.bradj.airshift.ui.theme.OnVipAmberContainer
import com.bradj.airshift.ui.theme.VipAmberContainer

/**
 * 全部执勤页任务卡：
 * 左侧 4px 色条区分类型（出港红 / 进港蓝 / 接续航班上蓝下红双色）；
 * 接续航班两段同卡，中间登机牌撕线虚线分隔；
 * 底部图标+小字 meta 行（机号 / 机型）。
 */
@Composable
fun AssignmentCard(
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
    val inboundServices = assignment.inboundFlight?.let { flight ->
        specialServiceRecords.forFlight(flight, assignment.scheduledArrival?.toLocalDate())
    }.orEmpty()
    val outboundServices = assignment.outboundFlight?.let { flight ->
        specialServiceRecords.forFlight(flight, assignment.scheduledDeparture?.toLocalDate())
    }.orEmpty()
    val hasSpecialService = inboundServices.isNotEmpty() || outboundServices.isNotEmpty()

    QuietCard(modifier = Modifier.fillMaxWidth(), vip = assignment.hasVip) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // 左侧 4px 类型色条：出港红 / 进港蓝 / 接续上蓝下红
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AirShiftSpacing.S),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (hasSpecialService) SpecialServiceBadge()
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
                }
                Spacer(Modifier.height(10.dp))
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
                        specialServices = emptyList(),
                        flightCancellation = flightCancellations.cancellationForFlight(flight, operationDate),
                        originDetails = listOf(
                            DetailEntry(
                                label = "登机口",
                                value = assignment.inboundBoardingGate ?: "--",
                                hasChange = gateChange != null,
                            ),
                            DetailEntry(label = "机位", value = assignment.inboundDepartureStand ?: "--"),
                        ),
                        destinationDetails = listOf(
                            DetailEntry(label = "登机口", value = "--"),
                            DetailEntry(
                                label = "机位",
                                value = assignment.arrivalStand ?: "--",
                                hasChange = standChange != null,
                            ),
                        ),
                        details = listOf(
                            DetailEntry(label = "登机口关闭", value = assignment.inboundGateClosedObservedAt.formatClock()),
                            DetailEntry(label = "实际离位", value = assignment.inboundActualOffBlock.formatClock()),
                        ),
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
                        direction = "出港",
                        flight = flight,
                        fromCode = assignment.localAirportCode,
                        fromName = assignment.localAirportName,
                        toCode = assignment.destinationCode,
                        toName = assignment.destination,
                        planned = assignment.scheduledDeparture,
                        estimated = assignment.estimatedDeparture,
                        actual = assignment.actualDeparture,
                        specialServices = emptyList(),
                        flightCancellation = flightCancellations.cancellationForFlight(flight, operationDate),
                        originDetails = listOf(
                            DetailEntry(
                                label = "登机口",
                                value = assignment.boardingGate ?: "--",
                                hasChange = gateChange != null,
                            ),
                            DetailEntry(
                                label = "机位",
                                value = assignment.departureStand ?: "--",
                                hasChange = standChange != null,
                            ),
                        ),
                        destinationDetails = listOf(
                            DetailEntry(label = "登机口", value = "--"),
                            DetailEntry(label = "机位", value = assignment.outboundArrivalStand ?: "--"),
                        ),
                        details = listOf(
                            DetailEntry(label = "登机口关闭", value = assignment.outboundGateClosedObservedAt.formatClock()),
                            DetailEntry(label = "实际离位", value = assignment.outboundActualOffBlock.formatClock()),
                        ),
                        aircraftRegistration = assignment.aircraftRegistration,
                        aircraftType = assignment.aircraftType ?: "--",
                        offBlock = assignment.outboundActualOffBlock,
                    )
                }
            }
        }
    }
}
