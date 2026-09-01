package com.bradj.airshift.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bradj.airshift.specialservice.FlightCancellationRecord
import com.bradj.airshift.specialservice.FlightCancellationScope
import com.bradj.airshift.specialservice.FlightServiceRecord
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.CeaNavy
import com.bradj.airshift.ui.theme.CeaRedSoft
import com.bradj.airshift.ui.theme.NumericMedium
import com.bradj.airshift.ui.theme.OnCeaRedSoft
import com.bradj.airshift.ui.theme.TextBody
import com.bradj.airshift.ui.theme.TextHint
import com.bradj.airshift.ui.theme.TextPrimary
import com.bradj.airshift.ui.theme.TextSecondary
import java.time.LocalDateTime

/**
 * 航段区块（白卡内，无彩色底块）：
 * 方向小标签 + 22sp 粗体航班号；右侧等宽实时时间；
 * 航线"起点 ——→ 终点"水平排列，中间细线箭头；无数据项用灰色"--"占位；
 * 详情行 label（灰 12sp）+ value（深色 14sp）两列对齐。
 */
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
    originDetails: List<DetailEntry> = emptyList(),
    destinationDetails: List<DetailEntry> = emptyList(),
) {
    val liveTime = actual ?: estimated
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DirectionTag(direction)
            Spacer(Modifier.width(AirShiftSpacing.S))
            Text(
                flight,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            TimeBlock(
                label = "实时",
                live = liveTime,
                planned = planned,
            )
        }
        Spacer(Modifier.height(AirShiftSpacing.M))
        // 航线：机场中文名与细线箭头同一行（箭头对齐中文名中轴线），三字码在下一行
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                RouteName(name = fromName, alignEnd = false)
            }
            RouteArrow(
                color = CeaNavy.copy(alpha = 0.55f),
                modifier = Modifier
                    .padding(horizontal = AirShiftSpacing.S)
                    .width(48.dp)
                    .height(12.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                RouteName(name = toName, alignEnd = true)
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                fromCode ?: "--",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = if (fromCode == null) TextHint else CeaNavy,
            )
            Spacer(Modifier.width(64.dp))
            Text(
                toCode ?: "--",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = if (toCode == null) TextHint else CeaNavy,
                textAlign = TextAlign.End,
            )
        }
        // 站点信息：始发站信息挂在左端点下方，到达站信息挂在右端点下方
        if (originDetails.isNotEmpty() || destinationDetails.isNotEmpty()) {
            Spacer(Modifier.height(AirShiftSpacing.S))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    originDetails.forEach { StationDetail(it, alignEnd = false) }
                }
                Spacer(Modifier.width(64.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    destinationDetails.forEach { StationDetail(it, alignEnd = true) }
                }
            }
        }
        flightCancellation?.let { cancellation ->
            Spacer(Modifier.height(AirShiftSpacing.S))
            Surface(color = CeaRedSoft, shape = CircleShape) {
                Text(
                    if (cancellation.scope == FlightCancellationScope.TRIP) "MUC：行程已取消" else "MUC：特服已取消",
                    modifier = Modifier.padding(horizontal = AirShiftSpacing.S, vertical = 4.dp),
                    color = OnCeaRedSoft,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (specialServices.isNotEmpty()) {
            Spacer(Modifier.height(AirShiftSpacing.S))
            SpecialServiceDetails(specialServices)
        }
        if (details.isNotEmpty()) {
            Spacer(Modifier.height(AirShiftSpacing.M))
            Column(verticalArrangement = Arrangement.spacedBy(AirShiftSpacing.S)) {
                details.forEach { detail ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            detail.label,
                            modifier = Modifier.width(88.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                        )
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                detail.value,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextBody,
                                fontWeight = FontWeight.Medium,
                            )
                            if (detail.hasChange) {
                                ChangeIndicator(modifier = Modifier.padding(start = AirShiftSpacing.S))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 时间块：标签（起飞/到达）+ 实时等宽大数字 + 计划时间。 */
@Composable
private fun TimeBlock(label: String, live: LocalDateTime?, planned: LocalDateTime?) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TextHint,
        )
        Text(
            live.formatClock(),
            style = NumericMedium,
            color = if (live == null) TextHint else TextPrimary,
        )
        Text(
            "计划 ${planned.formatClock()}",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )
    }
}

/** 站点下方的小信息行：label 灰 + value 深色，跟随站点左右对齐。 */
@Composable
private fun StationDetail(entry: DetailEntry, alignEnd: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            entry.label,
            style = MaterialTheme.typography.labelSmall,
            color = TextHint,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            entry.value,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.bodySmall,
            color = TextBody,
            fontWeight = FontWeight.Medium,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (entry.hasChange) {
            ChangeIndicator(modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** 航线端点中文名：深色粗体，无数据用灰色"--"占位。 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.RouteName(name: String?, alignEnd: Boolean) {
    Text(
        name ?: "--",
        modifier = Modifier.align(if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (name == null) TextHint else TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 特服详情：徽章 + 每条记录的类型/置信度/确认状态/更新时间。 */
@Composable
fun SpecialServiceDetails(records: List<FlightServiceRecord>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AirShiftSpacing.S),
        verticalArrangement = Arrangement.spacedBy(AirShiftSpacing.S),
    ) {
        records.sortedBy { it.serviceType.ordinal }.forEach { record ->
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = CircleShape,
            ) {
                Text(
                    record.badgeLabel(),
                    modifier = Modifier.padding(horizontal = AirShiftSpacing.S, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
    Spacer(Modifier.height(AirShiftSpacing.S))
    records.sortedBy { it.serviceType.ordinal }.forEach { record ->
        Text(
            "${record.typeLabel()} · ${record.confidence.label()} · 已确认 · 更新 ${record.updatedAtEpochMillis.formatEpoch("MM-dd HH:mm")}",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
    }
}
