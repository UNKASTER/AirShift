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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bradj.airshift.model.AssignmentKind
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.CeaRed
import com.bradj.airshift.ui.theme.InboundBlue
import com.bradj.airshift.ui.theme.OnVipAmberContainer
import com.bradj.airshift.ui.theme.VipAmberContainer

/**
 * 任务卡（全部执勤页与当前执勤页共用一套卡片语言）：
 * 左侧 4px 色条区分类型（出港红 / 进港蓝 / 接续上蓝下红双色）；
 * 接续航班两段同卡，中间登机牌撕线虚线分隔；每段由 [FlightRow] 渲染。
 * 详略由 [level] 决定：列表页只打角标，当前页展开 MUC 变更与特服明细。
 */
@Composable
fun AssignmentDetailCard(
    assignment: RosterAssignment,
    muc: MucContext,
    level: DetailLevel,
    modifier: Modifier = Modifier,
) {
    val legs = remember(assignment, muc, level) { assignment.legUiModels(muc, level) }
    val vipBadgeText = when {
        assignment.inboundHasVip && assignment.outboundHasVip -> "VIP"
        assignment.inboundHasVip -> "进港 VIP"
        assignment.outboundHasVip -> "出港 VIP"
        else -> null
    }
    val showSpecialServiceBadge = level == DetailLevel.SUMMARY && legs.any { it.hasSpecialServices }

    QuietCard(modifier = modifier.fillMaxWidth(), vip = assignment.hasVip) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            when (assignment.kind) {
                AssignmentKind.ARRIVAL_ONLY -> AccentBar(color = InboundBlue)
                AssignmentKind.DEPARTURE_ONLY -> AccentBar(color = CeaRed)
                AssignmentKind.TURNAROUND -> Column(modifier = Modifier.fillMaxHeight()) {
                    Box(modifier = Modifier.width(4.dp).weight(1f).background(InboundBlue))
                    Box(modifier = Modifier.width(4.dp).weight(1f).background(CeaRed))
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
                        if (showSpecialServiceBadge) SpecialServiceBadge()
                        vipBadgeText?.let { label ->
                            Surface(color = VipAmberContainer, shape = CircleShape) {
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
                legs.forEachIndexed { index, leg ->
                    if (index > 0) {
                        Spacer(Modifier.height(AirShiftSpacing.M))
                        BoardingPassDivider()
                        Spacer(Modifier.height(AirShiftSpacing.M))
                    }
                    FlightRow(leg)
                }
            }
        }
    }
}
