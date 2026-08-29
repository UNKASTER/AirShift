package com.bradj.airshift.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
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
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.specialservice.FlightCancellationRecord
import com.bradj.airshift.specialservice.FlightServiceRecord
import com.bradj.airshift.specialservice.GateChangeRecord
import com.bradj.airshift.specialservice.StandChangeRecord
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.BorderSoft
import com.bradj.airshift.ui.theme.OnVipAmberContainer
import com.bradj.airshift.ui.theme.TextPrimary
import com.bradj.airshift.ui.theme.TextSecondary
import com.bradj.airshift.ui.theme.VipAmberContainer

/**
 * 全部执勤页使用的精简任务卡片：
 * 白卡 + 方向小标签；特服仅显示小角标；登机口/机位变更仅显示最小提醒元素。
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
                    specialServices = emptyList(),
                    flightCancellation = flightCancellations.cancellationForFlight(flight, operationDate),
                    details = listOf(
                        DetailEntry(
                            label = "登机口",
                            value = assignment.inboundBoardingGate ?: "--",
                            hasChange = gateChange != null,
                        ),
                        DetailEntry(label = "登机口关闭", value = assignment.inboundGateClosedObservedAt.formatClock()),
                        DetailEntry(label = "实际离位", value = assignment.inboundActualOffBlock.formatClock()),
                        DetailEntry(
                            label = "到达机位",
                            value = assignment.arrivalStand ?: "--",
                            hasChange = standChange != null,
                        ),
                    ),
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
                    specialServices = emptyList(),
                    flightCancellation = flightCancellations.cancellationForFlight(flight, operationDate),
                    details = listOf(
                        DetailEntry(
                            label = "登机口",
                            value = assignment.boardingGate ?: "--",
                            hasChange = gateChange != null,
                        ),
                        DetailEntry(
                            label = "出发机位",
                            value = assignment.departureStand ?: "--",
                            hasChange = standChange != null,
                        ),
                        DetailEntry(label = "登机口关闭", value = assignment.outboundGateClosedObservedAt.formatClock()),
                        DetailEntry(label = "实际离位", value = assignment.outboundActualOffBlock.formatClock()),
                    ),
                )
            }
        }
    }
}
