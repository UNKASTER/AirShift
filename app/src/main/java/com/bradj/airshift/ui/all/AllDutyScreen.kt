package com.bradj.airshift.ui.all

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.specialservice.FlightCancellationRecord
import com.bradj.airshift.specialservice.FlightServiceRecord
import com.bradj.airshift.specialservice.GateChangeRecord
import com.bradj.airshift.specialservice.StandChangeRecord
import com.bradj.airshift.ui.components.AccentBar
import com.bradj.airshift.ui.components.AssignmentCard
import com.bradj.airshift.ui.components.LinearIcons
import com.bradj.airshift.ui.components.QuietCard
import com.bradj.airshift.ui.components.RouteArcsDecoration
import com.bradj.airshift.ui.theme.AirShiftRadius
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.AmberAccent
import com.bradj.airshift.ui.theme.AmberSoft
import com.bradj.airshift.ui.theme.CeaNavy
import com.bradj.airshift.ui.theme.CeaNavyGradient
import com.bradj.airshift.ui.theme.CeaRed
import com.bradj.airshift.ui.theme.TextHint
import com.bradj.airshift.ui.theme.currentCardShadow
import java.time.LocalDate

/** 全部执勤页：当日全部任务，含导入、状态消息与任务列表。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllDutyScreen(
    userName: String,
    currentAirport: String?,
    today: LocalDate,
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
            item { AllDutyHeader(userName, currentAirport, today) }
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                    ) {
                        Text(
                            "${assignments.size} 项",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
 * 页头：深藏青 135° 微渐变底 + 低透明度燕子弧线纹理；
 * 问候语缩小、日期信息放大、位置做成 chip。
 */
@Composable
private fun AllDutyHeader(name: String, airport: String?, today: LocalDate) {
    val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
    val dateText = "${today.monthValue}月${today.dayOfMonth}日 星期${weekdays[today.dayOfWeek.value - 1]}"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .currentCardShadow(MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
            .background(CeaNavyGradient),
    ) {
        RouteArcsDecoration(
            color = Color.White.copy(alpha = 0.08f),
            modifier = Modifier.matchParentSize(),
        )
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                "中国东方航空 · 地面服务保障",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "你好，$name",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                dateText,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Spacer(Modifier.height(10.dp))
            // 位置 chip
            Surface(
                color = Color.White.copy(alpha = 0.14f),
                shape = CircleShape,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = LinearIcons.Stand,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = Color.White,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        airport ?: "导入并刷新航班后自动识别机场",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/** 导入排班：主按钮东航红实心+白色图标，次按钮 1px 藏青描边；说明文字 12sp 灰。 */
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
                shape = RoundedCornerShape(AirShiftRadius.Button),
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
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "截图或 Excel 均可，只提取分配给你的航班",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(AirShiftSpacing.M))
                Row(horizontalArrangement = Arrangement.spacedBy(AirShiftSpacing.S)) {
                    Button(
                        onClick = onImportImage,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(AirShiftRadius.Button),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CeaRed,
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(
                            LinearIcons.Image,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(AirShiftSpacing.S))
                        Text("排班图片", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onImportExcel,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(AirShiftRadius.Button),
                        border = BorderStroke(1.dp, CeaNavy),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CeaNavy),
                    ) {
                        Icon(
                            LinearIcons.File,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(AirShiftSpacing.S))
                        Text("Excel 文件", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** "需要留意"提示条：琥珀左边条 + 浅琥珀底（深色模式为半透明琥珀）+ 三角警告图标。 */
@Composable
private fun WarningCard(warnings: List<String>) {
    val container = if (isSystemInDarkTheme()) AmberAccent.copy(alpha = 0.16f) else AmberSoft
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            AccentBar(color = AmberAccent)
            Column(
                modifier = Modifier.padding(AirShiftSpacing.M),
                verticalArrangement = Arrangement.spacedBy(AirShiftSpacing.S),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = LinearIcons.Alert,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = AmberAccent,
                    )
                    Spacer(Modifier.width(AirShiftSpacing.S))
                    Text(
                        "需要留意",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                warnings.distinct().forEach {
                    Text(
                        "• $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 轻量提醒条：浅琥珀底 + 琥珀左边条 + 文字按钮。 */
@Composable
private fun ActionNotice(text: String, action: String, onAction: () -> Unit) {
    val container = if (isSystemInDarkTheme()) AmberAccent.copy(alpha = 0.16f) else AmberSoft
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        shape = MaterialTheme.shapes.large,
    ) {
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
                Icon(
                    imageVector = LinearIcons.Alert,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = AmberAccent,
                )
                Spacer(Modifier.width(AirShiftSpacing.S))
                Text(
                    text,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Icon(
                imageVector = LinearIcons.Plane,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = TextHint,
            )
            Text(
                "还没有排班",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "导入排班图片或 Excel 文件后，你的保障任务会显示在这里。",
                style = MaterialTheme.typography.bodyMedium,
                color = TextHint,
            )
        }
    }
}
