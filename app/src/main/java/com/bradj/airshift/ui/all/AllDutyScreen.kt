package com.bradj.airshift.ui.all

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.specialservice.FlightCancellationRecord
import com.bradj.airshift.specialservice.FlightServiceRecord
import com.bradj.airshift.specialservice.GateChangeRecord
import com.bradj.airshift.specialservice.StandChangeRecord
import com.bradj.airshift.ui.components.AssignmentCard

/** 全部执勤页：当日全部任务，含导入、状态消息、待确认特服与任务列表。 */
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
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
                    Text("我的保障任务", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${assignments.size} 项", color = MaterialTheme.colorScheme.primary)
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
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun AllDutyHeader(name: String, airport: String?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("你好，$name", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            airport?.let { "当前位置：$it" } ?: "当前位置：导入并刷新航班后自动识别机场",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImportCard(
    isWorking: Boolean,
    onImportImage: () -> Unit,
    onImportExcel: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        if (isWorking) {
            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                enabled = false,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(10.dp))
                Text("处理中")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onImportImage,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("上传排班图片") }
                Button(
                    onClick = onImportExcel,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("导入 Excel") }
            }
        }
    }
}

@Composable
private fun WarningCard(warnings: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("需要留意", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            warnings.distinct().forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun ActionNotice(text: String, action: String, onAction: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun EmptyRoster() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("还没有排班", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("导入排班图片或 Excel 文件后，你的保障任务会显示在这里。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
