package com.bradj.airshift.ui.all

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.specialservice.FlightCancellationRecord
import com.bradj.airshift.specialservice.FlightServiceRecord
import com.bradj.airshift.specialservice.GateChangeRecord
import com.bradj.airshift.specialservice.StandChangeRecord
import com.bradj.airshift.ui.components.AccentBar
import com.bradj.airshift.ui.components.AssignmentCard
import com.bradj.airshift.ui.components.QuietCard
import com.bradj.airshift.ui.components.RouteArcsDecoration
import com.bradj.airshift.ui.components.StatusDot
import com.bradj.airshift.ui.theme.AmberAccent
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.CeaNavy
import com.bradj.airshift.ui.theme.CeaRedGradient
import com.bradj.airshift.ui.theme.InboundBlue
import com.bradj.airshift.ui.theme.TextHint
import com.bradj.airshift.ui.theme.TextPrimary
import com.bradj.airshift.ui.theme.TextSecondary

/** 全部执勤页：当日全部任务，含导入、状态消息与任务列表。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllDutyScreen(
    userName: String,
    currentAirport: String?,
    isWorking: Boolean,
    isLiveRefreshing: Boolean,
    statusMessage: String?,
    warnings: List<String>,
    exactAlarmWarning: Boolean,
    assignments: List<RosterAssignment>,
    specialServiceRecords: List<FlightServiceRecord>,
    gateChanges: List<GateChangeRecord>,
    standChanges: List<StandChangeRecord>,
    flightCancellations: List<FlightCancellationRecord>,
    onImportImage: () -> Unit,
    onImportExcel: () -> Unit,
    onRefresh: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = isLiveRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(all = AirShiftSpacing.M),
            verticalArrangement = Arrangement.spacedBy(AirShiftSpacing.M),
        ) {
            item { AllDutyHeader(userName, currentAirport) }
            item {
                ImportCard(
                    isWorking = isWorking,
                    onImportImage = onImportImage,
                    onImportExcel = onImportExcel,
                )
            }
            statusMessage?.let { message ->
                item {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
            if (exactAlarmWarning) {
                item {
                    ActionNotice(
                        text = "系统尚未允许精确闹钟，提醒时间可能略有偏差。",
                        action = "开启精确提醒",
                        onAction = onOpenExactAlarmSettings,
                    )
                }
            }
            if (warnings.isNotEmpty()) item { WarningCard(warnings) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "我的保障任务",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Text(
                        "${assignments.size} 项",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (assignments.isEmpty()) {
                item { EmptyRoster() }
            } else {
                items(assignments, key = { it.stableId }) { assignment ->
                    AssignmentCard(
                        assignment = assignment,
                        specialServiceRecords = specialServiceRecords,
                        gateChanges = gateChanges,
                        standChanges = standChanges,
                        flightCancellations = flightCancellations,
                    )
                }
            }
            item { Spacer(Modifier.height(AirShiftSpacing.L)) }
        }
    }
}

/**
 * 白底问候头部：红色小色块点缀 + 藏青大标题 + 低透明度航线弧线装饰。
 * 红色仅作标识区点缀，不大面积铺屏。
 */
@Composable
private fun AllDutyHeader(name: String, airport: String?) {
    QuietCard(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            RouteArcsDecoration(
                color = CeaNavy.copy(alpha = 0.07f),
                modifier = Modifier.matchParentSize(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AirShiftSpacing.M),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(CeaRedGradient),
                )
                Spacer(Modifier.width(AirShiftSpacing.M))
                Column {
                    Text(
                        "中国东方航空 · 地面服务保障",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = InboundBlue,
                    )
                    Spacer(Modifier.height(AirShiftSpacing.S))
                    Text(
                        "你好，$name",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(AirShiftSpacing.S))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(color = InboundBlue, modifier = Modifier.size(6.dp))
                        Spacer(Modifier.width(AirShiftSpacing.S))
                        Text(
                            airport?.let { "当前位置：$it" } ?: "当前位置：导入并刷新航班后自动识别机场",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportCard(
    isWorking: Boolean,
    onImportImage: () -> Unit,
    onImportExcel: () -> Unit,
) {
    QuietCard {
        if (isWorking) {
            Button(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AirShiftSpacing.M)
                    .height(48.dp),
                enabled = false,
                shape = CircleShape,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(AirShiftSpacing.S))
                Text("处理中")
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(AirShiftSpacing.M)) {
                Text(
                    "导入排班",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    "截图或 Excel 均可，只提取分配给你的航班",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(AirShiftSpacing.M))
                Row(horizontalArrangement = Arrangement.spacedBy(AirShiftSpacing.S)) {
                    Button(
                        onClick = onImportImage,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = CircleShape,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(AirShiftSpacing.S))
                        Text("排班图片")
                    }
                    OutlinedButton(
                        onClick = onImportExcel,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CeaNavy),
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(AirShiftSpacing.S))
                        Text("Excel 文件")
                    }
                }
            }
        }
    }
}

/** “需要留意”提示：白卡 + 左侧琥珀竖条 + 小图标，不用大色块。 */
@Composable
private fun WarningCard(warnings: List<String>) {
    QuietCard {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            AccentBar(color = AmberAccent)
            Column(
                modifier = Modifier.padding(AirShiftSpacing.M),
                verticalArrangement = Arrangement.spacedBy(AirShiftSpacing.S),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(AmberAccent.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "!",
                            color = AmberAccent,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(AirShiftSpacing.S))
                    Text(
                        "需要留意",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                }
                warnings.distinct().forEach {
                    Text(
                        "• $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

/** 轻量提醒条：白卡 + 琥珀竖条 + 文字按钮。 */
@Composable
private fun ActionNotice(text: String, action: String, onAction: () -> Unit) {
    QuietCard {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccentBar(color = AmberAccent)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AirShiftSpacing.M),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                TextButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

@Composable
private fun EmptyRoster() {
    QuietCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AirShiftSpacing.L),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AirShiftSpacing.S),
        ) {
            Text(
                "还没有排班",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Text(
                "导入排班图片或 Excel 文件后，你的保障任务会显示在这里。",
                style = MaterialTheme.typography.bodyMedium,
                color = TextHint,
            )
        }
    }
}
