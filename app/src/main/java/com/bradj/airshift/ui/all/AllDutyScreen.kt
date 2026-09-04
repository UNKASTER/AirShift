package com.bradj.airshift.ui.all

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.specialservice.FlightCancellationRecord
import com.bradj.airshift.specialservice.FlightServiceRecord
import com.bradj.airshift.specialservice.GateChangeRecord
import com.bradj.airshift.specialservice.StandChangeRecord
import com.bradj.airshift.ui.components.BayTitle
import com.bradj.airshift.ui.components.BoardHeader
import com.bradj.airshift.ui.components.DutyStrip
import com.bradj.airshift.ui.components.EmptyBay
import com.bradj.airshift.ui.components.LinearIcons
import com.bradj.airshift.ui.components.MucContext
import com.bradj.airshift.ui.components.NoticeStrip
import com.bradj.airshift.ui.components.boardDateText
import com.bradj.airshift.ui.components.splitIntoBays
import com.bradj.airshift.ui.theme.AirShiftMotion
import com.bradj.airshift.ui.theme.AirShiftRadius
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.AirShiftTokens
import java.time.LocalDateTime

private const val EXPANDED_SEPARATOR = "\n"

/** 全部执勤页：板头 + 导入条 + 通知条 + 当前 / 接下来 / 已完成三个栏位。条点击就地展开。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllDutyScreen(
    currentAirport: String?,
    now: LocalDateTime,
    isWorking: Boolean,
    isLiveRefreshing: Boolean,
    statusMessage: String?,
    warnings: List<String>,
    exactAlarmWarning: Boolean,
    assignments: List<RosterAssignment>,
    manuallyCompletedCount: Int,
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
    val c = AirShiftTokens.colors
    val muc = remember(specialServiceRecords, gateChanges, standChanges, flightCancellations) {
        MucContext(specialServiceRecords, gateChanges, standChanges, flightCancellations)
    }
    val bays = remember(assignments, manuallyCompletedCount, now) {
        assignments.splitIntoBays(manuallyCompletedCount, now)
    }
    // 展开态跨配置变化保留；用换行拼接的字符串存 stableId 集合。
    var expandedIds by rememberSaveable { mutableStateOf("") }
    val expanded = remember(expandedIds) { expandedIds.split(EXPANDED_SEPARATOR).filter { it.isNotEmpty() }.toSet() }
    val toggle: (String) -> Unit = { id ->
        val next = if (id in expanded) expanded - id else expanded + id
        expandedIds = next.joinToString(EXPANDED_SEPARATOR)
    }

    Column(modifier = modifier.fillMaxSize()) {
        BoardHeader(
            title = "全部执勤",
            subtitle = if (assignments.isEmpty()) null else "${assignments.size} 项 · ${bays.completed.size} 已完成",
            now = now,
            dateText = now.toLocalDate().boardDateText(),
            footer = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = LinearIcons.Stand,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = c.onBoardSecondary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (currentAirport != null) "$currentAirport · 本场" else "导入并刷新航班后自动识别机场",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.onBoardSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (statusMessage != null) {
                        Text(
                            statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.onBoardSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
        )
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isLiveRefreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = isLiveRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = c.strip,
                    color = c.board,
                )
            },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = AirShiftSpacing.M,
                    end = AirShiftSpacing.M,
                    top = 12.dp,
                    bottom = AirShiftSpacing.M,
                ),
                verticalArrangement = Arrangement.spacedBy(AirShiftSpacing.S),
            ) {
                item(key = "import") {
                    ImportStrip(isWorking = isWorking, onImportImage = onImportImage, onImportExcel = onImportExcel)
                }
                if (exactAlarmWarning) {
                    item(key = "exact_alarm") {
                        NoticeStrip(
                            lines = listOf("系统尚未允许精确闹钟，提醒时间可能略有偏差。"),
                            actionText = "开启",
                            onAction = onOpenExactAlarmSettings,
                        )
                    }
                }
                if (warnings.isNotEmpty()) {
                    item(key = "warnings") { NoticeStrip(title = "需要留意", lines = warnings) }
                }
                if (assignments.isEmpty()) {
                    item(key = "empty") {
                        EmptyBay(
                            icon = LinearIcons.Plane,
                            title = "还没有排班",
                            hint = "导入排班图片或 Excel 文件后，你的保障任务会显示在这里。",
                        )
                    }
                }
                bays.current?.let { index ->
                    item(key = "bay_current") { BayTitle("当前", testTag = "bay_current") }
                    stripItem(assignments[index], muc, expanded, toggle, emphasized = true)
                }
                if (bays.upcoming.isNotEmpty()) {
                    item(key = "bay_upcoming") {
                        BayTitle("接下来", count = bays.upcoming.size, testTag = "bay_upcoming")
                    }
                    items(bays.upcoming, key = { assignments[it].stableId }) { index ->
                        AnimatedStrip(assignments[index], muc, expanded, toggle)
                    }
                }
                if (bays.completed.isNotEmpty()) {
                    item(key = "bay_completed") {
                        BayTitle("已完成", count = bays.completed.size, testTag = "bay_completed")
                    }
                    items(bays.completed, key = { assignments[it].stableId }) { index ->
                        AnimatedStrip(assignments[index], muc, expanded, toggle, completed = true)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.stripItem(
    assignment: RosterAssignment,
    muc: MucContext,
    expanded: Set<String>,
    toggle: (String) -> Unit,
    emphasized: Boolean,
) {
    item(key = assignment.stableId) {
        AnimatedStrip(assignment, muc, expanded, toggle, emphasized = emphasized)
    }
}

@Composable
private fun LazyItemScope.AnimatedStrip(
    assignment: RosterAssignment,
    muc: MucContext,
    expanded: Set<String>,
    toggle: (String) -> Unit,
    emphasized: Boolean = false,
    completed: Boolean = false,
) {
    DutyStrip(
        assignment = assignment,
        muc = muc,
        expanded = assignment.stableId in expanded,
        emphasized = emphasized,
        completed = completed,
        onClick = { toggle(assignment.stableId) },
        modifier = Modifier.animateItem(
            fadeInSpec = null,
            fadeOutSpec = null,
            placementSpec = tween(AirShiftMotion.EmphasizedMs, easing = AirShiftMotion.Standard),
        ),
    )
}

/** 导入排班：一条 48dp 的操作条，两个小按钮；处理中时换成进度指示。 */
@Composable
private fun ImportStrip(isWorking: Boolean, onImportImage: () -> Unit, onImportExcel: () -> Unit) {
    val c = AirShiftTokens.colors
    val shape = RoundedCornerShape(AirShiftRadius.Strip)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(c.strip)
            .border(1.dp, c.rule, shape)
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = LinearIcons.File,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = c.inkSecondary,
        )
        Spacer(Modifier.width(10.dp))
        if (isWorking) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = c.board)
            Spacer(Modifier.width(10.dp))
            Text("正在解析排班…", style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary)
        } else {
            Text(
                "导入排班 · 截图或 Excel，只提取你的航班",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = c.inkSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            MiniButton(text = "图片", icon = LinearIcons.Image, onClick = onImportImage)
            Spacer(Modifier.width(6.dp))
            MiniButton(text = "Excel", icon = null, onClick = onImportExcel)
        }
    }
}

@Composable
private fun MiniButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit) {
    val c = AirShiftTokens.colors
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(AirShiftRadius.Small))
            .background(c.neutralSoft)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = c.ink)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.ink)
    }
}
