package com.bradj.airshift.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.bradj.airshift.ui.components.BayTitle
import com.bradj.airshift.ui.components.BoardHeader
import com.bradj.airshift.ui.components.EmptyBay
import com.bradj.airshift.ui.components.HolderBar
import com.bradj.airshift.ui.components.LampKind
import com.bradj.airshift.ui.components.LinearIcons
import com.bradj.airshift.ui.components.StatusLamp
import com.bradj.airshift.ui.components.boardDateText
import com.bradj.airshift.ui.theme.AirShiftRadius
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.AirShiftTokens
import com.bradj.airshift.ui.theme.BoardValue
import com.bradj.airshift.ui.theme.NumericSmall
import com.bradj.airshift.ui.theme.StripTime
import com.bradj.airshift.ui.theme.currentCardShadow
import java.time.LocalDate
import java.time.LocalDateTime

/** 日历向前看 7 天、向后看 6 周，覆盖当前周期与接下来的整数个周期。 */
private const val DAYS_BEFORE_TODAY = 7L
private const val DAYS_AFTER_TODAY = 42L

/**
 * 排班日历页：板面给今天的班次与班车，下面按月排信息条。
 * 全部由 [ShiftSchedule] 纯计算得出，只有与已导入排班同一天的那一行改用真实航班时间。
 */
@Composable
fun ShiftCalendarScreen(
    userName: String,
    schedule: ShiftSchedule,
    groupId: Int?,
    assignments: List<RosterAssignment>,
    reportMarginMinutes: Int,
    now: LocalDateTime,
    onGoToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = now.toLocalDate()
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
    val todayRow = rows.firstOrNull { it.isToday }

    Column(modifier = modifier.fillMaxSize()) {
        BoardHeader(
            title = "排班日历",
            subtitle = if (groupId != null) "上三休三 · 第 $groupId 组" else "上三休三",
            now = now,
            dateText = today.boardDateText(),
            content = { TodayBlock(todayRow = todayRow, today = today, hasGroup = groupId != null) },
            footer = { CalendarFooter(todayRow = todayRow, calibrated = schedule.isCalibrated) },
        )
        if (groupId == null) {
            EmptyBay(
                icon = LinearIcons.Alert,
                title = "未匹配到班组",
                hint = "姓名“$userName”不在已知的班组名单里，无法推算班次。" +
                    "可在设置中核对姓名、手动指定班组，或导入一份带“候机早班/中班/夜班”的 Excel 让应用自动校正。",
                actionText = "前往设置",
                onAction = onGoToSettings,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = AirShiftSpacing.M,
                    end = AirShiftSpacing.M,
                    top = 12.dp,
                    bottom = AirShiftSpacing.M,
                ),
                verticalArrangement = Arrangement.spacedBy(AirShiftSpacing.S),
            ) {
                var lastMonth = -1
                rows.forEach { row ->
                    if (row.day.date.monthValue != lastMonth) {
                        lastMonth = row.day.date.monthValue
                        item(key = "month-${row.day.date.year}-${row.day.date.monthValue}") {
                            BayTitle("${row.day.date.monthValue}月")
                        }
                    }
                    item(key = row.day.date.toString()) { ShiftStrip(row) }
                }
            }
        }
    }
}

/** 板面主体：今天的班次大字 + 一句说明，右侧班车时间。 */
@Composable
private fun TodayBlock(todayRow: ShiftCalendarRow?, today: LocalDate, hasGroup: Boolean) {
    val c = AirShiftTokens.colors
    val cycleDay = ShiftCycle.dayIndexInCycle(today) + 1
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val kind = todayRow?.let(::dayKindShort) ?: "未匹配到班组"
            Text(
                "今天 · $kind · 本周期第 $cycleDay / ${ShiftCycle.CYCLE_LENGTH_DAYS} 天",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onBoardSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    todayHeadline(todayRow, hasGroup),
                    style = MaterialTheme.typography.displayMedium,
                    color = c.onBoard,
                    maxLines = 1,
                )
                todayRow?.takeIf { it.day.attends }?.let { row ->
                    Spacer(Modifier.width(12.dp))
                    Text(
                        dayKindText(row),
                        modifier = Modifier.padding(bottom = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.onBoardSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        todayRow?.bus?.takeIf { todayRow.day.attends }?.let { bus ->
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 4.dp)) {
                Text("班车", style = MaterialTheme.typography.bodySmall, color = c.onBoardSecondary)
                Spacer(Modifier.height(2.dp))
                Text(bus.departureText(), style = BoardValue, color = c.onBoard)
            }
        }
    }
}

@Composable
private fun CalendarFooter(todayRow: ShiftCalendarRow?, calibrated: Boolean) {
    val c = AirShiftTokens.colors
    val offDuty = todayRow?.takeIf { it.day.attends }?.offDutyMinutes
    Text(
        buildString {
            if (offDuty != null) {
                append(if (todayRow.day.kind == ShiftDayKind.HANDOVER) "交班 " else "预计下班 ")
                append(ShiftClock.format(offDuty))
                append(" · ")
            }
            append(if (calibrated) "已按排班表校正" else "内置班组表，导入带班次行的 Excel 后自动校正")
        },
        style = MaterialTheme.typography.bodyMedium,
        color = c.onBoardSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun todayHeadline(row: ShiftCalendarRow?, hasGroup: Boolean): String = when {
    !hasGroup -> "—"
    row == null || row.day.kind.isRest -> "休息"
    !row.day.attends -> "不到岗"
    else -> row.day.slot?.label ?: "上班"
}

private fun dayKindShort(row: ShiftCalendarRow): String = when {
    row.day.kind.isRest -> "休息"
    row.day.isHandoverExempt -> "交接班日"
    row.day.kind == ShiftDayKind.HANDOVER -> "交接班"
    row.day.kind == ShiftDayKind.WORK_FIRST -> "接班日"
    else -> "整班"
}

/** 一天一条：日期列 | 班次灯 + 说明 + 到位/到场 | 班车与下班。休息日无底、无边。 */
@Composable
private fun ShiftStrip(row: ShiftCalendarRow) {
    val c = AirShiftTokens.colors
    val rest = row.day.kind.isRest || !row.day.attends
    val shape = RoundedCornerShape(AirShiftRadius.Strip)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (row.isToday) Modifier.currentCardShadow(shape) else Modifier)
            .clip(shape)
            .then(if (rest) Modifier else Modifier.background(c.strip))
            .then(if (rest || row.isToday) Modifier else Modifier.border(1.dp, c.rule, shape))
            .height(IntrinsicSize.Min),
    ) {
        HolderBar(color = holderColor(row))
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateColumn(row = row, dimmed = rest)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ShiftLine(row = row, dimmed = rest)
                if (row.day.attends) {
                    BusDetail(row.bus)
                }
            }
            if (row.day.attends) {
                Spacer(Modifier.width(10.dp))
                BusColumn(row)
            }
        }
    }
}

@Composable
private fun holderColor(row: ShiftCalendarRow): androidx.compose.ui.graphics.Color {
    val c = AirShiftTokens.colors
    return when {
        row.isToday -> c.board
        row.day.kind.isRest || !row.day.attends -> c.ruleStrong
        row.day.kind == ShiftDayKind.HANDOVER -> c.estimate
        else -> c.departure
    }
}

@Composable
private fun DateColumn(row: ShiftCalendarRow, dimmed: Boolean) {
    val c = AirShiftTokens.colors
    Column(modifier = Modifier.width(54.dp)) {
        Text(
            "${row.day.date.monthValue}/${row.day.date.dayOfMonth}",
            style = StripTime.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            color = if (dimmed) c.hint else c.ink,
        )
        Text(
            "周${WEEKDAYS[row.day.date.dayOfWeek.value - 1]}",
            style = MaterialTheme.typography.labelSmall,
            color = c.hint,
        )
    }
}

/** 班次灯 + 日型说明 + 今天灯。 */
@Composable
private fun ShiftLine(row: ShiftCalendarRow, dimmed: Boolean) {
    val c = AirShiftTokens.colors
    val handover = row.day.kind == ShiftDayKind.HANDOVER
    Row(verticalAlignment = Alignment.CenterVertically) {
        row.day.slot?.takeIf { row.day.attends }?.let { slot ->
            StatusLamp(text = slot.label, kind = if (handover) LampKind.Estimate else LampKind.Arrival)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            if (row.day.attends && row.day.slot != null && handover) "只上上午" else dayKindText(row),
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.bodyMedium,
            color = if (dimmed) c.hint else c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (row.isToday) {
            Spacer(Modifier.width(8.dp))
            StatusLamp(text = "今天", kind = LampKind.Neutral)
        }
    }
}

/** 右列：班车时刻（晚于到位时琥珀）+ 下班/交班时间。 */
@Composable
private fun BusColumn(row: ShiftCalendarRow) {
    val c = AirShiftTokens.colors
    val bus = row.bus
    Column(horizontalAlignment = Alignment.End) {
        Text("班车", style = MaterialTheme.typography.labelSmall, color = c.hint)
        Text(
            bus?.departureText() ?: "—",
            style = StripTime.copy(fontSize = 18.sp),
            color = when {
                bus == null -> c.hint
                bus.spareMinutes < 0 -> c.estimate
                else -> c.ink
            },
        )
        row.offDutyMinutes?.let { offDuty ->
            val label = if (row.day.kind == ShiftDayKind.HANDOVER) "交班" else "下班"
            Text(
                "$label ${ShiftClock.format(offDuty)}",
                style = MaterialTheme.typography.labelSmall,
                color = c.hint,
                maxLines = 1,
            )
        }
    }
}

/** 到位 / 到场 / 富余；到场晚于到位时用琥珀灯提示提前一班，而不是混在灰字里。 */
@Composable
private fun BusDetail(bus: BusRecommendation?) {
    val c = AirShiftTokens.colors
    if (bus == null) {
        Text("没有合适班车，需自行安排到场", style = MaterialTheme.typography.bodySmall, color = c.estimate)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildString {
                append("到位 ${ShiftClock.format(bus.reportByMinutes)}")
                append(" · 到场 ${ShiftClock.format(bus.arriveAtMinutes)}")
                if (bus.spareMinutes >= 0) append(" · 富余 ${bus.spareMinutes} 分")
                if (bus.isExtraHandoverBus) append(" · 加班车")
                append(if (bus.source == ShiftEstimateSource.ROSTER) " · 按当日排班" else " · 预估")
            },
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = NumericSmall.fontFamily,
                fontFeatureSettings = "tnum",
            ),
            color = c.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (bus.spareMinutes < 0) {
            Spacer(Modifier.width(6.dp))
            StatusLamp(text = "晚 ${-bus.spareMinutes} 分 · 建议提前一班", kind = LampKind.Estimate)
        }
    }
}

private fun BusRecommendation.departureText(): String = "%02d:%02d".format(departure.hour, departure.minute)

private fun dayKindText(row: ShiftCalendarRow): String = when {
    row.day.kind.isRest -> "休息"
    row.day.isHandoverExempt ->
        "交接班日不到岗（${row.day.slotInheritedFrom?.let { "${it.monthValue}月${it.dayOfMonth}日" }.orEmpty()}" +
            "排${row.day.slot?.label.orEmpty()}，干到次日凌晨）"
    row.day.kind == ShiftDayKind.HANDOVER -> "交接班 · 只上上午"
    row.day.kind == ShiftDayKind.WORK_FIRST -> "接班日 · 上午由上一班交出"
    else -> "整班"
}

private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")
