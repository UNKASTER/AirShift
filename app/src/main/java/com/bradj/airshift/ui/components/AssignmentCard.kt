package com.bradj.airshift.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.bradj.airshift.ui.theme.OnVipAmberContainer
import com.bradj.airshift.ui.theme.VipAmber
import com.bradj.airshift.ui.theme.VipAmberContainer

/**
 * 全部执勤页使用的精简任务卡片：
 * 特服仅显示小角标；登机口/机位变更仅显示最小提醒元素，不展示变更后的实际值。
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasSpecialService) SpecialServiceBadge()
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
                    specialServices = emptyList(),
                    flightCancellation = flightCancellations.cancellationForFlight(flight, operationDate),
                    details = listOf(
                        DetailEntry(
                            text = "登机口：${assignment.inboundBoardingGate ?: "--"}",
                            hasChange = gateChange != null,
                        ),
                        DetailEntry("登机口关闭：${assignment.inboundGateClosedObservedAt.formatClock()}"),
                        DetailEntry("实际离位：${assignment.inboundActualOffBlock.formatClock()}"),
                        DetailEntry(
                            text = "到达机位：${assignment.arrivalStand ?: "--"}",
                            hasChange = standChange != null,
                        ),
                    ),
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
                    specialServices = emptyList(),
                    flightCancellation = flightCancellations.cancellationForFlight(flight, operationDate),
                    details = listOf(
                        DetailEntry(
                            text = "登机口：${assignment.boardingGate ?: "--"}",
                            hasChange = gateChange != null,
                        ),
                        DetailEntry(
                            text = "出发机位：${assignment.departureStand ?: "--"}",
                            hasChange = standChange != null,
                        ),
                        DetailEntry("登机口关闭：${assignment.outboundGateClosedObservedAt.formatClock()}"),
                        DetailEntry("实际离位：${assignment.outboundActualOffBlock.formatClock()}"),
                    ),
                )
            }
        }
    }
}
