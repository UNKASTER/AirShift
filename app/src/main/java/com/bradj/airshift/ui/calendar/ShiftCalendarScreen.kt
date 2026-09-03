package com.bradj.airshift.ui.calendar

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.shift.BusRecommendation
import com.bradj.airshift.model.shift.ShiftCalendarRow
import com.bradj.airshift.model.shift.ShiftCalendarRows
import com.bradj.airshift.model.shift.ShiftClock
import com.bradj.airshift.model.shift.ShiftCycle
import com.bradj.airshift.model.shift.ShiftDayKind
import com.bradj.airshift.model.shift.ShiftEstimateSource
import com.bradj.airshift.model.shift.ShiftRosterBridge
import com.bradj.airshift.model.shift.ShiftSchedule
import com.bradj.airshift.ui.components.AccentBar
import com.bradj.airshift.ui.components.LinearIcons
import com.bradj.airshift.ui.components.MetaItem
import com.bradj.airshift.ui.components.QuietCard
import com.bradj.airshift.ui.components.RouteArcsDecoration
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.AmberAccent
import com.bradj.airshift.ui.theme.CeaNavyGradient
import com.bradj.airshift.ui.theme.CeaRed
import com.bradj.airshift.ui.theme.CeaRedSoft
import com.bradj.airshift.ui.theme.InboundBlue
import com.bradj.airshift.ui.theme.InboundBlueSoft
import com.bradj.airshift.ui.theme.OnCeaRedSoft
import com.bradj.airshift.ui.theme.TextHint
import com.bradj.airshift.ui.theme.currentCardShadow
import java.time.LocalDate

/** 日历向前看 7 天、向后看 6 周，覆盖当前周期与接下来的整数个周期。 */
private const val DAYS_BEFORE_TODAY = 7L
private const val DAYS_AFTER_TODAY = 42L

/**
 * 排班日历页：上三休三周期内的上班日、休息日、班次与应乘班车。
 *
 * 全部由 [ShiftSchedule] 纯计算得出，不依赖当天是否导入过排班；
 * 只有与已导入排班同一天的那一行会改用真实航班时间。
 */
@Composable
fun ShiftCalendarScreen(
    userName: String,
    schedule: ShiftSchedule,
    groupId: Int?,
    assignments: List<RosterAssignment>,
    reportMarginMinutes: Int,
    today: LocalDate,
    onGoToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rosterDate = remember(assignments) { ShiftRosterBridge.rosterDate(assignments) }
    val rosterReportBy = remember(assignments) { ShiftRosterBridge.reportByMinutes(assignments) }
    val rosterLastTask = remember(assignments) { ShiftRosterBridge.lastTaskMinutes(assignments) }

    val rows = remember(schedule, groupId, today, rosterDate, rosterReportBy, rosterLastTask, reportMarginMinutes) {
        groupId?.let {
            ShiftCalendarRows.build(
                schedule = schedule,
                groupId = it,
                from = today.minusDays(DAYS_BEFORE_TODAY),
                toInclusive = today.plusDays(DAYS_AFTER_TODAY),
                today = today,
                rosterDate = rosterDate,
                rosterReportByMinutes = rosterReportBy,
                rosterLastTaskMinutes = rosterLastTask,
                marginMinutes = reportMarginMinutes,
            )
        }.orEmpty()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(all = AirShiftSpacing.M),
        verticalArrangement = Arrangement.spacedBy(AirShiftSpacing.M),
    ) {
        item {
            CalendarHeader(
                userName = userName,
                schedule = schedule,
                groupId = groupId,
                today = today,
                todayRow = rows.firstOrNull { it.isToday },
            )
        }
        if (groupId == null) {
            item { UnknownGroupCard(userName = userName, onGoToSettings = onGoToSettings) }
        } else {
            var lastMonth = -1
            rows.forEach { row ->
                if (row.day.date.monthValue != lastMonth) {
                    lastMonth = row.day.date.monthValue
                    item(key = "month-${row.day.date.year}-${row.day.date.monthValue}") {
                        MonthLabel(row.day.date)
                    }
                }
                item(key = row.day.date.toString()) { ShiftDayCard(row) }
            }
        }
        item { Spacer(Modifier.height(AirShiftSpacing.L)) }
    }
}

/** 页头：与全部执勤页同一张深藏青渐变卡，显示班组、周期进度与今日班次。 */
@Composable
private fun CalendarHeader(
    userName: String,
    schedule: ShiftSchedule,
    groupId: Int?,
    today: LocalDate,
    todayRow: ShiftCalendarRow?,
) {
    val cycleDay = ShiftCycle.dayIndexInCycle(today) + 1
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
                "上三休三 · 六天一个周期",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                todayRow?.let { todaySummary(it) } ?: "今天休息",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "本周期第 $cycleDay / ${ShiftCycle.CYCLE_LENGTH_DAYS} 天",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(10.dp))
            Surface(color = Color.White.copy(alpha = 0.14f), shape = CircleShape) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = LinearIcons.Plane,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = Color.White,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        groupId?.let { "$userName · 第 $it 组${if (schedule.isCalibrated) " · 已按排班表校正" else ""}" }
                            ?: "$userName · 未匹配到班组",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

private fun todaySummary(row: ShiftCalendarRow): String = when {
    row.day.kind.isRest -> "今天休息"
    !row.day.attends -> "今天不到岗"
    row.day.kind == ShiftDayKind.HANDOVER -> "今天交接班 · ${row.day.slot?.label.orEmpty()}"
    else -> "今天上班 · ${row.day.slot?.label.orEmpty()}"
}

@Composable
private fun MonthLabel(date: LocalDate) {
    Text(
        "${date.year} 年 ${date.monthValue} 月",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun UnknownGroupCard(userName: String, onGoToSettings: () -> Unit) {
    QuietCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AirShiftSpacing.L),
            verticalArrangement = Arrangement.spacedBy(AirShiftSpacing.S),
        ) {
            Icon(
                imageVector = LinearIcons.Alert,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = TextHint,
            )
            Text(
                "未匹配到班组",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "姓名“$userName”不在已知的班组名单里，无法推算班次。" +
                    "可在设置中核对姓名、手动指定班组，或导入一份带“候机早班/中班/夜班”的 Excel 让应用自动校正。",
                style = MaterialTheme.typography.bodyMedium,
                color = TextHint,
            )
            TextButton(onClick = onGoToSettings) { Text("前往设置") }
        }
    }
}

/** 一天一张卡：左侧色条区分日型，右侧班次、班车与预计下班。 */
@Composable
private fun ShiftDayCard(row: ShiftCalendarRow) {
    val accent = when {
        row.day.kind.isRest -> TextHint
        row.day.kind == ShiftDayKind.HANDOVER -> AmberAccent
        else -> CeaRed
    }
    QuietCard(vip = row.isToday) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            AccentBar(color = accent)
            Column(
                modifier = Modifier.fillMaxWidth().padding(AirShiftSpacing.M),
                verticalArrangement = Arrangement.spacedBy(AirShiftSpacing.XS),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${row.day.date.monthValue}月${row.day.date.dayOfMonth}日",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(AirShiftSpacing.S))
                    Text(
                        weekdayLabel(row.day.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (row.isToday) {
                        Spacer(Modifier.width(AirShiftSpacing.S))
                        Pill(text = "今天", background = CeaRedSoft, foreground = OnCeaRedSoft)
                    }
                    Spacer(Modifier.weight(1f))
                    row.day.slot?.takeIf { row.day.attends }?.let { slot ->
                        Pill(text = slot.label, background = InboundBlueSoft, foreground = InboundBlue)
                    }
                }
                Text(
                    dayKindText(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (row.day.attends) {
                    row.bus?.let { bus ->
                        MetaItem(
                            icon = LinearIcons.Clock,
                            label = "班车",
                            value = "%02d:%02d".format(bus.departure.hour, bus.departure.minute),
                        )
                        Text(
                            busDetail(bus),
                            style = MaterialTheme.typography.labelSmall,
                            // 到场晚于到位时间是异常情况，必须显眼而不是混在灰字里。
                            color = if (bus.spareMinutes < 0) AmberAccent else TextHint,
                        )
                    } ?: Text(
                        "没有合适班车，需自行安排到场",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberAccent,
                    )
                    row.offDutyMinutes?.let { offDuty ->
                        MetaItem(
                            icon = LinearIcons.PlaneTakeoff,
                            label = if (row.day.kind == ShiftDayKind.HANDOVER) "交班" else "预计下班",
                            value = ShiftClock.format(offDuty),
                            hasChange = false,
                        )
                        if (row.offDutySource == ShiftEstimateSource.ESTIMATE) {
                            Text(
                                "按历史规律预估",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextHint,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun dayKindText(row: ShiftCalendarRow): String = when {
    row.day.kind.isRest -> "休息"
    row.day.isHandoverExempt ->
        "交接班日不到岗（${row.day.slotInheritedFrom?.let { "${it.monthValue}月${it.dayOfMonth}日" }.orEmpty()}" +
            "排${row.day.slot?.label.orEmpty()}，干到次日凌晨）"
    row.day.kind == ShiftDayKind.HANDOVER ->
        "交接班 · 只上上午，交班后回家"
    row.day.kind == ShiftDayKind.WORK_FIRST -> "上班 · 接班日，上午由上一班交出"
    else -> "上班 · 整班"
}

private fun busDetail(bus: BusRecommendation): String = buildString {
    append("${ShiftClock.format(bus.arriveAtMinutes)} 到场")
    append(" · 最晚 ${ShiftClock.format(bus.reportByMinutes)} 到位")
    when {
        bus.spareMinutes < 0 -> append("，比规定晚 ${-bus.spareMinutes} 分钟，建议提前一班")
        else -> append("，富余 ${bus.spareMinutes} 分钟")
    }
    if (bus.isExtraHandoverBus) append(" · 交接班加班车")
    append(if (bus.source == ShiftEstimateSource.ROSTER) " · 按当日排班" else " · 预估")
}

private fun weekdayLabel(date: LocalDate): String =
    "星期" + WEEKDAYS[date.dayOfWeek.value - 1]

private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 与 DirectionTag 同规格的小胶囊：全圆角、12sp Semibold。 */
@Composable
private fun Pill(text: String, background: Color, foreground: Color) {
    Surface(color = background, shape = CircleShape) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            color = foreground,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
