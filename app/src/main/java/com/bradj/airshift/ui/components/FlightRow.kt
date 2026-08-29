package com.bradj.airshift.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bradj.airshift.specialservice.FlightCancellationRecord
import com.bradj.airshift.specialservice.FlightCancellationScope
import com.bradj.airshift.specialservice.FlightServiceRecord
import java.time.LocalDateTime

/** 完整航段行：航班号、实时/计划时间、机场三字码与名称、取消标记、特服详情、详情行。 */
@Composable
fun FlightRow(
    direction: String,
    flight: String,
    fromCode: String?,
    fromName: String?,
    toCode: String?,
    toName: String?,
    planned: LocalDateTime?,
    estimated: LocalDateTime?,
    actual: LocalDateTime?,
    specialServices: List<FlightServiceRecord>,
    flightCancellation: FlightCancellationRecord?,
    details: List<DetailEntry>,
) {
    val liveTime = actual ?: estimated
    val timeColor = if (liveTime == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    val isInbound = direction == "进港"
    val sectionColor = if (isInbound) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
    val accentColor = if (isInbound) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = sectionColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(flight, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                Column(modifier = Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.End) {
                    Text(
                        "实时",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        liveTime.formatClock(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = timeColor,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "计划：${planned.formatClock()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AirportRouteLabel(
                    code = fromCode,
                    name = fromName,
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                )
                Text("→", modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                AirportRouteLabel(
                    code = toCode,
                    name = toName,
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                )
            }
            flightCancellation?.let { cancellation ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(7.dp),
                ) {
                    Text(
                        if (cancellation.scope == FlightCancellationScope.TRIP) "MUC：行程已取消" else "MUC：特服已取消",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (specialServices.isNotEmpty()) {
                Spacer(Modifier.height(9.dp))
                SpecialServiceDetails(specialServices)
            }
            if (details.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    details.forEach { detail ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                detail.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = accentColor,
                                fontWeight = FontWeight.Medium,
                            )
                            if (detail.hasChange) {
                                ChangeIndicator(modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AirportRouteLabel(
    code: String?,
    name: String?,
    modifier: Modifier,
    horizontalAlignment: Alignment.Horizontal,
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Text(
            code ?: "---",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            name ?: "机场名称待更新",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 特服详情：徽章 + 每条记录的类型/置信度/确认状态/更新时间。 */
@Composable
fun SpecialServiceDetails(records: List<FlightServiceRecord>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        records.sortedBy { it.serviceType.ordinal }.forEach { record ->
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(7.dp),
            ) {
                Text(
                    record.badgeLabel(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
    Spacer(Modifier.height(5.dp))
    records.sortedBy { it.serviceType.ordinal }.forEach { record ->
        Text(
            "${record.typeLabel()} · ${record.confidence.label()} · 已确认 · 更新 ${record.updatedAtEpochMillis.formatEpoch("MM-dd HH:mm")}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
